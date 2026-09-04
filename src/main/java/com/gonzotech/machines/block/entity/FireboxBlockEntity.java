package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.ResourceBuffer;
import com.gonzotech.machines.energy.Sinks.GthSink;
import com.gonzotech.machines.energy.Transfer;
import com.gonzotech.machines.menu.FireboxMenu;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Топка — печка на 3 слота (вход-нагрузка, топливо, выход) + шкала GTH.
 * <p>
 * <ul>
 *   <li>Топливо горит «ванильную» длительность (уголь 80с и т.д.) и наполняет
 *       буфер {@code GTH} на {@link MachineDefs#FIREBOX_GTH_PER_TICK}/t
 *       (независимо от вида топлива).</li>
 *   <li>Нагрузка плавится ВСЕГДА, пока горит топливо — даже при полной шкале GTH
 *       и когда GTH никуда не вытекает. Скорость плавки зависит от запаса GTH
 *       (см. {@link MachineDefs#fireboxSpeedPermille}). GTH на переплавку не тратится.</li>
 *   <li>GTH раздаётся соседям равномерно, максимум
 *       {@link MachineDefs#FIREBOX_GTH_OUTPUT}/t.</li>
 *   <li>Паразитная потеря {@link MachineDefs#FIREBOX_GTH_LOSS} GTH/t — всегда.</li>
 * </ul>
 */
public class FireboxBlockEntity extends BaseMachineBlockEntity implements GthSink {

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_FUEL = 1;
    public static final int SLOT_OUTPUT = 2;

    private final ResourceBuffer gth = new ResourceBuffer(MachineDefs.FIREBOX_GTH_CAPACITY);

    /** Оставшееся время горения текущей единицы топлива, тиков. */
    private int litTime;
    /** Полное время горения текущей единицы топлива (для шкалы пламени). */
    private int litDuration;
    /** Прогресс переплавки нагрузки, тиков. */
    private int cookProgress;
    /** Полное время переплавки нагрузки, тиков. */
    private int cookTotal;
    /** Дробный аккумулятор скорости плавки (промилле), серверный, не синкается. */
    private int cookAccum;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> gth.amount();
                case 1 -> gth.capacity();
                case 2 -> litTime;
                case 3 -> litDuration;
                case 4 -> cookProgress;
                case 5 -> cookTotal;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {
            switch (i) {
                case 0 -> gth.set(v);
                case 2 -> litTime = v;
                case 3 -> litDuration = v;
                case 4 -> cookProgress = v;
                case 5 -> cookTotal = v;
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return 6;
        }
    };

    public FireboxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FIREBOX.get(), pos, state, 3);
    }

    public ResourceBuffer gth() {
        return gth;
    }

    public ContainerData data() {
        return data;
    }

    public boolean isLit() {
        return litTime > 0;
    }

    // ─────────────────────────── GthSink ───────────────────────────

    @Override
    public int receiveGth(int amount, boolean simulate) {
        return gth.receive(amount, simulate);
    }

    // ─────────────────────────── тик (сервер) ───────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, FireboxBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        boolean changed = false;

        // 1. Горение топлива → наполняем GTH.
        if (be.litTime > 0) {
            be.litTime--;
            be.gth.receive(MachineDefs.FIREBOX_GTH_PER_TICK, false);
            changed = true;
        }

        // Разжечь новую единицу топлива, если погасло. ВАЖНО: разжигаем даже при
        // полной шкале GTH — топка обязана плавить всегда (лишний GTH просто
        // не влезает в буфер).
        if (be.litTime <= 0) {
            ItemStack fuel = be.items.get(SLOT_FUEL);
            int burn = fuel.getBurnTime(RecipeType.SMELTING, server.fuelValues());
            boolean hasWork = FireboxBlockEntity.wantsToBurn(server, be);
            if (burn > 0 && hasWork) {
                be.litTime = burn;
                be.litDuration = burn;
                ItemStack container = fuel.getCraftingRemainder();
                fuel.shrink(1);
                if (fuel.isEmpty() && !container.isEmpty()) {
                    be.items.set(SLOT_FUEL, container);
                }
                changed = true;
            }
        }

        // 2. Паразитная потеря GTH — всегда.
        if (be.gth.amount() > 0) {
            be.gth.extract(MachineDefs.FIREBOX_GTH_LOSS, false);
            changed = true;
        }

        // 3. Плавка нагрузки — идёт, пока горит топка (GTH не тратится).
        //    Скорость зависит от запаса GTH.
        if (be.isLit() && SmeltHelper.canOutput(server, be.items.get(SLOT_INPUT), be.items.get(SLOT_OUTPUT))) {
            if (be.cookTotal == 0) {
                be.cookTotal = SmeltHelper.cookTime(server, be.items.get(SLOT_INPUT), MachineDefs.FIREBOX_BASE_COOK_TIME);
            }
            be.cookAccum += MachineDefs.fireboxSpeedPermille(be.gth.amount());
            while (be.cookAccum >= 1000) {
                be.cookAccum -= 1000;
                be.cookProgress++;
            }
            if (be.cookProgress >= be.cookTotal) {
                SmeltHelper.finish(server, be.items, SLOT_INPUT, SLOT_OUTPUT);
                be.cookProgress = 0;
                be.cookTotal = 0;
                be.cookAccum = 0;
            }
            changed = true;
        } else if (be.cookProgress != 0 || be.cookTotal != 0 || be.cookAccum != 0) {
            be.cookProgress = 0;
            be.cookTotal = 0;
            be.cookAccum = 0;
            changed = true;
        }

        // 4. Раздать GTH соседям равномерно (макс. отдача за тик).
        if (be.pushGth(server, pos)) {
            changed = true;
        }

        if (changed) {
            be.setChanged();
        }
    }

    /**
     * Стоит ли разжигать новую единицу топлива: есть смысл, если можно плавить
     * нагрузку ИЛИ есть куда девать GTH (буфер не полон, либо рядом приёмник).
     * На практике достаточно проверить «есть работа по плавке или буфер не полон».
     */
    private static boolean wantsToBurn(ServerLevel server, FireboxBlockEntity be) {
        boolean canSmelt = SmeltHelper.canOutput(server, be.items.get(SLOT_INPUT), be.items.get(SLOT_OUTPUT));
        return canSmelt || !be.gth.isFull();
    }

    /** Равномерно раздать GTH соседям-приёмникам (котлам). */
    private boolean pushGth(Level level, BlockPos pos) {
        if (gth.isEmpty()) return false;
        int budget = Math.min(MachineDefs.FIREBOX_GTH_OUTPUT, gth.amount());
        int moved = Transfer.distribute(level, pos, budget, level.getGameTime(), be -> {
            if (be instanceof FireboxBlockEntity) return null;
            if (be instanceof GthSink sink) return sink::receiveGth;
            return null;
        });
        if (moved > 0) {
            gth.extract(moved, false);
            return true;
        }
        return false;
    }

    // ─────────────────────────── NBT ───────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        gth.save(tag, "Gth");
        tag.putInt("LitTime", litTime);
        tag.putInt("LitDuration", litDuration);
        tag.putInt("CookProgress", cookProgress);
        tag.putInt("CookTotal", cookTotal);
        tag.putInt("CookAccum", cookAccum);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        gth.load(tag, "Gth");
        litTime = tag.getInt("LitTime");
        litDuration = tag.getInt("LitDuration");
        cookProgress = tag.getInt("CookProgress");
        cookTotal = tag.getInt("CookTotal");
        cookAccum = tag.getInt("CookAccum");
    }

    // ─────────────────────────── Menu ───────────────────────────

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.gonzotech.firebox");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new FireboxMenu(id, inv, this, data);
    }
}
