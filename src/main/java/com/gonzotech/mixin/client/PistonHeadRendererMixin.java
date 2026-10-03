package com.gonzotech.mixin.client;

import com.gonzotech.machines.block.ThirdPistonBlock;
import com.gonzotech.machines.block.ThirdPistonHeadBlock;
import com.gonzotech.machines.block.ThirdStickyPistonBlock;
import com.gonzotech.machines.registry.ModMachines;
import net.minecraft.client.renderer.blockentity.PistonHeadRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Vanilla rebuilds the moving piston head from {@code Blocks.PISTON_HEAD} in
 * PistonHeadRenderer, even when PistonBaseBlock.moveBlocks stored our custom
 * head. Redirect that client-only read too, so the animation uses the same
 * model/texture as the settled head. The render method's first argument is
 * captured as context; GETSTATIC itself still has no operation arguments.
 */
@Mixin(PistonHeadRenderer.class)
public abstract class PistonHeadRendererMixin {

    @Redirect(method = "render(Lnet/minecraft/world/level/block/piston/PistonMovingBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
        at = @At(value = "FIELD",
                 target = "Lnet/minecraft/world/level/block/Blocks;PISTON_HEAD:Lnet/minecraft/world/level/block/Block;"))
    private Block gonzotech$customMovingHead(PistonMovingBlockEntity moving) {
        if (gonzotech$isThirdLeadHead(moving)) {
            return ModMachines.THIRD_LEAD_PISTON_HEAD.get();
        }
        return Blocks.PISTON_HEAD;
    }

    private static boolean gonzotech$isThirdLeadHead(PistonMovingBlockEntity moving) {
        if (moving == null) return false;
        BlockState moved = moving.getMovedState();
        if (moved.getBlock() instanceof ThirdPistonHeadBlock) return true;
        if (!moving.isSourcePiston()) return false;
        if (gonzotech$isThirdLeadPiston(moved)) return true;

        Level level = moving.getLevel();
        Direction facing = moving.getDirection();
        if (level == null || facing == null) return false;
        BlockPos pos = moving.getBlockPos();
        BlockPos back = pos.relative(facing.getOpposite());
        if (level.hasChunkAt(back) && gonzotech$isThirdLeadPiston(level.getBlockState(back))) return true;
        BlockPos front = pos.relative(facing);
        return level.hasChunkAt(front) && gonzotech$isThirdLeadPiston(level.getBlockState(front));
    }

    private static boolean gonzotech$isThirdLeadPiston(BlockState state) {
        return state.getBlock() instanceof ThirdPistonBlock
                || state.getBlock() instanceof ThirdStickyPistonBlock;
    }
}
