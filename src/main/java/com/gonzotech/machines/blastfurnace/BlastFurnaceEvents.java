package com.gonzotech.machines.blastfurnace;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Клик по котлу собранной доменной печи (0.3.70; 0.3.73 — только котёл):
 * ванильный котёл не знает о структуре, поэтому правый клик по нему
 * перехватывается событием. Узлы обслуживаются хуками сетевых блоков через
 * FormedMenus — там поведение шифта ровно как у турбины: ПКМ открывает меню,
 * Shift+ПКМ ставит блок.
 */
public final class BlastFurnaceEvents {

    private BlastFurnaceEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel server)) return;
        // Шифт+ПКМ — ставить блоки (паритет с турбиной/парогеном).
        if (event.getEntity().isShiftKeyDown()) return;
        // Только котёл: у ванильного котла нет хука на меню печи. Узлы ловятся
        // в хуках самих сетевых блоков (FormedMenus.open) — там ваниль сама
        // разруливает шифт.
        var state = server.getBlockState(event.getPos());
        if (!state.is(Blocks.CAULDRON) && !state.is(Blocks.WATER_CAULDRON)) return;
        if (BlastFurnaceStructure.openMenu(server, event.getPos(), event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
