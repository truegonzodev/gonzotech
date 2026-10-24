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
 * (time constant ~0.1 с). Погружение, как и везде в 281+, считается по КАМЕРЕ:
 * {@code y < blockY + height}, не по ногам.
 *
 * <p>0.3.285 — два корня прежней «дискретности» (автор: «в жиже сразу 100 %,
 * после выхода ещё 5 секунд 100 % и резкое исчезновение; ступеней не было»):
 * <ol>
 *   <li>GUI-пайплайн 1.21.2+ (RenderPipeline) снимает цвет при создании типа,
 *       поэтому {@code RenderSystem.setShaderColor} до blit'а <b>игнорируется</b>:
 *       альфа рампы никогда не попадала в вершины, оверлей рисовался на своей
 *       собственной альфе PNG, а «пропадал» по guard'у percent==0 — ровно 100/0.
 *       Теперь прозрачность печатается в вершинный цвет: 13-аргументный
 *       {@code blit(..., texUW, texVH, texW, texH, color)} ванили 1.21.4;</li>
 *   <li>смена среды — кросс-фейд: текущий оверлей уезжает в «уходящий» слот
 *       и гасится той же ускоряющейся кривой, новый нарастает с 0 % — без
 *       перескока суммы (автор: «оверлей воды уходит вниз, лава нарастать»).
 *       </li>
 * </ol>
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

    /**
     * Один канал оверлея: id, целевая плотность 0..100 % (тикается), счётчик
     * тиков угасания (кривая 1,2,3…) и рисуемая (сглаженная) плотность.
     */
    private static final class Slot {
        String id;
        int percent;
        int fadeTicks;
        float shown;

        boolean idle() {
            return id == null || (percent == 0 && shown <= 0.0F);
        }
    }

    /** Нарастающий/плато-канал. */
    private static final Slot ACTIVE = new Slot();
    /** Уходящий канал — кросс-фейд при смене среды (0.3.285). */
    private static final Slot OUTGOING = new Slot();
    /** Момент последнего кадра — шаг сглаживания по wall-clock (не по тикам). */
    private static long lastNano;
    /** Общий счётчик тиков для листания кадров (одна фаза на все среды). */
    private static int tickCounter;

    private FluidScreenOverlays() {
    }

    /** Сессийная гигиена: оверлей не должен переехать в следующий мир. */
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ACTIVE.id = null;
        ACTIVE.percent = 0;
        ACTIVE.fadeTicks = 0;
        ACTIVE.shown = 0.0F;
        OUTGOING.id = null;
        OUTGOING.percent = 0;
        OUTGOING.fadeTicks = 0;
        OUTGOING.shown = 0.0F;
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
        // 0.3.285 (автор): кросс-фейд сред — «вода уходит вниз, лава нарастает».
        if (id != null && ACTIVE.id != null && !ACTIVE.id.equals(id)) {
            OUTGOING.id = ACTIVE.id;
            OUTGOING.percent = ACTIVE.percent;
            OUTGOING.shown = ACTIVE.shown;
            OUTGOING.fadeTicks = 0;
            ACTIVE.percent = 0;
            ACTIVE.shown = 0.0F;
        }
        // Уходящий канал гасится ускоряющейся кривой.
        if (OUTGOING.id != null) {
            OUTGOING.fadeTicks++;
            OUTGOING.percent = Math.max(0, OUTGOING.percent - OUTGOING.fadeTicks);
        }
        if (id != null) {
            ACTIVE.id = id;
            ACTIVE.fadeTicks = 0;
            ACTIVE.percent = Math.min(100, ACTIVE.percent + 1);
        } else if (ACTIVE.id != null && ACTIVE.percent > 0) {
            // 0.3.284 (автор): угасание ускоряющееся — минус k % на k-м тике (1, 2, 3 …).
            ACTIVE.fadeTicks++;
            ACTIVE.percent = Math.max(0, ACTIVE.percent - ACTIVE.fadeTicks);
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
        // Тиковая цель меняется ступенями по 5 % — рисуемое значение догоняет её
        // непрерывно (экспоненциальное сглаживание, константа ~0.1 с, wall-clock).
        float k = (float) (1.0 - Math.exp(-dt * 10.0));
        follow(OUTGOING, k);
        follow(ACTIVE, k);
        if (ACTIVE.id == null && OUTGOING.id == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        drawSlot(g, OUTGOING);
        drawSlot(g, ACTIVE);
    }

    /** Шаг следования: shown догоняет percent; на нуле канала канал гаснет. */
    private static void follow(Slot slot, float k) {
        if (slot.id == null) {
            return;
        }
        slot.shown += (slot.percent - slot.shown) * k;
        if (Math.abs(slot.percent - slot.shown) < 0.02F) {
            slot.shown = slot.percent; // на плато — без «дребезга»
        }
        if (slot.percent == 0 && slot.shown < 0.05F) {
            slot.shown = 0.0F;
            slot.id = null;
            slot.fadeTicks = 0;
        }
    }

    private static void drawSlot(GuiGraphics g, Slot slot) {
        if (slot.id == null || slot.shown <= 0.0F) {
            return;
        }
        int width = g.guiWidth();
        int height = g.guiHeight();
        String id = slot.id;
        Anim anim = ANIMS.computeIfAbsent(id, FluidScreenOverlays::readAnim);
        float alpha = Mth.clamp(slot.shown / 100.0F, 0.0F, 1.0F);

        int v = (tickCounter / anim.frameTicks) % anim.frames * anim.h;
        ResourceLocation tex = TEXTURES.computeIfAbsent(id, rid ->
                ResourceLocation.fromNamespaceAndPath("gonzotech", "textures/gui/" + rid + ".png"));
        // Альфа ПЕЧАТАЕТСЯ В ВЕРШИНЫ (13-й параметр blit'а 1.21.4): GUI-пайплайн
        // 1.21.2+ фиксирует цвет при создании RenderType, и RenderSystem
        // .setShaderColor до blit'а игнорируется — та была природа «100 % или
        // ничего» (автор, 24.10).
        int argb = ((int) (alpha * 255.0F) << 24) | 0xFFFFFF;
        g.pose().pushPose();
        g.pose().scale(width / (float) anim.w, height / (float) anim.h, 1.0F);
        g.blit(RenderType::guiTextured, tex, 0, 0, 0.0F, (float) v,
                anim.w, anim.h, anim.w, anim.h, anim.w, anim.h * anim.frames, argb);
        g.pose().popPose();
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
