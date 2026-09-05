package com.gonzotech.chalkboard.network;

import com.gonzotech.GonzoTechMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C: сервер просит клиент проиграть визуальную анимацию «выброса» предмета
 * (как у тотема бессмертия) для «Открытия» под номером {@code discoveryNumber}.
 * <p>
 * Отправляется ТОЛЬКО в момент реальной активации открытия (первое использование,
 * когда рецепты действительно разблокировались) — чтобы эффектная анимация не
 * играла на повторный ПКМ уже активированного открытия. Это чистый визуал: тотем
 * бессмертия здесь ни при чём, бессмертие не даётся, партиклов нет.
 */
public record DiscoveryActivationPayload(int discoveryNumber) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DiscoveryActivationPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "discovery_activation"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DiscoveryActivationPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DiscoveryActivationPayload::discoveryNumber,
            DiscoveryActivationPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
