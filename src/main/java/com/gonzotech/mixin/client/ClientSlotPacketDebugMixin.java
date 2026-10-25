package com.gonzotech.mixin.client;

import com.gonzotech.radiation.DropTraceFormat;
import com.gonzotech.radiation.InventoryDebugClientGate;
import com.gonzotech.radiation.ItemRadioactivity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 0.3.291 (пункт 3 таблицы автора): приём setSlot на клиенте — экран, слот,
 * старое и пришедшее количество, наведённая радиация и метка времени. Пара к
 * серверным [CLICK-E]/[SLOT-WRITE][S]: расхождение «принято ≠ сервер имеет»
 * = призрак на клиенте, совпадение при растущей мировой Σ = физический дюп.
 */
@Mixin(net.minecraft.client.multiplayer.ClientPacketListener.class)
public abstract class ClientSlotPacketDebugMixin {
    @Shadow
    private Minecraft minecraft;

    @Unique
    private static final Logger GONZOTECH$LOG =
        org.slf4j.LoggerFactory.getLogger("gonzotech/radiation-inventory");

    @Inject(method = "handleContainerSetSlot", at = @At("HEAD"))
    private void gonzotech$traceSetSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo callback) {
        if (!InventoryDebugClientGate.isOn()) return;
        if (this.minecraft.player == null) return;
        AbstractContainerMenu menu = packet.getContainerId() == 0
            ? this.minecraft.player.inventoryMenu
            : (this.minecraft.player.containerMenu.containerId == packet.getContainerId()
                ? this.minecraft.player.containerMenu : null);
        ItemStack was = ItemStack.EMPTY;
        if (menu != null && packet.getSlot() >= 0 && packet.getSlot() < menu.countSlots()) {
            was = menu.getSlot(packet.getSlot()).getItem();
        }
        ItemStack incoming = packet.getItem();
        GONZOTECH$LOG.info(
            "[RAD-INV][SET-SLOT] t={}ms gt={} screen={} cid={} state={} slot={} | было {} → принято {} | rad {}→{}",
            System.currentTimeMillis(),
            this.minecraft.level != null ? this.minecraft.level.getGameTime() : -1L,
            this.minecraft.screen == null ? "нет" : this.minecraft.screen.getClass().getSimpleName(),
            packet.getContainerId(), packet.getStateId(), packet.getSlot(),
            DropTraceFormat.fmt(was), DropTraceFormat.fmt(incoming),
            String.format("%.2f", ItemRadioactivity.getInduced(was)),
            String.format("%.2f", ItemRadioactivity.getInduced(incoming)));
    }
}
