package com.gonzotech.core.client;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.item.CustomAlloyItem;
import com.gonzotech.core.registry.ModItems;
import com.gonzotech.machines.processing.AlloyMaterialCatalog;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.List;
import java.util.Locale;

/**
 * Статы базовых материалов в тултипе (автор 2026-09-18): после аттачмента
 * «Открытие 3» (предмет {@code gonzotech:discovery_3} в инвентаре) ГЛАВНАЯ
 * форма каждого материала каталога — слиток (для ванильных: слитки
 * железо/медь/золото, сам алмаз, сам редстоун) — показывает те же строки
 * статов, что и custom_alloy, но статично, из {@link AlloyMaterialCatalog}:
 * материал их «не несёт», просто имеет тултип. Пыль и самородки НЕ
 * покрываются. Recipe-only материалы (уголь, глина — только компоненты
 * именных формул, genericAllowed = false) тоже НЕ покрываются.
 * Покрыто 52 автора (26 рудных + 21 сплав + 5 ванильных) + кремний
 * (53-я строка каталога — реальный предмет). До аттачмента тултипы не видны.
 */
@EventBusSubscriber(modid = GonzoTechMod.MOD_ID, value = net.minecraft.util.Dist.CLIENT)
public final class MaterialStatTooltips {

    private MaterialStatTooltips() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        Player player = event.getEntity();
        // На старте тултип собирается для search-tree без игрока — не трогаем.
        if (player == null) return;
        // Ленивая выборка: класс может загрузиться раньше заморозки реестра.
        Item attachment = ModItems.getDiscoveryItem(3).get();
        if (!hasAttachment(player, attachment)) return;

        ItemStack stack = event.getItemStack();
        AlloyMaterialCatalog.Ingredient ingredient = AlloyMaterialCatalog.ingredient(stack);
        if (ingredient == null) return;
        // Recipe-only (уголь/глина — компоненты формул, не материал) — мимо.
        if (!ingredient.genericAllowed()) return;
        AlloyMaterialCatalog.Material m = ingredient.material();
        // Только main host: сам слиток (или алмаз/редстоун для ванильных).
        if (m.displayItem() != stack.getItem()) return;

        List<Component> tip = event.getToolTip();
        tip.add(Component.empty());
        tip.add(CustomAlloyItem.stat("tooltip.gonzotech.custom_alloy.strength", m.strength(),
                CustomAlloyItem.beneficialColor(m.strength())));
        tip.add(CustomAlloyItem.stat("tooltip.gonzotech.custom_alloy.brittleness", m.brittleness(),
                CustomAlloyItem.brittlenessColor(m.brittleness())));
        tip.add(CustomAlloyItem.stat("tooltip.gonzotech.custom_alloy.inertness", m.inertness(),
                CustomAlloyItem.beneficialColor(m.inertness())));
        tip.add(CustomAlloyItem.stat("tooltip.gonzotech.custom_alloy.conductivity", m.conductivity(),
                CustomAlloyItem.conductivityColor(m.conductivity())));
        tip.add(CustomAlloyItem.stat("tooltip.gonzotech.custom_alloy.heat", m.heatResistance(),
                CustomAlloyItem.beneficialColor(m.heatResistance())));
        tip.add(CustomAlloyItem.stat("tooltip.gonzotech.custom_alloy.plasticity", m.plasticity(), 0x5555FF));
        tip.add(CustomAlloyItem.stat("tooltip.gonzotech.custom_alloy.weight", m.weight(),
                CustomAlloyItem.weightColor(m.weight())));
        tip.add(Component.translatable("tooltip.gonzotech.custom_alloy.tier",
                Component.translatable("tooltip.gonzotech.custom_alloy.tier." + m.toolTier().name().toLowerCase(Locale.ROOT))
                        .withColor(CustomAlloyItem.tierColor(m.toolTier()))));
    }

    /** Аттачмент «Открытие 3» в любом слоте (инвентарь + экипировка + offhand). */
    private static boolean hasAttachment(Player player, Item attachment) {
        NonNullList<ItemStack> main = player.getInventory().items;
        for (int i = 0; i < main.size(); i++) {
            if (main.get(i).getItem() == attachment) return true;
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (player.getItemBySlot(slot).getItem() == attachment) return true;
        }
        return false;
    }
}
