package com.gonzotech.core.worldgen;

import com.gonzotech.GonzoTechMod;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.slf4j.Logger;

/**
 * Инжект «Пустоши» в пресет оверворлда ПОСЛЕ сборки реестров (автор, 2026-10-12).
 *
 * <p>На {@code ServerAboutToStartEvent} реестры датапака уже полностью
 * собраны (в 0.3.188 здесь же успешно захватывался холдер биома): холдеры
 * bound, {@code RegistrySetBuilder} не участвует, трекинга «висячих» ссылок
 * нет. Берём готовый объект {@code minecraft:overworld} из реестра
 * {@code multi_noise_biome_source_parameter_list} и дописываем в его список
 * 6 климат-точек desolation — дальше ванильный nearest-neighbor расставляет
 * биом сам (сетка чанка → цвета/F3//locate/конверсия/радиация).
 *
 * <p>Повторяется на каждый старт сервера: каждая загрузка мира строит свежие
 * реестры датапака, и мутация применяется к свежему объекту.
 *
 * <p>Fail-safe: любая ошибка → пресет остаётся ванильным, краша нет.
 */
@EventBusSubscriber(modid = GonzoTechMod.MOD_ID)
public final class DesolationPresetInject {

    private static final Logger LOGGER = LogUtils.getLogger();

    private DesolationPresetInject() {
    }

    /** Ключ биома «Пустошь» (data/gonzotech/worldgen/biome/desolation.json). */
    private static final ResourceKey<Biome> DESOLATION = ResourceKey.create(
        Registries.BIOME,
        ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "desolation"));

    /** Ключ ванильного пресета параметр-листов оверворлда. */
    private static final ResourceKey<MultiNoiseBiomeSourceParameterList> OVERWORLD_LIST =
        ResourceKey.create(
            Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST,
            ResourceLocation.withDefaultNamespace("overworld"));

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        try {
            MinecraftServer server = event.getServer();
            Holder<Biome> desolation = server.registryAccess()
                .lookupOrThrow(Registries.BIOME)
                .get(DESOLATION)
                .orElse(null);
            if (desolation == null) {
                LOGGER.warn("[gonzotech] desolation: биом не найден в реестре — "
                    + "инжект в пресет пропущен");
                return;
            }
            MultiNoiseBiomeSourceParameterList list = server.registryAccess()
                .lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .get(OVERWORLD_LIST)
                .map(Holder::value)
                .orElse(null);
            if (list instanceof DesolationPresetExt ext) {
                ext.gonzotech$addDesolationPoints(desolation);
                LOGGER.info("[gonzotech] desolation: +6 климат-точек в пресет "
                    + "оверворлда (после сборки реестров)");
            } else {
                LOGGER.warn("[gonzotech] desolation: пресет оверворлда недоступен "
                    + "для инъекции (объект не трансформирован mixin'ом)");
            }
        } catch (Throwable t) {
            LOGGER.warn("[gonzotech] desolation: инжект в пресет не выполнен: {}",
                String.valueOf(t));
        }
    }

    /**
     * Дозаправка ЖИВОГО источника биома оверворлда (фикс перезахода, 0.3.195).
     *
     * <p>На перезаходе (интегрированный сервер) к ServerAboutToStartEvent
     * датапак-реестры могут быть ещё не собраны, и источник биома строится из
     * level.dat/пресета без наших точек: /locate «не находит» биом, стоя в нём,
     * а новые чанки генерируются ванильными. Здесь (сразу после постройки
     * ServerLevel, реестры гарантированно готовы) берём живой
     * {@code MultiNoiseBiomeSource} генератора и дописываем точки прямо в его
     * поле — /locate и генерация новых чанков читают именно его. Только
     * OVERWORLD: в Незер свой пресет, и трогать его нельзя. Идемпотентно:
     * если точки уже есть (первое создание мира) — no-op.
     */
    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        try {
            if (!(event.getLevel() instanceof ServerLevel serverLevel)
                    || serverLevel.dimension() != Level.OVERWORLD) {
                return;
            }
            Holder<Biome> desolation = serverLevel.registryAccess()
                .lookupOrThrow(Registries.BIOME)
                .get(DESOLATION)
                .orElse(null);
            if (desolation == null) {
                LOGGER.warn("[gonzotech] desolation: биом не найден при "
                    + "LevelEvent.Load — дозаправка источника пропущена");
                return;
            }
            BiomeSource source = serverLevel.getChunkSource().getGenerator().getBiomeSource();
            if (source instanceof DesolationPresetExt ext) {
                ext.gonzotech$addDesolationPoints(desolation);
                LOGGER.info("[gonzotech] desolation: климат-точки живого источника "
                    + "оверворлда проверены/дописаны (LevelEvent.Load)");
            }
        } catch (Throwable t) {
            LOGGER.warn("[gonzotech] desolation: дозаправка источника не выполнена: {}",
                String.valueOf(t));
        }
    }
}
