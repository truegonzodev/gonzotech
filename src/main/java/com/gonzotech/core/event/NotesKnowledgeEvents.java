package com.gonzotech.core.event;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.chalkboard.notes.ScholarNoteFlags;
import com.gonzotech.chalkboard.network.NotesNetwork;
import com.gonzotech.chalkboard.progress.ModAttachments;
import com.gonzotech.core.registry.ModEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Observation only: never changes the effect, radiation or inventory. */
@EventBusSubscriber(modid = GonzoTechMod.MOD_ID)
public final class NotesKnowledgeEvents {
    private NotesKnowledgeEvents() {}

    private static void remember(ServerPlayer player, String flag) {
        var progress = player.getData(ModAttachments.CHALKBOARD_PROGRESS);
        if (progress.unlockNoteFlag(flag)) {
            player.setData(ModAttachments.CHALKBOARD_PROGRESS, progress);
            NotesNetwork.sendToPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onPickup(ItemEntityPickupEvent.Post event) {
        if (event.getPlayer() instanceof ServerPlayer player
            && event.getOriginalStack().is(Items.COBBLESTONE)
            && event.getOriginalStack().getCount() > event.getCurrentStack().getCount()) {
            remember(player, ScholarNoteFlags.COBBLESTONE);
        }
    }

    @SubscribeEvent
    public static void onEffect(MobEffectEvent.Added event) {
        if (event.getEntity() instanceof ServerPlayer player
            && event.getEffectInstance().getEffect().value() == ModEffects.NECROSIS.value()) {
            remember(player, ScholarNoteFlags.NECROSIS);
        }
    }

    /** Catch pre-existing saves/effects and publish the two playtime thresholds live. */
    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        var progress = player.getData(ModAttachments.CHALKBOARD_PROGRESS);
        boolean changed = false;
        if (!progress.hasNoteFlag(ScholarNoteFlags.NECROSIS) && player.hasEffect(ModEffects.NECROSIS)) {
            changed |= progress.unlockNoteFlag(ScholarNoteFlags.NECROSIS);
        }
        // Bootstrap pre-existing inventory once; component-bearing cobblestone counts too.
        if (!progress.hasNoteFlag(ScholarNoteFlags.COBBLESTONE)) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (player.getInventory().getItem(i).is(Items.COBBLESTONE)) {
                    changed |= progress.unlockNoteFlag(ScholarNoteFlags.COBBLESTONE);
                    break;
                }
            }
        }
        if (changed) player.setData(ModAttachments.CHALKBOARD_PROGRESS, progress);
        int time = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAY_TIME));
        if (changed || (time >= 72_000 && time < 72_020) || (time >= 144_000 && time < 144_020)) {
            NotesNetwork.sendToPlayer(player);
        }
    }
}
