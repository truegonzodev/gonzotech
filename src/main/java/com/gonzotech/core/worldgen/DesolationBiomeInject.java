package com.gonzotech.core.worldgen;

import com.gonzotech.GonzoTechMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/**
 * Десоляция в оверворлде — runtime-инжект биома (автор, 2026-10-12).
 *
 * <p>Датапак-пути добавить биом в оверворлд без оверрайда измерения не
 * существует: {@code multi_noise_biome_source_parameter_list} принимает только
 * {@code {"preset":...}} (инлайн-список биомов не парсится — подтверждено
 * крашем), а {@code dimension/overworld.json} ломает суперплоский мир.Mixin в
 * {@code OverworldBiomeBuilder} тоже не годится — он выполняется во время
 * ванильного bootstrap'а, где модовых биомов ещё нет.
 *
 * <p>Поэтому биом подменяется на этапе ГЕНЕРАЦИИ (когда реестр датапака уже
 * загружен): {@code MultiNoiseBiomeSourceDesolationMixin} перехватывает
 * {@code getNoiseBiome(TargetPoint)} и, если климат-точка попадает в один из
 * четырёх гиперкубов десоляции, возвращает её вместо ванильного биома.
 * Генератор не переопределяется → суперплоский/большие/расширенный пресеты
 * остаются собой.
 *
 * <p>Fail-safe: холдер биома захватывается на старте сервера; если его нет
 * (или любая ошибка) — инъекция молчит и остаётся ванильный биом, краша нет.
 */
@EventBusSubscriber(modid = GonzoTechMod.MOD_ID)
public final class DesolationBiomeInject {

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

    /** Холдер биома десоляции (null, пока сервер не стартовал или биом не найден). */
    private static volatile Holder<Biome> desolation;

    /** @return холдер десоляции или {@code null} — инъекция тогда молчит. */
    public static Holder<Biome> get() {
        return desolation;
    }

    /**
     * Захватываем холдер на старте сервера — к этому моменту реестр датапака
     * уже загружен (десоляция существует), но спавн-чанки ещё не сгенерированы.
     */
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        try {
            desolation = event.getServer().registryAccess()
                .lookupOrThrow(Registries.BIOME)
                .getOrThrow(DESOLATION);
        } catch (Throwable t) {
            desolation = null; // fail-safe: инъекция останется немой
        }
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
