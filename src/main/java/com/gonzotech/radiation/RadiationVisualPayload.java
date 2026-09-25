package com.gonzotech.radiation;

import com.gonzotech.GonzoTechMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Server snapshot of nearby visual radiation sources. */
public record RadiationVisualPayload(List<Source> sources) implements CustomPacketPayload {
    public static final Type<RadiationVisualPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "radiation_visual"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RadiationVisualPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> {
                int size = Math.min(payload.sources.size(), 128);
                buf.writeVarInt(size);
                for (int i = 0; i < size; i++) {
                    Source s = payload.sources.get(i);
                    buf.writeLong(s.pos());
                    buf.writeFloat(s.emission());
                }
            }, buf -> {
                int size = Math.min(buf.readVarInt(), 128);
                List<Source> out = new ArrayList<>(size);
                for (int i = 0; i < size; i++) out.add(new Source(buf.readLong(), buf.readFloat()));
                return new RadiationVisualPayload(out);
            });

    public record Source(long pos, float emission) {}

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
