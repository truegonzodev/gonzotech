package com.gonzotech.machines.blastfurnace;

import com.gonzotech.machines.registry.ModMachines;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Клик по «вырезанным» частям собранной доменной печи (0.3.70): котёл и узлы
 * теплотруб/универсальные — обычные блоки, которые ничего не знают о структуре,
 * поэтому правый клик перехватывается событием: если в кубе ±1 есть
 * сформированная топка — открываем меню доменной печи (как у турбины/парогена).
 */
public final class BlastFurnaceEvents {

    private BlastFurnaceEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel server)) return;
        if (!isCutoutPart(server.getBlockState(event.getPos()))) return;
        if (BlastFurnaceStructure.openMenu(server, event.getPos(), event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /** «Вырезанная» часть собранной печи: котёл (пустой/с водой) или тепло-узел. */
    private static boolean isCutoutPart(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(Blocks.CAULDRON) || state.is(Blocks.WATER_CAULDRON)
            || state.is(ModMachines.HEAT_NODE.get()) || state.is(ModMachines.SECOND_HEAT_NODE.get())
            || state.is(ModMachines.UNIVERSAL_NODE.get()) || state.is(ModMachines.SECOND_UNIVERSAL_NODE.get());
    }
}
