package com.gonzotech.machines.miner;

import com.gonzotech.GonzoTechMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;

/**
 * Синк меток автошахтера клиентам (0.3.256): полный снимок {@link MarkerService}
 * при изменении и при входе игрока. Клиентский кэш живёт в
 * {@code com.gonzotech.machines.client.MarkerClient}.
 */
public final class MarkerNetwork {

    private MarkerNetwork() {
    }

    public record Entry(long id, BlockPos pos, int kind, long expiresAtTick, List<ItemStack> ores) {
    }

    public record MarkersPayload(List<Entry> markers) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<MarkersPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "miner_markers"));

        public static final StreamCodec<RegistryFriendlyByteBuf, MarkersPayload> STREAM_CODEC =
            StreamCodec.of(MarkersPayload::encode, MarkersPayload::decode);

        private static void encode(RegistryFriendlyByteBuf buf, MarkersPayload payload) {
            buf.writeVarInt(payload.markers.size());
            for (Entry entry : payload.markers) {
                buf.writeVarLong(entry.id());
                BlockPos.STREAM_CODEC.encode(buf, entry.pos());
                buf.writeVarInt(entry.kind());
                buf.writeVarLong(entry.expiresAtTick());
                buf.writeVarInt(entry.ores().size());
                for (ItemStack ore : entry.ores()) {
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, ore);
                }
            }
        }

        private static MarkersPayload decode(RegistryFriendlyByteBuf buf) {
            int count = buf.readVarInt();
            List<Entry> markers = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                long id = buf.readVarLong();
                BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                int kind = buf.readVarInt();
                long expiresAtTick = buf.readVarLong();
                int oreCount = buf.readVarInt();
                List<ItemStack> ores = new ArrayList<>(oreCount);
                for (int j = 0; j < oreCount; j++) {
                    ores.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
                }
                markers.add(new Entry(id, pos, kind, expiresAtTick, ores));
            }
            return new MarkersPayload(markers);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(MarkersPayload.TYPE, MarkersPayload.STREAM_CODEC,
            MarkerNetwork::handleClient);
    }

    private static void handleClient(MarkersPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
            com.gonzotech.machines.client.MarkerClient.accept(payload));
    }

    /** Полный снимок меток уровня одним пакетом. */
    public static MarkersPayload snapshot(MarkerService service) {
        List<Entry> entries = new ArrayList<>();
        for (MinerMarker marker : service.markers()) {
            List<ItemStack> ores = new ArrayList<>(marker.ores().size());
            for (Item ore : marker.ores()) {
                ores.add(new ItemStack(ore, 1));
            }
            entries.add(new Entry(marker.id(), marker.pos(), marker.kind().ordinal(),
                marker.expiresAtTick(), ores));
        }
        return new MarkersPayload(entries);
    }

    public static void sendTo(ServerPlayer player, MarkerService service) {
        PacketDistributor.sendToPlayer(player, snapshot(service));
    }

    public static void broadcast(ServerLevel level, MarkerService service) {
        MarkersPayload payload = snapshot(service);
        for (ServerPlayer player : level.players()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /** Клиентское восстановление {@link MinerMarker} из пейлоада. */
    public static MinerMarker toMarker(Entry entry) {
        List<Item> ores = new ArrayList<>(entry.ores().size());
        for (ItemStack stack : entry.ores()) {
            if (!stack.isEmpty()) ores.add(stack.getItem());
        }
        MinerMarker.Kind kind = switch (entry.kind()) {
            case 0 -> MinerMarker.Kind.STATION;
            case 1 -> MinerMarker.Kind.ORE;
            default -> MinerMarker.Kind.TEST;
        };
        return new MinerMarker(entry.id(), entry.pos(), kind, entry.expiresAtTick(), ores);
    }
}
