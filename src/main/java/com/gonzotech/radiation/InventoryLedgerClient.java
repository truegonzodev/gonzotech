package com.gonzotech.radiation;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * (0.3.277) Раз в секунду считает клиентскую сумму предметов открытого меню и
 * отправляет серверу для журнала {@code [LEDGER]}. Никакого влияния на игру:
 * только чтение.
 */
@EventBusSubscriber(modid = "gonzotech", value = Dist.CLIENT)
public final class InventoryLedgerClient {
    private InventoryLedgerClient() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.player.tickCount % 20 != 1) return;
        AbstractContainerMenu menu = mc.player.containerMenu;
        long sum = mc.player.isSpectator() ? 0 : menu.getCarried().getCount();
        for (Slot slot : menu.slots) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) sum += stack.getCount();
        }
        PacketDistributor.sendToServer(new InventoryLedgerPayload(sum));
    }
}
