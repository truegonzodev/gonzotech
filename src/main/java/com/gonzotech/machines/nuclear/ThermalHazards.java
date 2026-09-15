package com.gonzotech.machines.nuclear;

import com.gonzotech.core.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Common heat-fire and three-by-three meltdown behavior for nuclear thermal blocks. */
public final class ThermalHazards {

    private ThermalHazards() {
    }

    /**
     * At most once per second, has a 25% chance to place fire over one random block
     * of the horizontal 3×3 footprint. The threshold caller owns the decision.
     */
    public static void maybeIgniteAround(ServerLevel level, BlockPos center) {
        if (level.getGameTime() % 20L != 0L || level.random.nextInt(4) != 0) return;
        RandomSource random = level.random;
        BlockPos base = center.offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);
        BlockPos fire = base.above();
        if (level.getBlockState(fire).isAir() && BaseFireBlock.canBePlacedAt(level, fire, Direction.UP)) {
            level.setBlock(fire, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /** Touching an overheated source block ignites a player for four seconds. */
    public static void igniteTouchingPlayer(Entity entity) {
        if (entity instanceof Player && !entity.fireImmune()) {
            entity.igniteForSeconds(4.0F);
        }
    }

    /**
     * Replaces every non-protected position in the horizontal 3×3 footprint with
     * molten corium source fluid. The solidified corium block appears only where
     * this lava meets water (see {@code MoltenCoriumBlock}).
     */
    public static void meltToCorium(ServerLevel level, BlockPos center) {
        replaceFootprint(level, center, ModBlocks.MOLTEN_CORIUM.get().defaultBlockState());
    }

    /** Replaces every non-protected position in the horizontal 3×3 footprint with vanilla lava source fluid. */
    public static void meltToLava(ServerLevel level, BlockPos center) {
        replaceFootprint(level, center, Blocks.LAVA.defaultBlockState());
    }

    private static void replaceFootprint(ServerLevel level, BlockPos center, BlockState replacement) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos target = center.offset(dx, 0, dz);
                if (isMeltProtected(level.getBlockState(target))) continue;
                level.setBlock(target, replacement, Block.UPDATE_ALL);
            }
        }
    }

    /** Explicitly protected materials plus admin/world-integrity blocks. */
    private static boolean isMeltProtected(BlockState state) {
        Block block = state.getBlock();
        if (state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN)
            || state.is(ModBlocks.CRIMSON_OBSIDIAN.get()) || state.is(Blocks.BEDROCK)
            || state.is(Blocks.BARRIER) || state.is(Blocks.COMMAND_BLOCK)
            || state.is(Blocks.CHAIN_COMMAND_BLOCK) || state.is(Blocks.REPEATING_COMMAND_BLOCK)
            || state.is(Blocks.STRUCTURE_BLOCK) || state.is(Blocks.STRUCTURE_VOID)
            || state.is(Blocks.JIGSAW) || state.is(Blocks.LIGHT)
            || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY)
            || state.is(ModBlocks.SUPERDENSE_ICE.get())
            || state.is(ModBlocks.TUNGSTEN_ABSORBER.get())) {
            return true;
        }
        // VR-20 and stellite are registered through the common metal-block map.
        return block == ModBlocks.METAL_BLOCKS.get("vr20_block").get()
            || block == ModBlocks.METAL_BLOCKS.get("stellite_block").get();
    }
}
