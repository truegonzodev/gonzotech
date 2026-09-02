package com.gonzotech.chalkboard.client;

import com.gonzotech.chalkboard.network.ChalkboardNetwork;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client handler for opening ResonanceScreen and requesting personal progress sync from server.
 */
public final class ChalkboardClientHandler {

    public static void openScreen() {
        PacketDistributor.sendToServer(new ChalkboardNetwork.SyncRequestPayload());
        Minecraft.getInstance().setScreen(new ResonanceScreen());
    }

    private ChalkboardClientHandler() {
    }
}
