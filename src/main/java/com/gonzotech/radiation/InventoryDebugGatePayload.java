package com.gonzotech.radiation;

import com.gonzotech.GonzoTechMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 0.3.291: гейт клиентских частей дебаггера кликов. Мастер — серверный TOML
 * ({@code radiationInventoryDebug}); клиент узнаёт о нём при входе в мир,
 * чтобы {@code [SET-SLOT]} и клиентская половина {@code [SLOT-WRITE]}
 * печатались ровно в тех же сессиях, что и серверный лог (автор: «одним
 * включаемым через toml дебаггером»).
 */
public record InventoryDebugGatePayload(boolean enabled) implements CustomPacketPayload {
    public static final Type<InventoryDebugGatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "inventory_debug_gate"));
    public static final StreamCodec<RegistryFriendlyByteBuf, InventoryDebugGatePayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> buf.writeBoolean(payload.enabled()),
                    buf -> new InventoryDebugGatePayload(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
