package com.gonzotech;

import com.gonzotech.chalkboard.command.ChalkboardCommand;
import com.gonzotech.chalkboard.network.ChalkboardNetwork;
import com.gonzotech.chalkboard.progress.ModAttachments;
import com.gonzotech.core.network.CesiumBlastRequestPayload;
import com.gonzotech.core.ore.CesiumOreBlock;
import com.gonzotech.core.registry.ModBlocks;
import com.gonzotech.core.registry.ModCreativeTabs;
import com.gonzotech.core.registry.ModDataComponents;
import com.gonzotech.core.registry.ModFeatures;
import com.gonzotech.core.fluid.ModFluids;
import com.gonzotech.core.registry.ModItems;
import com.gonzotech.core.registry.ModRecipeSerializers;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.gonzotech.machines.registry.ModMachines;
import com.gonzotech.machines.registry.ModMenus;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

@Mod(GonzoTechMod.MOD_ID)
public class GonzoTechMod {

    public static final String MOD_ID = "gonzotech";
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Максимальная дистанция (блоков) до цезиевой руды для запроса взрыва с клиента. */
    private static final double MAX_BLAST_REQUEST_DISTANCE = 5.0D;

    public GonzoTechMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloads);

        // Жидкости регистрируются ДО блоков: LiquidBlock расплавленного кориума
        // берёт источник жидкости из уже заполненного регистра при своём событии.
        ModFluids.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModDataComponents.register(modEventBus);
        // Эффекты мода: радиация (некроз/очищение) + психика (тремор/сердечный приступ).
        com.gonzotech.core.registry.ModEffects.register(modEventBus);
        ModRecipeSerializers.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModFeatures.register(modEventBus);
        ModAttachments.register(modEventBus);
        com.gonzotech.core.psyche.ModPsycheAttachments.register(modEventBus);
        com.gonzotech.core.registry.ModParticles.PARTICLE_TYPES.register(modEventBus);

        // Фаза 2 — паровая ветка энергетики (машины, BlockEntity, меню).
        ModMachines.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenus.register(modEventBus);

        // При остановке сервера сбросить транзитный учёт потока труб (держит ссылки на Level).
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e) -> {
            com.gonzotech.machines.network.FlowTracker.clearAll();
            com.gonzotech.machines.network.ItemFlowTracker.clearAll();
            com.gonzotech.machines.network.UniversalNodeComparator.clearAll();
            com.gonzotech.machines.network.PipeFlowLedger.clearAll();
            com.gonzotech.machines.turbine.TurbineStructure.clearAll();
            com.gonzotech.machines.steamgen.SteamGenStructure.clearAll();
        });

        NeoForge.EVENT_BUS.addListener(ChalkboardCommand::onRegisterCommands);
        // Радиационная диагностика: /gonzotech debug purge|getdose.
        NeoForge.EVENT_BUS.addListener(com.gonzotech.radiation.RadiationDebugCommand::onRegisterCommands);
        // Админ-команды психики: /gonzotech debug psyche add|remove|set|trigger (автор 22.09).
        NeoForge.EVENT_BUS.addListener(com.gonzotech.core.psyche.command.PsycheCommand::onRegisterCommands);

        // Фаза 4 — космос: отладочный телепорт /gonzotech tp <dimension>.
        NeoForge.EVENT_BUS.addListener(com.gonzotech.space.SpaceCommand::onRegisterCommands);
        // Фаза 4 — гравитация космических миров (атрибуты по измерению).
        NeoForge.EVENT_BUS.register(com.gonzotech.space.SpaceGravity.class);
        NeoForge.EVENT_BUS.register(com.gonzotech.space.SpaceSleep.class);
        NeoForge.EVENT_BUS.addListener(com.gonzotech.chalkboard.advancement.ModAdvancements::onPlayerLoggedIn);
        // Постоянные «Открытия» (тиры 1/2) должны доехать до клиента сразу при входе:
        // на них завязаны клиентские гейты (тултипы статов материалов, страницы заметок),
        // а не только события в мире.
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
                com.gonzotech.chalkboard.network.NotesNetwork.sendToPlayer(sp);
                // Рецепты пройденного тира 1 «догоняют» обновления списка (тир 2 — в TierTwoCrafting).
                com.gonzotech.chalkboard.advancement.RecipeUnlocks.regrantOnLogin(sp);
            }
        });

        // Фаза 3 — «мелкие фишки»: гейт крафта, свинец в ванильных печах, эффекты в воде.
        NeoForge.EVENT_BUS.register(com.gonzotech.core.event.Phase3Events.class);
        // Радиация (спека 2026-09-20): доза шкалы, наведённый фон предметов,
        // динамический фон чанков, учёт поставленных радио-блоков.
        NeoForge.EVENT_BUS.register(com.gonzotech.radiation.RadiationSystem.class);
        // Психика (спека 2026-09-22): стресс и экзистенциальный кризис в очках.
        NeoForge.EVENT_BUS.register(com.gonzotech.core.psyche.PsycheStress.class);
        // Психика: событийные источники (урон, взрывы, смерти зверей, тотем, рычаг, скример).
        NeoForge.EVENT_BUS.register(com.gonzotech.core.psyche.PsycheStressEvents.class);
        // Психика: эффекты экзистенциального кризиса (каскад-чекпойнт, подмена предмета, сон).
        NeoForge.EVENT_BUS.register(com.gonzotech.core.psyche.PsycheCrisis.class);

        // «Вечные» эффекты не снимаются молоком (автор 22.09.2026).
        NeoForge.EVENT_BUS.register(com.gonzotech.core.event.UncurableEffects.class);

        // УФ шкала (в тик) и химическое заражение (в секунду) — автор 22.09.2026.
        NeoForge.EVENT_BUS.register(com.gonzotech.core.psyche.PsycheUltraviolet.class);
        NeoForge.EVENT_BUS.register(com.gonzotech.core.psyche.PsycheChemical.class);
        // Суневеты (багровые дни): драйвер + синк при заходе.
        NeoForge.EVENT_BUS.register(com.gonzotech.sunevent.SunEventServer.class);

        // Клиентская привязка экранов машин — только на физическом клиенте.
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            modEventBus.addListener(com.gonzotech.machines.client.MachineClient::onRegisterScreens);
            modEventBus.addListener(com.gonzotech.machines.client.AlloyClient::onRegisterItemTintSources);
            // Клиентские текстуры/тинт расплавленного кориума и жидкостей в мире.
            modEventBus.addListener(com.gonzotech.core.fluid.client.CoriumFluidClient::registerClientExtensions);
            modEventBus.addListener(com.gonzotech.core.fluid.client.CoriumFluidClient::onClientSetup);
            modEventBus.addListener(com.gonzotech.core.client.particle.ModParticleClient::onRegisterParticleProviders);
            // Развёртка и тинт надетой брони custom_alloy.
            modEventBus.addListener(com.gonzotech.machines.client.AlloyClient::onRegisterClientExtensions);
            // Тряска камеры от эффекта «Тремор» — только на клиенте.
            NeoForge.EVENT_BUS.register(com.gonzotech.core.psyche.client.PsycheTremorClient.class);
            // Эффекты кризиса на клиенте: экранный эффект, фиксация камеры, ложная смерть.
            NeoForge.EVENT_BUS.register(com.gonzotech.core.psyche.client.PsycheCrisisClient.class);
            // Texture-only Smart CTM корпусной оболочки турбины.
            modEventBus.addListener(com.gonzotech.machines.client.ctm.SmartCtmModelLoader::register);
            // HUD-подсказка гаечного ключа (тип+режим трубы, на которую смотришь).
            NeoForge.EVENT_BUS.register(com.gonzotech.machines.client.WrenchHud.class);
            // Три HUD-шкалы «психики» слева от хотбара.
            NeoForge.EVENT_BUS.register(com.gonzotech.core.psyche.client.PsycheHud.class);
            // Спидометр измеряет клиентскую скорость и выводит её над хотбаром.
            NeoForge.EVENT_BUS.register(com.gonzotech.core.client.SpeedometerHud.class);
            // Единый порядок lore, радиации, токсичности, экранирования и F3+H.
            // Регистрируется через UniversalTooltip (@EventBusSubscriber).
            // Солнечные часы: день/следующий кризис/эффективность панелей над хотбаром.
            NeoForge.EVENT_BUS.register(com.gonzotech.core.client.SolarWatchHud.class);
            // Фаза 4 — скайбоксы космических измерений (Луна/Марс/Европа).
            modEventBus.addListener(com.gonzotech.space.client.SpaceClient::onRegisterDimensionEffects);
            // Фаза 4 — рендерер горизонта событий Чёрных Дыр.
            NeoForge.EVENT_BUS.register(com.gonzotech.space.client.BlackHoleRenderer.class);
            // Фаза 4 — авто-подтверждение ванильного предупреждения об
            // «экспериментальных настройках» (наши измерения = эксперим. датапак).
            NeoForge.EVENT_BUS.addListener(
                com.gonzotech.space.client.ExperimentalWarningSkip::onScreenOpening);
        }

        LOGGER.info("[Gonzo Tech] Mod class constructed, mod_id={}, {} руд зарегистрировано",
            MOD_ID, com.gonzotech.core.ore.OreDefinition.ALL.size());
    }

    private void registerPayloads(final RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MOD_ID);
        registrar.playToServer(
            CesiumBlastRequestPayload.TYPE,
            CesiumBlastRequestPayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    ServerLevel level = player.serverLevel();
                    BlockPos pos = payload.pos();
                    if (level.getBlockState(pos).getBlock() instanceof CesiumOreBlock
                        && pos.distToCenterSqr(player.getX(), player.getY(), player.getZ())
                           <= MAX_BLAST_REQUEST_DISTANCE * MAX_BLAST_REQUEST_DISTANCE) {
                        CesiumOreBlock.tryBlast(level, pos);
                    }
                }
            })
        );

        ChalkboardNetwork.register(registrar);

        // Sync для GUI «Заметок учёного» (наигранное время + tier 1).
        com.gonzotech.chalkboard.network.NotesNetwork.register(registrar);

        // Sync трёх HUD-шкал «психики» (зависимость/стресс/кризис).
        com.gonzotech.core.psyche.PsycheNetwork.register(registrar);

        // Эффекты кризиса: фиксация камеры, ложный экран смерти, ЛКМ-«использование предмета».
        com.gonzotech.core.psyche.PsycheCrisisNetwork.register(registrar);

        registrar.playToClient(
            com.gonzotech.radiation.RadiationVisualPayload.TYPE,
            com.gonzotech.radiation.RadiationVisualPayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() ->
                com.gonzotech.radiation.RadiationVisualClient.accept(payload))
        );

        // HUD живого потока труб (ключ ↔ сервер).
        com.gonzotech.machines.network.PipeFlowNetwork.register(registrar);

        com.gonzotech.space.SpaceSkyNetwork.register(registrar);

        // Суневеты: nextEventDay/lastEventDay/suneventDays на клиент.
        com.gonzotech.sunevent.SunEventNetwork.register(registrar);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Дать пакету network ссылки на блоки труб (сборка/разборка связки).
        com.gonzotech.machines.network.ModCompositeAccess.set(
            com.gonzotech.machines.registry.ModMachines.COMPOSITE_PIPE.get());
        com.gonzotech.machines.network.ModCompositeAccess.setSecond(
            com.gonzotech.machines.registry.ModMachines.SECOND_COMPOSITE_PIPE.get());
        com.gonzotech.machines.network.ModCompositeAccess.registerSingle(
            com.gonzotech.machines.network.PipeType.WIRE,
            com.gonzotech.machines.registry.ModMachines.WIRE.get());
        com.gonzotech.machines.network.ModCompositeAccess.registerSingle(
            com.gonzotech.machines.network.PipeType.HEAT,
            com.gonzotech.machines.registry.ModMachines.HEAT_PIPE.get());
        com.gonzotech.machines.network.ModCompositeAccess.registerSingle(
            com.gonzotech.machines.network.PipeType.WATER,
            com.gonzotech.machines.registry.ModMachines.WATER_PIPE.get());
        com.gonzotech.machines.network.ModCompositeAccess.registerSingle(
            com.gonzotech.machines.network.PipeType.STEAM,
            com.gonzotech.machines.registry.ModMachines.STEAM_PIPE.get());
        LOGGER.info("[Gonzo Tech] Common setup complete — core systems ready to attach.");
    }
}
