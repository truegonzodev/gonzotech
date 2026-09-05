package com.gonzotech.machines.network;

import com.gonzotech.GonzoTechMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Синхронизация живого потока провода в HUD гаечного ключа.
 * <p>
 * Провод пассивен и потока не хранит — фактический объём за тик считает
 * {@link FlowTracker} на СЕРВЕРЕ (только там реально идёт слив). Клиент не может
 * знать поток сам, поэтому пока игрок держит ключ и смотрит на трубу, он раз в
 * несколько тиков шлёт {@link RequestPayload} с позицией трубы; сервер отвечает
 * {@link FlowPayload} с двумя числами — сколько ушло в каждый конец ОСИ провода
 * по мировым сторонам (напр. для оси X → восток и запад). Направления, а не
 * «лево/право», чтобы показания не зависели от того, откуда смотрит игрок.
 */
public final class PipeFlowNetwork {

    private PipeFlowNetwork() {
    }

    /** Клиент → сервер: «покажи поток трубы в этой позиции». */
    public record RequestPayload(BlockPos pos) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<RequestPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "pipe_flow_request"));

        public static final StreamCodec<RegistryFriendlyByteBuf, RequestPayload> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, RequestPayload::pos, RequestPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Сервер → клиент: поток трубы. {@code posAmount} — объём, ушедший в
     * положительный конец оси (+X=восток, +Y=верх, +Z=юг), {@code negAmount} — в
     * отрицательный (−X=запад, −Y=низ, −Z=север). {@code axis3d} —
     * {@link Direction.Axis#ordinal()} (0=X,1=Y,2=Z).
     */
    public record FlowPayload(BlockPos pos, int axis3d, int posAmount, int negAmount) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<FlowPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "pipe_flow"));

        public static final StreamCodec<RegistryFriendlyByteBuf, FlowPayload> STREAM_CODEC =
            StreamCodec.composite(
                BlockPos.STREAM_CODEC, FlowPayload::pos,
                ByteBufCodecs.VAR_INT, FlowPayload::axis3d,
                ByteBufCodecs.VAR_INT, FlowPayload::posAmount,
                ByteBufCodecs.VAR_INT, FlowPayload::negAmount,
                FlowPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Регистрация обоих пакетов. Вызывается из {@code GonzoTechMod#registerPayloads}. */
    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(
            RequestPayload.TYPE,
            RequestPayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    respond(player, payload.pos());
                }
            }));

        registrar.playToClient(
            FlowPayload.TYPE,
            FlowPayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() ->
                com.gonzotech.machines.client.WrenchHud.acceptFlow(payload)));
    }

    /** Максимальная дистанция (блоков), в пределах которой отвечаем на запрос. */
    private static final double MAX_DISTANCE_SQR = 8.0D * 8.0D;

    private static void respond(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (pos.distToCenterSqr(player.getX(), player.getY(), player.getZ()) > MAX_DISTANCE_SQR) return;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PipeBlock)) return;

        Direction.Axis axis = state.getValue(PipeBlock.AXIS);
        Direction posDir = positiveOf(axis);
        Direction negDir = posDir.getOpposite();

        int[] flow = FlowTracker.get(level, pos);
        int posAmount = flow[posDir.get3DDataValue()];
        int negAmount = flow[negDir.get3DDataValue()];

        PacketDistributor.sendToPlayer(player, new FlowPayload(pos, axis.ordinal(), posAmount, negAmount));
    }

    /** Положительная мировая сторона оси: +X=восток, +Y=верх, +Z=юг. */
    private static Direction positiveOf(Direction.Axis axis) {
        return switch (axis) {
            case X -> Direction.EAST;
            case Y -> Direction.UP;
            case Z -> Direction.SOUTH;
        };
    }
}
