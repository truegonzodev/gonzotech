package com.gonzotech.chalkboard.client;

import com.gonzotech.core.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * Клиентская сторона {@code DiscoveryActivationPayload}: проигрывает анимацию
 * «выброса» предмета на экран (как тотем бессмертия) с моделью самого «Открытия».
 * <p>
 * Это чистый визуал через {@code GameRenderer.displayItemActivation} — тот же
 * вызов, что делает ванильный тотем. Бессмертие не даётся, партиклов нет.
 */
public final class DiscoveryActivationClient {

    private DiscoveryActivationClient() {
    }

    /** Проиграть анимацию тотема для «Открытия» под номером {@code num} (1..16). */
    public static void play(int num) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameRenderer == null) return;
        ItemStack icon = new ItemStack(ModItems.getDiscoveryItem(num).get());
        mc.gameRenderer.displayItemActivation(icon);
    }
}
