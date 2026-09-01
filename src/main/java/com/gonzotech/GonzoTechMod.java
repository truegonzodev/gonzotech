package com.gonzotech;

import com.gonzotech.core.network.CesiumBlastRequestPayload;
import com.gonzotech.core.ore.CesiumOreBlock;
import com.gonzotech.core.registry.ModBlocks;
import com.gonzotech.core.registry.ModCreativeTabs;
import com.gonzotech.core.registry.ModFeatures;
import com.gonzotech.core.registry.ModItems;
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

/**
 * Точка входа мода. Здесь только регистрация шины событий и заглушка
 * common-setup — реальная инициализация подсистем (core/chalkboard/
 * machines/space/mind/swag) будет вешаться сюда по мере готовности.
 */
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

        // Игровая шина (блоки, тики, взаимодействия) — сюда позже
        // повесится, например, SeedFormulaManager (core) на WorldEvent.Load.
        // NeoForge.EVENT_BUS.register(this);

        LOGGER.info("[Gonzo Tech] Mod class constructed, mod_id={}, {} руд зарегистрировано",
            MOD_ID, com.gonzotech.core.ore.OreDefinition.ALL.size());
    }

    /**
     * Сетевой канал «удержание ЛКМ на цезиевой руде»: клиент шлёт позицию,
     * сервер перепроверяет блок/открытость/КД + дистанцию до игрока и
     * выполняет взрыв авторитетно.
     */
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
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("[Gonzo Tech] Common setup complete — core systems ready to attach.");
    }
}