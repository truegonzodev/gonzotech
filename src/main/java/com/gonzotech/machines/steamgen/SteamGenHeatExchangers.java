package com.gonzotech.machines.steamgen;

import com.gonzotech.core.registry.ModBlocks;
import com.gonzotech.machines.processing.AlloyMaterialCatalog;
import com.gonzotech.machines.processing.AlloyMaterialCatalog.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Карта драгоценных блоков-теплопередатчиков продвинутого парогенератора.
 *
 * <p>Каждый допустимый блок — это блок-хранилище, собранный из слитка; его
 * параметры берутся у материала-хоста из {@link AlloyMaterialCatalog}:
 * {@code проводимость (C)} и {@code термостойкость (H)}. Сумма {@code C+H}
 * входит в множитель выработки пара (см. {@link SteamGenMath#multiplier}).</p>
 *
 * <p>Все метальные блоки мода подключаются автоматически по хост-материалу —
 * данные уже есть в каталоге (например, платина: C 75 + H 75 = 150 → E 0.75).
 * Ванильные драгоценные блоки добавляются в {@link #VANILLA_EXCHANGERS}
 * явным списком по утверждённой таблице.</p>
 */
public final class SteamGenHeatExchangers {

    /** Параметры одного теплообменника. */
    public record Stats(int conductivity, int heatResistance) {
        /** C + H — вход в формулу множителя. */
        public int sum() {
            return conductivity + heatResistance;
        }
    }

    /**
     * Ванильные блоки-теплообменники — явный список по утверждённой таблице
     * (автор 2026-09-18). Значения — «стат, который берётся именно для
     * парогена», а НЕ фактические свойства материалов из
     * {@link AlloyMaterialCatalog} (к ним не привязаны). В формулу множителя
     * попадает только сумма C+H; разбивка C/H: H как в каталоге, C пересчитан
     * под утверждённую сумму.
     */
    private static final Map<Block, Stats> VANILLA_EXCHANGERS = new LinkedHashMap<>();
    static {
        VANILLA_EXCHANGERS.put(Blocks.IRON_BLOCK, new Stats(25, 60));     // C+H = 85
        VANILLA_EXCHANGERS.put(Blocks.COPPER_BLOCK, new Stats(75, 35));   // C+H = 110
        VANILLA_EXCHANGERS.put(Blocks.GOLD_BLOCK, new Stats(95, 30));     // C+H = 125
        VANILLA_EXCHANGERS.put(Blocks.DIAMOND_BLOCK, new Stats(8, 92));   // C+H = 100
        VANILLA_EXCHANGERS.put(Blocks.REDSTONE_BLOCK, new Stats(55, 40)); // C+H = 95
    }

    private static final Map<Block, Stats> MAP = buildMap();

    private SteamGenHeatExchangers() {
    }

    private static Map<Block, Stats> buildMap() {
        Map<Block, Stats> map = new LinkedHashMap<>();
        // Метальные блоки мода: хост-материал по id (у ключа "<metal>_block"
        // убираем суффикс "_block" — получаем id материала из каталога).
        for (Map.Entry<String, DeferredBlock<? extends Block>> entry : ModBlocks.METAL_BLOCKS.entrySet()) {
            String metalId = entry.getKey().substring(0, entry.getKey().length() - "_block".length());
            Material material = AlloyMaterialCatalog.material(
                ResourceLocation.fromNamespaceAndPath("gonzotech", metalId));
            if (material == null) continue;
            map.put(entry.getValue().get(), new Stats(material.conductivity(), material.heatResistance()));
        }
        // Ванильные блоки — явный список (статы парогена, см. VANILLA_EXCHANGERS).
        VANILLA_EXCHANGERS.forEach(map::putIfAbsent);
        return Collections.unmodifiableMap(map);
    }

    /** Параметры блока, или null, если он не теплообменник. */
    public static Stats of(Block block) {
        return MAP.get(block);
    }

    public static boolean isHeatExchanger(Block block) {
        return MAP.containsKey(block);
    }

    /** Число известных теплообменников (для отладки/документации). */
    public static int size() {
        return MAP.size();
    }
}
