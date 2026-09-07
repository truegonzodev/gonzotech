package com.gonzotech.core.psyche;

import com.gonzotech.GonzoTechMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Синхронизация «психики» игрока на клиент для отрисовки трёх HUD-шкал.
 * Сервер шлёт {@link PsycheDataPayload} при входе и при каждом изменении;
 * клиент кэширует в {@link #CLIENT_DATA}.
 */
public final class PsycheNetwork {

    private PsycheNetwork() {
    }

    /** S2C: текущее состояние всех шкал (в тысячных, 0..1000). */
    public record PsycheDataPayload(int addiction, int stress, int crisis,
                                    int radiation, int uv, int chemical) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<PsycheDataPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "psyche_data"));

        public static final StreamCodec<RegistryFriendlyByteBuf, PsycheDataPayload> STREAM_CODEC =
                StreamCodec.of(
                        (buf, v) -> {
                            buf.writeVarInt(v.addiction());
                            buf.writeVarInt(v.stress());
                            buf.writeVarInt(v.crisis());
                            buf.writeVarInt(v.radiation());
                            buf.writeVarInt(v.uv());
                            buf.writeVarInt(v.chemical());
                        },
                        buf -> new PsycheDataPayload(
                                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                                buf.readVarInt(), buf.readVarInt(), buf.readVarInt())
                );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Клиентский кэш последнего полученного состояния. */
    public static volatile PsycheDataPayload CLIENT_DATA = null;

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                PsycheDataPayload.TYPE,
                PsycheDataPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> CLIENT_DATA = payload)
        );
    }

    /** Собрать состояние из attachment и отправить игроку. */
    public static void sendToPlayer(ServerPlayer player) {
        PlayerPsyche psyche = player.getData(ModPsycheAttachments.PSYCHE);
        PacketDistributor.sendToPlayer(player, new PsycheDataPayload(
                psyche.getAddiction(), psyche.getStress(), psyche.getCrisis(),
                psyche.getRadiation(), psyche.getUv(), psyche.getChemical()));
    }
}
