package com.gonzotech.music;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.music.client.GonzoMusicClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** S2C request for the client-authoritative debug music roll and playback check. */
public final class MusicDebugNetwork {

    private MusicDebugNetwork() {
    }

    public record TriggerPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<TriggerPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                        GonzoTechMod.MOD_ID, "debug_music_trigger"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TriggerPayload> STREAM_CODEC =
                StreamCodec.unit(new TriggerPayload());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(TriggerPayload.TYPE, TriggerPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(GonzoMusicClient::debugTriggerMusic));
    }

    public static void requestDebugRoll(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new TriggerPayload());
    }
}
