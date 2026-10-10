package com.gonzotech.core.client.onboard;

import com.gonzotech.GonzoTechMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerLoggedInEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Клиентский онбординг первого запуска: кнопка «О моде» в главном меню,
 * авто-показ экрана-введения при первом запуске и приветствие в чат при первом
 * входе в мир (пересказ описания выбранного режима).
 *
 * <p>Флаги «онбординг показан» и «приветствие отправлено» хранятся маркер-
 * файлами в каталоге игры, чтобы не показывать повторно.
 */
@EventBusSubscriber(modid = GonzoTechMod.MOD_ID, value = Dist.CLIENT)
public final class OnboardingClient {

    private static final String ONBOARDED_FILE = "gonzotech_onboarded.marker";
    private static final String WELCOMED_FILE = "gonzotech_welcomed.marker";

    private static boolean autoShownThisSession = false;

    private OnboardingClient() {
    }

    /** Кнопка «О моде» справа от «Одиночная игра» + авто-показ при первом запуске. */
    @SubscribeEvent
    public static void onTitleScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen title)) {
            return;
        }
        int x = title.width / 2 + 104;
        int y = title.height / 4 + 48;
        GonzoInfoButton button = new GonzoInfoButton(x, y, 20, b ->
            Minecraft.getInstance().setScreen(new GonzoOnboardingScreen()));
        button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.translatable("gui.gonzotech.onboarding.button.tooltip")));
        event.addListener(button);

        if (!autoShownThisSession && !hasMarker(ONBOARDED_FILE)) {
            autoShownThisSession = true;
            Minecraft mc = Minecraft.getInstance();
            mc.tell(() -> mc.setScreen(new GonzoOnboardingScreen()));
        }
    }

    /** Приветствие в чат при самом первом входе в мир. */
    @SubscribeEvent
    public static void onFirstJoin(ClientPlayerLoggedInEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || hasMarker(WELCOMED_FILE)) {
            return;
        }
        writeMarker(WELCOMED_FILE);
        boolean hardcore = mc.player.level().getLevelData().isHardcore();
        GameType mode = mc.player.gameMode != null
            ? mc.player.gameMode.getGameModeForPlayer() : GameType.SURVIVAL;
        String key = hardcore ? "gui.gonzotech.welcome.hardcore"
            : (mode == GameType.SURVIVAL || mode == GameType.ADVENTURE)
                ? "gui.gonzotech.welcome.survival"
                : "gui.gonzotech.welcome.generic";
        Component message = Component.translatable(key);
        mc.tell(() -> mc.player.displayClientMessage(message, false));
    }

    /** Вызывается онбординг-экраном при закрытии. */
    public static void markOnboarded() {
        writeMarker(ONBOARDED_FILE);
    }

    private static Path markerPath(String name) {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(name);
    }

    private static boolean hasMarker(String name) {
        try {
            return Files.exists(markerPath(name));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static void writeMarker(String name) {
        try {
            Files.write(markerPath(name), new byte[0]);
        } catch (IOException | RuntimeException e) {
            // Не критично: просто покажем в следующий раз ещё раз.
        }
    }
}
