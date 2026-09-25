package com.gonzotech.cleanroom;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.registry.ModBlocks;
import com.gonzotech.core.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;

/** First clean-room runtime slice: topology, air quality and player dirt. */
@EventBusSubscriber(modid = GonzoTechMod.MOD_ID)
public final class CleanRoomSystem {
    private static final Map<RoomKey, RoomState> ROOMS = new HashMap<>();

    private CleanRoomSystem() {}

    private record RoomKey(String dimension, long fingerprint) {}
    private static final class RoomState {
        private double quality;
        private long topologyTick;
        private RoomState(long tick) { topologyTick = tick; }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        ServerLevel level = player.serverLevel();
        CleanRoomDetector.Result room = CleanRoomDetector.find(level, player.blockPosition());
        Cleanliness dirt = player.getData(com.gonzotech.chalkboard.progress.ModAttachments.CLEANLINESS);
        if (!room.valid()) {
            dirt.setDirt(dirt.dirt() + 5.0);
        } else {
            RoomState state = roomState(level, room, player.tickCount);
            double before = dirt.dirt();
            if (cleanerActive(level, player.blockPosition())) {
                dirt.setDirt(before - 11.5);
            } else {
                dirt.setDirt(before - 1.0);
                double naturallyRemoved = Math.max(0.0, before - dirt.dirt());
                state.quality = Math.max(0.0, state.quality - naturallyRemoved * 3.0);
            }
        }
        player.setData(com.gonzotech.chalkboard.progress.ModAttachments.CLEANLINESS, dirt);
    }

    private static RoomState roomState(ServerLevel level, CleanRoomDetector.Result result, long tick) {
        RoomKey key = new RoomKey(level.dimension().location().toString(), result.fingerprint());
        return ROOMS.computeIfAbsent(key, ignored -> new RoomState(tick));
    }

    public static double quality(ServerLevel level, BlockPos pos) {
        CleanRoomDetector.Result result = CleanRoomDetector.find(level, pos);
        if (!result.valid()) return -1.0;
        return roomState(level, result, level.getGameTime()).quality;
    }

    public static boolean isInCleanerStream(ServerLevel level, BlockPos playerPos) {
        return cleanerActive(level, playerPos);
    }

    private static boolean cleanerActive(ServerLevel level, BlockPos playerPos) {
        for (int dy = 0; dy <= 3; dy++) {
            BlockPos candidate = playerPos.below(dy);
            if (level.getBlockState(candidate).is(ModBlocks.AIR_CLEANER.get())
                    && level.getBlockEntity(candidate) instanceof com.gonzotech.cleanroom.AirCleanerBlockEntity cleaner
                    && cleaner.active()) {
                double dx = (playerPos.getX() + 0.5) - (candidate.getX() + 0.5);
                double dz = (playerPos.getZ() + 0.5) - (candidate.getZ() + 0.5);
                if (Math.abs(dx) <= 0.7 && Math.abs(dz) <= 0.7) {
                    boolean clear = true;
                    for (int y = candidate.getY() + 1; y <= playerPos.getY(); y++) {
                        if (!level.getBlockState(new BlockPos(candidate.getX(), y, candidate.getZ())).isAir()) {
                            clear = false;
                            break;
                        }
                    }
                    if (clear) return true;
                }
            }
        }
        return false;
    }

    public static boolean isCoal(ItemStack stack) { return stack.is(net.minecraft.world.item.Items.COAL); }
    public static boolean isCatalyst(ItemStack stack) {
        return stack.is(ModItems.NUGGET_ITEMS.getOrDefault("platinum_nugget", null) == null
                ? net.minecraft.world.item.Items.AIR : ModItems.NUGGET_ITEMS.get("platinum_nugget").get())
                || stack.is(ModItems.NUGGET_ITEMS.getOrDefault("palladium_nugget", null) == null
                ? net.minecraft.world.item.Items.AIR : ModItems.NUGGET_ITEMS.get("palladium_nugget").get());
    }

    /** Called by a running air filter once per second. */
    public static void improve(ServerLevel level, BlockPos pos, double amount) {
        CleanRoomDetector.Result result = CleanRoomDetector.find(level, pos.above());
        if (!result.valid()) return;
        roomState(level, result, level.getGameTime()).quality = Math.min(100.0,
                roomState(level, result, level.getGameTime()).quality + amount);
    }
}
