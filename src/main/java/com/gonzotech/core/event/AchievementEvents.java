package com.gonzotech.core.event;

import com.gonzotech.chalkboard.advancement.ModAdvancements;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.Locale;

/** Event-driven triggers for the new GonzoTech advancement branches. */
public final class AchievementEvents {
    private AchievementEvents() {
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (hasCesiumName(player.getItemInHand(event.getHand()))) {
            ModAdvancements.awardCesiumInteraction(player);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) return;
        awardForBlockInteraction(player, level, event.getPos(), player.getItemInHand(event.getHand()));
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) return;
        awardForBlockInteraction(player, level, event.getPos(), player.getItemInHand(event.getHand()));
    }

    /** Inventory checks also cover picking up, crafting, or otherwise receiving cesium items. */
    public static void checkCesiumInventory(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (hasCesiumName(inventory.getItem(slot))) {
                ModAdvancements.awardCesiumInteraction(player);
                return;
            }
        }
    }

    private static void awardForBlockInteraction(ServerPlayer player, ServerLevel level,
                                                  BlockPos pos, ItemStack heldStack) {
        BlockState blockState = level.getBlockState(pos);
        FluidState fluidState = level.getFluidState(pos);
        if (hasCesiumName(heldStack)
                || hasCesiumName(BuiltInRegistries.BLOCK.getKey(blockState.getBlock()))
                || hasCesiumName(BuiltInRegistries.FLUID.getKey(fluidState.getType()))) {
            ModAdvancements.awardCesiumInteraction(player);
        }
    }

    private static boolean hasCesiumName(ItemStack stack) {
        return !stack.isEmpty() && hasCesiumName(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    private static boolean hasCesiumName(ResourceLocation id) {
        return id != null && id.toString().toLowerCase(Locale.ROOT).contains("cesium");
    }
}
