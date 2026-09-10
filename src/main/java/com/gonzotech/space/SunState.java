package com.gonzotech.space;

import net.minecraft.util.StringRepresentable;

/**
 * Глобальные состояния Солнца (Оверворлд и Солнечная система).
 *
 * <ul>
 *   <li>{@link #DEFAULT} — стандартное солнце ({@code sun.png})</li>
 *   <li>{@link #DYSON} — солнце со сферой Дайсона ({@code sun_dyson.png} + {@code .mcmeta})</li>
 *   <li>{@link #GONE} — солнце взорвалось/погасло ({@code sun_gone.png}, вечная тьма на Земле, Луне, Марсе, Европе, орбите)</li>
 *   <li>{@link #BLACKHOLE} — вместо солнца сияет Чёрная Дыра ({@code sun_blackhole.png})</li>
 *   <li>{@link #BLACKHOLE_DYSON} — Чёрная Дыра с Кольцом Всевластия / сферой Дайсона ({@code sun_blackhole_dyson.png})</li>
 * </ul>
 */
public enum SunState implements StringRepresentable {
    DEFAULT("default"),
    DYSON("dyson"),
    GONE("gone"),
    BLACKHOLE("blackhole"),
    BLACKHOLE_DYSON("blackhole_dyson");

    private final String name;

    SunState(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    public static SunState fromString(String str) {
        if (str == null) return DEFAULT;
        String s = str.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (SunState state : values()) {
            if (state.name.equals(s)) {
                return state;
            }
        }
        if (s.contains("black") && s.contains("dyson")) return BLACKHOLE_DYSON;
        if (s.contains("black")) return BLACKHOLE;
        if (s.contains("dyson")) return DYSON;
        if (s.contains("gone") || s.contains("no_sun") || s.contains("dead")) return GONE;
        return DEFAULT;
    }
}
