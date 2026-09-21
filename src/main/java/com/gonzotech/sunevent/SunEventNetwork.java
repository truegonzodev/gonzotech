package com.gonzotech.sunevent;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.sunevent.client.SunEventClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Синхронизация суневетов на клиент: {@code nextEventDay} +
 * {@code lastEventDay} (ВАНИЛЬНЫЕ дни — клиент считает их сам из времени
 * мира) + {@code suneventDays} (для будущих HUD/батареи). Клиент из них
 * считает интенсивность багровости I(t) (fade-in ночью перед ивентом,
 * см. {@link SunEventClient}).
 */
public final class SunEventNetwork {

    private SunEventNetwork() {
    }

    /** S2C: состояние суневетов. */
    public record SunEventPayload(long suneventDays, long nextEventDay, long lastEventDay)
            implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<SunEventPayload> TYPE =
            new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "sun_event"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SunEventPayload> STREAM_CODEC =
            StreamCodec.of(
                (buf, v) -> {
                    buf.writeVarLong(v.suneventDays());
                    buf.writeVarLong(v.nextEventDay());
                    buf.writeVarLong(v.lastEventDay());
                },
                buf -> new SunEventPayload(buf.readVarLong(), buf.readVarLong(), buf.readVarLong()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
            SunEventPayload.TYPE,
            SunEventPayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                SunEventClient.suneventDays = payload.suneventDays();
                SunEventClient.nextEventDay = payload.nextEventDay();
                SunEventClient.lastEventDay = payload.lastEventDay();
            }));
    }

    /** Всем игрокам (состояние одно на мир). */
    public static void sendToAll(ServerLevel overworld) {
        SunEventData data = getData(overworld);
        for (ServerPlayer p : overworld.getServer().getPlayerList().getPlayers()) {
            sendToPlayer(p, data);
        }
    }

    public static void sendToPlayer(ServerPlayer player, SunEventData data) {
        PacketDistributor.sendToPlayer(player, new SunEventPayload(
            data.suneventDays, data.nextEventDay, data.lastEventDay));
    }

    /** Чтение данных суневетов из Оверворлда. */
    public static SunEventData getData(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(SunEventData.FACTORY, "gonzotech_sunevent");
    }
}
