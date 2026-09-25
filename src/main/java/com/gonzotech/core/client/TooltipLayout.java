package com.gonzotech.core.client;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Keeps mod lore before vanilla's optional F3+H diagnostics. */
public final class TooltipLayout {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern COMPONENTS = Pattern.compile(".*(components|компонент).*", Pattern.CASE_INSENSITIVE);

    private TooltipLayout() {}

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
