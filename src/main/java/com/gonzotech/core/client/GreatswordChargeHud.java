package com.gonzotech.core.client;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.item.GreatswordItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** Small charge bar anchored just above the crosshair while a greatsword is held. */
@EventBusSubscriber(modid = GonzoTechMod.MOD_ID, value = Dist.CLIENT)
public final class GreatswordChargeHud {

    private static final int BAR_WIDTH = 112;
    private static final int BAR_HEIGHT = 7;

    private GreatswordChargeHud() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.options.hideGui
            || !player.isUsingItem() || !(player.getUseItem().getItem() instanceof GreatswordItem)) {
            return;
        }

        float progress = GreatswordItem.chargeProgress(player);
        int percent = Math.round(progress * 100.0F);
        GuiGraphics graphics = event.getGuiGraphics();
        int x = (graphics.guiWidth() - BAR_WIDTH) / 2;
        int y = graphics.guiHeight() / 2 - 31;

        graphics.fill(x - 2, y - 2, x + BAR_WIDTH + 2, y + BAR_HEIGHT + 2, 0xC9000000);
        graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0xFF282522);
        int fillWidth = Math.round((BAR_WIDTH - 2) * progress);
        if (fillWidth > 0) {
            int fillColor = progress < 0.10F ? 0xFFE05B45 : progress < 1.0F ? 0xFFFFB84D : 0xFF9EE36D;
            graphics.fill(x + 1, y + 1, x + 1 + fillWidth, y + BAR_HEIGHT - 1, fillColor);
        }
        int threshold = x + 1 + Math.round((BAR_WIDTH - 2) * 0.10F);
        graphics.fill(threshold, y, threshold + 1, y + BAR_HEIGHT, 0xFFFFE7A8);

        Component label = Component.translatable("hud.gonzotech.greatsword_charge", percent);
        int labelX = (graphics.guiWidth() - minecraft.font.width(label)) / 2;
        graphics.drawString(minecraft.font, label, labelX, y - minecraft.font.lineHeight - 3,
            0xFFFFE8BA, true);
    }
}
