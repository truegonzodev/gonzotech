package com.gonzotech.core.psyche.client;

import com.gonzotech.core.psyche.PlayerPsyche;
import com.gonzotech.core.psyche.PsycheCrisisNetwork;
import com.gonzotech.core.psyche.PsycheNetwork;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
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

import java.util.Random;

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
 *       камера продолжала бы крутиться, а кнопки нельзя было бы нажать (автор 22.09).
 *       <b>Картинка — ровно ванильный {@code DeathScreen}</b> (автор 22.09: «экран смерти всё
 *       ещё выглядит не очень»): тот же градиент-затемнение, заголовок в масштабе 2×, строка
 *       причины смерти, строка счёта, ванильные спрайты кнопок и ванильные координаты
 *       {@code width / 2 - 100} и {@code height / 4 + 72 / + 96};</li>
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

    // ─────────────────────── ложный экран смерти: ванильные константы ───────────────────────
    // Всё ниже снято с ванильного DeathScreen 1.21 (mcp-исходник): фон — renderDeathBackground,
    // текст — render(), кнопки — init(). Ничего не «на глаз»: при любом GUI-масштабе выходит
    // ровно та же раскладка, что у настоящей смерти (сверено со скрином автора «true death»).

    /** Затемнение фона: ваниль рисует градиент сверху вниз {@code 0x60500000 → 0xA0803030}. */
    private static final int DEATH_BG_TOP = 0x60500000;
    private static final int DEATH_BG_BOTTOM = 0xA0803030;

    /** Ванильные спрайты кнопок ({@code assets/minecraft/textures/gui/sprites/widget}). */
    private static final ResourceLocation SPRITE_BUTTON =
            ResourceLocation.withDefaultNamespace("widget/button");
    private static final ResourceLocation SPRITE_BUTTON_HIGHLIGHTED =
            ResourceLocation.withDefaultNamespace("widget/button_highlighted");

    /** Раскладка ванильного DeathScreen в GUI-координатах. */
    private static final int DEATH_TITLE_Y = 30;   // рисуется в масштабе 2× → визуально y ≈ 60
    private static final int DEATH_CAUSE_Y = 85;
    private static final int DEATH_SCORE_Y = 100;
    private static final int DEATH_BUTTON_WIDTH = 200;
    private static final int DEATH_BUTTON_HEIGHT = 20;
    private static final int DEATH_BUTTON_FIRST_Y = 72;   // height / 4 + 72
    private static final int DEATH_BUTTON_SECOND_Y = 96;  // height / 4 + 96
    /** Цвет текста активной ванильной кнопки (ARGB, как {@code AbstractButton}). */
    private static final int DEATH_TEXT_COLOR = 0xFFFFFFFF;

    /**
     * «Причина смерти» ложного экрана. Ванильные ключи, у которых ровно один аргумент
     * ({@code %1$s} — имя игрока): полный список ключей с одним аргументом в 1.21.4 — это
     * 31 сообщение, здесь все они, чтобы фраза была любой, как просил автор.
     */
    private static final String[] DEATH_CAUSE_KEYS = {
            "death.attack.generic", "death.attack.genericKill", "death.attack.explosion",
            "death.attack.fall", "death.attack.flyIntoWall", "death.attack.inFire",
            "death.attack.onFire", "death.attack.lava", "death.attack.drown",
            "death.attack.freeze", "death.attack.starve", "death.attack.magic",
            "death.attack.even_more_magic", "death.attack.wither", "death.attack.dragonBreath",
            "death.attack.sonic_boom", "death.attack.anvil", "death.attack.fallingBlock",
            "death.attack.cactus", "death.attack.sweetBerryBush", "death.attack.hotFloor",
            "death.attack.cramming", "death.attack.inWall", "death.attack.outOfWorld",
            "death.attack.lightningBolt", "death.attack.fireworks", "death.attack.sting",
            "death.attack.stalagmite", "death.attack.fallingStalactite", "death.attack.dryout",
            "death.attack.outsideBorder"};

    /** Фиксация камеры (после переноса на чекпойнт). */
    private static float lockedYaw;
    private static float lockedPitch;
    private static int lockTicks;

    /** Ложный экран смерти (кнопки — просто закрывают его). */
    private static boolean fakeDeath;
    /** Выбранная причина смерти: выбирается один раз при показе, чтобы не мигала каждый кадр. */
    private static Component fakeDeathCause;

    /** Кадр анимации и прошлое состояние ЛКМ. */
    private static int frame;
    private static boolean attackWasDown;

    private static final Random RNG = new Random();

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
        fakeDeathCause = rollCauseOfDeath();
        Minecraft.getInstance().mouseHandler.releaseMouse();
    }

    /** Закрыть ложный экран смерти и вернуть захват мыши (как ванильный экран при закрытии). */
    private static void closeFakeDeath() {
        fakeDeath = false;
        fakeDeathCause = null;
        Minecraft.getInstance().mouseHandler.grabMouse();
    }

    /**
     * Случайная строка «причины смерти»: либо ванильное сообщение о смерти, либо авторская
     * шутка «&lt;ник&gt; [обрезано] был убит» — обфусцированный хвост, как ванильный {@code &k}.
     */
    private static Component rollCauseOfDeath() {
        Minecraft mc = Minecraft.getInstance();
        String name = mc.player != null ? mc.player.getName().getString() : "???";
        if (RNG.nextInt(4) == 0) {
            return Component.literal(name + " ").append(
                    Component.translatable("gui.gonzotech.crisis.death_cause_obf")
                            .withStyle(ChatFormatting.OBFUSCATED));
        }
        return Component.translatable(DEATH_CAUSE_KEYS[RNG.nextInt(DEATH_CAUSE_KEYS.length)], name);
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
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        double mouseX = mouseGuiX(mc);
        double mouseY = mouseGuiY(mc);
        for (int i = 0; i < 2; i++) {
            if (isOverButton(width, height, i, mouseX, mouseY)) {
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
     * Ложный экран смерти — пиксель в пиксель как ванильный {@code DeathScreen}: градиент,
     * крупный заголовок, причина смерти, счёт и две ванильные кнопки на своих местах.
     * Порядок отрисовки тоже ванильный: фон → текст → кнопки.
     */
    private static void renderFakeDeath(GuiGraphics g, Minecraft mc, int width, int height) {
        // 1. Фон: ваниль DeathScreen.renderDeathBackground().
        g.fillGradient(0, 0, width, height, DEATH_BG_TOP, DEATH_BG_BOTTOM);

        // 2. Заголовок: ваниль рисует его в масштабе 2× в точке (width / 2 / 2, 30).
        g.pose().pushPose();
        g.pose().scale(2.0F, 2.0F, 2.0F);
        g.drawCenteredString(mc.font, Component.translatable("deathScreen.title"),
                width / 2 / 2, DEATH_TITLE_Y, DEATH_TEXT_COLOR);
        g.pose().popPose();

        // 3. Причина смерти (у 25 % случаев — обфусцированная авторская шутка) и счёт.
        if (fakeDeathCause != null) {
            g.drawCenteredString(mc.font, fakeDeathCause, width / 2, DEATH_CAUSE_Y, DEATH_TEXT_COLOR);
        }
        int score = mc.player != null ? mc.player.getScore() : 0;
        g.drawCenteredString(mc.font, Component.translatable("deathScreen.score.value",
                Component.literal(Integer.toString(score)).withStyle(ChatFormatting.YELLOW)),
                width / 2, DEATH_SCORE_Y, DEATH_TEXT_COLOR);

        // 4. Кнопки: ванильный спрайт widget/button (+_highlighted под курсором) и ванильные
        //    координаты; подпись — ванильные ключи, чтобы текст совпадал в любой локали.
        Component[] labels = {
                Component.translatable("deathScreen.respawn"),
                Component.translatable("deathScreen.titleScreen")
        };
        double mouseX = mouseGuiX(mc);
        double mouseY = mouseGuiY(mc);
        int x = buttonX(width);
        for (int i = 0; i < labels.length; i++) {
            int y = buttonY(height, i);
            boolean hovered = mc.screen == null && isOverButton(width, height, i, mouseX, mouseY);
            g.blitSprite(RenderType::guiTextured,
                    hovered ? SPRITE_BUTTON_HIGHLIGHTED : SPRITE_BUTTON,
                    x, y, DEATH_BUTTON_WIDTH, DEATH_BUTTON_HEIGHT);
            // Ваниль центрирует подпись как (y + y + height - 9) / 2 + 1 — для 20 px это y + 6.
            g.drawCenteredString(mc.font, labels[i],
                    x + DEATH_BUTTON_WIDTH / 2, y + (DEATH_BUTTON_HEIGHT - 8) / 2, DEATH_TEXT_COLOR);
        }
    }

    // ─────────────────────────── геометрия кнопок ───────────────────────────

    /** X ванильной кнопки: {@code width / 2 - 100} (при ширине 200 это одно и то же). */
    private static int buttonX(int width) {
        return width / 2 - DEATH_BUTTON_WIDTH / 2;
    }

    /** Y ванильных кнопок: {@code height / 4 + 72} и {@code height / 4 + 96}. */
    private static int buttonY(int height, int index) {
        return height / 4 + (index == 0 ? DEATH_BUTTON_FIRST_Y : DEATH_BUTTON_SECOND_Y);
    }

    private static boolean isOverButton(int width, int height, int index, double mouseX, double mouseY) {
        int x = buttonX(width);
        int y = buttonY(height, index);
        return mouseX >= x && mouseX <= x + DEATH_BUTTON_WIDTH
                && mouseY >= y && mouseY <= y + DEATH_BUTTON_HEIGHT;
    }

    /** Курсор в GUI-координатах (ванильная формула масштабирования окна). */
    private static double mouseGuiX(Minecraft mc) {
        return mc.mouseHandler.xpos() * (double) mc.getWindow().getGuiScaledWidth()
                / (double) mc.getWindow().getScreenWidth();
    }

    private static double mouseGuiY(Minecraft mc) {
        return mc.mouseHandler.ypos() * (double) mc.getWindow().getGuiScaledHeight()
                / (double) mc.getWindow().getScreenHeight();
    }
}
