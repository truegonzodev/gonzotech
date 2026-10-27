package com.gonzotech.radiation.client;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.text.GtUnits;
import com.gonzotech.radiation.RadSources;
import com.gonzotech.radiation.engine.RadiationViewNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;

/** Separate paint-only window. Never updates an ItemStack or an inventory slot. */
@EventBusSubscriber(modid = GonzoTechMod.MOD_ID, value = Dist.CLIENT)
public final class RadiationPanel {
    private static RadiationViewNetwork.View view;
    private static long sequence;
    private RadiationPanel() {}
    public static void accept(RadiationViewNetwork.View next) {
        if (next.ack() >= sequence) view = next;
    }
    private static int pendingMenu;
    private static net.minecraft.world.inventory.AbstractContainerMenu pendingClientMenu;
    private static long pendingSequence;
    private static void fence(boolean begin) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;
        if (pendingSequence != 0) {
            PacketDistributor.sendToServer(new RadiationViewNetwork.Fence(pendingMenu, pendingSequence, false,
                    pendingClientMenu.getCarried().getCount(), RadiationViewNetwork.fingerprint(pendingClientMenu.getCarried(), mc.player.registryAccess())));
            pendingSequence = 0;
        }
        if (!begin || !(mc.screen instanceof AbstractContainerScreen<?> screen)) return;
        pendingMenu = screen instanceof CreativeModeInventoryScreen ? mc.player.inventoryMenu.containerId : screen.getMenu().containerId;
        pendingClientMenu = screen.getMenu();
        pendingSequence = ++sequence;
        view = null;
        PacketDistributor.sendToServer(new RadiationViewNetwork.Fence(pendingMenu, pendingSequence, true, 0, 0));
    }
    // Keyboard Post is skipped when a screen handles the key. Always close that gesture.
    @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) { fence(false); }
    @SubscribeEvent public static void mouse(ScreenEvent.MouseButtonPressed.Post event) { fence(false); }
    @SubscribeEvent public static void mouseBefore(ScreenEvent.MouseButtonPressed.Pre event) { fence(true); }
    @SubscribeEvent public static void released(ScreenEvent.MouseButtonReleased.Post event) { fence(false); }
    @SubscribeEvent public static void releasedBefore(ScreenEvent.MouseButtonReleased.Pre event) { fence(true); }
    @SubscribeEvent public static void key(ScreenEvent.KeyPressed.Post event) { fence(false); }
    @SubscribeEvent public static void keyBefore(ScreenEvent.KeyPressed.Pre event) { fence(true); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { view = null; sequence = 0; pendingSequence = 0; pendingClientMenu = null; }

    private static Double rate(ItemStack stack) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) return RadSources.emissionDeep(stack);
        var slot = screen.getSlotUnderMouse();
        if (slot == null || !ItemStack.matches(slot.getItem(), stack)) return RadSources.emissionDeep(stack);
        // Creative catalogue is a sample, not an existing inventory stack.
        if (screen instanceof CreativeModeInventoryScreen && slot.container != mc.player.getInventory()
                && !((CreativeModeInventoryScreen) screen).isInventoryOpen()) return RadSources.emissionDeep(stack);
        if (view == null || view.ack() < sequence) return null;
        int menu = screen instanceof CreativeModeInventoryScreen ? mc.player.inventoryMenu.containerId : screen.getMenu().containerId;
        if (view.menu() != menu) return null;
        if (!(screen instanceof CreativeModeInventoryScreen) && view.state() != screen.getMenu().getStateId()) return null;
        var entries = slot.container == mc.player.getInventory() ? view.inventory() : view.slots();
        int index = slot.container == mc.player.getInventory() ? slot.getSlotIndex() : slot.index;
        if (index < 0 || index >= entries.size()) return null;
        var entry = entries.get(index);
        return entry.fingerprint() == RadiationViewNetwork.fingerprint(stack, mc.player.registryAccess()) ? entry.rate() : null;
    }

    @SubscribeEvent public static void render(RenderTooltipEvent.Texture event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        Double total = rate(stack);
        if (!com.gonzotech.radiation.RadiationPanelLayout.visible(total)) return;
        Component label = Component.literal("Радиоактивность: ").withColor(0xAAAAAA)
                .append(GtUnits.zt(total).copy().withColor(0xCEEB2D));
        var graphics = event.getGraphics(); var font = event.getFont();
        int tooltipHeight = event.getComponents().stream().mapToInt(c -> c.getHeight(font)).sum()
                + (event.getComponents().size() == 1 ? -2 : 0);
        var box = com.gonzotech.radiation.RadiationPanelLayout.place(font.width(label), font.lineHeight,
                event.getX(), event.getY(), tooltipHeight, graphics.guiWidth(), graphics.guiHeight());
        int x = box.x(), y = box.y(), width = box.width(), height = box.height();
        graphics.pose().pushPose(); graphics.pose().translate(0, 0, 450);
        // One-pixel dark silhouette with cut corners; inset bevel matches the author's pixel reference.
        int background = com.gonzotech.radiation.RadiationPanelLayout.BACKGROUND;
        int light = com.gonzotech.radiation.RadiationPanelLayout.LIGHT_EDGE;
        int dark = com.gonzotech.radiation.RadiationPanelLayout.DARK_EDGE;
        graphics.fill(x + 1, y, x + width - 1, y + height, background);
        graphics.fill(x, y + 1, x + 1, y + height - 1, background);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, background);
        graphics.fill(x + 2, y + 1, x + width - 2, y + 2, light);
        graphics.fill(x + 1, y + 2, x + 2, y + height - 2, light);
        graphics.fill(x + 2, y + height - 2, x + width - 2, y + height - 1, dark);
        graphics.fill(x + width - 2, y + 2, x + width - 1, y + height - 2, dark);
        graphics.drawString(font, label, box.textX(), box.textY(), 0xFFFFFFFF, true);
        graphics.pose().popPose();
    }
}
