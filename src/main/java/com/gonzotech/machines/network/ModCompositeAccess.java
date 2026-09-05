package com.gonzotech.machines.network;

/**
 * Тонкий держатель ссылки на зарегистрированный составной блок труб, чтобы
 * {@link PipeBlock} мог «пересобраться» в связку, не завися напрямую от реестра
 * ({@code ModMachines}). Заполняется один раз при регистрации блоков.
 */
public final class ModCompositeAccess {

    private static CompositePipeBlock instance;

    private ModCompositeAccess() {
    }

    public static void set(CompositePipeBlock block) {
        instance = block;
    }

    public static CompositePipeBlock get() {
        return instance;
    }
}
