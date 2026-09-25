package com.gonzotech.cleanroom;

import com.gonzotech.machines.energy.GtBuffer;
import com.gonzotech.machines.energy.Sinks.GtuSink;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Clean-room filter: three item slots and an internal GTU buffer. */
public final class AirFilterBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider, GtuSink {
    private static final int SLOT_COUNT = 3;
    private static final long GTU_PER_SECOND = 1_000L;
    private static final NonNullList<ItemStack> EMPTY = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final GtBuffer gtu = new GtBuffer(100_000L * 1_000L);
    private int coalTicks;
    private int catalystTicks;

    public AirFilterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AIR_FILTER.get(), pos, state);
    }

    public GtBuffer gtuBuffer() { return gtu; }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AirFilterBlockEntity be) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server) || server.getGameTime() % 20 != 0) return;
        if (!be.gtu.has(GTU_PER_SECOND)) return;
        if (!be.hasCoal() || !be.hasCatalyst()) return;
        be.gtu.extract(GTU_PER_SECOND, false);
        be.coalTicks++;
        be.catalystTicks++;
        if (be.coalTicks >= 60 * 60) { if (!be.consume(AirFilterBlockEntity::isCoal)) return; be.coalTicks = 0; }
        if (be.catalystTicks >= 180 * 60) { if (!be.consume(AirFilterBlockEntity::isCatalyst)) return; be.catalystTicks = 0; }
        CleanRoomSystem.improve(server, pos, 0.2);
        be.setChanged();
    }

    private boolean hasCoal() { return find(AirFilterBlockEntity::isCoal) >= 0; }
    private boolean hasCatalyst() { return find(AirFilterBlockEntity::isCatalyst) >= 0; }
    private static boolean isCoal(ItemStack stack) { return stack.is(net.minecraft.world.item.Items.COAL); }
    private static boolean isCatalyst(ItemStack stack) {
        var platinum = com.gonzotech.core.registry.ModItems.NUGGET_ITEMS.get("platinum_nugget");
        var palladium = com.gonzotech.core.registry.ModItems.NUGGET_ITEMS.get("palladium_nugget");
        return (platinum != null && stack.is(platinum.get())) || (palladium != null && stack.is(palladium.get()));
    }
    private int find(java.util.function.Predicate<ItemStack> predicate) {
        for (int i = 0; i < items.size(); i++) if (predicate.test(items.get(i))) return i;
        return -1;
    }
    private boolean consume(java.util.function.Predicate<ItemStack> predicate) {
        int slot = find(predicate); if (slot < 0) return false;
        items.get(slot).shrink(1); setChanged(); return true;
    }
    @Override public long receiveGtu(long amount, boolean simulate) { return gtu.receive(amount, simulate); }
    @Override public int getContainerSize() { return SLOT_COUNT; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int count) { return ContainerHelper.removeItem(items, slot, count); }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) { items.set(slot, stack.copyWithCount(Math.min(stack.getCount(), getMaxStackSize()))); setChanged(); }
    @Override public int getMaxStackSize() { return 64; }
    @Override public boolean stillValid(Player player) { return player.distanceToSqr(worldPosition.getX()+0.5, worldPosition.getY()+0.5, worldPosition.getZ()+0.5) <= 64.0; }
    @Override public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new com.gonzotech.machines.menu.AirFilterMenu(id, inventory, this);
    }
    @Override public void clearContent() { items.clear(); }
    @Override public int[] getSlotsForFace(Direction side) { return new int[]{0,1,2}; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) { return true; }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return true; }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.saveAdditional(tag, registries); ContainerHelper.saveAllItems(tag, items, registries); gtu.save(tag, "Gtu"); tag.putInt("CoalTicks", coalTicks); tag.putInt("CatalystTicks", catalystTicks); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.loadAdditional(tag, registries); ContainerHelper.loadAllItems(tag, items, registries); gtu.load(tag, "Gtu"); coalTicks=tag.getInt("CoalTicks"); catalystTicks=tag.getInt("CatalystTicks"); }
}
