package com.gonzotech.core.client;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.item.CustomAlloyItem;
import com.gonzotech.chalkboard.network.NotesNetwork;
import com.gonzotech.machines.processing.AlloyMaterialCatalog;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;
import java.util.Locale;

/**
 * Статы базовых материалов в тултипе (автор 2026-09-18, гейт исправлен 22.09.2026):
 * после активации <b>«Открытия 2»</b> ГЛАВНАЯ форма каждого материала каталога —
 * слиток (для ванильных: слитки железо/медь/золото, сам алмаз, сам редстоун) —
 * показывает те же строки статов, что и custom_alloy, но статично, из
 * {@link AlloyMaterialCatalog}: материал их «не несёт», просто имеет тултип.
 * Пыль и самородки НЕ покрываются. Recipe-only материалы (уголь, глина — только
 * компоненты именных формул, genericAllowed = false) тоже НЕ покрываются.
 * Покрыто 52 автора (26 рудных + 21 сплав + 5 ванильных) + кремний
 * (53-я строка каталога — реальный предмет).
 *
 * <p><b>Почему не «предмет в инвентаре».</b> Раньше гейт проверял наличие
 * {@code gonzotech:discovery_3} в инвентаре — то есть открывался «на время
 * ношения»: получил предмет (играешь дальше с открытыми статами) — проюзал —
 * статы исчезли. Автор 22.09.2026: статы должны НАЧИНАТЬ показываться и
 * показываться НАВСЕГДА после активации второго «Открытия». Теперь гейт —
 * постоянный тир из {@link NotesNetwork#isTierUnlocked(int)} (тот же серверный
 * прогресс, что открывает крафты тира 2), а сам payload приходит на входе в игру
 * и сразу после использования «Открытия».</p>
 */
@EventBusSubscriber(modid = GonzoTechMod.MOD_ID, value = Dist.CLIENT)
public final class MaterialStatTooltips {

    private MaterialStatTooltips() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        Player player = event.getEntity();
        // На старте тултип собирается для search-tree без игрока — не трогаем.
        if (player == null) return;
        // Гейт — ПОСТОЯННАЯ активация «Открытия 2» (автор 22.09.2026), а не предмет
        // в инвентаре: статы видны навсегда, в том числе после проюза всех открытий.
        if (!NotesNetwork.isTierUnlocked(2)) return;

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

}
