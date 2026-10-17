package com.gonzotech.machines.client;

import com.gonzotech.machines.menu.ReactorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Panel-driven reactor menu with a dynamic Z3 overlay for slots after the built-in first three. */
public final class ReactorScreen extends MachineScreen<ReactorMenu> {

    public ReactorScreen(ReactorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected ResourceLocation backgroundTexture() {
        return gui("third_uranium_reactor_gui_bg.png");
    }

    @Override
    protected ResourceLocation foregroundTexture() {
        return gui("third_uranium_reactor_gui.png");
    }

    @Override
    protected void drawMachine(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        // Reactor fuel/thermal bars are intentionally not implemented yet.
    }

    @Override
    protected void drawAfterForeground(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        if (menu.activeFuelSlots() <= 3) return;

        ResourceLocation slotFrame = usesClassicMachineGuis()
            ? gui("third_uranium_reactor_gui_slot.png")
            : gui("third_uranium_reactor_gui_slot_neo1.png");

        // The first sheath's three frames are part of gui.png; only later slots
        // are overlaid here, above that sheet and below vanilla slot tints/items.
        for (int slot = 3; slot < menu.activeFuelSlots(); slot++) {
            int slotX = x + ReactorMenu.fuelSlotX(slot) - 1;
            int slotY = y + ReactorMenu.fuelSlotY(slot) - 1;
            graphics.blit(RenderType::guiTextured, slotFrame, slotX, slotY,
                0.0F, 0.0F, 18, 18, 18, 18);
        }
    }
}
