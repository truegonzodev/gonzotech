package com.gonzotech.mixin;

import com.gonzotech.core.config.GonzoServerConfig;
import com.gonzotech.radiation.RadiationClickWatch;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.3.271, автор 23.10) Помечает клик по контейнеру до исполнения меню-логики,
 * чтобы радиационный пересчёт не переписывал стаки в окне drag&drop (дюп через
 * carried). Никаких изменений самих кликов.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ContainerClickRadiationMixin {

    @Shadow
    public ServerPlayer player;

    @Unique
    private static final Logger GONZOTECH$LOG =
        org.slf4j.LoggerFactory.getLogger("gonzotech/radiation-inventory");

    /** Серверная сумма предметов открытого меню до клика (для дельты после). */
    @Unique
    private int gonzotech$radInvBefore;

    @Inject(
            method = "handleContainerClick",
            at = @At("HEAD"))
    private void gonzotech$markContainerClick(ServerboundContainerClickPacket packet, CallbackInfo callback) {
        RadiationClickWatch.markClick(this.player);
        if (GonzoServerConfig.RADIATION_INVENTORY_DEBUG.get()) {
            this.gonzotech$radInvBefore = gonzotech$countContainerItems(this.player.containerMenu);
        }
    }

    @Inject(
            method = "handleContainerClick",
            at = @At("RETURN"))
    private void gonzotech$reportContainerClick(ServerboundContainerClickPacket packet, CallbackInfo callback) {
        if (!GonzoServerConfig.RADIATION_INVENTORY_DEBUG.get()) return;
        int after = gonzotech$countContainerItems(this.player.containerMenu);
        int delta = after - this.gonzotech$radInvBefore;
        if (delta == 0) return;
        GONZOTECH$LOG.info("[RAD-INV] {} {} {} предмета(ов): серверная сумма {} -> {} (меню {})",
            this.player.getGameProfile().getName(),
            delta > 0 ? "ПОЛУЧИЛА" : "ПОТЕРЯЛА",
            Math.abs(delta), this.gonzotech$radInvBefore, after,
            this.player.containerMenu.getClass().getSimpleName());
    }

    @Unique
    private static int gonzotech$countContainerItems(AbstractContainerMenu menu) {
        int total = 0;
        for (Slot slot : menu.slots) total += slot.getItem().getCount();
        return total + menu.getCarried().getCount();
    }
}
