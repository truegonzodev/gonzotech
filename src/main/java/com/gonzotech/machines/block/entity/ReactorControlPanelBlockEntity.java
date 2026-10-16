package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.reactor.ReactorStructure;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Persistent controller metadata for the first-generation uranium reactor structure. */
public final class ReactorControlPanelBlockEntity extends BlockEntity {

    private boolean formed;
    private boolean validationPending;
    private BlockPos min;
    private BlockPos max;

    public ReactorControlPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.THIRD_URANIUM_REACTOR_CONTROL_PANEL.get(), pos, state);
    }

    public boolean isFormedController() {
        return formed && min != null && max != null;
    }

    public BlockPos min() {
        return min;
    }

    public BlockPos max() {
        return max;
    }

    public boolean contains(BlockPos pos) {
        return isFormedController()
            && pos.getX() >= min.getX() && pos.getX() <= max.getX()
            && pos.getY() >= min.getY() && pos.getY() <= max.getY()
            && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    /** Called only after the entire outer shell and interior pass validation. */
    public void configureStructure(BlockPos min, BlockPos max) {
        this.min = min.immutable();
        this.max = max.immutable();
        this.formed = true;
        this.validationPending = false;
        setChanged();
    }

    /** The structure is no longer valid; the panel remains as a standalone debug block. */
    public void clearStructure() {
        formed = false;
        validationPending = false;
        min = null;
        max = null;
        setChanged();
    }

    public void markValidationPending() {
        validationPending = true;
    }

    public void clearValidationPending() {
        validationPending = false;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            ReactorStructure.panelLoaded(serverLevel, this);
        }
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel) {
            ReactorStructure.panelUnloaded(serverLevel, getBlockPos());
        }
        super.setRemoved();
    }

    /** Retry a deferred structure check after the relevant chunk boundary loads. */
    public void serverTick() {
        if (!validationPending || !(level instanceof ServerLevel serverLevel)
            || serverLevel.getGameTime() % 20L != 0L) {
            return;
        }
        ReactorStructure.restorePanel(serverLevel, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Formed", formed);
        if (min != null) tag.putLong("StructureMin", min.asLong());
        if (max != null) tag.putLong("StructureMax", max.asLong());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        formed = tag.getBoolean("Formed");
        min = tag.contains("StructureMin") ? BlockPos.of(tag.getLong("StructureMin")) : null;
        max = tag.contains("StructureMax") ? BlockPos.of(tag.getLong("StructureMax")) : null;
        validationPending = formed;
        if (!formed || min == null || max == null
            || min.getX() > max.getX() || min.getY() > max.getY() || min.getZ() > max.getZ()) {
            clearStructure();
        }
    }
}
