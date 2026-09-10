package com.gonzotech.machines.network;

import java.util.EnumMap;
import java.util.Map;

/**
 * Тонкий держатель ссылок на зарегистрированные блоки труб, чтобы код в пакете
 * {@code network} мог «пересобирать» блоки (одиночная труба ↔ связка), не завися
 * напрямую от реестра ({@code ModMachines}). Заполняется один раз при регистрации.
 */
public final class ModCompositeAccess {

    private static CompositePipeBlock composite;
    private static final Map<PipeType, PipeBlock> SINGLES = new EnumMap<>(PipeType.class);

    private ModCompositeAccess() {
    }

    /** Составной блок (связка). */
    public static void set(CompositePipeBlock block) {
        composite = block;
    }

    public static CompositePipeBlock get() {
        return composite;
    }

    /** Зарегистрировать одиночную трубу под её тип (для «схлопывания» связки). */
    public static void registerSingle(PipeType type, PipeBlock block) {
        SINGLES.put(type, block);
    }

    /** Одиночная труба данного типа, или {@code null}. */
    public static PipeBlock singleOf(PipeType type) {
        return SINGLES.get(type);
    }
}
