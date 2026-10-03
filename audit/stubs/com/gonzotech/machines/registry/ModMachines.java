package com.gonzotech.machines.registry;

/**
 * Стаб компилятора для миксинов, которым нужен доступ к реестру (полный
 * ModMachines в локальный typecheck не входит — тянет полдерева блоков).
 * Если реальный ModMachines добавят в список typecheck — удалить этот стаб.
 */
public final class ModMachines {
    public static final net.neoforged.neoforge.registries.DeferredBlock<net.minecraft.world.level.block.Block>
        THIRD_LEAD_PISTON_HEAD = null;

    private ModMachines() {
    }
}
