package com.gonzotech;

import com.gonzotech.chalkboard.command.ChalkboardCommand;
import com.gonzotech.chalkboard.network.ChalkboardNetwork;
import com.gonzotech.chalkboard.progress.ModAttachments;
import com.gonzotech.core.network.CesiumBlastRequestPayload;
import com.gonzotech.core.ore.CesiumOreBlock;
import com.gonzotech.core.registry.ModBlocks;
import com.gonzotech.core.registry.ModCreativeTabs;
import com.gonzotech.core.registry.ModFeatures;
import com.gonzotech.core.registry.ModItems;
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

        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModFeatures.register(modEventBus);
        ModAttachments.register(modEventBus);

        // Фаза 2 — паровая ветка энергетики (машины, BlockEntity, меню).
        ModMachines.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenus.register(modEventBus);

        // При остановке сервера сбросить транзитный учёт потока труб (держит ссылки на Level).
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e) ->
            com.gonzotech.machines.network.FlowTracker.clearAll());

        NeoForge.EVENT_BUS.addListener(ChalkboardCommand::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(com.gonzotech.chalkboard.advancement.ModAdvancements::onPlayerLoggedIn);

        // Фаза 3 — «мелкие фишки»: гейт крафта, свинец в ванильных печах, эффекты в воде.
        NeoForge.EVENT_BUS.register(com.gonzotech.core.event.Phase3Events.class);

        // Клиентская привязка экранов машин — только на физическом клиенте.
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            modEventBus.addListener(com.gonzotech.machines.client.MachineClient::onRegisterScreens);
            // HUD-подсказка гаечного ключа (тип+режим трубы, на которую смотришь).
            NeoForge.EVENT_BUS.register(com.gonzotech.machines.client.WrenchHud.class);
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

        // HUD живого потока труб (ключ ↔ сервер).
        com.gonzotech.machines.network.PipeFlowNetwork.register(registrar);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("[Gonzo Tech] Common setup complete — core systems ready to attach.");
    }
}
