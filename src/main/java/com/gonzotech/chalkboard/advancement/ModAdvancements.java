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
 * Awards the silent Energy-tree root, the sequential [1] -> ... -> [16] discovery chain,
 * and the event-driven side branches. Discovery advancements still unlock strictly
 * sequentially: the recipe tier must be activated AND the parent advancement completed.
 */
public class ModAdvancements {

    public static void checkAndAwardAdvancements(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;

        // The silent tree root is the parent of discovery_1 and every side branch.
        awardAchievement(player, "root");

        PlayerChalkboardProgress progress = player.getData(ModAttachments.CHALKBOARD_PROGRESS);
        ServerAdvancementManager manager = player.getServer().getAdvancements();
        PlayerAdvancements playerAdvancements = player.getAdvancements();

        for (int i = 1; i <= 16; i++) {
            if (progress.isRecipeTierUnlocked(i)) {
                ResourceLocation advId = ResourceLocation.fromNamespaceAndPath("gonzotech", "discovery_" + i);
                AdvancementHolder advHolder = manager.get(advId);
                if (advHolder != null) {
                    ResourceLocation parentId = i == 1
                            ? ResourceLocation.fromNamespaceAndPath("gonzotech", "root")
                            : ResourceLocation.fromNamespaceAndPath("gonzotech", "discovery_" + (i - 1));
                    AdvancementHolder parentHolder = manager.get(parentId);
                    boolean parentUnlocked = false;
                    if (parentHolder != null) {
                        AdvancementProgress parentProg = playerAdvancements.getOrStartProgress(parentHolder);
                        parentUnlocked = parentProg.isDone();
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

    public static void awardFirstMash(ServerPlayer player) {
        awardAchievement(player, "first_mash");
    }

    public static void awardCrimsonDay(ServerPlayer player) {
        awardAchievement(player, "crimson_day");
    }

    public static void awardCesiumInteraction(ServerPlayer player) {
        awardAchievement(player, "cesium_interaction");
    }

    public static void awardLeakyBucket(ServerPlayer player) {
        awardAchievement(player, "leaky_bucket");
    }

    public static void awardForbiddenBath(ServerPlayer player) {
        awardAchievement(player, "forbidden_bath");
    }

    /** Idempotently grants all remaining criteria for a code-driven achievement. */
    private static void awardAchievement(ServerPlayer player, String path) {
        if (player == null || player.getServer() == null) return;
        AdvancementHolder holder = player.getServer().getAdvancements().get(
                ResourceLocation.fromNamespaceAndPath("gonzotech", path));
        if (holder == null) return;

        PlayerAdvancements advancements = player.getAdvancements();
        AdvancementProgress progress = advancements.getOrStartProgress(holder);
        if (progress.isDone()) return;
        for (String criterion : progress.getRemainingCriteria()) {
            advancements.award(holder, criterion);
        }
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            checkAndAwardAdvancements(serverPlayer);
            // Фаза 3: рецепты, видимые в книге априори (доска/катушка/заметки).
            RecipeUnlocks.grantAlwaysUnlocked(serverPlayer);
            // Восстановить time-gated рецепты игрока, который уже наиграл 20 минут.
            RecipeUnlocks.grantAfterTwentyMinutesPlayed(serverPlayer);
            // Фаза 3: восстановить видимость рецептов машин для уже открытых «Открытий».
            RecipeUnlocks.grantForUnlockedTiers(serverPlayer);
        }
    }
}
