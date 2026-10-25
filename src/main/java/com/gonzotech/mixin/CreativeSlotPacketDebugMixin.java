package com.gonzotech.mixin;

import com.gonzotech.core.config.GonzoServerConfig;
import com.gonzotech.radiation.DropTraceFormat;
import com.gonzotech.radiation.RadDoseIndex;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 0.3.291 (пункт 2 таблицы автора): креативный пакет — присланный слот и
 * стак, прежний серверный стак, итог после обработки. Отрицательный слот
 * (выброс) печатается отдельно: [CREATIVE-DROP], его «итог» — это [ENT+]
 * создание с id сущности в том же логе.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class CreativeSlotPacketDebugMixin {
    @Shadow
    public ServerPlayer player;

    @Unique
    private static final Logger GONZOTECH$LOG =
        org.slf4j.LoggerFactory.getLogger("gonzotech/radiation-inventory");

    /** Слот текущего пакета для пары HEAD→RETURN; MIN_VALUE — гейт выключен. */
    @Unique
    private int gonzotech$creativeSlotSeen = Integer.MIN_VALUE;

    @Inject(method = "handleSetCreativeModeSlot", at = @At("HEAD"))
    private void gonzotech$beforeCreative(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo callback) {
        if (this.player == null) return;
        // 0.3.294: креативный канал (handleSetCreativeModeSlot) — единственный
        // без stateId-валидации; его активность тоже закрывает flush-окно.
        RadDoseIndex.noteInteraction(this.player);
        // 0.3.295: тот же класс гонки, что у клик-миксина (creative-пакеты тоже
        // перевызываются на серверный поток) — поля-состояние трогаем только там.
        net.minecraft.server.MinecraftServer gonzotech$srv = this.player.getServer();
        if (gonzotech$srv == null || !gonzotech$srv.isSameThread()) return;
        if (!GonzoServerConfig.RADIATION_INVENTORY_DEBUG.get()) return;
        int slot = packet.slotNum(); // short → int; <0 — «бросить»
        ItemStack sent = packet.itemStack();
        this.gonzotech$creativeSlotSeen = slot;
        if (slot < 0) {
            GONZOTECH$LOG.info("[RAD-INV][CREATIVE-DROP] {} t={} брошен {} | carried до: {}",
                this.player.getGameProfile().getName(),
                this.player.level().getGameTime(), DropTraceFormat.fmt(sent),
                DropTraceFormat.fmt(this.player.containerMenu.getCarried()));
            return;
        }
        ItemStack was = slot >= 0 && slot < this.player.inventoryMenu.slots.size()
            ? this.player.inventoryMenu.getSlot(slot).getItem() : ItemStack.EMPTY;
        GONZOTECH$LOG.info("[RAD-INV][CREATIVE] {} t={} slot={} прислан {} | сервер было {} | creative={}",
            this.player.getGameProfile().getName(), this.player.level().getGameTime(), slot,
            DropTraceFormat.fmt(sent), DropTraceFormat.fmt(was), this.player.gameMode.isCreative());
    }

    @Inject(method = "handleSetCreativeModeSlot", at = @At("RETURN"))
    private void gonzotech$afterCreative(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo callback) {
        if (this.player == null) return;
        net.minecraft.server.MinecraftServer gonzotech$srvR = this.player.getServer();
        if (gonzotech$srvR == null || !gonzotech$srvR.isSameThread()) return;
        int slot = this.gonzotech$creativeSlotSeen;
        this.gonzotech$creativeSlotSeen = Integer.MIN_VALUE;
        if (slot == Integer.MIN_VALUE) return;
        if (slot < 0) {
            GONZOTECH$LOG.info("[RAD-INV][CREATIVE-DROP-E] {} carried после: {} (создание на земле — см. [ENT+])",
                this.player.getGameProfile().getName(),
                DropTraceFormat.fmt(this.player.containerMenu.getCarried()));
            return;
        }
        ItemStack now = slot >= 0 && slot < this.player.inventoryMenu.slots.size()
            ? this.player.inventoryMenu.getSlot(slot).getItem() : ItemStack.EMPTY;
        GONZOTECH$LOG.info("[RAD-INV][CREATIVE-E] {} slot={} стало {}",
            this.player.getGameProfile().getName(), slot, DropTraceFormat.fmt(now));
    }
}
