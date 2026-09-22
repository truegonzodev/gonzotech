package com.gonzotech.core.psyche.client;

import com.gonzotech.core.psyche.PlayerPsyche;
import com.gonzotech.core.psyche.PsycheCrisisNetwork;
import com.gonzotech.core.psyche.PsycheNetwork;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Клиентская часть эффектов кризиса (спека автора 22.09.2026): то, что нельзя показать
 * серверно — экранные оверлеи, фиксация камеры и «использование предмета» по ЛКМ.
 *
 * <ul>
 *   <li><b>Экранный эффект</b> (кризис &gt; 74 %): анимированный оверлей, непрозрачность
 *       {@code 10 % + 2 % за каждый процент выше 74} (потолок 62 % на 100 %). Кадры анимации
 *       листаются вручную: GUI-текстуры не проходят через атлас, поэтому {@code .mcmeta}
 *       для них не «тикает» — плейсхолдер-анимация лежит в файле, а номер кадра считает мод;</li>
 *   <li><b>Фиксация камеры</b> после переноса на чекпойнт: 0.3 с игрок не может шевелить камерой;</li>
 *   <li><b>Ложный экран смерти</b> (кризис &gt; 87 %): рисуется оверлеем, а не {@code Screen} —
 *       поэтому WASD продолжают работать, а кнопки просто убирают экран (клик по ним
 *       перехватывается до ванильной обработки). Курсор при этом <b>отпускается</b>
 *       ({@code mouseHandler.releaseMouse()}), иначе он был бы приклеен к центру экрана,
 *       камера продолжала бы крутиться, а кнопки нельзя было бы нажать (автор 22.09);</li>
 *   <li><b>ЛКМ как «использование предмета»</b>: нажатие (в том числе по воздуху) уходит на сервер
 *       пакетом {@code crisis_click} — сервер решает, подменить ли предмет слотами.</li>
 * </ul>
 */
public final class PsycheCrisisClient {

    /** Экранный эффект: порог и шаг непрозрачности (совпадают с PsycheCrisis на сервере). */
    private static final int OVERLAY_MIN_PERCENT = 74;
    private static final float OVERLAY_BASE_ALPHA = 0.10F;
    private static final float OVERLAY_ALPHA_PER_PERCENT = 0.02F;

    /** Анимированный оверлей: {@code 64x64} кадров, 24 кадра в вертикальной ленте. */
    private static final ResourceLocation TEX_SCREEN_EFFECT =
            ResourceLocation.fromNamespaceAndPath("gonzotech", "textures/gui/crisis_screen_effect.png");
    private static final int FRAME_SIZE = 64;
    private static final int FRAME_COUNT = 24;
    private static final int FRAME_TICKS = 2;

    /** Фиксация камеры (после переноса на чекпойнт). */
    private static float lockedYaw;
    private static float lockedPitch;
    private static int lockTicks;

    /** Ложный экран смерти (кнопки — просто закрывают его). */
    private static boolean fakeDeath;

    /** Кадр анимации и прошлое состояние ЛКМ. */
    private static int frame;
    private static boolean attackWasDown;

    private PsycheCrisisClient() {
    }

    // ─────────────────────────── сеть → клиент ───────────────────────────

    /** Зафиксировать взгляд на {@code ticks} тиков (0.3 с = 6). */
    public static void lockCamera(float yaw, float pitch, int ticks) {
        lockedYaw = yaw;
        lockedPitch = pitch;
        lockTicks = Math.max(0, ticks);
    }

    /**
     * Показать ложный экран смерти. Курсор отпускаем (автор 22.09: «курсор не появляется,
     * камера всё ещё двигается, кнопки не нажать») — пока мышь не захвачена, камера стоит,
     * а по нашим кнопкам можно кликать. WASD продолжают работать: экран остаётся оверлеем.
     */
    public static void showFakeDeath() {
        fakeDeath = true;
        Minecraft.getInstance().mouseHandler.releaseMouse();
    }

    /** Закрыть ложный экран смерти и вернуть захват мыши (как ванильный экран при закрытии). */
    private static void closeFakeDeath() {
        fakeDeath = false;
        Minecraft.getInstance().mouseHandler.grabMouse();
    }

    // ─────────────────────────── тик ───────────────────────────

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.isPaused()) {
            return;
        }
        frame = (frame + 1) % (FRAME_COUNT * FRAME_TICKS);

        // Фиксация камеры: держим сохранённый взгляд, пока тикает таймер.
        if (lockTicks > 0) {
            player.setYRot(lockedYaw);
            player.setYHeadRot(lockedYaw);
            player.setXRot(Mth.clamp(lockedPitch, -90.0F, 90.0F));
            lockTicks--;
        }

        // ЛКМ (в том числе по воздуху) — «использование предмета» для подмены слотами.
        // Пока висит ложный экран смерти, клики принадлежат кнопкам, а не миру.
        boolean attackDown = mc.options.keyAttack.isDown();
        if (attackDown && !attackWasDown && !fakeDeath) {
            PacketDistributor.sendToServer(new PsycheCrisisNetwork.ClickPayload());
        }
        attackWasDown = attackDown;

        // Держим курсор отпущенным: после alt-tab (возврат фокуса) ваниль может захватить
        // мышь обратно — тогда курсор пропал бы, а камера снова начала бы крутиться.
        if (fakeDeath) {
            mc.mouseHandler.releaseMouse();
        }
    }

    // ─────────────────────────── клики по кнопкам ложной смерти ───────────────────────────

    /**
     * Кнопки ложного экрана смерти: WASD работают (это оверлей, а не Screen), поэтому клики
     * ловим до ванильной обработки и гасим событие — иначе клик ещё и ударил бы по миру.
     */
    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (!fakeDeath || event.getAction() != 1) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || event.getButton() != 0) {
            return;
        }
        int[] rect = buttonRect(mc);
        double mouseX = mc.mouseHandler.xpos() * (double) mc.getWindow().getGuiScaledWidth()
                / (double) mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * (double) mc.getWindow().getGuiScaledHeight()
                / (double) mc.getWindow().getScreenHeight();
        for (int i = 0; i < 2; i++) {
            int bx = rect[0];
            int by = rect[1] + i * (rect[3] + 4);
            if (mouseX >= bx && mouseX <= bx + rect[2] && mouseY >= by && mouseY <= by + rect[3]) {
                closeFakeDeath();
                event.setCanceled(true);
                return;
            }
        }
    }

    // ─────────────────────────── рендер ───────────────────────────

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int width = g.guiWidth();
        int height = g.guiHeight();

        renderScreenEffect(g, width, height);
        if (fakeDeath) {
            renderFakeDeath(g, mc, width, height);
        }
    }

    /** Кризисный «налёт на экран»: анимированная текстура + дымка, растущая с процентами. */
    private static void renderScreenEffect(GuiGraphics g, int width, int height) {
        PsycheNetwork.PsycheDataPayload data = PsycheNetwork.CLIENT_DATA;
        if (data == null) {
            return;
        }
        int percent = PlayerPsyche.pointsPercent(data.crisis());
        if (percent <= OVERLAY_MIN_PERCENT) {
            return;
        }
        float alpha = OVERLAY_BASE_ALPHA + (percent - OVERLAY_MIN_PERCENT) * OVERLAY_ALPHA_PER_PERCENT;
        alpha = Mth.clamp(alpha, 0.0F, 1.0F);

        // Анимированный кадр (номер кадра считаем сами — см. шапку класса).
        // Автор 22.09: оверлей «дробился на мелкие квадраты». Причина — блит брал источник
        // размером со весь экран, и текстура повторялась мозаикой. Теперь берём ровно кадр
        // 64×64, а на весь экран его растягивает масштаб позы: PNG любого размера ляжет так же.
        int v = (frame / FRAME_TICKS) * FRAME_SIZE;
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        g.pose().pushPose();
        g.pose().scale(width / (float) FRAME_SIZE, height / (float) FRAME_SIZE, 1.0F);
        g.blit(RenderType::guiTextured, TEX_SCREEN_EFFECT, 0, 0, 0.0F, (float) v,
                FRAME_SIZE, FRAME_SIZE, FRAME_SIZE, FRAME_SIZE * FRAME_COUNT);
        g.pose().popPose();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        // Дымка поверх — та самая «непрозрачность 10 % (+2 % за процент)».
        int tint = ((int) (alpha * 255.0F) << 24) | 0x1A0000;
        g.fill(0, 0, width, height, tint);
    }

    /**
     * Ложный экран смерти: плотное красное затемнение (как у ванильного экрана — прицел и HUD
     * под ним почти не видны) и две безобидные кнопки.
     */
    private static void renderFakeDeath(GuiGraphics g, Minecraft mc, int width, int height) {
        g.fill(0, 0, width, height, 0xD8100000);
        Component title = Component.translatable("gui.gonzotech.crisis.death_title");
        int titleWidth = mc.font.width(title);
        g.drawString(mc.font, title, (width - titleWidth) / 2, height / 2 - 40, 0xFFFFFF, true);

        int[] rect = buttonRect(mc);
        Component[] labels = {
                Component.translatable("gui.gonzotech.crisis.death_respawn"),
                Component.translatable("gui.gonzotech.crisis.death_menu")
        };
        for (int i = 0; i < labels.length; i++) {
            int x = rect[0];
            int y = rect[1] + i * (rect[3] + 4);
            g.fill(x, y, x + rect[2], y + rect[3], 0xFF3A3A3A);
            g.fill(x + 1, y + 1, x + rect[2] - 1, y + rect[3] - 1, 0xFF8A8A8A);
            int textWidth = mc.font.width(labels[i]);
            g.drawString(mc.font, labels[i],
                    x + (rect[2] - textWidth) / 2, y + (rect[3] - 8) / 2, 0xFF303030, false);
        }
    }

    /** Геометрия кнопок ложного экрана: {x, y, ширина, высота}. */
    private static int[] buttonRect(Minecraft mc) {
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        int buttonWidth = 200;
        int buttonHeight = 20;
        return new int[]{(width - buttonWidth) / 2, height / 2 - 20, buttonWidth, buttonHeight};
    }
}
