package com.gonzotech.cleanroom;

import com.gonzotech.core.registry.ModItems;
import com.gonzotech.machines.energy.GtBuffer;
import com.gonzotech.machines.energy.Sinks.GtuSink;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Three input-only supply slots; 2.6 GTU/t active + 0.019 GTU/t leakage; +0.2 quality/s. */
public final class AirFilterBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider, GtuSink {
    public static final int SLOT_COUNT = 3;
    public static final int DATA_COUNT = 5;
    public static final int CAPACITY_GTU = FilterCycle.CAPACITY_GTU;
    private final FilterIntake intake = new FilterIntake();
    private RoomLedger.Room room;
    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final GtBuffer gtu = new GtBuffer(CAPACITY_GTU * 1_000L);
    private FilterCycle cycle = new FilterCycle(0, 0);
    private int airQuality = -1;

    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                // Preserve milli-GTU precision across the signed-short container protocol.
                case 0 -> (int) gtu.amountAsLong() & 0xffff;
                case 1 -> (int) gtu.amountAsLong() >>> 16;
                case 2 -> cycle.coalUsedHundredths();
                case 3 -> cycle.catalystUsedHundredths();
                case 4 -> airQuality;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) { /* server-owned */ }
        @Override public int getCount() { return DATA_COUNT; }
    };

    public AirFilterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AIR_FILTER.get(), pos, state);
    }

    public GtBuffer gtuBuffer() { return gtu; }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AirFilterBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        // Known rooms are checked every working tick (breach/unload stops work).
        // Failed/open-space searches retry once per second, not 20 flood fills/s.
        if (be.room != null || server.getGameTime() % 20 == 0) {
            be.room = CleanRoomSystem.filterRoom(server, pos);
        }
        be.airQuality = be.room == null ? -1 : (int) Math.round(be.room.quality() * 100);
        int coal = be.find(AirFilterBlockEntity::isCoal);
        int catalyst = be.find(AirFilterBlockEntity::isCatalyst);
        FilterCycle.Step step = be.cycle.tick(be.gtu.amountAsLong(), be.room != null, coal >= 0, catalyst >= 0);
        if (step.loadCoal()) be.items.get(coal).shrink(1);
        if (step.loadCatalyst()) be.items.get(catalyst).shrink(1);
        if (step.energySpent() > 0) {
            be.gtu.extract(step.energySpent(), false);
            be.setChanged();
        }
        if (step.working()) {
            CleanRoomSystem.improve(server, be.room, FilterCycle.QUALITY_PER_TICK);
            be.airQuality = (int) Math.round(be.room.quality() * 100);
        }
    }

    public static boolean isSupply(ItemStack stack) { return isCoal(stack) || isCatalyst(stack); }
    private static boolean isCoal(ItemStack stack) { return stack.is(Items.COAL); }
    private static boolean isCatalyst(ItemStack stack) {
        var platinum = ModItems.NUGGET_ITEMS.get("platinum_nugget");
        var palladium = ModItems.NUGGET_ITEMS.get("palladium_nugget");
        return (platinum != null && stack.is(platinum.get())) || (palladium != null && stack.is(palladium.get()));
    }
    private int find(java.util.function.Predicate<ItemStack> predicate) {
        for (int i = 0; i < items.size(); i++) if (predicate.test(items.get(i))) return i;
        return -1;
    }
    @Override public long receiveGtu(long amount, boolean simulate) {
        long accepted = intake.offer(level == null ? 0 : level.getGameTime(), amount,
                CAPACITY_GTU * 1000L - gtu.amountAsLong(), simulate);
        long received = gtu.receive(accepted, simulate);
        if (!simulate && received > 0) setChanged();
        return received;
    }
    @Override public int getContainerSize() { return SLOT_COUNT; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int count) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, count);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed = ContainerHelper.takeItem(items, slot);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }
    @Override public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack.copyWithCount(Math.min(stack.getCount(), Math.min(stack.getMaxStackSize(), getMaxStackSize()))));
        setChanged();
    }
    @Override public int getMaxStackSize() { return 64; }
    @Override public boolean stillValid(Player player) {
        return level != null && !isRemoved() && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }
    @Override public Component getDisplayName() { return Component.translatable(getBlockState().getBlock().getDescriptionId()); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new com.gonzotech.machines.menu.AirFilterMenu(id, inventory, this, data);
    }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return isSupply(stack); }
    @Override public int[] getSlotsForFace(Direction side) { return new int[]{0, 1, 2}; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) { return canPlaceItem(slot, stack); }
    // Supplies are inputs, never outputs: automation must not redistribute them between filters.
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return false; }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        gtu.save(tag, "Gtu");
        tag.putInt("CoalCreditTicks", cycle.coalTicks());
        tag.putInt("CatalystCreditTicks", cycle.catalystTicks());
        tag.putBoolean("CoalStarted", cycle.coalStarted());
        tag.putBoolean("CatalystStarted", cycle.catalystStarted());
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        gtu.load(tag, "Gtu");
        // Legacy CoalTicks/CatalystTicks were unpaid elapsed counters, not fuel credit.
        cycle = tag.contains("CoalCreditTicks")
                ? FilterCycle.fromTicks(tag.getInt("CoalCreditTicks"), tag.getInt("CatalystCreditTicks"),
                        tag.getBoolean("CoalStarted"), tag.getBoolean("CatalystStarted"))
                : new FilterCycle(tag.getInt("CoalSeconds"), tag.getInt("CatalystSeconds"));
        room = null;
        airQuality = -1;
    }
}
