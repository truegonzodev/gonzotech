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

/** Three supply slots; one shared room, 1 GTU/s and +0.2 quality percentage points/s. */
public final class AirFilterBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider, GtuSink {
    public static final int SLOT_COUNT = 3;
    public static final int DATA_COUNT = 5;
    public static final int CAPACITY_GTU = 100_000;
    private static final long GTU_PER_SECOND = 1_000L;
    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final GtBuffer gtu = new GtBuffer(CAPACITY_GTU * 1_000L);
    private FilterCycle cycle = new FilterCycle(0, 0);
    private int airQuality = -1;

    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                // ContainerData travels as signed shorts: split 100,000 GTU into two words.
                case 0 -> gtu.amountUnitsInt() & 0xffff;
                case 1 -> gtu.amountUnitsInt() >>> 16;
                case 2 -> cycle.coal();
                case 3 -> cycle.catalyst();
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
        if (!(level instanceof ServerLevel server) || server.getGameTime() % 20 != 0) return;
        RoomLedger.Room room = CleanRoomSystem.filterRoom(server, pos);
        be.airQuality = room == null ? -1 : (int) Math.round(room.quality() * 100);
        if (room == null || !be.gtu.has(GTU_PER_SECOND)) return;
        int coal = be.find(AirFilterBlockEntity::isCoal);
        int catalyst = be.find(AirFilterBlockEntity::isCatalyst);
        if ((be.cycle.needsCoal() && coal < 0) || (be.cycle.needsCatalyst() && catalyst < 0)) return;
        // All preconditions checked before spending anything. One item buys 60/180
        // actual operating seconds, so breaking the machine cannot reset unpaid debt.
        if (be.cycle.needsCoal()) be.items.get(coal).shrink(1);
        if (be.cycle.needsCatalyst()) be.items.get(catalyst).shrink(1);
        be.gtu.extract(GTU_PER_SECOND, false);
        be.cycle.workedSecond();
        CleanRoomSystem.improve(server, room, 0.2);
        be.airQuality = (int) Math.round(room.quality() * 100);
        be.setChanged();
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
        long received = gtu.receive(amount, simulate);
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
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return true; }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        gtu.save(tag, "Gtu");
        tag.putInt("CoalSeconds", cycle.coal());
        tag.putInt("CatalystSeconds", cycle.catalyst());
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        gtu.load(tag, "Gtu");
        // Legacy CoalTicks/CatalystTicks were unpaid elapsed counters, not fuel credit.
        cycle = new FilterCycle(tag.getInt("CoalSeconds"), tag.getInt("CatalystSeconds"));
        airQuality = -1;
    }
}
