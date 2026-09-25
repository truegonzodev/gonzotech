package com.gonzotech.core.client;

import com.gonzotech.radiation.ItemRadioactivity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Keeps mod lore before vanilla's optional F3+H diagnostics. */
public final class TooltipLayout {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern COMPONENTS = Pattern.compile(".*(components|компонент).*", Pattern.CASE_INSENSITIVE);

    private TooltipLayout() {}

    public static void removeRadiatedCreativeCategory(List<Component> tooltip, ItemStack stack, boolean creative) {
        if (!creative || ItemRadioactivity.getInduced(stack) <= 0.0) return;
        for (int i = 0; i < tooltip.size(); i++) {
            String text = tooltip.get(i).getString();
            if (text.equals("Ресурсы и материалы Gonzo Tech")
                    || text.equals("Resources and Materials Gonzo Tech")
                    || text.endsWith("Gonzo Tech")) {
                tooltip.remove(i);
                if (i < tooltip.size() && tooltip.get(i).getString().isEmpty()) tooltip.remove(i);
                return;
            }
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
