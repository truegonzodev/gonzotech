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

    /**
     * Класс с учётом контекста (0.3.47): shell-блок литографии классифицируется
     * как его ОРИГИНАЛ (контроллер структуры → NBT оболочки) — подмена участника
     * при сборке/распаде не меняет класс ячейки, и комната не теряет качество.
     *
     * <p>0.3.49 (деинициализация): программные свапы invalidate() происходят при
     * уже удалённом BE старой оболочки и под стражей структурного индекса, а
     * внешний blockChanged слома приходит после распуска структуры — поэтому
     * shell-состояние без живого прокси смотрит в карту оригиналов структуры, а
     * позиция из окна деинициализации (мгновение воздуха между сломом оболочки и
     * восстановлением оригинала) классифицируется по последнему оригиналу.</p>
     */
    public static RoomTopology.Kind kindAt(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof com.gonzotech.machines.litho.SiliconFactoryShellBlock) {
            if (level.getBlockEntity(pos) instanceof com.gonzotech.machines.litho.SiliconFactoryShellBlockEntity proxy) {
                RoomTopology.Kind preserved = proxy.preservedKind();
                if (preserved != null) return preserved;
            }
            RoomTopology.Kind withoutProxy =
                com.gonzotech.machines.litho.SiliconFactoryStructure.shellKindWithoutProxy(pos);
            if (withoutProxy != null) return withoutProxy;
        }
        RoomTopology.Kind deinit =
            com.gonzotech.machines.litho.SiliconFactoryStructure.deinitKindOverrideAt(level, pos);
        if (deinit != null) return deinit;
        return kind(state);
    }

    public static RoomTopology.Kind read(ServerLevel level, RoomTopology.Pos pos) {
        BlockPos blockPos = blockPos(pos);
        if (level.isOutsideBuildHeight(blockPos)) return RoomTopology.Kind.FORBIDDEN;
        if (!level.hasChunkAt(blockPos)) return RoomTopology.Kind.UNLOADED;
        return kindAt(level, blockPos, level.getBlockState(blockPos));
    }

    public static RoomTopology.Pos pos(BlockPos pos) { return new RoomTopology.Pos(pos.getX(), pos.getY(), pos.getZ()); }
    public static BlockPos blockPos(RoomTopology.Pos pos) { return new BlockPos(pos.x(), pos.y(), pos.z()); }
}
