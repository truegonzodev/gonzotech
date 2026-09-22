package com.gonzotech.radiation;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.List;
import java.util.Map;

/**
 * Токсичность предметов — «Химическое заражение» (спека автора 22.09.2026).
 *
 * <p>Проще радиации ({@link ItemRadioactivity}): <b>предметы не делятся друг между
 * другом, не заражают чанк</b> — они просто заражают игрока, у которого лежат в
 * инвентаре. Параметр — «Токсичность: Tx/s» (аналог nZt/с, единицы — Tx).</p>
 *
 * <p>Два источника значения:</p>
 * <ul>
 *   <li><b>пресет</b> по материалу (таблица ниже, mTx/с на один предмет): палладий 2 ·
 *       ртуть 9 · сера 1.3 · йод 0.3 · свинец 0.2 · марганец 0.1 · барий 0.33 ·
 *       бор 0.19 · осмий 0.12 · хром 0.08 · никель 0.04 · алюминий 0.02 ·
 *       кобальт 0.002 · медь 0.001;</li>
 *   <li><b>NBT-тег</b> {@code gonzo_toxicity} в {@code custom_data} (как у радиации, но
 *       проще) — если он есть, он перебивает пресет: значение nTx/с на один предмет.</li>
 * </ul>
 *
 * <p>Формы считаются как у радиации (чтобы блоки и самородки не были «наравне со
 * слитком»): слиток ×1 · пыль ×2 · блок ×9 · самородок/руда/сырьё ÷9 · прочие формы
 * (плита, провод, стержень…) ×1.</p>
 */
public final class ItemToxicity {

    /** Имя NBT-поля токсичности в {@code custom_data} стака (nTx/с, double). */
    public static final String TAG_TOXICITY = "gonzo_toxicity";

    /** Базовая токсичность слитка по материалу, nTx/с (в скобках — mTx/с из спеки автора). */
    private static final Map<String, Double> INGOT_BASE = Map.ofEntries(
            Map.entry("palladium", 2.0 * RadUnits.MILLI),      // 2 mTx/с
            Map.entry("mercury", 9.0 * RadUnits.MILLI),        // 9 mTx/с
            Map.entry("sulfur", 1.3 * RadUnits.MILLI),         // 1.3 mTx/с
            Map.entry("iodine", 0.3 * RadUnits.MILLI),         // 0.3 mTx/с
            Map.entry("lead", 0.2 * RadUnits.MILLI),           // 0.2 mTx/с
            Map.entry("barium", 0.33 * RadUnits.MILLI),        // 0.33 mTx/с
            Map.entry("boron", 0.19 * RadUnits.MILLI),         // 0.19 mTx/с
            Map.entry("osmium", 0.12 * RadUnits.MILLI),        // 0.12 mTx/с
            Map.entry("manganese", 0.1 * RadUnits.MILLI),      // 0.1 mTx/с
            Map.entry("chromium", 0.08 * RadUnits.MILLI),      // 0.08 mTx/с
            Map.entry("nickel", 0.04 * RadUnits.MILLI),        // 0.04 mTx/с
            Map.entry("aluminum", 0.02 * RadUnits.MILLI),      // 0.02 mTx/с
            Map.entry("cobalt", 0.002 * RadUnits.MILLI),       // 0.002 mTx/с
            Map.entry("copper", 0.001 * RadUnits.MILLI)        // 0.001 mTx/с
    );

    private ItemToxicity() {
    }

    /** Токсичность ОДНОГО предмета (nTx/с): NBT-тег, иначе пресет по материалу. */
    public static double toxicityPerItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            CompoundTag tag = data.copyTag();
            if (tag.contains(TAG_TOXICITY)) {
                return Math.max(0.0, tag.getDouble(TAG_TOXICITY));
            }
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!id.getNamespace().equals("gonzotech")) {
            return 0.0;
        }
        return toxicityByPath(id.getPath());
    }

    /** Токсичность стака: per-item × count (как у радиации, п.3). */
    public static double toxicityOfStack(ItemStack stack) {
        double per = toxicityPerItem(stack);
        return per <= 0.0 ? 0.0 : per * stack.getCount();
    }

    /** Пресет по id-пути предмета: находим материал, затем форму (см. таблицу форм). */
    static double toxicityByPath(String path) {
        String[] parts = path.split("_");
        Double base = null;
        int materialIndex = -1;
        for (int i = 0; i < parts.length; i++) {
            Double found = INGOT_BASE.get(parts[i]);
            if (found != null) {
                base = found;
                materialIndex = i;
                break;
            }
        }
        if (base == null) {
            return 0.0;
        }
        // Материал последним токеном (raw_palladium) — это «сырьё», а не слиток.
        String form = materialIndex == parts.length - 1 ? "raw" : parts[parts.length - 1];
        return switch (form) {
            case "block" -> base * 9.0;
            case "dust" -> base * 2.0;
            case "nugget", "ore", "raw" -> base / 9.0;
            default -> base;   // слиток и «прочие формы» (плита, провод, стержень…)
        };
    }

    /** Суммарная токсичность инвентаря игрока (предметы, броня, оффхенд), nTx/с. */
    public static double inventoryTotal(ServerPlayer player) {
        double total = 0.0;
        List<List<ItemStack>> compartments = List.of(
                player.getInventory().items, player.getInventory().armor, player.getInventory().offhand);
        for (List<ItemStack> part : compartments) {
            for (ItemStack stack : part) {
                total += toxicityOfStack(stack);
            }
        }
        return total;
    }

    /** Записать NBT-параметр «Токсичность: Tx/s» (nTx/с) на предмет. */
    public static void setTagged(ItemStack stack, double nTxPerSecond) {
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY,
                data -> data.update(tag -> tag.putDouble(TAG_TOXICITY, nTxPerSecond)));
    }

    /** Строка тултипа: «2mTx/t» — те же приставки, что у радиации, только единица Tx. */
    public static String format(double nTx) {
        return RadUnits.format(nTx).replace("Zt", "Tx");
    }
}
