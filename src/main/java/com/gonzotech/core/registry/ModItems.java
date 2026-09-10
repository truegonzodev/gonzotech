package com.gonzotech.core.registry;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.chalkboard.item.DiscoveryItem;
import com.gonzotech.core.ore.OreDefinition;
import com.gonzotech.core.ore.OreDefinition.Host;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ModItems {

    public static final DeferredRegister.Items ITEMS =
        DeferredRegister.createItems(GonzoTechMod.MOD_ID);

    /** ore id -> (host -> BlockItem этого host-варианта). */
    public static final Map<String, Map<Host, DeferredItem<BlockItem>>> ORE_BLOCK_ITEMS = new LinkedHashMap<>();

    /**
     * ore id -> предмет "сырья" (raw_<id> или raw_calcite для кальция).
     * Зарегистрирован для ВСЕХ руд из OreDefinition.ALL.
     */
    public static final Map<String, DeferredItem<Item>> RAW_ORE_ITEMS = new LinkedHashMap<>();

    /** Слитки металлов и сплавов (26 рудных + 21 сплава/дополнительный). */
    public static final Map<String, DeferredItem<Item>> INGOT_ITEMS = new LinkedHashMap<>();

    /**
     * Список id слитков — единый источник правды в {@link Metals#INGOT_IDS}
     * (вынесен туда, чтобы не было цикла статической инициализации с ModBlocks).
     */
    public static final List<String> INGOT_IDS = Metals.INGOT_IDS;

    /** Фаза 3 — пыль ({@code <metal>_dust}); ключ карты — id пыли. Не у всех металлов. */
    public static final Map<String, DeferredItem<Item>> DUST_ITEMS = new LinkedHashMap<>();

    /** Фаза 3 — самородки ({@code <metal>_nugget}); ключ карты — id самородка. Не у всех металлов. */
    public static final Map<String, DeferredItem<Item>> NUGGET_ITEMS = new LinkedHashMap<>();

    /** Фаза 3 — BlockItem'ы блоков-хранилищ ({@code <metal>_block}); ключ — id блока. У всех слитков. */
    public static final Map<String, DeferredItem<BlockItem>> METAL_BLOCK_ITEMS = new LinkedHashMap<>();

    /** BlockItem доски резонанса — см. ModBlocks.CHALKBOARD. */
    public static final DeferredItem<BlockItem> CHALKBOARD_ITEM =
        ITEMS.registerSimpleBlockItem("chalkboard", ModBlocks.CHALKBOARD);

    /**
     * Фаза 3 — «Заметки учёного»: будущее руководство по моду (аналог таумономикона /
     * лексикона Botania). Пока только предмет-заглушка без функционала: выдаётся
     * игроку один раз при первом входе в мир, крафтится бесформенно (книга + верстак).
     * ПКМ открывает GUI-буклет ({@link com.gonzotech.chalkboard.item.ScholarNotesItem}).
     */
    public static final DeferredItem<com.gonzotech.chalkboard.item.ScholarNotesItem> SCHOLAR_NOTES =
        ITEMS.registerItem("scholar_notes",
            props -> new com.gonzotech.chalkboard.item.ScholarNotesItem(props.stacksTo(1)));

    /** Фаза 3 — компонент для крафтов (псевдо-катушка). Вкладка «Компоненты». */
    public static final DeferredItem<Item> PSEUDO_COIL =
        ITEMS.registerSimpleItem("pseudo_coil");

    /**
     * Фаза 3 — «прикол»: ведро обсидиана. Бесполезный предмет: ведро лавы в
     * инвентаре при попадании в воду «застывает» в него (см. WaterPhase3Events).
     */
    public static final DeferredItem<Item> OBSIDIAN_BUCKET =
        ITEMS.registerItem("obsidian_bucket", props -> new Item(props.stacksTo(1)));

    /**
     * Фаза 3 — «прикол»: неудавшийся механизм. Выдаётся вместо результата, если
     * игрок пытается скрафтить закрытую машину (напр. эл. печь) до нужного
     * «Открытия» (см. RecipeGateEvents). Заготовка под будущую механику стресса.
     */
    public static final DeferredItem<Item> BOTCHED_MECHANISM =
        ITEMS.registerSimpleItem("botched_mechanism");

    /**
     * Фаза 3 — измерительный прибор «дозиметр». Пока предмет-плейсхолдер без
     * рецепта: когда игрок держит его в руке, на HUD показывается шкала
     * «Облучение» (см. {@code PsycheHud}).
     */
    public static final DeferredItem<Item> DOSIMETER =
        ITEMS.registerSimpleItem("dosimeter");

    /**
     * Фаза 3 — измерительный прибор «УФ-радиометр». Пока плейсхолдер без рецепта:
     * пока игрок держит его в руке, на HUD видна шкала «УФ излучение».
     */
    public static final DeferredItem<Item> UV_METER =
        ITEMS.registerSimpleItem("uv_meter");


    // ─── «Приколы»: два сусла (еда с тошнотой) ───

    /** Общая еда сусла: 1.5 голода (nutrition 3 полу-очка), 0 сытости. */
    private static final net.minecraft.world.food.FoodProperties MASH_FOOD =
        new net.minecraft.world.food.FoodProperties.Builder()
            .nutrition(3)
            .saturationModifier(0f)
            .build();

    /** Эффект употребления: тошнота 1 сек (20 т), шанс 100%. */
    private static final net.minecraft.world.item.component.Consumable MASH_CONSUMABLE =
        net.minecraft.world.item.component.Consumable.builder()
            .onConsume(new net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect(
                new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.CONFUSION, 20, 0), 1.0f))
            .build();

    /**
     * Фаза 3 — «прикол»: прото-сусло. Крафтится бесформенно (семена + костная
     * мука). Съедобно: +1.5 голода, 0 сытости, тошнота 1 сек.
     */
    public static final DeferredItem<Item> THE_PROTO_MASH =
        ITEMS.registerItem("the_proto_mash",
            props -> new Item(props.food(MASH_FOOD, MASH_CONSUMABLE)));

    /**
     * Фаза 3 — «прикол»: фруктовое сусло. Крафтится бесформенно (любой фрукт +
     * семена + костная мука). Съедобно: +1.5 голода, 0 сытости, тошнота 1 сек.
     */
    public static final DeferredItem<Item> THE_FRUIT_MASH =
        ITEMS.registerItem("the_fruit_mash",
            props -> new Item(props.food(MASH_FOOD, MASH_CONSUMABLE)));


    /** BlockItem тестового блока «лунный грунт» — см. ModBlocks.LUNAR_DIRT. Вкладка «Блоки». */
    public static final DeferredItem<BlockItem> LUNAR_DIRT_ITEM =
        ITEMS.registerSimpleBlockItem("lunar_dirt", ModBlocks.LUNAR_DIRT);

    // ─────────────────────── Фаза 4: BlockItem'ы блоков космоса ───────────────────────
    public static final DeferredItem<BlockItem> LUNAR_STONE_ITEM =
        ITEMS.registerSimpleBlockItem("lunar_stone", ModBlocks.LUNAR_STONE);
    public static final DeferredItem<BlockItem> RICH_LUNAR_STONE_ITEM =
        ITEMS.registerSimpleBlockItem("rich_lunar_stone", ModBlocks.RICH_LUNAR_STONE);
    public static final DeferredItem<BlockItem> LUNAR_SAND_ITEM =
        ITEMS.registerSimpleBlockItem("lunar_sand", ModBlocks.LUNAR_SAND);
    public static final DeferredItem<BlockItem> MARTIAN_STONE_ITEM =
        ITEMS.registerSimpleBlockItem("martian_stone", ModBlocks.MARTIAN_STONE);
    public static final DeferredItem<BlockItem> RICH_MARTIAN_STONE_ITEM =
        ITEMS.registerSimpleBlockItem("rich_martian_stone", ModBlocks.RICH_MARTIAN_STONE);
    public static final DeferredItem<BlockItem> MARTIAN_DIRT_ITEM =
        ITEMS.registerSimpleBlockItem("martian_dirt", ModBlocks.MARTIAN_DIRT);
    public static final DeferredItem<BlockItem> SUPERDENSE_ICE_ITEM =
        ITEMS.registerSimpleBlockItem("superdense_ice", ModBlocks.SUPERDENSE_ICE);
    public static final DeferredItem<BlockItem> EUROPAN_ICE_ITEM =
        ITEMS.registerSimpleBlockItem("europan_ice", ModBlocks.EUROPAN_ICE);
    public static final DeferredItem<BlockItem> METEOR_ITEM =
        ITEMS.registerSimpleBlockItem("meteor", ModBlocks.METEOR);

    /** Предметы «Открытие 1» .. «Открытие 16». */
    public static final List<DeferredItem<DiscoveryItem>> DISCOVERY_ITEMS = new ArrayList<>();

    static {
        for (OreDefinition ore : OreDefinition.ALL) {
            Map<Host, DeferredItem<BlockItem>> byHost = new EnumMap<>(Host.class);
            for (Host host : ore.hosts()) {
                var block = ModBlocks.ORE_BLOCKS.get(ore.id()).get(host);
                byHost.put(host, ITEMS.registerSimpleBlockItem(ore.blockId(host), block));
            }
            ORE_BLOCK_ITEMS.put(ore.id(), byHost);

            RAW_ORE_ITEMS.put(ore.id(), ITEMS.registerSimpleItem(ore.rawItemId()));
        }

        for (String ingotId : INGOT_IDS) {
            INGOT_ITEMS.put(ingotId, ITEMS.registerSimpleItem(ingotId));
        }

        // Фаза 3 — производные каждого слитка: блок-хранилище (у всех), пыль и
        // самородок (с исключениями, см. Metals). Регистрируем В ТОМ ЖЕ ПОРЯДКЕ,
        // что и слитки, чтобы во вкладке шло: слитки → блоки → пыль → самородки.
        for (String ingotId : INGOT_IDS) {
            String blockId = Metals.base(ingotId) + "_block";
            METAL_BLOCK_ITEMS.put(blockId,
                ITEMS.registerSimpleBlockItem(blockId, ModBlocks.METAL_BLOCKS.get(blockId)));
        }
        for (String ingotId : INGOT_IDS) {
            if (Metals.hasDust(ingotId)) {
                String dustId = Metals.base(ingotId) + "_dust";
                DUST_ITEMS.put(dustId, ITEMS.registerSimpleItem(dustId));
            }
        }
        for (String ingotId : INGOT_IDS) {
            if (Metals.hasNugget(ingotId)) {
                String nuggetId = Metals.base(ingotId) + "_nugget";
                NUGGET_ITEMS.put(nuggetId, ITEMS.registerSimpleItem(nuggetId));
            }
        }

        for (int i = 1; i <= 16; i++) {
            final int num = i;
            String name = "discovery_" + num;
            DISCOVERY_ITEMS.add(ITEMS.registerItem(name, props -> new DiscoveryItem(num, props.stacksTo(16))));
        }
    }

    public static DeferredItem<DiscoveryItem> getDiscoveryItem(int number) {
        int idx = Math.max(1, Math.min(16, number)) - 1;
        return DISCOVERY_ITEMS.get(idx);
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    private ModItems() {
    }
}
