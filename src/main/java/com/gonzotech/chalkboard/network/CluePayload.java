package com.gonzotech.chalkboard.network;

import com.gonzotech.GonzoTechMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C: подсказка по доске резонанса (автор 22.09.2026) — блок из решения текущей
 * задачи, который надо подсветить в лотке.
 *
 * <p>Выдаётся {@code chalkboard.ResonanceClue.give(player)}: тогда же блок становится
 * доступен игроку (иначе решение не примет сервер), а клиент обводит его плитку
 * толстой белой рамкой. Обводка снимается при первом использовании блока
 * (нажал/перетащил) — состояние держит {@code client.ChalkboardClueClient}.</p>
 */
public record CluePayload(String quantityId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CluePayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "chalkboard_clue"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CluePayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CluePayload::quantityId,
            CluePayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
