package com.gonzotech.core.client;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.config.GonzoClientConfig;
import com.gonzotech.core.event.Phase3Events;
import com.gonzotech.core.tooltip.GateRequirement;
import com.gonzotech.core.tooltip.GateTooltipNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

/** The optional last two lines of the existing vanilla advanced-tooltip block. */
@EventBusSubscriber(modid = GonzoTechMod.MOD_ID, value = Dist.CLIENT)
public final class GateTooltips {
    private GateTooltips() {}

    public static void registerConfigScreen(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (mod, parent) -> new ConfigurationScreen(mod, parent));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        GateTooltipNetwork.clearClient();
    }

    // Called once, by UniversalTooltip AFTER the vanilla id/components lines.
    public static void append(ItemTooltipEvent event) {
        var player = event.getEntity();
        if (player == null || event.getItemStack().isEmpty()) return;
        if (!GateRequirement.visible(player.isCreative(), event.getFlags().isAdvanced(),
                GonzoClientConfig.SPEC.isLoaded() && GonzoClientConfig.EXTENDED_ADVANCED_TOOLTIPS.get())) return;

        var item = event.getItemStack().getItem();
        Integer tier = Phase3Events.requiredTierFor(item);
        GateRequirement craft = new GateRequirement(tier == null ? 0 : tier,
                Phase3Events.requiresSunEvent(item) ? GateRequirement.Extra.SUN_EVENT : GateRequirement.Extra.NONE);
        event.getToolTip().add(Component.translatable("tooltip.gonzotech.gate.craft", describe(craft))
                .withStyle(ChatFormatting.DARK_GRAY));
        List<GateRequirement> book = GateTooltipNetwork.bookGates(BuiltInRegistries.ITEM.getKey(item));
        Component bookText = book == null ? Component.translatable("tooltip.gonzotech.gate.waiting") : alternatives(book);
        event.getToolTip().add(Component.translatable("tooltip.gonzotech.gate.recipe", bookText)
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static Component alternatives(List<GateRequirement> rules) {
        MutableComponent text = Component.empty();
        for (var rule : rules) {
            if (!text.getSiblings().isEmpty()) text.append(" / ");
            text.append(describe(rule));
        }
        return text;
    }

    private static Component describe(GateRequirement rule) {
        MutableComponent text = rule.discovery() > 0
                ? Component.translatable("tooltip.gonzotech.gate.discovery", rule.discovery())
                : Component.empty();
        if (rule.extra() != GateRequirement.Extra.NONE) {
            if (rule.discovery() > 0) text.append(" + ");
            text.append(Component.translatable(rule.extra() == GateRequirement.Extra.SUN_EVENT
                    ? "tooltip.gonzotech.gate.sun_event" : "tooltip.gonzotech.gate.play_time"));
        } else if (rule.discovery() == 0) {
            return Component.translatable("tooltip.gonzotech.gate.none");
        }
        return text;
    }
}
