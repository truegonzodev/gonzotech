package com.gonzotech.radiation.client;

import com.gonzotech.core.registry.ModItems;
import com.gonzotech.core.text.GtUnits;
import com.gonzotech.radiation.Hazmat;
import com.gonzotech.radiation.RadUnits;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Лор комплекта хазмата (автор 22.09.2026) — серый, у КАЖДОЙ из четырёх частей
 * (маска, фартук, трико, шуфли), две строки:
 *
 * <pre>
 *   Полный комплект: 60% защиты от облучения,
 *   дозой меньше 20 mZt/с.
 * </pre>
 *
 * <p>Числа берутся из {@link Hazmat} ({@code FULL_SHIELDING}, {@code SOFT_DOSE_MILLI}),
 * а не переписаны в lang: если автор поменяет баланс — лор поменяется сам. Значение
 * «20 mZt» покрашено цветом радиации по ГОСТу единиц, «/с» наследует серый цвет
 * строки. Знак «%» собирается в java (в lang — только {@code %s}: одиночный «%»
 * в шаблоне ломает форматирование строки).</p>
 */
public final class HazmatTooltips {

    private HazmatTooltips() {
    }

    public static void append(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!isHazmatPiece(stack)) {
            return;
        }
        Component percent = Component.literal(Hazmat.fullShieldingPercent() + "%");
        event.getToolTip().add(Component.translatable("tooltip.gonzotech.hazmat.set", percent)
                .withStyle(ChatFormatting.GRAY));
        event.getToolTip().add(Component.translatable("tooltip.gonzotech.hazmat.soft_dose",
                        GtUnits.ztPerSecond(Hazmat.SOFT_DOSE_MILLI * RadUnits.MILLI))
                .withStyle(ChatFormatting.GRAY));
    }

    /** Это одна из частей хазмата (та же четвёрка, что считает сет в {@link Hazmat#pieces}). */
    private static boolean isHazmatPiece(ItemStack stack) {
        for (var piece : ModItems.HAZMAT_PIECES) {
            if (stack.is(piece.get())) {
                return true;
            }
        }
        return false;
    }
}
