package com.gonzotech.chalkboard.network;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.chalkboard.progress.ModAttachments;
import com.gonzotech.chalkboard.progress.PlayerChalkboardProgress;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Отдельный sync для GUI «Заметок учёного»: клиенту нужны данные для gating
 * страниц, которых нет в chalkboard-sync — наигранное время (для страницы
 * «Брожение», >5 мин) и разблокирован ли recipe tier 1 (для страниц после
 * «Открытия 1»).
 *
 * <p>Паттерн как у {@link ChalkboardNetwork}: C2S request → сервер отвечает
 * S2C data; клиент кэширует в {@link #CLIENT_DATA}.
 */
public final class NotesNetwork {

    private NotesNetwork() {
    }

    /** C2S: клиент просит актуальное состояние для буклета. */
    public record NotesRequestPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<NotesRequestPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "notes_request"));

        public static final StreamCodec<RegistryFriendlyByteBuf, NotesRequestPayload> STREAM_CODEC =
                StreamCodec.unit(new NotesRequestPayload());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** S2C: наигранное время (тики) + разблокирован ли tier 1. */
    public record NotesDataPayload(long playtimeTicks, boolean tier1Unlocked) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<NotesDataPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "notes_data"));

        public static final StreamCodec<RegistryFriendlyByteBuf, NotesDataPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_LONG, NotesDataPayload::playtimeTicks,
                        ByteBufCodecs.BOOL, NotesDataPayload::tier1Unlocked,
                        NotesDataPayload::new
                );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Клиентский кэш последнего полученного состояния. */
    public static volatile NotesDataPayload CLIENT_DATA = null;

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(
                NotesRequestPayload.TYPE,
                NotesRequestPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        sendToPlayer(player);
                    }
                })
        );

        registrar.playToClient(
                NotesDataPayload.TYPE,
                NotesDataPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> CLIENT_DATA = payload)
        );
    }

    /** Собрать и отправить актуальное состояние игроку. */
    public static void sendToPlayer(ServerPlayer player) {
        long playtime = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
        PlayerChalkboardProgress progress = player.getData(ModAttachments.CHALKBOARD_PROGRESS);
        boolean tier1 = progress.isRecipeTierUnlocked(1);
        PacketDistributor.sendToPlayer(player, new NotesDataPayload(playtime, tier1));
    }
}
