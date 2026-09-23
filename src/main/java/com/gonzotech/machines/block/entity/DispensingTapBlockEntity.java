package com.gonzotech.machines.block.entity;

import com.gonzotech.core.fluid.ModFluids;
import com.gonzotech.core.item.CanisterItem;
import com.gonzotech.core.registry.ModItems;
import com.gonzotech.machines.energy.ResourceBuffer;
import com.gonzotech.machines.energy.Sinks.DistillateSink;
import com.gonzotech.machines.energy.Sinks.WortSink;
import com.gonzotech.machines.menu.DispensingTapMenu;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Блок-энтити разливного крана:
 * <ul>
 *   <li>Шкала дистиллята: 256 mB</li>
 *   <li>Шкала сусла: 1024 mB</li>
 *   <li>Слот 0 (вход тары): пустое ведро, стеклянный пузырёк, канистра;</li>
 *   <li>Слот 1 (выход): ведро пива, бутылка водки, кружка пива.</li>
 * </ul>
 */
public class DispensingTapBlockEntity extends BaseMachineBlockEntity
        implements DistillateSink, WortSink, WorldlyContainer {

    public static final int DISTILLATE_CAPACITY = 256;
    public static final int WORT_CAPACITY = 1024;

    public static final int SLOT_CONTAINER_IN = 0;
    public static final int SLOT_FILLED_OUT = 1;

    private static final int[] SLOTS_ALL = { SLOT_CONTAINER_IN, SLOT_FILLED_OUT };

    private final ResourceBuffer distillate = new ResourceBuffer(DISTILLATE_CAPACITY);
    private final ResourceBuffer wort = new ResourceBuffer(WORT_CAPACITY);

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> distillate.amount();
                case 1 -> wort.amount();
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {
            switch (i) {
                case 0 -> distillate.set(v);
                case 1 -> wort.set(v);
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    public DispensingTapBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DISPENSING_TAP.get(), pos, state, 2);
    }

    public ResourceBuffer distillateBuffer() {
        return distillate;
    }

    public ResourceBuffer wortBuffer() {
        return wort;
    }

    public ContainerData data() {
        return data;
    }

    // ─────────────────────────── Sinks (трубы) ───────────────────────────

    @Override
    public long receiveDistillate(long amount, boolean simulate) {
        return distillate.receive(amount, simulate);
    }

    @Override
    public long receiveWort(long amount, int alcoholPercent, boolean simulate) {
        return wort.receive(amount, simulate);
    }

    // ─────────────────────────── Серверный тик ───────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, DispensingTapBlockEntity be) {
        if (!(level instanceof ServerLevel)) return;

        boolean changed = false;

        if (be.handleInputContainers()) {
            changed = true;
        }

        if (changed) {
            be.setChanged();
        }
    }

    private boolean handleInputContainers() {
        ItemStack in = items.get(SLOT_CONTAINER_IN);
        if (in.isEmpty()) return false;

        // 1. Ведро → Ведро пива (тратит 1000 mB сусла)
        if (in.is(Items.BUCKET)) {
            if (wort.amount() >= 1000 && tryOutput(new ItemStack(ModItems.BEER_BUCKET.get()))) {
                wort.extract(1000, false);
                in.shrink(1);
                return true;
            }
            return false;
        }

        // 2. Стеклянный пузырёк: «приоритет на водку»
        //    Если distillate >= 128 → Бутылка водки; иначе если wort >= 128 → Кружка пива.
        if (in.is(Items.GLASS_BOTTLE)) {
            if (distillate.amount() >= 128 && canOutput(new ItemStack(ModItems.VODKA_BOTTLE.get()))) {
                if (tryOutput(new ItemStack(ModItems.VODKA_BOTTLE.get()))) {
                    distillate.extract(128, false);
                    in.shrink(1);
                    return true;
                }
            } else if (wort.amount() >= 128 && canOutput(new ItemStack(ModItems.BEER_MUG.get()))) {
                if (tryOutput(new ItemStack(ModItems.BEER_MUG.get()))) {
                    wort.extract(128, false);
                    in.shrink(1);
                    return true;
                }
            }
            return false;
        }

        // 3. Канистра (заправка крана из канистры)
        if (in.is(ModItems.CANISTER.get())) {
            String canFluid = CanisterItem.getStoredFluid(in);
            int canAmount = CanisterItem.getStoredAmount(in);
            int canSalt = CanisterItem.getStoredSaltMb(in);

            if (canAmount > 0) {
                if ("distillate".equals(canFluid) && distillate.space() > 0) {
                    int transfer = Math.min(canAmount, distillate.space());
                    distillate.receive(transfer, false);
                    int remaining = canAmount - transfer;
                    CanisterItem.setFluidContent(in, remaining > 0 ? canFluid : "empty", remaining, 0);
                    return true;
                } else if ("wort".equals(canFluid) && wort.space() > 0) {
                    int transfer = Math.min(canAmount, wort.space());
                    wort.receive(transfer, false);
                    int remaining = canAmount - transfer;
                    CanisterItem.setFluidContent(in, remaining > 0 ? canFluid : "empty", remaining, 0);
                    return true;
                }
            }
        }

        // 4. Заливание вёдер дистиллята / сусла вручную
        if (in.is(ModFluids.DISTILLATE_BUCKET.get()) && distillate.space() >= 256) {
            if (canOutput(new ItemStack(Items.BUCKET))) {
                distillate.receive(256, false);
                in.shrink(1);
                tryOutput(new ItemStack(Items.BUCKET));
                return true;
            }
        } else if ((in.is(ModFluids.WORT_BUCKET.get()) || in.is(ModItems.BEER_BUCKET.get())) && wort.space() >= 1000) {
            if (canOutput(new ItemStack(Items.BUCKET))) {
                wort.receive(1000, false);
                in.shrink(1);
                tryOutput(new ItemStack(Items.BUCKET));
                return true;
            }
        }

        return false;
    }

    private boolean canOutput(ItemStack stack) {
        ItemStack out = items.get(SLOT_FILLED_OUT);
        if (out.isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(out, stack) && out.getCount() < out.getMaxStackSize();
    }

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

    // ─────────────────────────── NBT ───────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        distillate.save(tag, "Distillate");
        wort.save(tag, "Wort");
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        distillate.load(tag, "Distillate");
        wort.load(tag, "Wort");
    }

    // ─────────────────────── WorldlyContainer ───────────────────────

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == SLOT_FILLED_OUT) return false;
        if (slot == SLOT_CONTAINER_IN) {
            return stack.is(Items.BUCKET) || stack.is(Items.GLASS_BOTTLE)
                    || stack.is(ModItems.CANISTER.get())
                    || stack.is(ModFluids.DISTILLATE_BUCKET.get())
                    || stack.is(ModFluids.WORT_BUCKET.get())
                    || stack.is(ModItems.BEER_BUCKET.get());
        }
        return false;
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
        return slot == SLOT_FILLED_OUT;
    }

    // ─────────────────────────── Menu ───────────────────────────

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new DispensingTapMenu(id, inv, this, data);
    }
}
