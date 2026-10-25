package com.gonzotech.mixin;

import com.gonzotech.core.config.GonzoServerConfig;
import com.gonzotech.radiation.DropTraceFormat;
import com.gonzotech.radiation.InventoryDebugClientGate;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 0.3.291 (пункт 4 таблицы автора): любое запись стака «игроком» — финальная
 * точка и ПКМ-раскладки, и креативной выдачи, и клиентского применения.
 * Печатаем совместимость компонентов и количество ДО записи: «compat=false»
 * на QUICK_CRAFT-волне — улика рассинхрона клиентской картинки с сервером.
 * Серверная половина гейтится серверным TOML, клиентская — гейтом с входа
 * ({@code InventoryDebugClientGate}); строки помечаются [S]/[C].
 */
@Mixin(Slot.class)
public abstract class SlotSetByPlayerDebugMixin {
    @Unique
    private static final Logger GONZOTECH$LOG =
        org.slf4j.LoggerFactory.getLogger("gonzotech/radiation-inventory");

    @Inject(method = "setByPlayer(Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"))
    private void gonzotech$traceWrite(ItemStack stack, CallbackInfo callback) {
        this.gonzotech$log(stack);
    }

    @Inject(method = "setByPlayer(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"))
    private void gonzotech$traceWriteWithOriginal(ItemStack stack, ItemStack original, CallbackInfo callback) {
        this.gonzotech$log(stack);
    }

    private void gonzotech$log(ItemStack incoming) {
        Slot self = (Slot) (Object) this;
        boolean clientSide = gonzotech$isClientSideContainer(self);
        if (!(clientSide ? InventoryDebugClientGate.isOn()
                        : GonzoServerConfig.RADIATION_INVENTORY_DEBUG.get())) return;
        ItemStack stored = self.getItem();
        GONZOTECH$LOG.info("[RAD-INV][SLOT-WRITE][{}] container={} slot={} | было {} → станет {} | compat={} rad {}→{}",
            clientSide ? "C" : "S", self.container.getClass().getSimpleName(), self.getSlotIndex(),
            DropTraceFormat.fmt(stored), DropTraceFormat.fmt(incoming),
            DropTraceFormat.compat(stored, incoming),
            String.format("%.2f", com.gonzotech.radiation.ItemRadioactivity.getInduced(stored)),
            String.format("%.2f", com.gonzotech.radiation.ItemRadioactivity.getInduced(incoming)));
    }

    private static boolean gonzotech$isClientSideContainer(Slot slot) {
        var container = slot.container;
        if (container instanceof net.minecraft.world.entity.player.Inventory inventory
                && inventory.player != null) {
            return inventory.player.level().isClientSide();
        }
        if (container instanceof net.minecraft.world.level.block.entity.BlockEntity blockEntity
                && blockEntity.hasLevel()) {
            return blockEntity.getLevel().isClientSide();
        }
        // Неизвестный контейнер (чистый клиент меню без мира и т.п.): решаем по дистрибутиву.
        return net.neoforged.fml.loading.FMLEnvironment.dist.isClient();
    }
}
