package com.gonzotech;

import com.gonzotech.core.registry.ModBlocks;
import com.gonzotech.core.registry.ModCreativeTabs;
import com.gonzotech.core.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Точка входа мода. Здесь только регистрация шины событий и заглушка
 * common-setup — реальная инициализация подсистем (core/chalkboard/
 * machines/space/mind/swag) будет вешаться сюда по мере готовности.
 */
@Mod(GonzoTechMod.MOD_ID)
public class GonzoTechMod {

    public static final String MOD_ID = "gonzotech";
    private static final Logger LOGGER = LogUtils.getLogger();

    public GonzoTechMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);

        // Игровая шина (блоки, тики, взаимодействия) — сюда позже
        // повесится, например, SeedFormulaManager (core) на WorldEvent.Load.
        // NeoForge.EVENT_BUS.register(this);

        LOGGER.info("[Gonzo Tech] Mod class constructed, mod_id={}, {} руд зарегистрировано",
            MOD_ID, com.gonzotech.core.ore.OreDefinition.ALL.size());
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("[Gonzo Tech] Common setup complete — core systems ready to attach.");
    }
}
