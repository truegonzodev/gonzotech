package com.gonzotech.mixin;

import com.gonzotech.core.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Replaces a lava-bucket source placement with crimson obsidian when it touches
 * a redstone block on any of the five permitted sides.
 *
 * <p>{@code FluidPlaceBlockEvent} is intentionally not used here: it is for a
 * fluid producing a block (for example water + lava), rather than the source
 * block placed by a lava bucket. Redirecting this one {@link Level#setBlock}
 * call retains normal bucket success/consumption and works at the exact
 * target block.
 */
@Mixin(BucketItem.class)
public abstract class BucketItemMixin {

    @Redirect(
        method = "emptyContents(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/world/item/ItemStack;)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"
        )
    )
    private boolean gonzotech$createCrimsonObsidianFromLavaSource(
        Level level, BlockPos lavaPos, BlockState placedState, int flags,
        Player placer, Level ignoredLevel, BlockPos ignoredPos,
        BlockHitResult ignoredHit, ItemStack ignoredContainer
    ) {
        if (!placedState.getFluidState().isSourceOfType(Fluids.LAVA)
            || !touchesReactiveRedstone(level, lavaPos)) {
            return level.setBlock(lavaPos, placedState, flags);
        }

        return level.setBlock(lavaPos, ModBlocks.CRIMSON_OBSIDIAN.get().defaultBlockState(), flags);
    }

    /**
     * A redstone block above the source is the only excluded neighbour. The
     * placement-position check is defensive: unlike redstone dust, a redstone
     * block is not replaceable, so a lava source can never be placed into it.
     */
    private static boolean touchesReactiveRedstone(Level level, BlockPos lavaPos) {
        if (level.getBlockState(lavaPos).is(Blocks.REDSTONE_BLOCK)) return true;

        for (Direction direction : Direction.values()) {
            if (direction == Direction.UP) continue;
            if (level.getBlockState(lavaPos.relative(direction)).is(Blocks.REDSTONE_BLOCK)) {
                return true;
            }
        }
        return false;
    }
}
