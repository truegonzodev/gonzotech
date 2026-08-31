package com.gonzotech.core.registry;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.ore.OreDefinition;
import com.gonzotech.core.ore.OreDefinition.Host;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
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
                if (ore.dropsExperience()) {
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
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }

    private ModBlocks() {
    }
}
