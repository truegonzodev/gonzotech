package com.gonzotech.chalkboard.advancement;

import com.gonzotech.chalkboard.progress.ModAttachments;
import com.gonzotech.chalkboard.progress.PlayerChalkboardProgress;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Handles Gonzo Tech Advancement progression chain [1] -> [2] -> [3] -> ... -> [16].
 * Advancements are awarded strictly sequentially: an advancement is unlocked only if
 * its recipe tier is activated AND its parent advancement is completed.
 */
public class ModAdvancements {

    public static void checkAndAwardAdvancements(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;

        PlayerChalkboardProgress progress = player.getData(ModAttachments.CHALKBOARD_PROGRESS);
        ServerAdvancementManager manager = player.getServer().getAdvancements();
        PlayerAdvancements playerAdvancements = player.getAdvancements();

        for (int i = 1; i <= 16; i++) {
            if (progress.isRecipeTierUnlocked(i)) {
                ResourceLocation advId = ResourceLocation.fromNamespaceAndPath("gonzotech", "discovery_" + i);
                AdvancementHolder advHolder = manager.get(advId);
                if (advHolder != null) {
                    boolean parentUnlocked = true;
                    if (i > 1) {
                        ResourceLocation parentId = ResourceLocation.fromNamespaceAndPath("gonzotech", "discovery_" + (i - 1));
                        AdvancementHolder parentHolder = manager.get(parentId);
                        if (parentHolder != null) {
                            AdvancementProgress parentProg = playerAdvancements.getOrStartProgress(parentHolder);
                            parentUnlocked = parentProg.isDone();
                        } else {
                            parentUnlocked = false;
                        }
                    }

                    if (parentUnlocked) {
                        AdvancementProgress advProg = playerAdvancements.getOrStartProgress(advHolder);
                        if (!advProg.isDone()) {
                            for (String criterion : advProg.getRemainingCriteria()) {
                                playerAdvancements.award(advHolder, criterion);
                            }
                        }
                    }
                }
            } else {
                // If this tier is not unlocked in progress, the chain cannot advance further
                break;
            }
        }
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            checkAndAwardAdvancements(serverPlayer);
            // Фаза 3: восстановить видимость рецептов машин для уже открытых «Открытий».
            RecipeUnlocks.grantForUnlockedTiers(serverPlayer);
        }
    }
}
