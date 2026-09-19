package com.gonzotech.core.registry;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.chalkboard.ChalkboardBlock;
import com.gonzotech.core.block.TungstenAbsorberBlock;
import com.gonzotech.core.fluid.ModFluids;
import com.gonzotech.core.fluid.MoltenCoriumBlock;
import com.gonzotech.core.ore.CesiumOreBlock;
import com.gonzotech.core.ore.IodineOreBlock;
import com.gonzotech.core.ore.OreDefinition;
import com.gonzotech.core.ore.OreDefinition.Host;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
        DeferredRegister.createBlocks(GonzoTechMod.MOD_ID);

    /** ore id -> (host -> зарегистрированный блок этого host-варианта). */
    public static final Map<String, Map<Host, DeferredBlock<? extends Block>>> ORE_BLOCKS = new LinkedHashMap<>();

    /**
     * Фаза 3 — «драгоценные» блоки-хранилища из 9 слитков (по одному на КАЖДЫЙ
     * слиток из {@link ModItems#INGOT_IDS}, без исключений). Ключ карты — id блока
     * вида {@code <metal>_block} (у {@code *_ingot} убираем суффикс {@code _ingot}).
     * Все блоки — beacon base (тег {@code minecraft:beacon_base_blocks}).
     */
    public static final Map<String, DeferredBlock<? extends Block>> METAL_BLOCKS = new LinkedHashMap<>();

    /** A normal tungsten block becomes a hidden GTH absorber only when heat plumbing touches it. */
    public static final DeferredBlock<TungstenAbsorberBlock> TUNGSTEN_ABSORBER = BLOCKS.registerBlock(
        "tungsten_block", TungstenAbsorberBlock::new, metalBlockProperties("tungsten"));

    /**
     * Доска резонанса (com.gonzotech.chalkboard) — Фаза 1: просто ставится,
     * ПКМ открывает экран конструктора формул. См.
     * info/gonzo_tech_chalkboard_design.md. Дерево/мел — не руда, не
     * требует инструмента, ломается быстро.
     */
    public static final DeferredBlock<ChalkboardBlock> CHALKBOARD = BLOCKS.registerBlock(
        "chalkboard",
        ChalkboardBlock::new,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .sound(SoundType.WOOD)
            .strength(2.5f, 3.0f)
    );

    /**
     * Фаза 3 — тестовый размещаемый блок «лунный грунт» (вкладка «Блоки»).
     * Просто ставится; копает лопата, как ванильный dirt-подобный блок.
     */
    public static final DeferredBlock<Block> LUNAR_DIRT = BLOCKS.registerSimpleBlock(
        "lunar_dirt",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .sound(SoundType.GRAVEL)
            .strength(1.1f, 1.6f)
    );

    // ─────────────────────── Материалы переработки: строительные блоки ───────────────────────
    // Свойства уже соответствуют утверждённой таблице. Требуемый вид/тир кирки
    // задаётся ресурсными тегами minecraft:mineable/pickaxe и minecraft:needs_*_tool.
    public static final DeferredBlock<Block> ARMOR_CONCRETE = BLOCKS.registerSimpleBlock(
        "armor_concrete", constructionMaterialProperties(SoundType.STONE, 12.0f, 14.0f, 0.59f));
    public static final DeferredBlock<Block> REINFORCED_ARMOR_CONCRETE = BLOCKS.registerSimpleBlock(
        "reinforced_armor_concrete", constructionMaterialProperties(SoundType.HEAVY_CORE, 20.0f, 24.0f, 0.57f));
    public static final DeferredBlock<Block> DURABLE_CONCRETE = BLOCKS.registerSimpleBlock(
        "durable_concrete", constructionMaterialProperties(SoundType.STONE, 8.0f, 46.0f, 0.58f));
    public static final DeferredBlock<Block> PORCELAIN = BLOCKS.registerSimpleBlock(
        "porcelain", constructionMaterialProperties(SoundType.STONE, 1.0f, 3.0f, 0.60f));
    public static final DeferredBlock<Block> SLAG_CONCRETE = BLOCKS.registerSimpleBlock(
        "slag_concrete", constructionMaterialProperties(SoundType.TUFF, 3.0f, 3.0f, 0.60f));
    public static final DeferredBlock<Block> INDUSTRIAL_CONCRETE = BLOCKS.registerSimpleBlock(
        "industrial_concrete", constructionMaterialProperties(SoundType.STONE, 5.0f, 6.0f, 0.60f));
    public static final DeferredBlock<Block> REINFORCED_INDUSTRIAL_CONCRETE = BLOCKS.registerSimpleBlock(
        "reinforced_industrial_concrete", constructionMaterialProperties(SoundType.STONE, 10.0f, 8.0f, 0.60f));

    private static BlockBehaviour.Properties constructionMaterialProperties(
        SoundType sound, float hardness, float explosionResistance, float friction
    ) {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .sound(sound)
            .strength(hardness, explosionResistance)
            .friction(friction)
            .requiresCorrectToolForDrops();
    }

    // ─────────────────────── Декорации для данжей: «саспенс» ───────────────────────
    // Обычные кубы и ванильные формы кирпичей: специальные модели/BE не нужны.
    public static final DeferredBlock<TransparentBlock> LEAD_STAINED_GLASS = BLOCKS.registerBlock(
        "lead_stained_glass", TransparentBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS));
    public static final DeferredBlock<Block> CRIMSON_OBSIDIAN = BLOCKS.registerSimpleBlock(
        "crimson_obsidian", BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN));
    public static final DeferredBlock<Block> SCULK_BRICKS = BLOCKS.registerSimpleBlock(
        "sculk_bricks", sculkBrickProperties());
    public static final DeferredBlock<Block> CHISELED_SCULK_BRICKS = BLOCKS.registerSimpleBlock(
        "chiseled_sculk_bricks", sculkBrickProperties());
    public static final DeferredBlock<Block> SMOOTH_SCULK_BRICKS = BLOCKS.registerSimpleBlock(
        "smooth_sculk_bricks", sculkBrickProperties());
    public static final DeferredBlock<StairBlock> SCULK_BRICK_STAIRS = BLOCKS.registerBlock(
        "sculk_brick_stairs",
        properties -> new StairBlock(SCULK_BRICKS.get().defaultBlockState(), properties),
        sculkBrickProperties());
    public static final DeferredBlock<SlabBlock> SCULK_BRICK_SLAB = BLOCKS.registerBlock(
        "sculk_brick_slab", SlabBlock::new, sculkBrickProperties());
    /** «Ограда» следует vanilla-логике stone-brick wall, а не деревянного fence. */
    public static final DeferredBlock<WallBlock> SCULK_BRICK_WALL = BLOCKS.registerBlock(
        "sculk_brick_wall", WallBlock::new, sculkBrickProperties());

    // ───────────────────────────── Декорации радиации ─────────────────────────────
    public static final DeferredBlock<Block> DEAD_DIRT = BLOCKS.registerSimpleBlock(
        "dead_dirt", BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)
            .sound(SoundType.GRAVEL).strength(1.05f, 6.0f));
    public static final DeferredBlock<com.gonzotech.space.block.GonzoFallingBlock> DEAD_SAND = BLOCKS.registerBlock(
        "dead_sand", com.gonzotech.space.block.GonzoFallingBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.SAND).sound(SoundType.GRAVEL).strength(0.5f, 0.5f));
    public static final DeferredBlock<Block> DEAD_STONE = BLOCKS.registerSimpleBlock(
        "dead_stone", BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).strength(2.55f, 6.0f));
    /** Ванильный pillar-state нужен: копируемые свойства OAK_LOG считывают AXIS. */
    public static final DeferredBlock<RotatedPillarBlock> DEAD_LOG = BLOCKS.registerBlock(
        "dead_log", RotatedPillarBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LOG));
    /** Опалённая растительность (cross-модель, без age): саженец и три размера пучка. */
    public static final DeferredBlock<com.gonzotech.core.block.DeadVegetationBlock> CHARRED_SAPLING = BLOCKS.registerBlock(
        "charred_sapling", com.gonzotech.core.block.DeadVegetationBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.SHORT_GRASS)
            .offsetType(BlockBehaviour.OffsetType.XZ));
    public static final DeferredBlock<com.gonzotech.core.block.DeadVegetationBlock> SCORCHED_TUFT = BLOCKS.registerBlock(
        "scorched_tuft", com.gonzotech.core.block.DeadVegetationBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.SHORT_GRASS)
            .offsetType(BlockBehaviour.OffsetType.XZ));
    public static final DeferredBlock<com.gonzotech.core.block.DeadVegetationBlock> SCORCHED_TUFT_MEDIUM = BLOCKS.registerBlock(
        "scorched_tuft_medium", com.gonzotech.core.block.DeadVegetationBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.SHORT_GRASS)
            .offsetType(BlockBehaviour.OffsetType.XZ));
    public static final DeferredBlock<com.gonzotech.core.block.DeadVegetationBlock> SCORCHED_TUFT_LARGE = BLOCKS.registerBlock(
        "scorched_tuft_large", com.gonzotech.core.block.DeadVegetationBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.SHORT_GRASS)
            .offsetType(BlockBehaviour.OffsetType.XZ));
    /** Застывший кориум — результат реакции расплавленного кориума с водой (аналог обсидиана). */
    public static final DeferredBlock<Block> CORIUM = BLOCKS.registerSimpleBlock(
        "corium", BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).sound(SoundType.STONE));
    /** Gray lava-like fluid block produced by Nuclear Firebox meltdown. */
    public static final DeferredBlock<LiquidBlock> MOLTEN_CORIUM = BLOCKS.registerBlock(
        "molten_corium", properties -> new MoltenCoriumBlock(ModFluids.MOLTEN_CORIUM.get(), properties),
        BlockBehaviour.Properties.ofFullCopy(Blocks.LAVA));
    /** Чисто декоративная бочка: top/side/bottom — лишь текстурные грани, без BE. */
    public static final DeferredBlock<Block> WASTE_BARREL = BLOCKS.registerSimpleBlock(
        "waste_barrel", BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
            .sound(SoundType.METAL).strength(2.0f, 6.0f));
    /** Мёртвая жижа: зыбкость рыхлого снега + пружинность слизи (см. DeadSlimeBlock). */
    public static final DeferredBlock<com.gonzotech.core.block.DeadSlimeBlock> DEAD_SLIME_BLOCK = BLOCKS.registerBlock(
        "dead_slime_block", com.gonzotech.core.block.DeadSlimeBlock::new,
        // noCollission() в свойствах НЕ ставим: он обнуляет blocksMotion(), а вода
        // через FlowingFluid.canHoldFluid смотрит именно его — входила в клетку и
        // ломала жижу с дропом (автор 2026-09-19: «вода не должна её смывать»).
        // Проходимость сущностей даёт сам класс — getCollisionShape() = empty().
        BlockBehaviour.Properties.ofFullCopy(Blocks.SLIME_BLOCK));
    public static final DeferredBlock<SlimeBlock> RADIOACTIVE_SLIME_BLOCK = BLOCKS.registerBlock(
        "radioactive_slime_block", SlimeBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.SLIME_BLOCK));
    /** Миска для питомцев (автор 2026-09-19, «ЖИРНЫЕ КОТЫ»): см. swag/PetBowlBlock. */
    public static final DeferredBlock<com.gonzotech.swag.PetBowlBlock> PET_BOWL = BLOCKS.registerBlock(
        "pet_bowl", com.gonzotech.swag.PetBowlBlock::new,
        BlockBehaviour.Properties.of().strength(0.6F).sound(SoundType.METAL).noOcclusion());

    // ─────────────────── Декорации метеоров и старых механизмов ───────────────────
    public static final DeferredBlock<Block> WEATHERED_PLATING = BLOCKS.registerSimpleBlock(
        "weathered_plating", BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).sound(SoundType.COPPER));
    public static final DeferredBlock<Block> DEBRIS = BLOCKS.registerSimpleBlock(
        "debris", BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).sound(SoundType.STONE));
    public static final DeferredBlock<Block> WEATHERED_DEBRIS = BLOCKS.registerSimpleBlock(
        "weathered_debris", BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).sound(SoundType.COPPER));
    /** Единственный анимированный куб: textures/block/desolation/mechanisms.png.mcmeta. */
    public static final DeferredBlock<Block> MECHANISMS = BLOCKS.registerSimpleBlock(
        "mechanisms", BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).sound(SoundType.STONE));
    public static final DeferredBlock<Block> WEATHERED_MECHANISMS = BLOCKS.registerSimpleBlock(
        "weathered_mechanisms", BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).sound(SoundType.COPPER));
    public static final DeferredBlock<Block> SILICON_CACHE = BLOCKS.registerSimpleBlock(
        "silicon_cache", BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).sound(SoundType.STONE));
    public static final DeferredBlock<Block> PLASTIC_WASTE = BLOCKS.registerSimpleBlock(
        "plastic_waste", BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).sound(SoundType.GRAVEL));

    private static BlockBehaviour.Properties sculkBrickProperties() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_BRICKS).sound(SoundType.SCULK);
    }

    // ─────────────────────────── Фаза 4: блоки космоса ───────────────────────────
    // Плейсхолдер-текстуры (см. textures/block/*.png). Балансы прочности пока
    // грубые: породы — как камень, грунты/песок — как земля/песок. Все блоки
    // копаются киркой (породы) / лопатой (грунты, песок) — теги ниже в data/.

    /**
     * Свойства блока-хранилища: normal = 5.0/6.0, soft = −30%/−40%, hard =
     * +75%/+50%. Серный блок задан отдельно, согласно утверждённой таблице.
     */
    private static BlockBehaviour.Properties metalBlockProperties(String metalId) {
        if (metalId.equals("sulfur")) {
            return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_YELLOW)
                .sound(SoundType.NETHERRACK)
                .strength(3.0f, 1.0f)
                .friction(0.70f)
                .requiresCorrectToolForDrops();
        }

        float hardness = 5.0f;
        float explosionResistance = 6.0f;
        if (Metals.hasSoftStorageBlock(metalId)) {
            hardness *= 0.70f;
            explosionResistance *= 0.60f;
        } else if (Metals.hasHardStorageBlock(metalId)) {
            hardness *= 1.75f;
            explosionResistance *= 1.50f;
        }
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .sound(SoundType.METAL)
            .strength(hardness, explosionResistance)
            .requiresCorrectToolForDrops();
    }

    /** Луна: базовая порода недр (аналог камня). */
    public static final DeferredBlock<Block> LUNAR_STONE = BLOCKS.registerSimpleBlock(
        "lunar_stone",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .sound(SoundType.STONE)
            .strength(3.2f, 5.0f)
            .requiresCorrectToolForDrops()
    );

    /** Луна: «богатая» порода — жилы/вкрапления в недрах. */
    public static final DeferredBlock<Block> RICH_LUNAR_STONE = BLOCKS.registerSimpleBlock(
        "rich_lunar_stone",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .sound(SoundType.STONE)
            .strength(4.0f, 5.0f)
            .requiresCorrectToolForDrops()
    );

    /** Луна: поверхностный «лунный песок» — падает как ванильный песок. */
    public static final DeferredBlock<com.gonzotech.space.block.GonzoFallingBlock> LUNAR_SAND =
        BLOCKS.registerBlock(
            "lunar_sand",
            com.gonzotech.space.block.GonzoFallingBlock::new,
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.SAND)
                .sound(SoundType.SAND)
                .strength(1.1f, 0.5f)
        );

    /** Марс: базовая порода недр. */
    public static final DeferredBlock<Block> MARTIAN_STONE = BLOCKS.registerSimpleBlock(
        "martian_stone",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_ORANGE)
            .sound(SoundType.STONE)
            .strength(3.2f, 5.0f)
            .requiresCorrectToolForDrops()
    );

    /** Марс: «богатая» порода — жилы/вкрапления. */
    public static final DeferredBlock<Block> RICH_MARTIAN_STONE = BLOCKS.registerSimpleBlock(
        "rich_martian_stone",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_ORANGE)
            .sound(SoundType.STONE)
            .strength(4.0f, 5.0f)
            .requiresCorrectToolForDrops()
    );

    /** Марс: поверхностный грунт (как земля, не падает). */
    public static final DeferredBlock<Block> MARTIAN_DIRT = BLOCKS.registerSimpleBlock(
        "martian_dirt",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_ORANGE)
            .sound(SoundType.GRAVEL)
            .strength(1.6f, 1.2f)
    );

    /** Европа: «сверхплотный лёд» глубинного панциря (не тает, скользкий). */
    public static final DeferredBlock<Block> SUPERDENSE_ICE = BLOCKS.registerSimpleBlock(
        "superdense_ice",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.ICE)
            .sound(SoundType.STONE)
            .strength(70.0f, 10.0f)
            .friction(0.9938f)
            .requiresCorrectToolForDrops()
    );

    /** Европа: верхний «европианский лёд» корки/глыб (не тает). */
    public static final DeferredBlock<Block> EUROPAN_ICE = BLOCKS.registerSimpleBlock(
        "europan_ice",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.ICE)
            .sound(SoundType.GLASS)
            .strength(1.0f, 1.0f)
            .friction(0.94f)
            .requiresCorrectToolForDrops()
    );

    /** Метеорит: тёмная космическая порода парящих глыб (орбиты/открытый космос). */
    public static final DeferredBlock<Block> METEOR = BLOCKS.registerSimpleBlock(
        "meteor",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_GRAY)
            .sound(SoundType.DEEPSLATE)
            .strength(9.0f, 8.0f)
            .requiresCorrectToolForDrops()
    );

    static {
        for (OreDefinition ore : OreDefinition.ALL) {
            Map<Host, DeferredBlock<? extends Block>> byHost = new EnumMap<>(Host.class);
            // Каждый host-вариант руды — самостоятельный блок СО СВОИМ loot table
            // файлом (data/gonzotech/loot_table/blocks/<host_prefix><id>_ore.json,
            // подхватывается по умолчанию по имени блока). Раньше здесь была
            // попытка сэкономить на файлах через lootFrom(), но: (1) она не
            // компилировалась в этой версии BlockBehaviour.Properties, и (2) даже
            // если бы скомпилировалась — она была бы концептуально неверной:
            // глубиносланцевый вариант должен дропать СЕБЯ (или свой raw_-предмет),
            // а не item канонического (например, каменного) варианта. Поэтому
            // никакого lootFrom(...) — просто обычная регистрация, лут берётся
            // автоматически по имени блока из уже существующего per-host файла.
            for (Host host : ore.hosts()) {
                // Звук по host'у: камень и кальцит — обычный STONE (у кальцита нет
                // отдельного "рудного" звука, решили не выделять), сланец — свой
                // DEEPSLATE (раньше по ошибке тоже попадал в ветку STONE), незер —
                // NETHER_ORE как и было (звук устраивает, не трогаем).
                SoundType sound = switch (host) {
                    case NETHER -> SoundType.NETHER_ORE;
                    case DEEPSLATE -> SoundType.DEEPSLATE;
                    case STONE, CALCITE -> SoundType.STONE;
                };
                BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                    .mapColor(host == Host.NETHER ? MapColor.NETHER : MapColor.STONE)
                    .sound(sound)
                    .strength(ore.hardness(host), ore.resistance())
                    .requiresCorrectToolForDrops();

                DeferredBlock<? extends Block> block;
                if (ore.id().equals("iodine")) {
                    // Йод: опыт (DropExperienceBlock) + фиолетовые reddust-партиклы
                    // при ударе/ПКМ — см. IodineOreBlock (как у редстоуна, без LIT).
                    block = BLOCKS.registerBlock(ore.blockId(host), p -> new IodineOreBlock(ore.experience(), p), props);
                } else if (ore.id().equals("cesium")) {
                    // Цезий: маленький «взрыв» (урон + частицы, без разрушения) при
                    // взаимодействии, если руда открыта воздуху/воде; КД 8 тиков per-block.
                    block = BLOCKS.registerBlock(ore.blockId(host), CesiumOreBlock::new, props);
                } else if (ore.dropsExperience()) {
                    // Опыт как у ванильных руд (уголь/лазурит/редстоун): блок регистрируется
                    // как DropExperienceBlock, XP вычисляется в NeoForge-потоке
                    // BlockDropsEvent -> getExpDrop -> EnchantmentHelper.processBlockExperience,
                    // а шёлковое касание гасит его ванильным эффектом block_experience (set 0).
                    block = BLOCKS.registerBlock(ore.blockId(host), p -> new DropExperienceBlock(ore.experience(), p), props);
                } else {
                    block = BLOCKS.registerSimpleBlock(ore.blockId(host), props);
                }
                byHost.put(host, block);
            }
            ORE_BLOCKS.put(ore.id(), byHost);
        }

        // Блоки-хранилища из слитков: мягкие/обычные/прочные материалы имеют
        // разные утверждённые значения hardness и blast resistance; серный блок
        // намеренно особый. Все остаются базой маяка (тег beacon_base_blocks).
        for (String ingotId : Metals.INGOT_IDS) {
            String metalId = Metals.base(ingotId);
            String blockId = metalId + "_block";
            if (metalId.equals("tungsten")) {
                METAL_BLOCKS.put(blockId, TUNGSTEN_ABSORBER);
            } else {
                METAL_BLOCKS.put(blockId, BLOCKS.registerSimpleBlock(blockId, metalBlockProperties(metalId)));
            }
        }
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }

    private ModBlocks() {
    }
}