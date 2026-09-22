package com.gonzotech.core.psyche.client;

import com.gonzotech.core.registry.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * «Тремор» на клиенте: пока эффект активен, камеру дёргает раз в 1–10 тиков на
 * {@code 0.3–2.1 × уровень} градуса по yaw и pitch (спека автора 22.09.2026).
 *
 * <p>Дёргаем ровно на клиенте, потому что поворот игрока — клиентская прерогатива:
 * серверный {@code setYRot} клиент проигнорирует, а локальные изменения уезжают на
 * сервер обычными пакетами движения. Уровень берём «как видно в игре»: «Тремор I» =
 * {@code amplifier + 1}.</p>
 *
 * <p>Класс регистрируется только на клиенте (см. {@code GonzoTechMod}, блок
 * {@code FMLEnvironment.dist.isClient()}).</p>
 */
public final class PsycheTremorClient {

    /** Тиков до следующего рывка камеры. */
    private static int nextJitter;

    private PsycheTremorClient() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.isPaused()) {
            return;
        }

        MobEffectInstance tremor = player.getEffect(ModEffects.TREMOR);
        if (tremor == null) {
            nextJitter = 0;
            return;
        }
        if (nextJitter > 0) {
            nextJitter--;
            return;
        }
        // Раз в 1–10 тиков — случайный рывок.
        nextJitter = 1 + player.getRandom().nextInt(10);

        int level = tremor.getAmplifier() + 1;                       // «Тремор I» = 1
        float magnitude = (float) ((0.3 + player.getRandom().nextDouble() * 1.8) * level);
        float yaw = player.getRandom().nextBoolean() ? magnitude : -magnitude;
        float pitch = (float) ((player.getRandom().nextDouble() * 2.0 - 1.0) * magnitude);

        player.setYRot(player.getYRot() + yaw);
        player.setYHeadRot(player.getYRot());
        player.setXRot(Mth.clamp(player.getXRot() + pitch, -90.0F, 90.0F));
    }
}
