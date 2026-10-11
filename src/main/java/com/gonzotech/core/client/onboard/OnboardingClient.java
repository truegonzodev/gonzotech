package com.gonzotech.core.client.onboard;

import com.gonzotech.GonzoTechMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Клиентский онбординг: кнопка «О моде» в главном меню, авто-показ экрана-введения
 * при первом запуске и приветствие в чат при первом входе в каждый новый мир.
 *
 * <p>Флаг «онбординг показан» хранится глобальным маркер-файлом в каталоге игры.
 * Приветствие показывается один раз на мир: маркер пишется в каталог игры с
 * именем, производным от идентификатора мира (папка одиночной игры или адрес
 * сервера), поэтому каждый новый мир получает своё приветствие.
 */
@EventBusSubscriber(modid = GonzoTechMod.MOD_ID, value = Dist.CLIENT)
public final class OnboardingClient {

    private static final String ONBOARDED_FILE = "gonzotech_onboarded.marker";
    private static final String WELCOMED_PREFIX = "gonzotech_welcomed_";

    private static boolean autoShownThisSession = false;
    /** -1 = не определено; >0 = тики до отправки; 0 = отправить на следующем тике. */
    private static int welcomeDelay = -1;

    private OnboardingClient() {
    }

    /** Кнопка «О моде» справа от «Одиночная игра» + авто-показ при первом запуске. */
    @SubscribeEvent
    public static void onTitleScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen title)) {
            return;
        }
        int x = title.width / 2 + 104;
        // Поднята на 16 px выше базовой позиции (height/4 + 48 → + 32).
        int y = title.height / 4 + 32;
        GonzoInfoButton button = new GonzoInfoButton(x, y, 20, b ->
            Minecraft.getInstance().setScreen(new GonzoOnboardingScreen()));
        button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.translatable("gui.gonzotech.onboarding.button.tooltip")));
        event.addListener(button);

        if (!autoShownThisSession && !hasMarker(ONBOARDED_FILE)) {
            autoShownThisSession = true;
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> mc.setScreen(new GonzoOnboardingScreen()));
        }
    }

    /** Приветствие в чат при первом входе в каждый новый мир. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            welcomeDelay = -1;
            return;
        }
        // Не шлём, пока открыт экран (меню/загрузка).
        if (mc.screen != null) {
            return;
        }
        if (welcomeDelay == -1) {
            String worldKey = currentWorldKey(mc);
            if (worldKey == null) {
                return;
            }
            String marker = WELCOMED_PREFIX + sanitize(worldKey) + ".marker";
            if (hasMarker(marker)) {
                return;
            }
            writeMarker(marker);
            // Небольшая задержка, чтобы чат-HUD успел инициализироваться.
            welcomeDelay = 20;
            return;
        }
        if (welcomeDelay > 0) {
            welcomeDelay--;
            return;
        }
        sendWelcome(mc);
        welcomeDelay = -1;
    }

    private static void sendWelcome(Minecraft mc) {
        boolean hardcore = mc.level.getLevelData().isHardcore();
        GameType mode = mc.gameMode != null ? mc.gameMode.getPlayerMode() : GameType.SURVIVAL;
        String key = hardcore ? "gui.gonzotech.welcome.hardcore"
            : (mode == GameType.SURVIVAL || mode == GameType.ADVENTURE)
                ? "gui.gonzotech.welcome.survival"
                : "gui.gonzotech.welcome.generic";
        Component message = Component.translatable(key);
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.displayClientMessage(message, false);
            }
        });
    }

    /** Идентификатор текущего мира: папка одиночной игры или адрес сервера. */
    private static String currentWorldKey(Minecraft mc) {
        if (mc.level == null) {
            return null;
        }
        var server = mc.getSingleplayerServer();
        if (server != null) {
            // Имя папки одиночной игры клиенту недоступно (storageSource protected),
            // поэтому ключ мира — стабильная точка спавна.
            var spawn = mc.level.getLevelData().getSpawnPos();
            return "sp_" + spawn.getX() + "_" + spawn.getY() + "_" + spawn.getZ();
        }
        var data = mc.getCurrentServer();
        if (data != null && data.ip != null && !data.ip.isEmpty()) {
            return "mp_" + data.ip;
        }
        return null;
    }

    /** Вызывается онбординг-экраном при закрытии. */
    public static void markOnboarded() {
        writeMarker(ONBOARDED_FILE);
    }

    private static String sanitize(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            sb.append((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9') || c == '-' || c == '_' ? c : '_');
        }
        return sb.toString();
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
