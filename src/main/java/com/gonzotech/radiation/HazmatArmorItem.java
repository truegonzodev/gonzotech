package com.gonzotech.radiation;

import com.gonzotech.core.text.GtUnits;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Часть хазмата (0.3.86): ванильный {@link ArmorItem} + лор комплекта.
 *
 * <p>ГОСТ лора (автор 03.10.2026): лор идёт ПРЯМО под именем/креатив-табом,
 * поэтому строки рисует САМ предмет ({@code appendHoverText} ваниль ставит
 * до блока атрибутов «когда надето»); раньше лор добавлялся событием
 * (HazmatTooltips) и падал ПОСЛЕ ванильных атрибутов. Итоговый порядок:
 * имя → таб → лор → (ваниль) «когда надето» → отступ → радиоактивность.</p>
 *
 * <p>Числа — из {@link Hazmat} ({@code FULL_SHIELDING}, {@code SOFT_DOSE_MILLI}),
 * не из lang: сменит баланс — лор обновится сам.</p>
 */
public class HazmatArmorItem extends ArmorItem {

    public HazmatArmorItem(ArmorMaterial material, ArmorType type, Properties properties) {
        super(material, type, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        Component percent = Component.literal(Hazmat.fullShieldingPercent() + "%");
        tooltip.add(Component.translatable("tooltip.gonzotech.hazmat.set", percent)
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.gonzotech.hazmat.soft_dose",
                        GtUnits.ztPerSecond(Hazmat.SOFT_DOSE_MILLI * RadUnits.MILLI))
                .withStyle(ChatFormatting.GRAY));
    }
}
