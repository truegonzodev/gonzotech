package com.gonzotech.core.client;

import com.gonzotech.core.registry.ModCreativeTabs;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Keeps mod lore before vanilla's optional F3+H diagnostics. */
public final class TooltipLayout {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern COMPONENTS = Pattern.compile(".*(components|компонент).*", Pattern.CASE_INSENSITIVE);

    private TooltipLayout() {}

    /** Restore the creative-tab line when drifted NBT (наведёнка, содержимое
     * носителей) makes vanilla's component-sensitive tab membership check miss
     * the otherwise identical item stack.
     *
     * <p>0.3.88 (автор 04.10): вмешиваемся ТОЛЬКО когда ваниль свою строку НЕ
     * добавит — точное сравнение компонентов с витринным стаком. Раньше
     * (гейты по наведёнке/носителям) свежие предметы получали дубль: ваниль
     * добавляла свою строку, а мы ещё одну. Пустая строка-сепаратор после
     * таба убрана: по ГОСТ лора лор идёт сразу под табом (её инжект и сдвигал
     * лор на строку вниз при появлении дозы).</p> */
    public static void ensureCreativeCategory(List<Component> tooltip, ItemStack stack, boolean creative) {
        if (!creative) return;
        for (var holder : List.of(ModCreativeTabs.ORES_TAB, ModCreativeTabs.FUNCTIONAL_TAB,
                ModCreativeTabs.EQUIPMENT_TAB, ModCreativeTabs.BLOCKS_TAB,
                ModCreativeTabs.COMPONENTS_TAB, ModCreativeTabs.ADAPTATIONS_TAB,
                ModCreativeTabs.GAGS_TAB)) {
            CreativeModeTab tab = holder.get();
            var display = tab.getDisplayItems();
            if (!display.stream().anyMatch(candidate -> ItemStack.isSameItem(candidate, stack))) continue;
            // Компоненты совпадают с витринным стаком — ваниль справится сама.
            if (display.stream().anyMatch(candidate ->
                    ItemStack.isSameItemSameComponents(candidate, stack))) return;
            String title = tab.getDisplayName().getString();
            // Убираем случайные копии и вставляем ровно одну каноничную строку
            // таба; сепаратор не добавляем (ГОСТ: лор сразу под табом).
            tooltip.removeIf(line -> line.getString().equals(title));
            tooltip.add(1, tab.getDisplayName().copy().withStyle(net.minecraft.ChatFormatting.BLUE));
            return;
        }
    }

    public static void collapseEmptyRuns(List<Component> tooltip) {
        for (int i = tooltip.size() - 1; i > 0; i--) {
            if (tooltip.get(i).getString().isEmpty() && tooltip.get(i - 1).getString().isEmpty()) {
                tooltip.remove(i);
            }
        }
    }

    public static List<Component> takeAdvanced(List<Component> tooltip) {
        int start = -1;
        for (int i = 0; i < tooltip.size(); i++) {
            String text = tooltip.get(i).getString().trim();
            if (ID.matcher(text).matches() || COMPONENTS.matcher(text).matches()) {
                start = i;
                break;
            }
        }
        if (start < 0) return List.of();
        List<Component> advanced = new ArrayList<>(tooltip.subList(start, tooltip.size()));
        tooltip.subList(start, tooltip.size()).clear();
        return advanced;
    }
}
