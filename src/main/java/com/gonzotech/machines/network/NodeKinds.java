package com.gonzotech.machines.network;

import com.gonzotech.GonzoTechMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Сортировка узлов труб по ТИПУ, а не по конкретным id (правило автора
 * 0.3.255): для парогена, турбины и доменной печи подходят ВСЕ поколения
 * нужных узлов — любой универсальный узел любого поколения заменяет любой
 * другой универсальный узел, экранированные (III поколение) равноправны
 * обычным. Поколения будут и дальше (тир-99 и т.д.) — правило держится на
 * соглашении об именах путей реестра:
 *
 * <ul>
 *   <li>{@code *_heat_node} — теплотруба (GTH);</li>
 *   <li>{@code *_wire_node} — провод (GTU);</li>
 *   <li>{@code *_water_node} — водная труба;</li>
 *   <li>{@code *_steam_node} — паровая труба;</li>
 *   <li>{@code *_universal_fluid_node} — универсальная жидкостная труба;</li>
 *   <li>{@code *_universal_node} — универсальный узел;</li>
 *   <li>{@code *_item_node} — предметная труба.</li>
 * </ul>
 *
 * Суффиксы разрешают коллизии: {@code *_universal_fluid_node} не является
 * {@code *_universal_node}. Реактор этим правилом НЕ пользуется — ему нужны
 * только экранированные узлы (III поколение), см. ReactorStructure.
 */
public final class NodeKinds {

    private static final String HEAT = "_heat_node";
    private static final String WIRE = "_wire_node";
    private static final String WATER = "_water_node";
    private static final String STEAM = "_steam_node";
    private static final String UNIVERSAL_FLUID = "_universal_fluid_node";
    private static final String UNIVERSAL = "_universal_node";
    private static final String ITEM = "_item_node";

    private NodeKinds() {
    }

    private static boolean kind(Block block, String suffix) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return id.getNamespace().equals(GonzoTechMod.MOD_ID) && id.getPath().endsWith(suffix);
    }

    /** Теплотруба любого поколения (GTH). */
    public static boolean isHeat(Block block) {
        return kind(block, HEAT);
    }

    public static boolean isHeat(BlockState state) {
        return isHeat(state.getBlock());
    }

    /** Провод любого поколения (GTU). */
    public static boolean isWire(Block block) {
        return kind(block, WIRE);
    }

    public static boolean isWire(BlockState state) {
        return isWire(state.getBlock());
    }

    /** Водная труба любого поколения. */
    public static boolean isWater(Block block) {
        return kind(block, WATER);
    }

    public static boolean isWater(BlockState state) {
        return isWater(state.getBlock());
    }

    /** Паровая труба любого поколения. */
    public static boolean isSteam(Block block) {
        return kind(block, STEAM);
    }

    public static boolean isSteam(BlockState state) {
        return isSteam(state.getBlock());
    }

    /** Универсальная жидкостная труба любого поколения. */
    public static boolean isUniversalFluid(Block block) {
        return kind(block, UNIVERSAL_FLUID);
    }

    public static boolean isUniversalFluid(BlockState state) {
        return isUniversalFluid(state.getBlock());
    }

    /** Универсальный узел любого поколения. */
    public static boolean isUniversal(Block block) {
        return kind(block, UNIVERSAL) && !isUniversalFluid(block);
    }

    public static boolean isUniversal(BlockState state) {
        return isUniversal(state.getBlock());
    }

    /** Предметная труба любого поколения. */
    public static boolean isItem(Block block) {
        return kind(block, ITEM);
    }

    public static boolean isItem(BlockState state) {
        return isItem(state.getBlock());
    }
}
