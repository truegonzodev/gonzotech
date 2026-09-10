package com.gonzotech.space;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.space.client.SpaceSkyState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Синхронизация состояний скайбокса (Солнце, Альфа Центавра, кольца Чёрных Дыр) на клиент.
 */
public final class SpaceSkyNetwork {

    private SpaceSkyNetwork() {
    }

    /** Серверное состояние для синхронизации вновь подключившимся игрокам. */
    public static volatile SunState currentSunState = SunState.DEFAULT;
    public static volatile boolean currentAlphaCentauriDyson = false;
    public static volatile boolean currentYx989Dyson = false;
    public static volatile boolean currentZanglerDyson = false;

    /** S2C: Установка состояния Солнца (default, dyson, gone, blackhole, blackhole_dyson). */
    public record SunStatePayload(String stateName) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SunStatePayload> TYPE =
            new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "sun_state"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SunStatePayload> STREAM_CODEC =
            StreamCodec.of(
                (buf, v) -> buf.writeUtf(v.stateName()),
                buf -> new SunStatePayload(buf.readUtf()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** S2C: Режим сферы/кольца Дайсона для звёзд и чёрных дыр. */
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
            SunStatePayload.TYPE,
            SunStatePayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                SpaceSkyState.sunState = SunState.fromString(payload.stateName());
            }));

        registrar.playToClient(
            StarModePayload.TYPE,
            StarModePayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                String target = payload.target();
                boolean d = payload.dyson();
                if ("alpha_centauri".equalsIgnoreCase(target) || "alpha-centauri".equalsIgnoreCase(target)) {
                    SpaceSkyState.alphaCentauriDyson = d;
                } else if ("yx989".equalsIgnoreCase(target) || "yx989_k2".equalsIgnoreCase(target) || "y989".equalsIgnoreCase(target)) {
                    SpaceSkyState.yx989Dyson = d;
                } else if ("zangler".equalsIgnoreCase(target) || "zangler_11".equalsIgnoreCase(target)) {
                    SpaceSkyState.zanglerDyson = d;
                }
            }));
    }

    /** Отправить состояние Солнца всем игрокам. */
    public static void sendSunStateToAll(net.minecraft.server.MinecraftServer server, SunState state) {
        currentSunState = state;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(p, new SunStatePayload(state.getSerializedName()));
        }
    }

    /** Отправить режим звезды/кольца всем игрокам. */
    public static void sendStarModeToAll(net.minecraft.server.MinecraftServer server, String target, boolean dyson) {
        if ("alpha_centauri".equalsIgnoreCase(target) || "alpha-centauri".equalsIgnoreCase(target)) {
            currentAlphaCentauriDyson = dyson;
        } else if ("yx989".equalsIgnoreCase(target) || "yx989_k2".equalsIgnoreCase(target) || "y989".equalsIgnoreCase(target)) {
            currentYx989Dyson = dyson;
        } else if ("zangler".equalsIgnoreCase(target) || "zangler_11".equalsIgnoreCase(target)) {
            currentZanglerDyson = dyson;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(p, new StarModePayload(target, dyson));
        }
    }

    /** Синхронизировать текущее состояние подключившемуся игроку. */
    public static void syncToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SunStatePayload(currentSunState.getSerializedName()));
        PacketDistributor.sendToPlayer(player, new StarModePayload("alpha_centauri", currentAlphaCentauriDyson));
        PacketDistributor.sendToPlayer(player, new StarModePayload("yx989", currentYx989Dyson));
        PacketDistributor.sendToPlayer(player, new StarModePayload("zangler", currentZanglerDyson));
    }
}
