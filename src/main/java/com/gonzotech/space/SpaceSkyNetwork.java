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
 * <p>Режим звёзд (обычное ↔ сфера Дайсона) для Солнца и Альфы Центавра.
 * Сервер шлёт {@link StarModePayload} по команде {@code /gonzotech debug <star> default|dyson};
 * клиент выставляет флаги в {@code SpaceSkyState}.
 */
public final class SpaceSkyNetwork {

    private SpaceSkyNetwork() {
    }

    /** S2C: включить/выключить режим сферы Дайсона для звезды (sun, alpha_centauri, all). */
    public record StarModePayload(String target, boolean dyson) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<StarModePayload> TYPE =
            new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "star_mode"));

        public static final StreamCodec<RegistryFriendlyByteBuf, StarModePayload> STREAM_CODEC =
            StreamCodec.of(
                (buf, v) -> {
                    buf.writeUtf(v.target());
                    buf.writeBoolean(v.dyson());
                },
                buf -> new StarModePayload(buf.readUtf(), buf.readBoolean()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
            StarModePayload.TYPE,
            StarModePayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                String target = payload.target();
                boolean d = payload.dyson();
                if ("sun".equalsIgnoreCase(target)) {
                    com.gonzotech.space.client.SpaceSkyState.sunDyson = d;
                } else if ("alpha_centauri".equalsIgnoreCase(target) || "alpha-centauri".equalsIgnoreCase(target)) {
                    com.gonzotech.space.client.SpaceSkyState.alphaCentauriDyson = d;
                } else {
                    com.gonzotech.space.client.SpaceSkyState.sunDyson = d;
                    com.gonzotech.space.client.SpaceSkyState.alphaCentauriDyson = d;
                    com.gonzotech.space.client.SpaceSkyState.dysonSphere = d;
                }
            }));
    }

    /** Отправить текущий режим одному игроку. */
    public static void sendToPlayer(ServerPlayer player, String target, boolean dyson) {
        PacketDistributor.sendToPlayer(player, new StarModePayload(target, dyson));
    }

    /** Разослать режим всем игрокам сервера. */
    public static void sendToAll(net.minecraft.server.MinecraftServer server, String target, boolean dyson) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(p, new StarModePayload(target, dyson));
        }
    }
}
