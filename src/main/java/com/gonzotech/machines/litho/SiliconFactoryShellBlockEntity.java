package com.gonzotech.machines.litho;

import com.gonzotech.cleanroom.CleanRoomDetector;
import com.gonzotech.cleanroom.RoomTopology;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Прокси-контейнер оболочки литографии (0.3.46): позволяет трубам и воронкам
 * подключаться к ЛЮБОЙ части сформированной структуры. Раньше контейнером
 * была только сама фабрика (верхний центр) — потому трубы «цеплялись лишь
 * сверху по центру». Все операции пересылаются BE контроллера; вне
 * сформированной структуры прокси ведёт себя как пустой контейнер.
 */
public final class SiliconFactoryShellBlockEntity extends BlockEntity implements WorldlyContainer {

    /**
     * Класс оригинала (SEAL/INTERIOR), сохранённый при формировании: работает
     * до восстановления контроллера после загрузки мира (слой 2 после
     * контроллерного {@link SiliconFactoryStructure#originalKindAt}).
     */
    private RoomTopology.Kind preserved;

    public SiliconFactoryShellBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SILICON_FACTORY_SHELL.get(), pos, state);
    }

    /** Класс оригинала ячейки: сначала контроллер, затем собственное NBT-поле. */
    public RoomTopology.Kind preservedKind() {
        if (level instanceof ServerLevel server) {
            RoomTopology.Kind viaController = SiliconFactoryStructure.originalKindAt(server, worldPosition);
            if (viaController != null) return viaController;
        }
        return preserved;
    }

    void setPreserved(RoomTopology.Kind kind) {
        this.preserved = kind;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (preserved != null) {
            tag.putString("PreservedKind", preserved.name());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        preserved = tag.contains("PreservedKind")
            ? RoomTopology.Kind.valueOf(tag.getString("PreservedKind"))
            : null;
    }

    private SiliconFactoryBlockEntity controller() {
        if (level instanceof ServerLevel server) {
            BlockPos root = SiliconFactoryStructure.controllerAt(server, worldPosition);
            if (root != null
                && server.getBlockEntity(root) instanceof SiliconFactoryBlockEntity be
                && be.isFormed()) {
                return be;
            }
        }
        return null;
    }

    @Override
    public int getContainerSize() {
        SiliconFactoryBlockEntity be = controller();
        return be == null ? 0 : be.getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        SiliconFactoryBlockEntity be = controller();
        return be == null || be.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        SiliconFactoryBlockEntity be = controller();
        return be == null ? ItemStack.EMPTY : be.getItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        SiliconFactoryBlockEntity be = controller();
        return be == null ? ItemStack.EMPTY : be.removeItem(slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        SiliconFactoryBlockEntity be = controller();
        return be == null ? ItemStack.EMPTY : be.removeItemNoUpdate(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        SiliconFactoryBlockEntity be = controller();
        if (be != null) {
            be.setItem(slot, stack);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        SiliconFactoryBlockEntity be = controller();
        return be != null && !isRemoved() && be.stillValid(player);
    }

    @Override
    public void clearContent() {
        // Прокси ничем не владеет: содержимое живёт в контроллере.
    }

    // Правила — зеркально контроллеру: суп только в слот 0, наружу только чипы и кремень.
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        SiliconFactoryBlockEntity be = controller();
        return be != null && be.canPlaceItem(slot, stack);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        SiliconFactoryBlockEntity be = controller();
        return be == null ? new int[0] : be.getSlotsForFace(side);
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        SiliconFactoryBlockEntity be = controller();
        return be != null && be.canPlaceItemThroughFace(slot, stack, side);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        SiliconFactoryBlockEntity be = controller();
        return be != null && be.canTakeItemThroughFace(slot, stack, side);
    }
}
