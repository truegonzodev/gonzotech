package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.energy.GtBuffer;
import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.ResourceBuffer;
import com.gonzotech.machines.energy.Sinks.GtuSink;
import com.gonzotech.machines.energy.Sinks.SteamSink;
import com.gonzotech.machines.energy.Sinks.WaterSink;
import com.gonzotech.machines.network.PipeRouting;
import com.gonzotech.machines.network.PipeType;
import com.gonzotech.machines.menu.StirlingMenu;
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
import net.minecraft.world.level.block.state.BlockState;

/**
 * Генератор Стирлинга: {@code 40 пара → 2 GTU + вода}. Пар превращается в GTU, а
 * часть воды возвращается В КОТЁЛ. БЕЗ конденсаторов возврат мал
 * ({@link MachineDefs#STIRLING_WATER_BASE_PER_TICK}/t), поэтому цикл сам себя не
 * держит; каждый примыкающий {@link CondenserBlockEntity} добавляет
 * +{@link MachineDefs#STIRLING_WATER_PER_CONDENSER}/t. Достаточно конденсаторов —
 * и цикл замыкается без долива воды.
 * <p>
 * Крутится от пара из своего буфера — ОТКУДА бы он ни пришёл (труба дотянет пар
 * от любого котла; примыкающий сосед не требуется). Предметных слотов нет.
 * <p>
 * Паразитика: пар конденсируется в воду 1:1 ({@link MachineDefs#STIRLING_STEAM_LOSS}/t)
 * на возврат в котёл — ТОЛЬКО если пар есть (иначе при нескольких котлах-соседях
 * был бы дюп воды).
 */
public class StirlingBlockEntity extends BaseMachineBlockEntity implements SteamSink, GtuSink {

    private final ResourceBuffer steam = new ResourceBuffer(MachineDefs.STIRLING_STEAM_CAPACITY);
    // GTU — в GtBuffer (BigInteger, милли); пар/вода остаются в mB (int).
    private final GtBuffer gtu = new GtBuffer((long) MachineDefs.STIRLING_GTU_CAPACITY);
    /** Внутренний буфер воды на возврат в котёл (в GUI не показывается). */
    private final ResourceBuffer water = new ResourceBuffer(MachineDefs.STIRLING_WATER_CAPACITY);

    /** Идёт ли сейчас генерация — для анимации/индикатора. */
    private boolean running;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                // Пар/вода — в mB; GTU — в целых единицах (÷1000, ContainerData = short).
                case 0 -> steam.amount();
                case 1 -> gtu.amountUnitsInt();
                case 2 -> running ? 1 : 0;
                case 3 -> water.amount();   // конденсат (возвратная вода), mB
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {
            switch (i) {
                case 0 -> steam.set(v);
                case 1 -> gtu.set(MachineDefs.toMilli(v));
                case 2 -> running = v != 0;
                case 3 -> water.set(v);
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    public StirlingBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STIRLING.get(), pos, state, 0);
    }

    public ResourceBuffer steamBuffer() {
        return steam;
    }

    public GtBuffer gtuBuffer() {
        return gtu;
    }

    public ContainerData data() {
        return data;
    }

    public boolean running() {
        return running;
    }

    // ─────────────────────────── Sinks ───────────────────────────

    @Override
    public long receiveSteam(long amount, boolean simulate) {
        return steam.receive(Math.min(amount, (long) MachineDefs.STIRLING_STEAM_INTAKE), simulate);
    }

    @Override
    public long receiveGtu(long amount, boolean simulate) {
        // Стирлинг сам источник GTU; приём извне не используется.
        return 0;
    }

    // ─────────────────────────── тик (сервер) ───────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, StirlingBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        boolean changed = false;

        // Паразитика: пар «стынет» в воду 1:1, ТОЛЬКО если пар есть.
        //    Вода конденсируется из своего же пара — ничего не создаётся из
        //    воздуха, поэтому дюпа воды при нескольких котлах-соседях нет.
        if (be.steam.amount() > 0) {
            int cooled = be.steam.extract(MachineDefs.STIRLING_STEAM_LOSS, false);
            be.water.receive(cooled, false);
            changed = true;
        }

        // Основной цикл: 40 пара → 2 GTU + вода на возврат.
        // Водоотдача = база (6) + 5 за каждый примыкающий конденсатор.
        int condensers = countCondensers(server, pos);
        int waterOut = MachineDefs.stirlingWaterPerTick(condensers);
        // Развязано от «соседа»-котла: стирлинг крутится от пара, откуда бы он ни
        // пришёл (труба дотянет пар от любого котла). Псевдомногоблок оставлен
        // только для топка+котёл.
        //
        // ВАЖНО (гибрид, железное правило «отток не должен стопорить»): генератор
        // НЕ встаёт из-за переполнения буфера возвратной воды. Пар потребляется
        // всегда (если есть) и место под GTU; лишняя возвратная вода, которой
        // некуда деться, просто теряется (receive принимает лишь сколько влезло).
        // Но чем полнее буфер конденсата — тем ниже КПД выработки GTU (100%→90%),
        // это стимул провести обратный водный контур в котёл.
        boolean run = false;
        if (be.steam.has(MachineDefs.STIRLING_STEAM_PER_TICK)
            && be.gtu.receive(MachineDefs.STIRLING_GTU_PER_TICK, true) >= MachineDefs.STIRLING_GTU_PER_TICK) {
            // КПД считаем по уровню конденсата ДО добавления воды этого тика.
            // gtuOut ≤ STIRLING_GTU_PER_TICK (КПД ≤ 100%), поэтому место под него
            // гарантированно есть — GTU не теряется. Пауза при полном буфере GTU —
            // нормальный backpressure главного продукта, к воде отношения не имеет.
            int eff = MachineDefs.stirlingEfficiencyPermille(be.water.amount());
            long gtuOut = (long) MachineDefs.STIRLING_GTU_PER_TICK * eff / 1000;
            be.steam.extract(MachineDefs.STIRLING_STEAM_PER_TICK, false);
            be.gtu.receive(gtuOut, false);
            // Возвратная вода: излишек сверх ёмкости теряется (receive клампит).
            be.water.receive(waterOut, false);
            run = true;
            changed = true;
        }
        if (run != be.running) {
            be.running = run;
            changed = true;
        }

        // Возврат воды в котёл (равномерно).
        if (be.pushWater(server, pos)) {
            changed = true;
        }

        // Раздать GTU соседям равномерно.
        if (be.pushGtu(server, pos)) {
            changed = true;
        }

        if (changed) {
            be.setChanged();
        }
    }

    /** Сколько конденсаторов примыкает к 6 граням. */
    private static int countCondensers(Level level, BlockPos pos) {
        int n = 0;
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            if (level.getBlockEntity(pos.relative(dir)) instanceof CondenserBlockEntity) {
                n++;
            }
        }
        return n;
    }

    /**
     * Вернуть воду котлам: прямым соседям-{@link WaterSink} ИЛИ дальше по водным
     * трубам ({@link PipeType#WATER}) через {@link PipeRouting#drain} —
     * равномерно. Без труб рядом = прямая передача соседу (прежнее поведение).
     */
    private boolean pushWater(Level level, BlockPos pos) {
        if (water.isEmpty()) return false;
        long budget = Math.min((long) MachineDefs.STIRLING_WATER_OUTPUT, water.amount());
        long moved = PipeRouting.drain(level, pos, PipeType.WATER, budget, level.getGameTime(), (be, p) -> {
            if (be instanceof StirlingBlockEntity) return null;
            if (be instanceof WaterSink sink) return sink::receiveWater;
            return null;
        });
        if (moved > 0) {
            water.extract(moved, false);
            return true;
        }
        return false;
    }

    /** Равномерно раздать GTU соседям-приёмникам (электропечам). */
    private boolean pushGtu(Level level, BlockPos pos) {
        if (gtu.isEmpty()) return false;
        long budget = Math.min((long) MachineDefs.STIRLING_GTU_OUTPUT, gtu.amountAsLong());
        // Слив GTU: прямым соседям-электропечам ИЛИ через провода дальше по цепи.
        long moved = PipeRouting.drain(level, pos, PipeType.WIRE, budget, level.getGameTime(), (be, p) -> {
            if (be instanceof StirlingBlockEntity) return null;
            if (be instanceof GtuSink sink) return sink::receiveGtu;
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
        steam.save(tag, "Steam");
        gtu.save(tag, "Gtu");
        water.save(tag, "Water");
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        steam.load(tag, "Steam");
        gtu.load(tag, "Gtu");
        water.load(tag, "Water");
    }

    // ─────────────────────────── Menu ───────────────────────────

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.gonzotech.stirling_generator");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new StirlingMenu(id, inv, this, data);
    }
}
