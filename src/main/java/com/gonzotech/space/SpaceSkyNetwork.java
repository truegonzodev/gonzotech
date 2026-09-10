package com.gonzotech.space;

import com.gonzotech.GonzoTechMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Синхронизация флагов скайбокса на клиент.
 *
 * <p>Пока — только режим главного солнца (обычное ↔ сфера Дайсона). Сервер шлёт
 * {@link SunModePayload} по команде {@code /gonzotech debug sun default|dyson};
 * клиент выставляет {@code SpaceSkyState.dysonSphere}. Обработчик — на клиенте
 * (загружается только на физической стороне клиента, чтобы не тянуть клиентские
 * классы на сервере).
 */
public final class SpaceSkyNetwork {

    private SpaceSkyNetwork() {
    }

    /** S2C: включить/выключить режим сферы Дайсона для солнца. */
    public record SunModePayload(boolean dyson) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SunModePayload> TYPE =
            new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "sun_mode"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SunModePayload> STREAM_CODEC =
            StreamCodec.of(
                (buf, v) -> buf.writeBoolean(v.dyson()),
                buf -> new SunModePayload(buf.readBoolean()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
            SunModePayload.TYPE,
            SunModePayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(
                () -> com.gonzotech.space.client.SpaceSkyState.dysonSphere = payload.dyson()));
    }

    /** Отправить текущий режим одному игроку. */
    public static void sendToPlayer(ServerPlayer player, boolean dyson) {
        PacketDistributor.sendToPlayer(player, new SunModePayload(dyson));
    }

    /** Разослать режим всем игрокам сервера. */
    public static void sendToAll(net.minecraft.server.MinecraftServer server, boolean dyson) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(p, new SunModePayload(dyson));
        }
    }
}
