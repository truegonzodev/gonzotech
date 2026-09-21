package com.gonzotech.mixin;

import com.gonzotech.chalkboard.progress.ModAttachments;
import com.gonzotech.chalkboard.progress.PlayerChalkboardProgress;
import com.gonzotech.core.event.Phase3Events;
import com.gonzotech.core.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Гейт крафта для Shift-кликов по слоту результата (quick-craft) — единственный
 * путь крафта, который НЕ проходит через {@code ResultSlot.onTake}.
 * <p>
 * Почему сломано в ванили (1.21.4, {@code CraftingMenu.quickMoveStack}):
 * <ol>
 *   <li>слот результата копируется: {@code copy = slot.getItem().copy()};</li>
 *   <li>реальный стак переносится в инвентарь {@code moveItemStackTo} (живой
 *       инстанс мутируется, инвентарь получает предметы);</li>
 *   <li>{@code ItemCraftedEvent} срабатывает из {@code onQuickCraft} с <b>копией</b> —
 *       наш обработчик в {@link Phase3Events#onItemCrafted} нулит копию, а в
 *       инвентаре игрока уже лежит настоящая машина;</li>
 *   <li>расход сетки крафта в рантайме не происходит — результат пересчитывается
 *       по-новому, и «закрытую» машину можно крафтить бесконечно и бесплатно.</li>
 * </ol>
 * Все остальные пути взятия (обычный клик, SWAP-горячая клавиша, Q/THROW,
 * PICKUP с тем же предметом на курсоре) проходят через {@code onTake} с живым
 * стакком и обрабатываются самим {@code Phase3Events.onItemCrafted}.
 * <p>
 * Здесь перехватываем быстрый крафт закрытого результата ДО ванильной логики:
 * сетка тратится ровно один раз, слот результата очищается, игрок получает
 * {@code botched_mechanism} — ровно как при обычном клике.
 */
@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void gonzotech$gateQuickCraft(Player player, int slotId, CallbackInfoReturnable<ItemStack> cir) {
        if (slotId != CraftingMenu.RESULT_SLOT) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        CraftingMenu self = (CraftingMenu) (Object) this;
        Slot resultSlot = self.getResultSlot();
        ItemStack result = resultSlot.getItem();
        if (result.isEmpty()) return;

        Integer requiredTier = Phase3Events.requiredTierFor(result.getItem());
        if (requiredTier == null) return;

        PlayerChalkboardProgress progress = serverPlayer.getData(ModAttachments.CHALKBOARD_PROGRESS);
        if (progress.isRecipeTierUnlocked(requiredTier)) return;

        // «Выстреливаем» крафт ровно один раз: расходим сетку по одному на каждую
        // непустую ячейку (как ResultSlot.onTake) и очищаем результат.
        for (Slot gridSlot : self.getInputGridSlots()) {
            if (!gridSlot.getItem().isEmpty()) {
                gridSlot.remove(1);
            }
        }
        resultSlot.setByPlayer(ItemStack.EMPTY);

        int count = result.getCount();
        for (int i = 0; i < count; i++) {
            ItemStack botched = new ItemStack(ModItems.BOTCHED_MECHANISM.get());
            if (!serverPlayer.getInventory().add(botched)) {
                serverPlayer.drop(botched, false);
            }
        }
        serverPlayer.displayClientMessage(
            Component.translatable("message.gonzotech.botched_craft").withStyle(ChatFormatting.RED),
            false
        );

        // Быстрый крафт «завершён»: продолжать ванильную quick-move-логику не надо
        // (покажет while-циклу в AbstractContainerMenu.clicked, что переносить нечего).
        cir.setReturnValue(ItemStack.EMPTY);
    }
}
