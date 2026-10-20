package com.gonzotech.machines.miner;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Обслуживание меток автошахтера (0.3.256): истечение TTL и синк меток
 * клиенту при входе в мир.
 */
public final class MarkerEvents {

    private MarkerEvents() {
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        MarkerService.get(level).tick(level);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MarkerService.get(player.serverLevel()).sync(player);
        }
    }
}
