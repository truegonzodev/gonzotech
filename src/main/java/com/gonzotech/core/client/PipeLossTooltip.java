package com.gonzotech.core.client;

import com.gonzotech.machines.network.PipeBlock;
import com.gonzotech.machines.network.PipeLoss;
import com.gonzotech.machines.network.PipeType;
import com.gonzotech.machines.network.SecondTierPipe;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.Locale;

/**
 * Лор-строка потерь за блок проноса (0.3.59): «Потери: 0.08 GTU/блок» для
 * проводов/узлов проводов и «Потери: 0.22 GTH/блок» для теплотруб/их узлов.
 * Числа берутся из {@link PipeLoss} — единственного источника (сменишь константу,
 * лор обновится сам). Универсальные узлы потерь не имеют — строки нет; жидкости,
 * предметы и составные блоки тоже без строки.
 */
public final class PipeLossTooltip {
    private PipeLossTooltip() {}

    public static void append(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof BlockItem item)) return;
        Block block = item.getBlock();
        if (!(block instanceof PipeBlock pipe)) return; // узлы/трубы — простые носители
        PipeType type = pipe.pipeType();
        if (type != PipeType.WIRE && type != PipeType.HEAT) return; // жидкости/предметы — без потерь
        long milli = PipeLoss.perCell(block instanceof SecondTierPipe, type == PipeType.HEAT);
        String value = String.format(Locale.ROOT, "%.2f", milli / 1000.0);
        event.getToolTip().add(Component.empty());
        event.getToolTip().add(Component.translatable(
                type == PipeType.HEAT ? "tooltip.gonzotech.pipe_loss_heat" : "tooltip.gonzotech.pipe_loss",
                value).withStyle(ChatFormatting.GRAY));
    }
}
