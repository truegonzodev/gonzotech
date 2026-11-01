package com.gonzotech.core.network;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.config.GonzoServerConfig;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.Map;
import java.util.WeakHashMap;

/** Only Plotnick buttons + radiation Fence. MUST run on HandlerThread.NETWORK,
 * before NeoForge's main-thread enqueue. Accepted work keeps vanilla packet ordering. */
@EventBusSubscriber(modid = GonzoTechMod.MOD_ID)
public final class PayloadFloodGuard {
    // Weak CONNECTION keys: UUID changes, menu ids and request sequences cannot create new buckets.
    // Values never refer back to a connection/player/level. Keep a closed tombstone until connection GC.
    private static final Map<Connection, PayloadFloodWindow> WINDOWS = new WeakHashMap<>();

    // Config events publish a primitive snapshot; Netty never touches mutable config/cache state.
    private static volatile int threshold = GonzoServerConfig.DEFAULT_FLOOD_REQUEST_THRESHOLD;
    public static void onConfigLoading(ModConfigEvent.Loading event) { publishConfig(event); }
    public static void onConfigReloading(ModConfigEvent.Reloading event) { publishConfig(event); }
    public static void onConfigUnloading(ModConfigEvent.Unloading event) {
        if (event.getConfig().getSpec() == GonzoServerConfig.SPEC)
            threshold = GonzoServerConfig.DEFAULT_FLOOD_REQUEST_THRESHOLD;
    }
    private static void publishConfig(ModConfigEvent event) {
        if (event.getConfig().getSpec() == GonzoServerConfig.SPEC)
            threshold = GonzoServerConfig.FLOOD_REQUEST_THRESHOLD.get();
    }

    public static void enqueue(IPayloadContext context, Runnable work) {
        Connection connection = context.connection();
        if (!connection.isConnected()) return;
        int limit = threshold;
        PayloadFloodWindow window;
        synchronized (WINDOWS) { window = WINDOWS.computeIfAbsent(connection, c -> new PayloadFloodWindow()); }
        PayloadFloodWindow.Admission admission = window.acquire(System.nanoTime(), limit);
        if (admission == PayloadFloodWindow.Admission.EXCEEDED) {
            context.disconnect(Component.translatable("disconnect.gonzotech.payload_flood", limit));
            return;
        }
        if (admission != PayloadFloodWindow.Admission.ACCEPTED) return;
        try {
            context.enqueueWork(() -> {
                // Once disconnected, don't replay the accepted backlog into world/menu state.
                if (!window.isClosed()) work.run();
            }).whenComplete((ignored, failure) -> window.release());
        } catch (RuntimeException | Error failure) {
            window.release();
            throw failure;
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        synchronized (WINDOWS) {
            PayloadFloodWindow window = WINDOWS.get(player.connection.getConnection());
            if (window != null) window.close();
        }
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) {
        synchronized (WINDOWS) {
            WINDOWS.values().forEach(PayloadFloodWindow::close);
            WINDOWS.clear();
        }
        threshold = GonzoServerConfig.DEFAULT_FLOOD_REQUEST_THRESHOLD;
    }
    private PayloadFloodGuard() {}
}
