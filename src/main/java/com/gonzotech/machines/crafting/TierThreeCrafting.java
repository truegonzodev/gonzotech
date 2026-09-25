package com.gonzotech.machines.crafting;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.chalkboard.progress.ModAttachments;
import com.gonzotech.chalkboard.progress.PlayerChalkboardProgress;
import com.gonzotech.core.registry.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Рецепты «Открытия 3» (Эпоха III) и их прогрессия — по образцу {@link TierTwoCrafting}.
 *
 * <p>Файлы рецептов — обычные ванильные shaped-рецепты; здесь два per-player правила,
 * которых рецепт-данные не умеют:</p>
 * <ul>
 *   <li>все рецепты текущего пакета выдаются в книгу только после активации «Открытия 3»
 *       ({@code PlayerChalkboardProgress.isRecipeTierUnlocked(3)});</li>
 *   <li>до открытия 3 только восемь machine-block outputs расходуют ингредиенты и
 *       подменяются на {@code botched_mechanism}. Компоненты и корпуса физически
 *       крафтятся до открытия 3 без подмены результата.</li>
 * </ul>
 *
 * <p>Быстрый крафт (Shift-клик) закрыт отдельно — {@code CraftingMenuMixin} берёт тир из
 * {@code Phase3Events.requiredTierFor}, который теперь знает и про третий тир.</p>
 *
 * <p>Состав тира (24.09.2026): три тяжёлые двери, линия брожения/разлива
 * (чан, сусловарочный котёл, дистиллятор, ректификатор, змеевиковый конденсатор,
 * разливной кран), наполнитель и химический завод.</p>
 */
@EventBusSubscriber(modid = GonzoTechMod.MOD_ID)
public final class TierThreeCrafting {

    /** Рецепты «Открытия 3» (показ в книге — по тиру, крафт — по тиру). */
    private static final List<String> RECIPE_IDS = List.of(
        // Компоненты и корпуса: видны после открытия 3, но физически крафтятся всегда.
        "gonzotech:energy_module_redstone",
        "gonzotech:energy_module_aluminum",
        "gonzotech:energy_module_gold",
        "gonzotech:energy_module_silver",
        "gonzotech:motor_copper",
        "gonzotech:motor_aluminum",
        "gonzotech:motor_silver",
        "gonzotech:motor_gold",
        "gonzotech:logic_module",
        "gonzotech:transistor",
        "gonzotech:fluid_module",
        "gonzotech:sheathing",
        "gonzotech:aluminum_housing",
        // Машины и двери текущей Эпохи III.
        "gonzotech:third_heavy_door_lead",
        "gonzotech:third_heavy_door_tungsten",
        "gonzotech:third_hermetic_door",
        "gonzotech:third_fermentation_vat",
        "gonzotech:third_wort_kettle",
        "gonzotech:third_distiller",
        "gonzotech:third_rectifier",
        "gonzotech:third_snaketype_condenser",
        "gonzotech:third_filler",
        "gonzotech:third_chemical_plant",
        "gonzotech:third_dispensing_tap"
    );

    /** Одна выдача книги на сессию (не каждый тик). Сбрасывается на выходе игрока. */
    private static final Set<UUID> BOOK_GRANTED = ConcurrentHashMap.newKeySet();

    private TierThreeCrafting() {
    }

    /** Выдать рецепты книги, когда «Открытие 3» активировано. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        PlayerChalkboardProgress progress = serverPlayer.getData(ModAttachments.CHALKBOARD_PROGRESS);
        if (!progress.isRecipeTierUnlocked(3) || !BOOK_GRANTED.add(serverPlayer.getUUID())) return;

        List<ResourceKey<Recipe<?>>> keys = RECIPE_IDS.stream()
            .map(id -> ResourceKey.<Recipe<?>>create(Registries.RECIPE, ResourceLocation.parse(id)))
            .toList();
        serverPlayer.awardRecipesByKey(keys);
    }

    /** Разрешить повторную выдачу после переподключения. */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        BOOK_GRANTED.remove(event.getEntity().getUUID());
    }

    /**
     * Крафт до «Открытия 3» расходует обычные ингредиенты, но каждый результат заменяется
     * «заплетённым механизмом» — как у тиров 1 и 2.
     */
    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack crafted = event.getCrafting();
        if (crafted.isEmpty() || !isGatedOutput(crafted.getItem())) return;

        PlayerChalkboardProgress progress = player.getData(ModAttachments.CHALKBOARD_PROGRESS);
        // «И»: тир 3 + дополнительные условия предмета (сейчас таких нет).
        if (progress.isRecipeTierUnlocked(3)
                && com.gonzotech.core.event.Phase3Events.extraGateMet(player, crafted.getItem())) return;

        int count = crafted.getCount();
        crafted.setCount(0);
        // Ветка PICKUP «тот же предмет на курсоре»: ванила увеличивает курсор ДО события.
        ItemStack carried = player.containerMenu.getCarried();
        if (!carried.isEmpty() && carried.is(crafted.getItem())) {
            carried.shrink(count);
        }
        com.gonzotech.core.event.Phase3Events.grantBotchedMechanism(player, count);
    }

    /** true, если предмет — «закрытый» вывод «Открытия 3» (гейт тира 3). */
    public static boolean isGatedOutput(Item item) {
        return item == com.gonzotech.machines.registry.ModMachines.THIRD_FERMENTATION_VAT_ITEM.get()
            || item == com.gonzotech.machines.registry.ModMachines.THIRD_WORT_KETTLE_ITEM.get()
            || item == com.gonzotech.machines.registry.ModMachines.THIRD_DISTILLER_ITEM.get()
            || item == com.gonzotech.machines.registry.ModMachines.THIRD_RECTIFIER_ITEM.get()
            || item == com.gonzotech.machines.registry.ModMachines.THIRD_SNAKETYPE_CONDENSER_ITEM.get()
            || item == com.gonzotech.machines.registry.ModMachines.THIRD_FILLER_ITEM.get()
            || item == com.gonzotech.machines.registry.ModMachines.THIRD_CHEMICAL_PLANT_ITEM.get()
            || item == com.gonzotech.machines.registry.ModMachines.THIRD_DISPENSING_TAP_ITEM.get();
    }
}
