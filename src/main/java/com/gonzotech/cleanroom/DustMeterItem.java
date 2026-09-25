package com.gonzotech.cleanroom;

import com.gonzotech.core.text.GtUnits;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Instrument that reports clean-room air quality. */
public final class DustMeterItem extends Item {
    public DustMeterItem(Properties properties) { super(properties.stacksTo(1)); }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level instanceof net.minecraft.server.level.ServerLevel server && player instanceof ServerPlayer serverPlayer) {
            double quality = CleanRoomSystem.quality(server, serverPlayer.blockPosition());
            if (quality < 0.0) {
                serverPlayer.sendSystemMessage(Component.literal("Качество воздуха: обычное")
                        .withStyle(ChatFormatting.GRAY));
            } else {
                int rounded = (int) Math.round(quality);
                serverPlayer.sendSystemMessage(Component.literal("Качество воздуха: ")
                        .append(Component.literal(rounded + "%").withStyle(color(rounded)))
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        return InteractionResult.SUCCESS;
    }

    private static ChatFormatting color(int quality) {
        if (quality < 20) return ChatFormatting.DARK_RED;
        if (quality < 40) return ChatFormatting.RED;
        if (quality < 60) return ChatFormatting.GOLD;
        if (quality < 80) return ChatFormatting.YELLOW;
        if (quality < 95) return ChatFormatting.GREEN;
        return ChatFormatting.DARK_GREEN;
    }
}
