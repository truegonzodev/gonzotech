package com.gonzotech.chalkboard.item;

import com.gonzotech.chalkboard.advancement.ModAdvancements;
import com.gonzotech.chalkboard.network.ChalkboardNetwork;
import com.gonzotech.chalkboard.progress.ModAttachments;
import com.gonzotech.chalkboard.progress.PlayerChalkboardProgress;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Consumable Discovery scroll item ("Открытие N").
 * Right-clicking permanently unlocks Tier N crafting recipes for the player.
 */
public class DiscoveryItem extends Item {

    private final int discoveryNumber; // 1..16

    public DiscoveryItem(int discoveryNumber, Properties properties) {
        super(properties);
        this.discoveryNumber = discoveryNumber;
    }

    public int getDiscoveryNumber() {
        return discoveryNumber;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            PlayerChalkboardProgress progress = serverPlayer.getData(ModAttachments.CHALKBOARD_PROGRESS);

            if (!progress.isRecipeTierUnlocked(discoveryNumber)) {
                progress.unlockRecipeTier(discoveryNumber);
                serverPlayer.setData(ModAttachments.CHALKBOARD_PROGRESS, progress);
                ChalkboardNetwork.sendSyncToPlayer(serverPlayer);
                ModAdvancements.checkAndAwardAdvancements(serverPlayer);
                stack.shrink(1);

                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.2F);

                player.displayClientMessage(
                        Component.literal("§aОткрытие " + discoveryNumber + " активировано! Разблокированы новые рецепты.")
                                .withStyle(ChatFormatting.GREEN),
                        true
                );
                return InteractionResult.CONSUME;
            } else {
                player.displayClientMessage(
                        Component.literal("§eРецепты Открытия " + discoveryNumber + " уже разблокированы!")
                                .withStyle(ChatFormatting.YELLOW),
                        true
                );
                return InteractionResult.PASS;
            }
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7Используйте (ПКМ), чтобы навсегда разблокировать рецепты Tier " + discoveryNumber + "."));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
