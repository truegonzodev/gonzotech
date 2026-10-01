package com.gonzotech.machines;

import com.gonzotech.machines.blastfurnace.BlastFurnaceStructure;
import com.gonzotech.machines.steamgen.SteamGenStructure;
import com.gonzotech.machines.turbine.TurbineStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Единый вход «ПКМ по блоку открывает меню сформированного многоблока»
 * (0.3.73): сетевые блоки (трубы/узлы) раньше звали только турбину, поэтому
 * парогенератор по узлу не открывал ничего, а доменная печь ловила клик
 * событием с собственным поведением шифта. Теперь все три семьи пробуются в
 * одном месте из хуков блока — ваниль сама разруливает шифт (Shift+ПКМ ставит
 * блок, ПКМ открывает меню), ровно как у турбины с самого начала.
 */
public final class FormedMenus {

    private FormedMenus() {
    }

    /** Истинно, если клик попал в часть собранного многоблока и меню открыто. */
    public static boolean open(Level level, BlockPos pos, Player player) {
        if (level.isClientSide()) return false;
        return TurbineStructure.openMenu(level, pos, player)
            || SteamGenStructure.openMenu(level, pos, player)
            || BlastFurnaceStructure.openMenu(level, pos, player);
    }
}
