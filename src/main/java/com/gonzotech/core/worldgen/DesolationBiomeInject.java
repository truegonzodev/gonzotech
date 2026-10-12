package com.gonzotech.core.worldgen;

import com.gonzotech.GonzoTechMod;
import com.mojang.logging.LogUtils;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

/**
 * Десоляция в оверворлде — runtime-инжект биома (автор, 2026-10-12).
 *
 * <p>Датапак-пути добавить биом в оверворлд без оверрайда измерения не
 * существует: {@code multi_noise_biome_source_parameter_list} принимает только
 * {@code {"preset":...}}, {@code dimension/overworld.json} ломает суперплоский,
 * mixin в {@code OverworldBiomeBuilder} ломает ванильный bootstrap. Поэтому
 * биом подменяется на этапе ГЕНЕРАЦИИ:
 * {@code MultiNoiseBiomeSourceDesolationMixin} перехватывает
 * {@code getNoiseBiome(TargetPoint)} и, если климат-точка в одном из четырёх
 * гиперкубов, возвращает десоляцию. Генератор не трогаем → суперплоский и
 * прочие типы мира остаются собой.
 *
 * <p>Захват холдера — ЛЕНИВЫЙ: на {@code ServerAboutToStartEvent} реестры
 * датапака ещё не загружены (они грузятся в {@code loadLevels()} позже),
 * поэтому холдер дозахватывается при первом обращении из генерации, когда
 * реестр гарантированно готов.
 *
 * <p>Fail-safe: холдера нет или любая ошибка → инъекция молчит, остаётся
 * ванильный биом; краш невозможен. Диагностический лог отмечает каждую
 * ступень (hook вызван / холдер захвачен / первая инъекция), чтобы по логам
 * видеть, какая ступень сработала.
 */
@EventBusSubscriber(modid = GonzoTechMod.MOD_ID)
public final class DesolationBiomeInject {

    private static final Logger LOGGER = LogUtils.getLogger();

    private DesolationBiomeInject() {
    }

    /** Ключ биома «Пустошь» (data/gonzotech/worldgen/biome/desolation.json). */
    public static final ResourceKey<Biome> DESOLATION = ResourceKey.create(
        Registries.BIOME,
        ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "desolation"));

    /**
     * Четыре климат-гиперкуба десоляции (те же параметры, что были в удалённом
     * dimension-оверрайде): температура, влажность, континентальность, эрозия,
     * странность. Глубина (depth) намеренно не участвует в проверке — у
     * поверхности она не ровно 0, а десоляция задумана как биом всей колонки.
     */
    public static final Climate.ParameterPoint[] BOXES = {
        box(0.15F, 0.50F, -0.35F, -0.05F,  0.20F,  0.50F, 0.15F, 0.45F, -0.15F,  0.25F),
        box(0.15F, 0.50F,  0.00F,  0.25F,  0.40F,  0.70F, 0.35F, 0.65F, -0.45F, -0.15F),
        box(0.15F, 0.50F, -0.35F, -0.05F, -0.19F, -0.11F, 0.15F, 0.45F, -0.15F,  0.25F),
        box(0.15F, 0.50F,  0.00F,  0.25F, -0.19F, -0.11F, 0.35F, 0.65F, -0.45F, -0.15F),
    };

    /** Холдер биома десоляции (null, пока не захвачен лениво). */
    private static volatile Holder<Biome> desolation;

    /** Сервер для ленивого захвата холдера (обнуляется на остановке). */
    private static volatile MinecraftServer server;

    /** Логируем один раз: hook генерации вызван. */
    public static final AtomicBoolean HOOK_LOGGED = new AtomicBoolean();

    /** Логируем один раз: первая успешная инъекция биома. */
    public static final AtomicBoolean INJECT_LOGGED = new AtomicBoolean();

    /** Логируем один раз: ленивый захват не удался (биом не найден в реестре). */
    private static final AtomicBoolean CAPTURE_FAIL_LOGGED = new AtomicBoolean();

    /**
     * Холдер десоляции; при первом обращении из генерации — ленивый захват
     * (к этому моменту реестр датапака уже загружен).
     */
    public static Holder<Biome> get() {
        Holder<Biome> h = desolation;
        return h != null ? h : capture();
    }

    /** Старт сервера: реестры датапака ЕЩЁ не готовы — только запоминаем сервер. */
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        server = event.getServer();
        Holder<Biome> h = capture(); // попытка раньше времени — обычно null
        LOGGER.info("[gonzotech] desolation: ServerAboutToStart, holder={} (null = "
            + "дозахватится лениво из генерации)", h == null ? "null" : "captured");
    }

    /** Остановка сервера: обнуляем всё, чтобы не держать ссылки и флаги. */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        server = null;
        desolation = null;
        HOOK_LOGGED.set(false);
        INJECT_LOGGED.set(false);
        CAPTURE_FAIL_LOGGED.set(false);
    }

    private static synchronized Holder<Biome> capture() {
        if (desolation != null) {
            return desolation;
        }
        MinecraftServer s = server;
        if (s == null) {
            return null; // сервер ещё не стартовал — инъекция молчит
        }
        try {
            desolation = s.registryAccess()
                .lookupOrThrow(Registries.BIOME)
                .getOrThrow(DESOLATION);
            LOGGER.info("[gonzotech] desolation biome holder captured");
        } catch (Throwable t) {
            if (CAPTURE_FAIL_LOGGED.compareAndSet(false, true)) {
                LOGGER.warn("[gonzotech] desolation: биом не найден в реестре: {}",
                    String.valueOf(t));
            }
        }
        return desolation;
    }

    private static Climate.ParameterPoint box(float t0, float t1, float h0, float h1,
                                              float c0, float c1, float e0, float e1,
                                              float w0, float w1) {
        return new Climate.ParameterPoint(
            Climate.Parameter.span(t0, t1),
            Climate.Parameter.span(h0, h1),
            Climate.Parameter.span(c0, c1),
            Climate.Parameter.span(e0, e1),
            Climate.Parameter.point(0.0F), // depth: в проверке не участвует
            Climate.Parameter.span(w0, w1),
            0L);
    }
}
