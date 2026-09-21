package com.gonzotech.machines.crafting;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.chalkboard.progress.ModAttachments;
import com.gonzotech.chalkboard.progress.PlayerChalkboardProgress;
import com.gonzotech.core.registry.ModItems;
import com.gonzotech.machines.registry.ModMachines;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Discovery-2 workbench recipes and their progression rules.
 *
 * <p>The recipe JSON files are conventional vanilla shaped recipes. This
 * subscriber supplies the two per-player behaviours that a data recipe cannot:
 * it reveals every variant in the recipe book after Discovery 2, and changes a
 * prematurely crafted listed output into {@code botched_mechanism}.</p>
 */
@EventBusSubscriber(modid = GonzoTechMod.MOD_ID)
public final class TierTwoCrafting {

    private static final List<String> RECIPE_IDS = List.of(
        // Хазмат I (автор 22.09): гейт на крафт — Открытие 2. Абсорбента здесь
        // СОЗНАТЕЛЬНО нет: его крафт доступен всегда, гейтится только
        // видимость рецепта (RecipeUnlocks) — прямая просьба автора.
        "gonzotech:hazmat_helmet",
        "gonzotech:hazmat_chestplate",
        "gonzotech:hazmat_leggings",
        "gonzotech:hazmat_boots",
        "gonzotech:coil",
        "gonzotech:inductive_module",
        "gonzotech:wedge_punch",
        "gonzotech:flat_punch",
        "gonzotech:ingot_form",
        "gonzotech:plate_form",
        "gonzotech:core_form",
        "gonzotech:second_grinder",
        "gonzotech:second_press",
        "gonzotech:second_cobble_generator",
        "gonzotech:second_alloy_foundry",
        "gonzotech:second_crusher",
        "gonzotech:second_centrifuge",
        "gonzotech:second_electric_furnace",
        "gonzotech:second_accumulator",
        "gonzotech:second_wire_aluminum",
        "gonzotech:second_wire_copper",
        "gonzotech:second_wire_silver",
        "gonzotech:second_wire_gold",
        "gonzotech:second_water_pipe",
        "gonzotech:second_steam_pipe",
        "gonzotech:second_universal_fluid_pipe",
        "gonzotech:second_heat_pipe",
        "gonzotech:second_item_pipe",
        "gonzotech:second_item_node",
        "gonzotech:second_item_filter",
        "gonzotech:second_item_scavenger",
        "gonzotech:second_heat_node",
        "gonzotech:second_water_node",
        "gonzotech:second_steam_node",
        "gonzotech:second_universal_fluid_node",
        "gonzotech:second_wire_node",
        "gonzotech:second_universal_node",
        "gonzotech:second_pump",
        "gonzotech:second_nuclear_firebox",
        "gonzotech:second_steamgen_casing",
        "gonzotech:second_steamgen_core"
    );

    /** Prevents a 35-entry recipe-book grant on every player tick. Cleared on logout. */
    private static final Set<UUID> BOOK_GRANTED = ConcurrentHashMap.newKeySet();

    private TierTwoCrafting() {
    }

    /** Give all Discovery-2 recipe-book entries once that discovery is active. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        PlayerChalkboardProgress progress = serverPlayer.getData(ModAttachments.CHALKBOARD_PROGRESS);
        if (!progress.isRecipeTierUnlocked(2) || !BOOK_GRANTED.add(serverPlayer.getUUID())) return;

        List<ResourceKey<Recipe<?>>> keys = RECIPE_IDS.stream()
            .map(id -> ResourceKey.<Recipe<?>>create(Registries.RECIPE, ResourceLocation.parse(id)))
            .toList();
        serverPlayer.awardRecipesByKey(keys);
    }

    /** Allow re-granting after a reconnect (and avoid retaining player UUIDs indefinitely). */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        BOOK_GRANTED.remove(event.getEntity().getUUID());
    }

    /**
     * Crafting before Discovery 2 consumes the normal input but replaces each
     * result item with one botched mechanism, matching the established Tier-1
     * gate behaviour.
     */
    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack crafted = event.getCrafting();
        if (crafted.isEmpty() || !isGatedOutput(crafted.getItem())) return;

        PlayerChalkboardProgress progress = player.getData(ModAttachments.CHALKBOARD_PROGRESS);
        if (progress.isRecipeTierUnlocked(2)) return;

        int count = crafted.getCount();
        crafted.setCount(0);
        // Ветка PICKUP «тот же предмет на курсоре»: ванила УВЕЛИЧИВАЕТ курсор ДО
        // события (grow перед onTake), а в событии — выделенная копия, так что
        // нуление курсор не спасает. Отменяем прирост (и заодно переполнение
        // полного стака).
        ItemStack carried = player.containerMenu.getCarried();
        if (!carried.isEmpty() && carried.is(crafted.getItem())) {
            carried.shrink(count);
        }
        for (int i = 0; i < count; i++) {
            ItemStack botched = new ItemStack(ModItems.BOTCHED_MECHANISM.get());
            if (!player.getInventory().add(botched)) {
                player.drop(botched, false);
            }
        }
        player.displayClientMessage(
            Component.translatable("message.gonzotech.botched_craft").withStyle(ChatFormatting.RED),
            false
        );
    }

    /** true, если предмет — «закрытый» вывод Discovery-2 (гейт тира 2). */
    public static boolean isGatedOutput(Item item) {
        return item == ModItems.COIL.get()
            || item == ModItems.INDUCTIVE_MODULE.get()
            || item == ModItems.WEDGE_PUNCH.get()
            || item == ModItems.FLAT_PUNCH.get()
            || item == ModItems.INGOT_FORM.get()
            || item == ModItems.PLATE_FORM.get()
            || item == ModItems.CORE_FORM.get()
            || item == ModMachines.SECOND_GRINDER_ITEM.get()
            || item == ModMachines.SECOND_PRESS_ITEM.get()
            || item == ModMachines.SECOND_COBBLE_GENERATOR_ITEM.get()
            || item == ModMachines.SECOND_ALLOY_FOUNDRY_ITEM.get()
            || item == ModMachines.SECOND_CRUSHER_ITEM.get()
            // This confirmed recipe intentionally crafts the existing centrifuge.
            || item == ModMachines.SECOND_CENTRIFUGE_ITEM.get()
            || item == ModMachines.SECOND_ELECTRIC_FURNACE_ITEM.get()
            || item == ModMachines.SECOND_ACCUMULATOR_ITEM.get()
            || item == ModMachines.SECOND_WIRE_ITEM.get()
            || item == ModMachines.SECOND_WATER_PIPE_ITEM.get()
            || item == ModMachines.SECOND_STEAM_PIPE_ITEM.get()
            || item == ModMachines.SECOND_UNIVERSAL_FLUID_PIPE_ITEM.get()
            || item == ModMachines.SECOND_HEAT_PIPE_ITEM.get()
            || item == ModMachines.SECOND_ITEM_PIPE_ITEM.get()
            || item == ModMachines.SECOND_ITEM_NODE_ITEM.get()
            || item == ModMachines.SECOND_ITEM_FILTER_ITEM.get()
            || item == ModMachines.SECOND_ITEM_SCAVENGER_ITEM.get()
            || item == ModMachines.SECOND_HEAT_NODE_ITEM.get()
            || item == ModMachines.SECOND_WATER_NODE_ITEM.get()
            || item == ModMachines.SECOND_STEAM_NODE_ITEM.get()
            || item == ModMachines.SECOND_UNIVERSAL_FLUID_NODE_ITEM.get()
            || item == ModMachines.SECOND_WIRE_NODE_ITEM.get()
            || item == ModMachines.SECOND_UNIVERSAL_NODE_ITEM.get()
            || item == ModMachines.SECOND_PUMP_ITEM.get()
            || item == ModMachines.SECOND_NUCLEAR_FIREBOX_ITEM.get()
            || item == ModMachines.SECOND_STEAMGEN_CASING_ITEM.get()
            || item == ModMachines.SECOND_STEAMGEN_CORE_ITEM.get();
    }
}
