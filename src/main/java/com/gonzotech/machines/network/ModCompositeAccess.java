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
    private static CompositePipeBlock thirdComposite;
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

    /** Внутренняя связка экранированной семьи (эпоха 3). Он не является BlockItem. */
    public static void setThird(CompositePipeBlock block) {
        thirdComposite = block;
    }

    /**
     * Тир трубы: 3 — экранированная семья (эпоха 3), 2 — тир II, 1 — тир I.
     * 0.3.114: ThirdTierPipe РАСШИРЯЕТ SecondTierPipe, поэтому булева проверка
     * {@code instanceof SecondTierPipe} считала третью семью вторым тиром —
     * пучки смешивали тиры и заменяли экранированные трубы трубами II
     * (репорт автора, раунд 12). Только явная лестница тиров.
     */
    public static int tierOf(net.minecraft.world.level.block.Block block) {
        if (block instanceof ThirdTierPipe) return 3;
        if (block instanceof SecondTierPipe) return 2;
        return 1;
    }

    /** Тир предмета-трубы (0 — не труба). */
    public static int tierOfItem(net.minecraft.world.item.ItemStack stack) {
        return stack.getItem() instanceof net.minecraft.world.item.BlockItem bi
            && bi.getBlock() instanceof PipeBlock pipe ? tierOf(pipe) : 0;
    }

    /** Связка того же уровня, что исходная одиночная труба. */
    public static CompositePipeBlock getFor(net.minecraft.world.level.block.Block pipe) {
        return switch (tierOf(pipe)) {
            case 3 -> thirdComposite;
            case 2 -> secondComposite;
            default -> composite;
        };
    }

    /**
     * Совпадает ли уровень существующей трубы/связки с уровнем предмета-трубы
     * в руке: пучки собираются только внутри одного тира (тир-микс закрыт
     * ещё в тир-II, автор).
     */
    public static boolean sameTier(net.minecraft.world.level.block.Block existing, net.minecraft.world.item.ItemStack stack) {
        int tier = tierOfItem(stack);
        return tier > 0 && tierOf(existing) == tier;
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
