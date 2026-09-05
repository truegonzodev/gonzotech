package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.ResourceBuffer;
import com.gonzotech.machines.energy.Sinks.GtuSink;
import com.gonzotech.machines.energy.Sinks.WaterSink;
import com.gonzotech.machines.menu.PumpMenu;
import com.gonzotech.machines.network.PipeRouting;
import com.gonzotech.machines.network.PipeType;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * Помпа: «тупая» водокачка паровой эры. Полный реверс котла по слотам —
 * <b>вход</b> — пустая тара (ведро / стеклянный пузырёк), <b>выход</b> — та же
 * тара, но наполненная водой из шкалы.
 * <p>
 * Слот 0 ({@link #SLOT_CONTAINER_IN}) — только пустое ведро/пузырёк (игрок
 * кладёт, автоматизация — тоже). Слот 1 ({@link #SLOT_FILLED_OUT}) — только
 * вывод: ни игрок, ни воронка/предметные трубы туда ничего положить не могут
 * (кладёт лишь сама помпа).
 * <p>
 * Рецепты забора:
 * <ul>
 *   <li>пустое ведро + {@link MachineDefs#PUMP_BUCKET_WATER_COST} mB из шкалы →
 *       ведро воды в выходной слот;</li>
 *   <li>пустой стеклянный пузырёк → пузырёк воды, БЕСПЛАТНО (из шкалы ничего не
 *       списывается), нужно лишь чтобы в шкале было ≥ 1 mB.</li>
 * </ul>
 * <p>
 * Питание: пассивный расход {@link MachineDefs#PUMP_GTU_MILLI_PER_TICK} milli-GTU
 * за тик. Есть GTU в шкале — помпа «запитана» и работает; нет — стоит,
 * НЕЗАВИСИМО от наличия воды рядом (это правильно для тупой техники). GTU
 * дотянет провод (реализует {@link GtuSink}).
 * <p>
 * Всасывание: раз в {@link MachineDefs#PUMP_SUCK_INTERVAL} тиков помпа, если
 * запитана и есть место в шкале, сканирует куб 3×3×3 вокруг себя СВЕРХУ ВНИЗ,
 * уничтожает первый найденный источник воды и разово получает
 * {@link MachineDefs#PUMP_WATER_PER_SOURCE} mB.
 * <p>
 * Слив: воду из шкалы помпа отдаёт напрямую соседям-{@link WaterSink} (котлу) —
 * до {@link MachineDefs#PUMP_WATER_OUTPUT} mB/т, равномерно (жидкостных труб пока
 * нет; когда появятся — слив пойдёт по ним через отдельный маршрутизатор).
 */
public class PumpBlockEntity extends BaseMachineBlockEntity implements GtuSink, WaterSink, WorldlyContainer {

    public static final int SLOT_CONTAINER_IN = 0;
    public static final int SLOT_FILLED_OUT = 1;

    private static final int[] SLOTS_ALL = { SLOT_CONTAINER_IN, SLOT_FILLED_OUT };

    private final ResourceBuffer gtu = new ResourceBuffer(MachineDefs.PUMP_GTU_CAPACITY);
    private final ResourceBuffer water = new ResourceBuffer(MachineDefs.PUMP_WATER_CAPACITY);

    /** Дробный аккумулятор расхода GTU (milli-GTU), серверный, не синкается. */
    private int gtuAccum;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> gtu.amount();
                case 1 -> water.amount();
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {
            switch (i) {
                case 0 -> gtu.set(v);
                case 1 -> water.set(v);
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    public PumpBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PUMP.get(), pos, state, 2);
    }

    public ResourceBuffer gtuBuffer() {
        return gtu;
    }

    public ResourceBuffer waterBuffer() {
        return water;
    }

    public ContainerData data() {
        return data;
    }

    // ─────────────────────────── Sinks ───────────────────────────

    @Override
    public int receiveGtu(int amount, boolean simulate) {
        return gtu.receive(Math.min(amount, MachineDefs.PUMP_GTU_INTAKE), simulate);
    }

    @Override
    public int receiveWater(int amount, boolean simulate) {
        // Помпа сама источник воды, но реализует WaterSink на случай приёма извне;
        // сейчас приём воды помпой не используется.
        return 0;
    }

    // ─────────────────────────── тик (сервер) ───────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, PumpBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        boolean changed = false;

        // 1. Наполнить тару из шкалы (ведро / пузырёк) — не зависит от питания.
        if (be.fillContainer()) {
            changed = true;
        }

        // 2. Питание: пассивный расход GTU. Хватает — помпа «запитана» и работает.
        boolean powered = false;
        be.gtuAccum += MachineDefs.PUMP_GTU_MILLI_PER_TICK;
        int need = be.gtuAccum / 1000;
        if (be.gtu.has(need)) {
            if (need > 0) be.gtu.extract(need, false);
            be.gtuAccum -= need * 1000;
            powered = true;
            changed = true;
        } else {
            // Нет энергии на этот тик — откатываем аккумулятор, стоим.
            be.gtuAccum -= MachineDefs.PUMP_GTU_MILLI_PER_TICK;
        }

        // 3. Всасывание воды: только если запитана, есть место и подошёл таймаут.
        if (powered
            && be.water.space() >= MachineDefs.PUMP_WATER_PER_SOURCE
            && server.getGameTime() % MachineDefs.PUMP_SUCK_INTERVAL == 0
            && be.suckOneSource(server, pos)) {
            changed = true;
        }

        // 4. Слив воды напрямую соседям-приёмникам (котлу), равномерно.
        if (be.pushWater(server, pos)) {
            changed = true;
        }

        if (changed) {
            be.setChanged();
        }
    }

    /**
     * Скан куба 3×3×3 вокруг помпы СВЕРХУ ВНИЗ; первый найденный источник воды
     * уничтожается, помпа получает {@link MachineDefs#PUMP_WATER_PER_SOURCE} mB.
     *
     * @return {@code true}, если источник был найден и всосан
     */
    private boolean suckOneSource(ServerLevel server, BlockPos pos) {
        for (int dy = 1; dy >= -1; dy--) {          // верхний слой первым
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue; // саму помпу пропускаем
                    BlockPos p = pos.offset(dx, dy, dz);
                    if (isWaterSource(server, p)) {
                        removeWaterSource(server, p);
                        water.receive(MachineDefs.PUMP_WATER_PER_SOURCE, false);
                        server.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.5F, 1.0F);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Является ли блок в позиции ИСТОЧНИКОМ воды (не текучей). */
    private static boolean isWaterSource(ServerLevel server, BlockPos p) {
        FluidState fluid = server.getFluidState(p);
        return fluid.getType() == Fluids.WATER && fluid.isSource();
    }

    /**
     * Убрать источник воды. Для водосодержащих блоков (waterlogged) снимаем только
     * воду; для чистой воды ставим воздух.
     */
    private static void removeWaterSource(ServerLevel server, BlockPos p) {
        BlockState st = server.getBlockState(p);
        if (st.hasProperty(BlockStateProperties.WATERLOGGED)
            && st.getValue(BlockStateProperties.WATERLOGGED)) {
            // waterlogged-блок (например плита/забор): выкачиваем только воду, блок оставляем.
            server.setBlock(p, st.setValue(BlockStateProperties.WATERLOGGED, false), 3);
        } else {
            // чистый источник воды (или водный блок без waterlogged, напр. водоросли) → воздух.
            server.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    /**
     * Наполнить тару в слоте входа из шкалы воды, результат — в слот вывода.
     * Ведро: −{@link MachineDefs#PUMP_BUCKET_WATER_COST} mB → ведро воды.
     * Пузырёк: бесплатно (нужно лишь ≥ 1 mB в шкале) → пузырёк воды.
     */
    private boolean fillContainer() {
        ItemStack in = items.get(SLOT_CONTAINER_IN);
        if (in.isEmpty()) return false;

        if (in.is(Items.BUCKET)) {
            if (water.amount() < MachineDefs.PUMP_BUCKET_WATER_COST) return false;
            if (!tryOutput(new ItemStack(Items.WATER_BUCKET))) return false;
            water.extract(MachineDefs.PUMP_BUCKET_WATER_COST, false);
            in.shrink(1);
            return true;
        }

        if (in.is(Items.GLASS_BOTTLE)) {
            if (water.amount() < 1) return false;
            if (!tryOutput(waterBottle())) return false;
            // Бесплатно: из шкалы ничего не списываем.
            in.shrink(1);
            return true;
        }

        return false;
    }

    /** Попытаться положить наполненную тару в выходной слот (стакая при возможности). */
    private boolean tryOutput(ItemStack filled) {
        ItemStack out = items.get(SLOT_FILLED_OUT);
        if (out.isEmpty()) {
            items.set(SLOT_FILLED_OUT, filled);
            return true;
        }
        if (ItemStack.isSameItemSameComponents(out, filled) && out.getCount() < out.getMaxStackSize()) {
            out.grow(1);
            return true;
        }
        return false;
    }

    /** Стеклянный пузырёк, наполненный водой (minecraft:potion → water). */
    private static ItemStack waterBottle() {
        ItemStack bottle = new ItemStack(Items.POTION);
        bottle.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS, new PotionContents(Potions.WATER));
        return bottle;
    }

    /**
     * Слить воду приёмникам: прямым соседям-{@link WaterSink} (котлу вплотную)
     * ИЛИ дальше по водным трубам ({@link PipeType#WATER}) через
     * {@link PipeRouting#drain} — равномерно. Без труб рядом работает как прямая
     * передача соседу (поведение до появления жидкостных труб сохранено).
     */
    private boolean pushWater(Level level, BlockPos pos) {
        if (water.isEmpty()) return false;
        int budget = Math.min(MachineDefs.PUMP_WATER_OUTPUT, water.amount());
        int moved = PipeRouting.drain(level, pos, PipeType.WATER, budget, level.getGameTime(), (be, p) -> {
            if (be instanceof PumpBlockEntity) return null;
            if (be instanceof WaterSink sink) return sink::receiveWater;
            return null;
        });
        if (moved > 0) {
            water.extract(moved, false);
            return true;
        }
        return false;
    }

    // ─────────────────────────── NBT ───────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        gtu.save(tag, "Gtu");
        water.save(tag, "Water");
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        gtu.load(tag, "Gtu");
        water.load(tag, "Water");
    }

    // ─────────────────────── Container / WorldlyContainer ───────────────────────
    //
    // Слот вывода (SLOT_FILLED_OUT) — ТОЛЬКО на вывод: ни игрок (см. OutputOnlySlot
    // в меню), ни воронка/предметные трубы туда ничего положить не могут. В слот
    // входа (SLOT_CONTAINER_IN) можно класть только пустое ведро/пузырёк.

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == SLOT_FILLED_OUT) return false;
        if (slot == SLOT_CONTAINER_IN) return isEmptyContainer(stack);
        return false;
    }

    /** Пустая тара, которую помпа умеет наполнять. */
    public static boolean isEmptyContainer(ItemStack stack) {
        return stack.is(Items.BUCKET) || stack.is(Items.GLASS_BOTTLE);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return SLOTS_ALL;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        // Автоматизация (воронка/предметные трубы) забирает ТОЛЬКО наполненную тару
        // из выходного слота — не пустую тару из входа.
        return slot == SLOT_FILLED_OUT;
    }

    // ─────────────────────────── Menu ───────────────────────────

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.gonzotech.pump");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new PumpMenu(id, inv, this, data);
    }
}
