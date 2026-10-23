package com.gonzotech.radiation;

import com.gonzotech.GonzoTechMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * (0.3.277) Клиентская копия серверного инвентарного журнала: сумма предметов
 * (Σ count по всем слотам открытого меню + курсор) — ровно по той же формуле,
 * что считает {@code RadiationSystem}. Сравнение сервер/клиент отделяет
 * физический дюп (растёт серверная) от призрака (расходится только клиентская).
 */
public record InventoryLedgerPayload(long clientSum) implements CustomPacketPayload {
    public static final Type<InventoryLedgerPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "inventory_ledger"));
    public static final StreamCodec<RegistryFriendlyByteBuf, InventoryLedgerPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> buf.writeLong(payload.clientSum()),
                    buf -> new InventoryLedgerPayload(buf.readLong()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
