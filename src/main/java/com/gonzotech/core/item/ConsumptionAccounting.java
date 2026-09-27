package com.gonzotech.core.item;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.gameevent.GameEvent;

/** Vanilla bookkeeping for successful custom consumption, BEFORE shrinking/replacing the stack.
 * Foods using the vanilla Consumable component (both mashes) already do this: do not call twice.
 */
public final class ConsumptionAccounting {
    private ConsumptionAccounting() {}

    public static void record(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return;
        player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        CriteriaTriggers.CONSUME_ITEM.trigger(player, stack);
        player.gameEvent(stack.getUseAnimation() == ItemUseAnimation.DRINK ? GameEvent.DRINK : GameEvent.EAT);
    }
}
