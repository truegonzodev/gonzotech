package com.gonzotech.radiation;

import com.gonzotech.core.psyche.ModPsycheAttachments;
import com.gonzotech.core.psyche.PlayerPsyche;
import com.gonzotech.core.psyche.PsycheNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Система радиации (автор, спека 20.09). Три контура:
 *
 * <p><b>1. Инвентарь (каждую секунду, на игрока).</b> Пресетные источники
 * ({@link RadSources}) плюс наведённый фон предметов ({@link ItemRadioactivity})
 * суммируются: «6 слитков по слотам = стопке из 6» (п.3). Сумма идёт в дозу
 * шкалы {@code PlayerPsyche.radiation} (permille, 1000 = 100%), облучает
 * соседние предметы (наведённый фон) и чуть-чуть заражает чанк. Наведённый
 * фон растёт/затухает экспонентой ×1.25/с — теги только в инвентаре игрока,
 * контейнеры НЕ тикаем (железное правило производительности).</p>
 *
 * <p><b>2. Чанки (доза — каждую секунду; обслуживание — каждые 20 с).</b>
 * Фон чанка бьёт по шкале игрока (вес 1/10 от «ручного» источника), на предметы
 * влияет только если чанк фонит СИЛЬНО (>30mZt, п.4). Каждые 20 с: затухание
 * 1% + диффузия к равновесию с соседями ({@link ChunkRadiationData#maintain}).</p>
 *
 * <p><b>3. Учёт поставленных блоков</b> через события места/слома
 * (взрывы/поршни — известная упрощёнка, фон от них затухает сам).</p>
 *
 * <p>Все ставки — в nZt/с (п.7: «всё считаем /в сек»).</p>
 */
public final class RadiationSystem {

    // ── доза → шкала ──
    /** Сколько nZt·с суммарной эмиссии = +1 тысячной шкалы (0.1%). 3e7 ⇒ один урановый
     *  слиток в инвентаре даёт +0.1%/с, полная шкала ~16.7 мин (стартовый баланс, крутить тут). */
    private static final double NZT_PER_PERMILLE = 3.0e7;
    /** Фон чанка действует на шкалу в 10 раз слабее, чем источник «в руках». */
    private static final double CHUNK_DOSE_WEIGHT = 0.1;

    // ── чанк ↔ предметы/игрок ──
    /** Предметы фонят от чанка только если он ОЧЕНЬ горячий (п.4: >30mZt). */
    private static final double ITEM_FROM_CHUNK_MIN = 30.0 * RadUnits.MILLI;
    /** Доля эмиссии инвентаря, уходящая в чанк каждую секунду («игрок с ураном заражает чанк»). */
    private static final double INVENTORY_TO_CHUNK_RATE = 0.01;
    /** Вклад содержимого сундуков в чанк за секунду сканирования (игрок стоит в чанке). */
    private static final double CHEST_TO_CHUNK_RATE = 0.0005;

    // ── естественное восстановление шкалы ──
    /** Доля шкалы, спадающая в секунду (≈0.2%/с, период полуспада ~5.8 мин). */
    private static final double RECOVERY_RATE = 0.002;
    /** Спадающая доза сбрасывается в чанк (п.4, «облучённый игрок заражает чанк»): 20%. */
    private static final double SHED_TO_CHUNK = 0.2;

    /** Обслуживание чанков — каждые 20 секунд (п.4). */
    private static final int CHUNK_MAINT_PERIOD_TICKS = 20 * 20;
    /** Тик игрока — каждую секунду. */
    private static final int PLAYER_PERIOD_TICKS = 20;

    /** Накопители дробной дозы/спада по игрокам (permille дискретен). */
    private static final Map<UUID, Double> DOSE_ACC = new HashMap<>();
    private static final Map<UUID, Double> SHED_ACC = new HashMap<>();

    private RadiationSystem() {
    }

    // ═══════════════════════ 1 сек: игрок ═══════════════════════

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % PLAYER_PERIOD_TICKS != 0) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        ChunkRadiationData data = ChunkRadiationData.get(level);
        long chunkKey = new ChunkPos(player.blockPosition()).toLong();

        // Скан инвентаря: пресетная эмиссия + обработка наведённого фона.
        List<List<ItemStack>> compartments = List.of(
                player.getInventory().items, player.getInventory().armor, player.getInventory().offhand);
        double intrinsic = 0.0;
        for (List<ItemStack> part : compartments) {
            for (ItemStack stack : part) {
                intrinsic += RadSources.emissionOfStack(stack);
            }
        }

        double chunkNzt = data.value(level, chunkKey);
        // Источники наведённого фона: собственные радио-предметы или очень горячий чанк (п.4).
        boolean hasSourceContext = intrinsic > 0.0 || chunkNzt > ITEM_FROM_CHUNK_MIN;

        double induced = 0.0;
        for (List<ItemStack> part : compartments) {
            for (ItemStack stack : part) {
                if (stack.isEmpty() || ItemRadioactivity.isLeadImmune(stack)) {
                    continue;
                }
                ItemRadioactivity.tickInduced(stack, hasSourceContext);
                induced += ItemRadioactivity.getInduced(stack);
            }
        }

        double totalNzt = intrinsic + induced;

        // Доза шкалы: инвентарь полным весом + фон чанка с весом 1/10.
        double totalNzt0 = totalNzt;
        double acc = DOSE_ACC.getOrDefault(player.getUUID(), 0.0)
                + totalNzt0 + chunkNzt * CHUNK_DOSE_WEIGHT;
        int gainPermille = (int) (acc / NZT_PER_PERMILLE);
        acc -= gainPermille * NZT_PER_PERMILLE;
        DOSE_ACC.put(player.getUUID(), acc);

        // Восстановление: спад экспоненциальный; спадающее облучение стекает в чанк (п.4).
        PlayerPsyche psyche = player.getData(ModPsycheAttachments.PSYCHE);
        int scale = psyche.getRadiation();
        double shedAcc = SHED_ACC.getOrDefault(player.getUUID(), 0.0) + scale * RECOVERY_RATE;
        int shedPermille = (int) shedAcc;
        shedAcc -= shedPermille;
        SHED_ACC.put(player.getUUID(), shedAcc);

        int newScale = scale + gainPermille - shedPermille;
        if (newScale != scale) {
            psyche.setRadiation(newScale);
            PsycheNetwork.sendToPlayer(player);
        }

        // Игрок → чанк: инвентарь греет местность, спадающая доза — тоже.
        double toChunk = totalNzt * INVENTORY_TO_CHUNK_RATE + shedPermille * NZT_PER_PERMILLE * SHED_TO_CHUNK;
        if (toChunk > 0.0) {
            data.addContamination(chunkKey, toChunk);
        }

        // Скан содержимого контейнеров своего чанка (сундуки/бочки с ураном греют чанк).
        scanContainersInto(level, chunkKey, data);
    }

    /** Контейнеры активного чанка: сумма пресетной эмиссии → малый вклад в чанк. */
    private static void scanContainersInto(ServerLevel level, long chunkKey, ChunkRadiationData data) {
        LevelChunk chunk = level.getChunk(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
        double found = 0.0;
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof Container container) {
                for (int i = 0; i < container.getContainerSize(); i++) {
                    found += RadSources.emissionOfStack(container.getItem(i));
                }
            }
        }
        if (found > 0.0) {
            data.addContamination(chunkKey, found * CHEST_TO_CHUNK_RATE);
        }
    }

    // ═══════════════════════ 20 сек: чанки ═══════════════════════

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % CHUNK_MAINT_PERIOD_TICKS != 0) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            ChunkRadiationData.get(level).maintain(level);
        }
    }

    // ═══════════════════════ поставленные блоки ═══════════════════════

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        double emission = blockEmission(event.getPlacedBlock().getBlock());
        if (emission > 0.0) {
            ChunkRadiationData data = ChunkRadiationData.get(level);
            BlockPos pos = event.getPos();
            data.onBlockPlaced(pos, emission);
            data.addContamination(new ChunkPos(pos).toLong(), emission);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        double emission = blockEmission(event.getState().getBlock());
        if (emission > 0.0) {
            ChunkRadiationData data = ChunkRadiationData.get(level);
            BlockPos pos = event.getPos();
            data.onBlockRemoved(pos, emission);
            data.addContamination(new ChunkPos(pos).toLong(), -emission);
        }
    }

    private static double blockEmission(net.minecraft.world.level.block.Block block) {
        net.minecraft.resources.ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return id.getNamespace().equals("gonzotech") ? RadSources.blockEmission(id.getPath()) : 0.0;
    }
}
