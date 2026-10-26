package com.gonzotech.chalkboard.network;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.chalkboard.progress.ModAttachments;
import com.gonzotech.chalkboard.notes.ScholarNotesContent;
import com.gonzotech.chalkboard.notes.NotesState;
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

import java.util.ArrayList;
import java.util.List;

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

    /**
     * S2C: наигранное время (тики) + активированные «Открытия» (tier 1 / tier 2)
     * + флаги действий «Познания мира» (см. ScholarNoteFlags).
     */
    public record NotesDataPayload(long playtimeTicks, boolean tier1Unlocked, boolean tier2Unlocked,
                                   boolean tier3Unlocked, List<String> noteFlags, List<Integer> favorites) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<NotesDataPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "notes_data"));

        public static final StreamCodec<RegistryFriendlyByteBuf, NotesDataPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_LONG, NotesDataPayload::playtimeTicks,
                        ByteBufCodecs.BOOL, NotesDataPayload::tier1Unlocked,
                        ByteBufCodecs.BOOL, NotesDataPayload::tier2Unlocked,
                        ByteBufCodecs.BOOL, NotesDataPayload::tier3Unlocked,
                        STRING_LIST, NotesDataPayload::noteFlags,
                        PAGE_LIST, NotesDataPayload::favorites,
                        NotesDataPayload::new
                );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Список строк: varint-длина + UTF-8 элементы. В 1.21.4 у {@link StreamCodec}
     * нет готового list-кодека, поэтому кодим вручную (сервер доверенный,
     * длина списка ограничена количеством флагов ScholarNoteFlags).
     */
    private static final StreamCodec<RegistryFriendlyByteBuf, List<String>> STRING_LIST =
            StreamCodec.of(
                    (buf, list) -> {
                        buf.writeVarInt(list.size());
                        for (String s : list) buf.writeUtf(s);
                    },
                    buf -> {
                        int n = buf.readVarInt();
                        List<String> out = new ArrayList<>(n);
                        for (int i = 0; i < n; i++) out.add(buf.readUtf());
                        return out;
                    }
            );

    private static final StreamCodec<RegistryFriendlyByteBuf, List<Integer>> PAGE_LIST = StreamCodec.of(
            (buf, list) -> {
                buf.writeVarInt(list.size());
                for (int id : list) buf.writeVarInt(id);
            }, buf -> {
                int count = buf.readVarInt();
                if (count < 0 || count > ScholarNotesContent.PAGES.size()) {
                    throw new IllegalArgumentException("Invalid favorite notes count: " + count);
                }
                List<Integer> ids = new ArrayList<>(count);
                for (int i = 0; i < count; i++) ids.add(buf.readVarInt());
                return List.copyOf(ids);
            });

    /** Explicit intent, not a toggle: retries never undo the user's previous click. */
    public record FavoriteNotePayload(int pageId, boolean favorite) implements CustomPacketPayload {
        public static final Type<FavoriteNotePayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "favorite_note"));
        public static final StreamCodec<RegistryFriendlyByteBuf, FavoriteNotePayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, FavoriteNotePayload::pageId,
                ByteBufCodecs.BOOL, FavoriteNotePayload::favorite, FavoriteNotePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Клиентский кэш последнего полученного состояния. */
    public static volatile NotesDataPayload CLIENT_DATA = null;

    /**
     * Разблокирован ли тир «Открытия» ПОСТОЯННО — клиентская сторона (автор 22.09.2026).
     *
     * <p>Нужен там, где клиент решает что-то показать (тултипы, подсказки), а
     * серверный прогресс — это аттачмент игрока, недоступный клиенту. Тир 1/2
     * приезжают в {@link NotesDataPayload}: на входе в игру и при каждом изменении
     * (использование «Открытия», команды). Пока payload не пришёл — считаем тир
     * закрытым (одна секунда после входа, дальше состояние всегда актуально).</p>
     */
    public static boolean isTierUnlocked(int tier) {
        NotesDataPayload data = CLIENT_DATA;
        if (data == null) {
            return false;
        }
        return switch (tier) {
            case 1 -> data.tier1Unlocked();
            case 2 -> data.tier2Unlocked();
            case 3 -> data.noteFlags().contains("discovery_3");
            default -> false;
        };
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(FavoriteNotePayload.TYPE, FavoriteNotePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (!(context.player() instanceof ServerPlayer player)) return;
                    if (!com.gonzotech.core.network.PayloadThrottle.allow(player, "notesFavorite", 20)) return;
                    int index = ScholarNotesContent.indexOfNumber(payload.pageId());
                    if (index < 0) return;
                    PlayerChalkboardProgress progress = player.getData(ModAttachments.CHALKBOARD_PROGRESS);
                    NotesDataPayload data = snapshot(player);
                    var state = new NotesState(data.playtimeTicks(),
                            data.tier1Unlocked(), data.tier2Unlocked(), data.tier3Unlocked(), java.util.Set.copyOf(data.noteFlags()));
                    // Removal stays possible after a debug gate reset; adding locked pages is forbidden.
                    if (payload.favorite() && !ScholarNotesContent.isUnlocked(
                            ScholarNotesContent.PAGES.get(index), state)) {
                        sendToPlayer(player);
                        return;
                    }
                    if (progress.setFavoriteNote(payload.pageId(), payload.favorite())) {
                        player.setData(ModAttachments.CHALKBOARD_PROGRESS, progress);
                    }
                    sendToPlayer(player);
                }));

        registrar.playToServer(
                NotesRequestPayload.TYPE,
                NotesRequestPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        if (!com.gonzotech.core.network.PayloadThrottle.allow(player, "notesSync", 4)) return;
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
        PacketDistributor.sendToPlayer(player, snapshot(player));
    }

    private static NotesDataPayload snapshot(ServerPlayer player) {
        long playtime = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
        PlayerChalkboardProgress progress = player.getData(ModAttachments.CHALKBOARD_PROGRESS);
        boolean tier1 = progress.isRecipeTierUnlocked(1);
        boolean tier2 = progress.isRecipeTierUnlocked(2);
        List<String> flags = List.copyOf(progress.getNoteFlags());
        return new NotesDataPayload(playtime, tier1, tier2, progress.isRecipeTierUnlocked(3), flags, progress.getFavoriteNotes());
    }
}
