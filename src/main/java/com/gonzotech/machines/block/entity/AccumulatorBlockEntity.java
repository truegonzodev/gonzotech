package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.energy.ComparatorOutput;
import com.gonzotech.machines.energy.GtBuffer;
import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.Sinks.GtuSink;
import com.gonzotech.machines.menu.AccumulatorMenu;
import com.gonzotech.machines.network.PipeRouting;
import com.gonzotech.machines.network.PipeType;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Энергохранилище (аккумулятор GTU): чисто пассивный буфер «электричества».
 * <p>
 * Предметных слотов нет. Реализует {@link GtuSink} — генераторы (стирлинг и
 * будущие) свободно заливают в него излишек GTU, когда потребители сыты. Сам же
 * каждый тик РАЗДАЁТ запас потребителям-{@link GtuSink} (электропечь, помпа…) —
 * напрямую соседям или по проводам через {@link PipeRouting#drain}. Так в
 * перенасыщенном замкнутом контуре генераторы не встают «в потолок», а
 * потребители не голодают в простое генераторов: аккумулятор сглаживает пики.
 * <p>
 * Аккумулятор-аккумулятор: энергия перетекает от более полного к менее полному
 * (по абсолютному запасу mGTU), пока запасы не уравняются. Так два стоящих
 * вплотную (или соединённых проводами) хранилища ведут себя как «узел + буфер»:
 * заряд растекается по всем накопителям сети. Пинг-понга нет — отдаём соседу,
 * только если у него СТРОГО меньше нашего, поэтому при равенстве переливание
 * прекращается. Буфер на {@link GtBuffer} (BigInteger, милли), ёмкость
 * {@link MachineDefs#ACCUMULATOR_GTU_CAPACITY}.
 */
public class AccumulatorBlockEntity extends BaseMachineBlockEntity implements GtuSink {

    private final GtBuffer gtu = new GtBuffer((long) MachineDefs.ACCUMULATOR_GTU_CAPACITY);

    /** Последнее опубликованное значение компаратора; не сохраняется, т.к. вычисляется из буфера. */
    private int lastComparatorOutput;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                // GTU — в целых единицах (÷1000, ContainerData = short); капа 10840 влезает.
                case 0 -> gtu.amountUnitsInt();
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {
            switch (i) {
                case 0 -> gtu.set(MachineDefs.toMilli(v));
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return 1;
        }
    };

    public AccumulatorBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.ACCUMULATOR.get(), pos, state);
    }

    /**
     * Внутренний конструктор для функционально идентичной машины другого
     * открытия: состояние и баланс остаются общими, тип BE — свой.
     */
    public AccumulatorBlockEntity(BlockEntityType<?> blockEntityType, BlockPos pos, BlockState state) {
        super(blockEntityType, pos, state, 0);
    }

    public GtBuffer gtuBuffer() {
        return gtu;
    }

    /** Аналоговый выход по заполненности внутреннего GTU-буфера. */
    public int comparatorOutput() {
        return ComparatorOutput.from(gtu);
    }

    /** Уведомляет компараторы только при пересечении очередной ступени 0..15. */
    private void updateComparatorOutput() {
        int next = comparatorOutput();
        if (next == lastComparatorOutput) return;
        lastComparatorOutput = next;
        if (level != null && !level.isClientSide()) {
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        updateComparatorOutput();
    }

    public ContainerData data() {
        return data;
    }

    // ─────────────────────────── GtuSink ───────────────────────────

    @Override
    public long receiveGtu(long amount, boolean simulate) {
        long accepted = gtu.receive(Math.min(amount, (long) MachineDefs.ACCUMULATOR_GTU_INTAKE), simulate);
        if (!simulate && accepted > 0) {
            setChanged();
            updateComparatorOutput();
        }
        return accepted;
    }

    // ─────────────────────────── тик (сервер) ───────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, AccumulatorBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        boolean changed = false;

        // Паразитная потеря GTU — всегда, но только если запас есть (без «долга»).
        if (!be.gtu.isEmpty()) {
            be.gtu.extract(MachineDefs.ACCUMULATOR_GTU_LOSS, false);
            changed = true;
        }

        // Раздать запас потребителям. Приём идёт пассивно через receiveGtu, когда
        // генераторы сами сливают в нас.
        if (be.pushGtu(server, pos)) {
            changed = true;
        }

        if (changed) {
            be.setChanged();
            be.updateComparatorOutput();
        }
    }

    /**
     * Раздать GTU потребителям-{@link GtuSink} равномерно: прямым соседям ИЛИ по
     * проводам ({@link PipeType#WIRE}).
     * <p>
     * Другому аккумулятору отдаём, ТОЛЬКО если у него запас меньше нашего, и не
     * более половины разницы за тик — так заряд растекается между хранилищами и
     * останавливается при равенстве (без пинг-понга). Обычные потребители берут
     * без этого ограничения.
     */
    private boolean pushGtu(Level level, BlockPos pos) {
        if (gtu.isEmpty()) return false;
        // Запас на начало тика — база для расчёта «половины разницы» с соседями.
        final long myStored = gtu.amountAsLong();
        long budget = Math.min((long) MachineDefs.ACCUMULATOR_GTU_OUTPUT, myStored);
        long moved = PipeRouting.drain(level, pos, PipeType.WIRE, budget, level.getGameTime(), (target, p) -> {
            if (target instanceof AccumulatorBlockEntity other) {
                long diff = myStored - other.gtu.amountAsLong();
                if (diff <= 0) return null;                 // у соседа не меньше — не льём
                long cap = diff / 2;                          // выравнивание без перелёта
                if (cap <= 0) return null;
                return (amount, simulate) -> other.receiveGtu(Math.min(amount, cap), simulate);
            }
            if (target instanceof GtuSink sink) return sink::receiveGtu;
            return null;
        });
        if (moved > 0) {
            gtu.extract(moved, false);
            return true;
        }
        return false;
    }

    // ─────────────────────────── NBT ───────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        gtu.save(tag, "Gtu");
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        gtu.load(tag, "Gtu");
    }

    // ─────────────────────────── Menu ───────────────────────────

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new AccumulatorMenu(id, inv, this, data);
    }
}
