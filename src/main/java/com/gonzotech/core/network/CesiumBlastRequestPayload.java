package com.gonzotech.core.network;

import com.gonzotech.GonzoTechMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Клиент -> сервер: «игрок держит ЛКМ на цезиевой руде в pos, дай взрыв».
 * Сервер повторно проверяет блок/открытость/КД и вызывает взрыв — клиент
 * не может выполнить урон авторитетно.
 */
public record CesiumBlastRequestPayload(BlockPos pos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CesiumBlastRequestPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "cesium_blast_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CesiumBlastRequestPayload> STREAM_CODEC =
        StreamCodec.composite(BlockPos.STREAM_CODEC, CesiumBlastRequestPayload::pos, CesiumBlastRequestPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}