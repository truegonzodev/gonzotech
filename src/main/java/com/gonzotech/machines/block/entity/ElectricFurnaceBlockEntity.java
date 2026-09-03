package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.ResourceBuffer;
import com.gonzotech.machines.energy.Sinks;
import com.gonzotech.machines.energy.Sinks.GtuSink;
import com.gonzotech.machines.menu.ElectricFurnaceMenu;
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
 * Электрическая печь — плавит ресурсы, питаясь GTU от соседнего генератора
 * Стирлинга. Слота под топливо НЕТ: только вход (0) и выход (1).
 * <p>
 * Работает ТОЛЬКО если к одной из граней примыкает генератор Стирлинга
 * ({@link StirlingBlockEntity}).
 */
public class ElectricFurnaceBlockEntity extends BaseMachineBlockEntity implements GtuSink {

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;

    private final ResourceBuffer gtu = new ResourceBuffer(MachineDefs.GTU_CAPACITY);

    private boolean chainOk;
    private int cookProgress;
    private int cookTotal;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> gtu.amount() & 0xFFFF;
                case 1 -> (gtu.amount() >> 16) & 0xFFFF;
                case 2 -> cookProgress;
                case 3 -> cookTotal;
                case 4 -> chainOk ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {
            switch (i) {
                case 0 -> gtu.set((gtu.amount() & ~0xFFFF) | (v & 0xFFFF));
                case 1 -> gtu.set((gtu.amount() & 0xFFFF) | ((v & 0xFFFF) << 16));
                case 2 -> cookProgress = v;
                case 3 -> cookTotal = v;
                case 4 -> chainOk = v != 0;
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return 5;
        }
    };

    public ElectricFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ELECTRIC_FURNACE.get(), pos, state, 2);
    }

    public ResourceBuffer gtuBuffer() {
        return gtu;
    }

    public ContainerData data() {
        return data;
    }

    public boolean chainOk() {
        return chainOk;
    }

    // ─────────────────────────── GtuSink ───────────────────────────

    @Override
    public int receiveGtu(int amount, boolean simulate) {
        return gtu.receive(amount, simulate);
    }

    // ─────────────────────────── тик (сервер) ───────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, ElectricFurnaceBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        boolean changed = false;

        boolean chain = Sinks.hasNeighbor(server, pos, StirlingBlockEntity.class);
        if (chain != be.chainOk) {
            be.chainOk = chain;
            changed = true;
        }

        boolean canSmelt = SmeltHelper.canOutput(server, be.items.get(SLOT_INPUT), be.items.get(SLOT_OUTPUT));

        if (chain && canSmelt && be.gtu.has(MachineDefs.ELECTRIC_GTU_PER_TICK)) {
            if (be.cookTotal == 0) {
                be.cookTotal = SmeltHelper.cookTime(server, be.items.get(SLOT_INPUT), MachineDefs.ELECTRIC_COOK_TIME);
            }
            be.gtu.extract(MachineDefs.ELECTRIC_GTU_PER_TICK, false);
            be.cookProgress++;
            if (be.cookProgress >= be.cookTotal) {
                SmeltHelper.finish(server, be.items, SLOT_INPUT, SLOT_OUTPUT);
                be.cookProgress = 0;
                be.cookTotal = 0;
            }
            changed = true;
        } else if (be.cookProgress != 0 || be.cookTotal != 0) {
            // Нет питания/рецепта — плавно откатываем прогресс.
            be.cookProgress = Math.max(0, be.cookProgress - 2);
            if (be.cookProgress == 0) be.cookTotal = 0;
            changed = true;
        }

        if (changed) {
            be.setChanged();
        }
    }

    // ─────────────────────────── NBT ───────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        gtu.save(tag, "Gtu");
        tag.putInt("CookProgress", cookProgress);
        tag.putInt("CookTotal", cookTotal);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        gtu.load(tag, "Gtu");
        cookProgress = tag.getInt("CookProgress");
        cookTotal = tag.getInt("CookTotal");
    }

    // ─────────────────────────── Menu ───────────────────────────

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.gonzotech.electric_furnace");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new ElectricFurnaceMenu(id, inv, this, data);
    }
}
