package com.gonzotech.core.fluid.client;

import com.gonzotech.core.fluid.ModFluids;
import com.gonzotech.core.registry.ModBlocks;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.FluidState;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.io.Reader;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Спрайтовые экранные оверлеи сред (0.3.283, автор): у каждой жидкости — свой
 * анимированный оверлей по образцу {@code crisis_screen_effect}: PNG-полоса
 * кадров 256×144 + {@code .mcmeta} с зацикленной анимацией, прозрачность —
 * альфа пикселей, умноженная на плотность погружения.
 *
 * <p>Плотность (0.3.284, автор): <b>появление</b> — линейно +1 %/тик (0→100 %),
 * <b>угасание</b> — ускоряющейся кривой 1, 2, 3 … %/тик: на k-м тике после выхода
 * цель теряет k процентов. Цель пересчитывается раз в тик, но рисуемое значение
 * — непрерывный «follow» цели с экспоненциальным сглаживанием по wall-clock
 * (time constant ~0.1 с): ни ступенек по 5 %, ни дискретного «есть/нет» —
 * плавная альфа на любом fps. Погружение, как и везде в 281+, считается по
 * КАМЕРЕ: {@code y < blockY + height}, не по ногам.
 *
 * <p>Мёртвая жижа (0.3.284, автор: «добавить подобный оверлей для dead_slime»)
 * получает спрайт {@code dead_slime_overlay} тем же механизмом; экранная
 * дымка 0..11 % из {@code DeadSlimeFogClient} остаётся как есть.
 *
 * <p>Механика кадрирования кадров — ручная, как у кризиса: GUI-текстуры не
 * проходят через атлас, поэтому {@code .mcmeta} для них не «тикает»; ширина/
 * высота кадра, frametime и число кадров читаются один раз из того же
 * {@code .mcmeta} (плейсхолдеры автора не сломают рендер: при отсутствии или
 * битости анимационного блока берётся один статичный кадр 256×144).
 */
public final class FluidScreenOverlays {

    /** Кадр оверлея: 256×144 (16:9), значения — дефолт и для плейсхолдеров. */
    private static final int FRAME_W = 256;
    private static final int FRAME_H = 144;
    /** Шаг анимации по умолчанию, если .mcmeta молчит (как у кризиса: 2 тика/кадр). */
    private static final int DEFAULT_FRAME_TICKS = 2;

    /** id → текстура оверлея (gonzotech:textures/gui/<id>.png). */
    private static final Map<String, ResourceLocation> TEXTURES = new ConcurrentHashMap<>();
    /** id → геометрия анимации из .mcmeta; null-экземпляр не создаём — см. Anim. */
    private static final Map<String, Anim> ANIMS = new ConcurrentHashMap<>();
    /** Жидкость → id оверлея; ленивая (реестр живёт позже класса). */
    private static volatile Map<Fluid, String> fluidToOverlay;

    /** Текущий оверлей и его ЦЕЛЕВАЯ плотность 0..100 % (пересчёт раз в тик). */
    private static String currentId;
    private static int percent;
    /** Сколько тиков длится угасание: цель теряет k % на k-м тике (кривая 1,2,3…). */
    private static int fadeTicks;
    /** Отрисовываемая (сглаженная) плотность; доживает до нуля и на «пустой» цели. */
    private static float shownPercent;
    /** id оверлея, который сейчас рисуется (живёт, пока shownPercent не стечёт). */
    private static String shownId;
    /** Момент последнего кадра — шаг сглаживания по wall-clock (не по тикам). */
    private static long lastNano;
    /** Общий счётчик тиков для листания кадров (одна фаза на все среды). */
    private static int tickCounter;

    private FluidScreenOverlays() {
    }

    /** Сессийная гигиена: оверлей не должен переехать в следующий мир. */
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        currentId = null;
        percent = 0;
        fadeTicks = 0;
        shownPercent = 0;
        shownId = null;
        lastNano = 0;
        tickCounter = 0;
    }

    // ─────────────────────────── карта жидкостей ───────────────────────────

    private static Map<Fluid, String> fluidMap() {
        Map<Fluid, String> local = fluidToOverlay;
        if (local == null) {
            synchronized (FluidScreenOverlays.class) {
                local = fluidToOverlay;
                if (local == null) {
                    // Гонзо-жидкости: названия оверлеев — по списку автора
                    // (vodka = этанол, rectificate = дистиллят). 14 пар — Map.of
                    // умеет только 10, поэтому ofEntries (сборка автора 24.10).
                    local = Map.ofEntries(
                            Map.entry(ModFluids.ETHANOL.get(), "vodka_overlay"),
                            Map.entry(ModFluids.FLOWING_ETHANOL.get(), "vodka_overlay"),
                            Map.entry(ModFluids.FORMALDEHYDE.get(), "formaldehyde_overlay"),
                            Map.entry(ModFluids.FLOWING_FORMALDEHYDE.get(), "formaldehyde_overlay"),
                            Map.entry(ModFluids.SULFURIC_ACID.get(), "sulfuric_acid_overlay"),
                            Map.entry(ModFluids.FLOWING_SULFURIC_ACID.get(), "sulfuric_acid_overlay"),
                            Map.entry(ModFluids.DISTILLATE.get(), "rectificate_overlay"),
                            Map.entry(ModFluids.FLOWING_DISTILLATE.get(), "rectificate_overlay"),
                            Map.entry(ModFluids.MASH.get(), "mash_overlay"),
                            Map.entry(ModFluids.FLOWING_MASH.get(), "mash_overlay"),
                            Map.entry(ModFluids.WORT.get(), "wort_overlay"),
                            Map.entry(ModFluids.FLOWING_WORT.get(), "wort_overlay"),
                            Map.entry(ModFluids.MOLTEN_CORIUM.get(), "corium_overlay"),
                            Map.entry(ModFluids.FLOWING_MOLTEN_CORIUM.get(), "corium_overlay"));
                    fluidToOverlay = local;
                }
            }
        }
        return local;
    }

    /**
     * Оверлей под камерой: ванильные вода/лава — water_overlay/lava_overlay,
     * семь гонзо-жидкостей — свои. Мёртвая жижа здесь НЕ обрабатывается:
     * у неё собственная дымка 0..11 % в {@code DeadSlimeFogClient}.
     */
    private static String resolveOverlayId(Camera camera, ClientLevel level) {
        // 0.3.284: мёртвая жижа — блок, не жидкость; спрайт-оверлей ей по соседству
        // с туманом (гейт высоты не нужен: полный блок, вход = «камера в блоке»).
        if (camera.getBlockAtCamera().is(ModBlocks.DEAD_SLIME_BLOCK.get())) {
            return "dead_slime_overlay";
        }
        BlockPos pos = camera.getBlockPosition();
        FluidState state = level.getFluidState(pos);
        if (state.isEmpty()) {
            return null;
        }
        if (camera.getPosition().y >= (double) pos.getY() + state.getHeight(level, pos)) {
            return null; // глаза над поверхностью — оверлей не показываем (гейт по камере)
        }
        Fluid fluid = state.getType();
        if (fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER) {
            return "water_overlay";
        }
        if (fluid == Fluids.LAVA || fluid == Fluids.FLOWING_LAVA) {
            return "lava_overlay";
        }
        return fluidMap().get(fluid);
    }

    // ─────────────────────────── тик: ramp ±1 %/тик ───────────────────────────

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) {
            return;
        }
        tickCounter++;
        String id = resolveOverlayId(mc.gameRenderer.getMainCamera(), level);
        if (id != null) {
            // Смена среды без сброса: из сусла в кислоту — плавный переход плотности.
            currentId = id;
            fadeTicks = 0;
            percent = Math.min(100, percent + 1);
        } else if (percent > 0) {
            // 0.3.284 (автор): угасание ускоряющееся — минус k % на k-м тике (1, 2, 3 …).
            fadeTicks++;
            percent = Math.max(0, percent - fadeTicks);
        }
    }

    // ─────────────────────────── экран: blit по образцу кризиса ───────────────────────────

    /**
     * Рисуем на финальном GUI-проходе, как кризисный оверлей и вейл жижи:
     * F1 прячет HUD, а муть среды — состояние игрока, не элемент интерфейса.
     */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        long now = System.nanoTime();
        double dt = lastNano == 0L ? 0.0 : Math.min(0.5, (now - lastNano) / 1.0E9);
        lastNano = now;
        // 0.3.284 (автор: «появление дискретное, либо есть либо его нет»): тиковая
        // цель перескакивает на 5 % за раз — на глаз это ступени. Рисуемое значение
        // догоняет цель непрерывно (экспоненциальное сглаживание, константа ~0.1 c);
        // после обнуления цели показ ещё «стекает» к нулю — плавны и вход, и выход.
        // Сглаживание на wall-clock, без API partial-tick — совпадает на любом fps.
        if (currentId != null) {
            shownId = currentId;
        }
        float k = (float) (1.0 - Math.exp(-dt * 10.0));
        shownPercent += (percent - shownPercent) * k;
        if (percent == 0) {
            if (shownPercent < 0.05F) {
                shownPercent = 0.0F;
                shownId = null;
                fadeTicks = 0;
            }
        } else if (percent - shownPercent < 0.02F && shownPercent - percent < 0.02F) {
            shownPercent = percent; // на плато — без «дребезга»
        }
        if (shownPercent <= 0.0F || shownId == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int width = g.guiWidth();
        int height = g.guiHeight();
        String id = shownId;
        Anim anim = ANIMS.computeIfAbsent(id, FluidScreenOverlays::readAnim);
        float alpha = Mth.clamp(shownPercent / 100.0F, 0.0F, 1.0F);

        int v = (tickCounter / anim.frameTicks) % anim.frames * anim.h;
        ResourceLocation tex = TEXTURES.computeIfAbsent(id, rid ->
                ResourceLocation.fromNamespaceAndPath("gonzotech", "textures/gui/" + rid + ".png"));
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        g.pose().pushPose();
        g.pose().scale(width / (float) anim.w, height / (float) anim.h, 1.0F);
        g.blit(RenderType::guiTextured, tex, 0, 0, 0.0F, (float) v,
                anim.w, anim.h, anim.w, anim.h * anim.frames);
        g.pose().popPose();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    // ─────────────────────────── .mcmeta ───────────────────────────

    /** Геометрия анимации, снятая с .mcmeta (width/height/frametime/frames). */
    private record Anim(int w, int h, int frameTicks, int frames) {
    }

    /**
     * Как у кризиса: атлас GUI не «тикает» mcmeta, поэтому кадр листает мод.
     * Здесь из .mcmeta берём хотя бы размер кадра и число кадров — автор сможет
     * доклеить в полосу новые кадры, не трогая код. Ошибки чтения не валят рендер:
     * один статичный кадр 256×144.
     */
    private static Anim readAnim(String id) {
        try {
            ResourceLocation meta = ResourceLocation.fromNamespaceAndPath(
                    "gonzotech", "textures/gui/" + id + ".png.mcmeta");
            Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(meta);
            JsonElement root;
            try (reader) {
                root = GsonHelper.parse(reader);
            }
            JsonObject rootObj = root.isJsonObject() ? root.getAsJsonObject() : null;
            JsonObject animation = rootObj != null && rootObj.has("animation")
                    ? GsonHelper.getAsJsonObject(rootObj, "animation") : null;
            if (animation == null) {
                return new Anim(FRAME_W, FRAME_H, DEFAULT_FRAME_TICKS, 1);
            }
            int w = GsonHelper.getAsInt(animation, "width", FRAME_W);
            int h = GsonHelper.getAsInt(animation, "height", FRAME_H);
            int frametime = GsonHelper.getAsInt(animation, "frametime", DEFAULT_FRAME_TICKS);
            int frames = animation.has("frames")
                    ? Math.max(1, GsonHelper.getAsJsonArray(animation, "frames").size())
                    : 1;
            if (w <= 0 || h <= 0 || frametime <= 0) {
                return new Anim(FRAME_W, FRAME_H, DEFAULT_FRAME_TICKS, 1);
            }
            return new Anim(w, h, frametime, frames);
        } catch (Exception e) {
            return new Anim(FRAME_W, FRAME_H, DEFAULT_FRAME_TICKS, 1);
        }
    }

    /** Только для пинов: множество id, обязанных иметь PNG+mcmeta в ассетах. */
    public static Set<String> requiredAssets() {
        return Set.of("water_overlay", "lava_overlay", "sulfuric_acid_overlay",
                "formaldehyde_overlay", "vodka_overlay", "rectificate_overlay",
                "mash_overlay", "wort_overlay", "corium_overlay", "dead_slime_overlay");
    }
}
