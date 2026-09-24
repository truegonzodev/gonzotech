package com.gonzotech.core.psyche;

import com.gonzotech.GonzoTechMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Сеть эффектов кризиса (спека автора 22.09.2026): то, что видит только сам игрок.
 *
 * <ul>
 *   <li><b>S2C {@code crisis_camera_lock}</b> — после «переноса на чекпойнт» на 0.3 с фиксируем
 *       взгляд в сохранённом положении: клиент держит yaw/pitch и не даёт двигать камерой;</li>
 *   <li><b>S2C {@code crisis_fake_death}</b> — ложный экран смерти (кризис &gt; 87 %): красное
 *       затемнение и две кнопки, игрок при этом жив и ходит — экран рисуется оверлеем, не Screen'ом;</li>
 *   <li><b>C2S {@code crisis_click}</b> — «использовал предмет» по ЛКМ (в том числе по воздуху):
 *       сервер по этому сигналу крутит шанс подмены предмета слотами (кризис &gt; 56 %).</li>
 * </ul>
 */
public final class PsycheCrisisNetwork {

    private PsycheCrisisNetwork() {
    }

    /** S2C: зафиксировать взгляд на N тиков (0.3 с = 6). */
    public record CameraLockPayload(float yaw, float pitch, int ticks) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<CameraLockPayload> TYPE =
                new CustomPacketPayload.Type<>(
                        ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "crisis_camera_lock"));

        public static final StreamCodec<RegistryFriendlyByteBuf, CameraLockPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.FLOAT, CameraLockPayload::yaw,
                        ByteBufCodecs.FLOAT, CameraLockPayload::pitch,
                        ByteBufCodecs.VAR_INT, CameraLockPayload::ticks,
                        CameraLockPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** S2C: показать ложный экран смерти. */
    public record FakeDeathPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<FakeDeathPayload> TYPE =
                new CustomPacketPayload.Type<>(
                        ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "crisis_fake_death"));

        public static final StreamCodec<RegistryFriendlyByteBuf, FakeDeathPayload> STREAM_CODEC =
                StreamCodec.unit(new FakeDeathPayload());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** C2S: игрок нажал ЛКМ (в том числе по воздуху) — «использование предмета». */
    public record ClickPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ClickPayload> TYPE =
                new CustomPacketPayload.Type<>(
                        ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "crisis_click"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ClickPayload> STREAM_CODEC =
                StreamCodec.unit(new ClickPayload());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                CameraLockPayload.TYPE,
                CameraLockPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.gonzotech.core.psyche.client.PsycheCrisisClient.lockCamera(
                                payload.yaw(), payload.pitch(), payload.ticks()))
        );
        registrar.playToClient(
                FakeDeathPayload.TYPE,
                FakeDeathPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.gonzotech.core.psyche.client.PsycheCrisisClient.showFakeDeath())
        );
        registrar.playToServer(
                ClickPayload.TYPE,
                ClickPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                        PsycheCrisis.onItemUsed(player);
                    }
                })
        );
    }

    /** Зафиксировать взгляд игрока на {@code ticks} тиков. */
    public static void sendCameraLock(net.minecraft.server.level.ServerPlayer player,
                                      float yaw, float pitch, int ticks) {
        PacketDistributor.sendToPlayer(player, new CameraLockPayload(yaw, pitch, ticks));
    }

    /** Показать ложный экран смерти. */
    public static void sendFakeDeath(net.minecraft.server.level.ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new FakeDeathPayload());
    }
}
