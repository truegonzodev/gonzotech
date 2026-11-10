package com.gonzotech.machines.network;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = "gonzotech")
public final class PipeNetworkEvents {
    private PipeNetworkEvents() {}
    @SubscribeEvent public static void pre(ServerTickEvent.Pre event) { PipeNetworkService.beginTick(); }
    @SubscribeEvent public static void post(ServerTickEvent.Post event) {
        PipeNetworkService.endTick();
        if (event.getServer().getTickCount() % 40 == 0)
            for (ServerLevel level : event.getServer().getAllLevels()) ItemRouting.trimCache(level);
    }
    @SubscribeEvent public static void load(ChunkEvent.Load event) { changed(event); }
    @SubscribeEvent public static void unload(ChunkEvent.Unload event) { changed(event); }
    private static void changed(ChunkEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var pos = event.getChunk().getPos();
            PipeNetworkService.chunkChanged(level, pos.x, pos.z);
        }
    }
    @SubscribeEvent public static void close(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            PipeNetworkService.closeLevel(level); ItemRouting.closeLevel(level);
        }
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) {
        PipeNetworkService.clearAll(); ItemRouting.clearAll(); PipeRouting.clearRouteCache();
    }
}
