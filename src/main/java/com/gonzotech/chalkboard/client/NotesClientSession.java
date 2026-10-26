package com.gonzotech.chalkboard.client;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.chalkboard.network.NotesNetwork;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** No stale favorites/progress may leak into another world or another server. */
@EventBusSubscriber(modid = GonzoTechMod.MOD_ID, value = Dist.CLIENT)
public final class NotesClientSession {
    private NotesClientSession() {}
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        NotesNetwork.CLIENT_DATA = null;
    }
}
