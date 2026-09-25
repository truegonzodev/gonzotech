package com.gonzotech.mixin;

import com.gonzotech.cleanroom.CleanRoomSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Invalidate only indexed rooms, including explosions, pistons and command edits. */
@Mixin(LevelChunk.class)
public abstract class LevelChunkCleanRoomMixin {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void gonzotech$cleanRoomChanged(BlockPos pos, BlockState state, boolean moving,
                                            CallbackInfoReturnable<BlockState> callback) {
        LevelChunk chunk = (LevelChunk) (Object) this;
        BlockState old = callback.getReturnValue();
        if (old != null && chunk.getLevel() instanceof ServerLevel level) {
            CleanRoomSystem.blockChanged(level, pos, old, chunk.getBlockState(pos));
        }
    }
}
