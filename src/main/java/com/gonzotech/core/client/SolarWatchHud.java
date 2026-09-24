package com.gonzotech.core.client;

import com.gonzotech.core.registry.ModItems;
import com.gonzotech.sunevent.SunEventData;
import com.gonzotech.sunevent.client.SunEventClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Клиентский HUD «Солнечных часов» (суневеты, финал ветки; автор 2026-09-18):
 * пока часы в главной или дополнительной руке, над хотбаром строка
 * «День: X, следующий Солнечный кризис — Y. Эффективность солнечных панелей: Z%».
 *
 * <p>Все три числа доступны клиенту локально: X — ванильный день мира,
 * Y — {@code nextEventDay} (ВАНИЛЬНЫЙ день, синкнет {@code SunEventNetwork}),
 * Z — деградация Солнца, та же формула, что и у сервера
 * ({@link SunEventData#solarMultiplierAt(long)}) от синкнутого
 * {@code suneventDays}. Если сегодня багровый день E — Y=X (это и есть
 * «текущий кризис», расписание пересчитается на смене дня).
 */
public final class SolarWatchHud {

    private static final int HUD_COLOR = 0xFFFFA640;

    private SolarWatchHud() {
    }

    /** Рисует строку над хотбаром (на строку выше спидометра — не сталкиваемся). */
    @SubscribeEvent
    public static void onRenderGui(final RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null || mc.options.hideGui || !isHoldingWatch(player)) {
            return;
        }

        long day = mc.level.getDayTime() / 24000L;
        long efficiencyPercent = Math.round(SunEventData.solarMultiplierAt(SunEventClient.suneventDays) * 100.0D);
        Component line = Component.translatable(
            "hud.gonzotech.solar_watch", day, SunEventClient.nextEventDay, efficiencyPercent);
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = mc.font;
        int x = (graphics.guiWidth() - font.width(line)) / 2;
        // Спидометр сидит на guiHeight−59; часы — на строку выше, оба видны сразу.
        int y = graphics.guiHeight() - 71;
        graphics.drawString(font, line, x, y, HUD_COLOR, true);
    }

    /** Солнечные часы — или «Телифон», который совмещает все приборы (автор 22.09). */
    private static boolean isHoldingWatch(Player player) {
        return player.getMainHandItem().is(ModItems.SOLAR_WATCH.get())
            || player.getOffhandItem().is(ModItems.SOLAR_WATCH.get())
            || player.getMainHandItem().is(ModItems.TELIFON.get())
            || player.getOffhandItem().is(ModItems.TELIFON.get());
    }
}
