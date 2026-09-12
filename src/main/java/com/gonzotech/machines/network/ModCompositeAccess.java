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
    private static CompositePipeBlock secondComposite;
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

    /** Внутренний составной блок второго уровня. Он не является BlockItem. */
    public static void setSecond(CompositePipeBlock block) {
        secondComposite = block;
    }

    /** Связка того же уровня, что исходная одиночная труба. */
    public static CompositePipeBlock getFor(PipeBlock pipe) {
        return pipe instanceof SecondTierPipe ? secondComposite : composite;
    }

    /** Совпадает ли уровень существующей трубы с уровнем предмета-трубы в руке. */
    public static boolean sameTier(PipeBlock existing, net.minecraft.world.item.ItemStack stack) {
        return (existing instanceof SecondTierPipe) == CompositePipeBlock.isSecondTierPipeItem(stack);
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
