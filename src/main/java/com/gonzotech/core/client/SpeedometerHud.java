package com.gonzotech.core.client;

import com.gonzotech.core.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.Locale;

/**
 * Клиентский HUD спидометра. Значение снимается каждый клиентский тик из
 * фактической скорости игрока (или транспорта, на котором он едет) и рисуется
 * над хотбаром, только когда спидометр находится в одной из рук.
 */
public final class SpeedometerHud {

    private static final int HUD_COLOR = 0xFFE8C95A;
    private static double speedBlocksPerSecond;

    private SpeedometerHud() {
    }

    /** Обновляет измерение ровно раз за клиентский тик. */
    @SubscribeEvent
    public static void onClientTick(final ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || !isHoldingSpeedometer(player)) {
            speedBlocksPerSecond = 0.0D;
            return;
        }

        // В транспорте скорость самого Player обычно нулевая, поэтому измеряем
        // root vehicle. Вне транспорта это тот же игрок.
        Entity movingEntity = player.isPassenger() ? player.getRootVehicle() : player;
        // Только ГОРИЗОНТАЛЬ: вертикальная компонента — осадочный нос гравитации
        // (~0.078 б/тик постоянно при стоянии на земле), из-за неё «спидометр»
        // показывал ~1.6 б/с абсолютно неподвижному игроку.
        var dm = movingEntity.getDeltaMovement();
        speedBlocksPerSecond = Math.hypot(dm.x, dm.z) * 20.0D;
    }

    /** Рисует строку непосредственно над хотбаром, не занимая action bar. */
    @SubscribeEvent
    public static void onRenderGui(final RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null || mc.options.hideGui || !isHoldingSpeedometer(player)) {
            return;
        }

        String value = String.format(Locale.ROOT, "%.1f", speedBlocksPerSecond);
        Component line = Component.translatable("hud.gonzotech.speedometer", value);
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = mc.font;
        int x = (graphics.guiWidth() - font.width(line)) / 2;
        // Hotbar заканчивается у нижней границы; надпись находится над уровнем XP
        // и не заменяет ванильные сообщения action bar.
        int y = graphics.guiHeight() - 59;
        graphics.drawString(font, line, x, y, HUD_COLOR, true);
    }

    private static boolean isHoldingSpeedometer(Player player) {
        return player.getMainHandItem().is(ModItems.SPEEDOMETER.get())
            || player.getOffhandItem().is(ModItems.SPEEDOMETER.get());
    }
}
