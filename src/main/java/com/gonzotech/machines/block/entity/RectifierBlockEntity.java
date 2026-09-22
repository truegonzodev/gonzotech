package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.ResourceBuffer;
import com.gonzotech.machines.energy.Sinks.DistillateSink;
import com.gonzotech.machines.energy.Sinks.GthSink;
import com.gonzotech.machines.energy.Sinks.GtuSink;
import com.gonzotech.machines.energy.Sinks.RectificateSink;
import com.gonzotech.machines.menu.RectifierMenu;
import com.gonzotech.machines.network.PipeRouting;
import com.gonzotech.machines.network.PipeType;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Блок-сущность Ректификатора (Эпоха III, «Открытие 3»).
 * <ul>
 *   <li>Бак дистиллята: 10 012 mB.</li>
 *   <li>Бак ректификата (чистого спирта): 7 200 mB.</li>
 *   <li>Буфер GTH: 6 912; буфер GTU: 8 490.</li>
 *   <li>Ректификация: 2 mB дистиллята → 1 mB ректификата за 6 GTH/t + 9 GTU/t.</li>
 *   <li>Автовыдача ректификата: до 688 mB/t.</li>
 * </ul>
 */
public class RectifierBlockEntity extends BaseMachineBlockEntity
    implements GthSink, GtuSink, DistillateSink, WorldlyContainer {

    public static final int DISTILLATE_CAPACITY = 10_012;
    public static final int RECTIFICATE_CAPACITY = 7_200;
    public static final int GTH_CAPACITY = 6_912;
    public static final int GTU_CAPACITY = 8_490;

    public static final int PROCESS_DISTILLATE_COST = 2;
    public static final int PROCESS_RECTIFICATE_YIELD = 1;
    public static final int PROCESS_GTH_COST = 6;
    public static final int PROCESS_GTU_COST = 9;
    public static final int MAX_DRAIN_PER_TICK = 688;

    private static final int PACKET_BASE = 10_000;
    private static final int[] NO_SLOTS = new int[0];

    private long currentGthMilli = 0;
    private long currentGtuMilli = 0;
    private final ResourceBuffer distillate = new ResourceBuffer(DISTILLATE_CAPACITY);
    private final ResourceBuffer rectificate = new ResourceBuffer(RECTIFICATE_CAPACITY);

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return getData(i);
        }

        @Override
        public void set(int i, int value) {
        }

        @Override
        public int getCount() {
            return RectifierMenu.DATA_COUNT;
        }
    };

    public RectifierBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.THIRD_RECTIFIER.get(), pos, state, 0);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.gonzotech.third_rectifier");
    }

    public ContainerData data() {
        return data;
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new RectifierMenu(id, inv, this, data);
    }

    public int getCurrentGth() {
        return (int) (currentGthMilli / MachineDefs.MILLI);
    }

    public int getMaxGth() {
        return GTH_CAPACITY;
    }

    public int getCurrentGtu() {
        return (int) (currentGtuMilli / MachineDefs.MILLI);
    }

    public int getMaxGtu() {
        return GTU_CAPACITY;
    }

    public ResourceBuffer getDistillate() {
        return distillate;
    }

    public ResourceBuffer getRectificate() {
        return rectificate;
    }

    // ─────────────────────────── Sinks ───────────────────────────

    @Override
    public long receiveGth(long amountMilli, boolean simulate) {
        long maxMilli = (long) GTH_CAPACITY * MachineDefs.MILLI;
        long space = Math.max(0, maxMilli - currentGthMilli);
        long accepted = Math.min(amountMilli, space);
        if (!simulate && accepted > 0) {
            currentGthMilli += accepted;
            setChanged();
        }
        return accepted;
    }

    @Override
    public long receiveGtu(long amountMilli, boolean simulate) {
        long maxMilli = (long) GTU_CAPACITY * MachineDefs.MILLI;
        long space = Math.max(0, maxMilli - currentGtuMilli);
        long accepted = Math.min(amountMilli, space);
        if (!simulate && accepted > 0) {
            currentGtuMilli += accepted;
            setChanged();
        }
        return accepted;
    }

    @Override
    public long receiveDistillate(long amount, boolean simulate) {
        int accepted = distillate.receive(amount, simulate);
        if (!simulate && accepted > 0) {
            setChanged();
        }
        return accepted;
    }

    // ─────────────────────────── Тик ───────────────────────────

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide()) return;
        boolean changed = false;

        // 1. Ректификация
        long gthCostMilli = (long) PROCESS_GTH_COST * MachineDefs.MILLI;
        long gtuCostMilli = (long) PROCESS_GTU_COST * MachineDefs.MILLI;

        if (distillate.amount() >= PROCESS_DISTILLATE_COST
            && rectificate.space() >= PROCESS_RECTIFICATE_YIELD
            && currentGthMilli >= gthCostMilli
            && currentGtuMilli >= gtuCostMilli) {

            currentGthMilli -= gthCostMilli;
            currentGtuMilli -= gtuCostMilli;
            distillate.extract(PROCESS_DISTILLATE_COST, false);
            rectificate.receive(PROCESS_RECTIFICATE_YIELD, false);
            changed = true;
        }

        // 2. Автовыдача ректификата до 688 mB/t
        if (rectificate.amount() > 0) {
            long budget = Math.min((long) MAX_DRAIN_PER_TICK, (long) rectificate.amount());
            long moved = PipeRouting.drain(level, pos, PipeType.RECTIFICATE, budget, level.getGameTime(), (be, p) -> {
                if (be instanceof RectifierBlockEntity) return null;
                if (be instanceof RectificateSink sink) return sink::receiveRectificate;
                return null;
            });
            if (moved > 0) {
                rectificate.extract(moved, false);
                changed = true;
            }
        }

        if (changed) {
            setChanged();
        }
    }

    // ─────────────────────────── ContainerData ───────────────────────────

    public int getData(int index) {
        int gth = getCurrentGth();
        int gtu = getCurrentGtu();
        return switch (index) {
            case 0 -> gth % PACKET_BASE;
            case 1 -> gth / PACKET_BASE;
            case 2 -> gtu % PACKET_BASE;
            case 3 -> gtu / PACKET_BASE;
            case 4 -> distillate.amount();
            case 5 -> rectificate.amount();
            default -> 0;
        };
    }

    // ─────────────────────────── WorldlyContainer ───────────────────────────

    @Override
    public int[] getSlotsForFace(Direction side) {
        return NO_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction dir) {
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
        return false;
    }

    // ─────────────────────────── Сериализация ───────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("GthMilli", currentGthMilli);
        tag.putLong("GtuMilli", currentGtuMilli);
        distillate.save(tag, "Distillate");
        rectificate.save(tag, "Rectificate");
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        currentGthMilli = tag.getLong("GthMilli");
        currentGtuMilli = tag.getLong("GtuMilli");
        distillate.load(tag, "Distillate");
        rectificate.load(tag, "Rectificate");
    }
}
