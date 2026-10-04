package com.gonzotech.mixin;

import com.gonzotech.core.fluid.LiquidFireBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets ordinary vanilla fire ignite modded fuel fluids within one block. */
@Mixin(FireBlock.class)
public abstract class FireBlockLiquidIgnitionMixin {

    @Inject(method = "tick", at = @At("RETURN"))
    private void gonzotech$igniteNearbyLiquid(BlockState state, ServerLevel level, BlockPos pos,
                                              RandomSource random, CallbackInfo ci) {
        if (state.getBlock() instanceof LiquidFireBlock) return;
        LiquidFireBlock.igniteNearbyFuel(level, pos, random);
    }
}
