package com.gonzotech.cleanroom;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.machines.network.NodeBlock;
import com.gonzotech.machines.network.PipeCarrier;
import com.gonzotech.machines.network.UniversalFluidNodeBlock;
import com.gonzotech.machines.network.UniversalNodeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Clean-room rules are independent of radiation shielding and collision shapes. */
public final class CleanRoomDetector {
    public static final TagKey<Block> SEALS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "clean_room_seal"));
    public static final TagKey<Block> INTERIOR = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "clean_room_interior"));

    private CleanRoomDetector() {}

    public static RoomTopology.Kind kind(BlockState state) {
        if (state.isAir()) return RoomTopology.Kind.INTERIOR;
        if (!state.getFluidState().isEmpty()) return RoomTopology.Kind.FORBIDDEN;
        Block block = state.getBlock();
        // Nodes are checked before PipeCarrier: a node seals, a pipe never does.
        if (state.is(SEALS) || block instanceof NodeBlock || block instanceof UniversalNodeBlock
                || block instanceof UniversalFluidNodeBlock) return RoomTopology.Kind.SEAL;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if (block instanceof PipeCarrier || state.is(INTERIOR)
                || (id.getNamespace().equals(GonzoTechMod.MOD_ID) && id.getPath().startsWith("third_"))) {
            return RoomTopology.Kind.INTERIOR;
        }
        return RoomTopology.Kind.FORBIDDEN;
    }

    public static RoomTopology.Kind read(ServerLevel level, RoomTopology.Pos pos) {
        BlockPos blockPos = blockPos(pos);
        if (level.isOutsideBuildHeight(blockPos)) return RoomTopology.Kind.FORBIDDEN;
        if (!level.hasChunkAt(blockPos)) return RoomTopology.Kind.UNLOADED;
        return kind(level.getBlockState(blockPos));
    }

    public static RoomTopology.Pos pos(BlockPos pos) { return new RoomTopology.Pos(pos.getX(), pos.getY(), pos.getZ()); }
    public static BlockPos blockPos(RoomTopology.Pos pos) { return new BlockPos(pos.x(), pos.y(), pos.z()); }
}
