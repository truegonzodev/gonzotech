package com.gonzotech.radiation;

import com.gonzotech.core.psyche.ModPsycheAttachments;
import com.gonzotech.core.psyche.PlayerPsyche;
import com.gonzotech.core.psyche.PsycheChemical;
import com.gonzotech.core.psyche.PsycheUltraviolet;
import com.gonzotech.core.psyche.PsycheNetwork;
import com.gonzotech.core.registry.ModEffects;
import com.gonzotech.core.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Система радиации (автор, спека 20.09 + правки 21.09). Три контура:
 *
 * <p><b>1. Инвентарь (каждую секунду, на игрока).</b> Пресетные источники
 * ({@link RadSources}) плюс наведённый фон предметов ({@link ItemRadioactivity})
 * суммируются (п.3). Сумма идёт в дозу шкалы {@code PlayerPsyche.radiation}
 * (permille), облучает соседние предметы ЛОГИСТИЧЕСКИ к уровню сильнейшего
 * источника («источник не может заразить больше, чем имеет сам»; материал цели
 * замедляет накопление — {@link RadMaterials}) и чуть-чуть заражает чанк.
 * Контейнеры НЕ тикаем (железное правило производительности).</p>
 *
 * <p><b>2. Чанки (доза — каждую секунду; обслуживание — каждые 20 с).</b>
 * Фон чанка бьёт по шкале с весом 1/10 (п.4). На предметы — только при фоне
 * &gt;30mZt. Каждые 20 с: подтяжка к поставленным блокам (× экран контура
 * {@link Containment}), затухание 1% для ЛЮБОГО чанка, диффузия
 * ({@link ChunkRadiationData#maintain}).</p>
 *
 * <p><b>3. Учёт поставленных блоков</b> через события места/слома — реестром
 * позиций: заражение тянется к ним логистически, экранируется контуром
 * (взрывы/поршни — известная упрощёнка, остаток вымывает затухание).</p>
 *
 * <p>Восстановление шкалы — только когда игрока НЕ облучают (автор 21.09):
 * 0.2% от текущего значения в секунду, спадающее стекает в чанк. После смерти
 * шкала сбрасывается в ноль (PlayerEvent.Clone).</p>
 *
 * <p><b>Последствия шкалы (автор 22.09.2026, «заняться шкалами»):</b> категории
 * {@link RadDose} бьют по игроку эффектами, вспышками и смертью на 100 %
 * ({@link RadSickness}); при высокой дозе случаен вечный {@link ModEffects#NECROSIS}
 * ({@link Necrosis} — спринт жжёт воздух, моб-агр растёт); дозу режет
 * {@link Hazmat} и выводит {@link RadAbsorbentItem} через {@link RadCleanse}.</p>
 *
 * <p>Все ставки — в nZt/с (п.7: «всё считаем /в сек»).</p>
 */
public final class RadiationSystem {

    // ── доза → шкала ──
    /** Сколько nZt·с суммарной эмиссии = +1 тысячной шкалы (0.1%). Откалибровано
     *  под целевые темпы автора 21.09 (без учёта одновременного спада — спад
     *  идёт только БЕЗ облучения): уран-слиток (3mZt) ≈ 8%/час, торий (0.6mZt)
     *  ≈ 1.6%/час, плутоний (13mZt) ≈ 35%/час, радий (75mZt) ≈ 100%/30 мин. */
    public static final double NZT_PER_PERMILLE = 1.35e8;
    /** Фон чанка действует на шкалу в 10 раз слабее, чем источник «в руках». */
    private static final double CHUNK_DOSE_WEIGHT = 0.1;
    /** Доза в тик, НИЖЕ которой игрок «не облучается» и шкала спадает
     *  (природный фон 1–10nZt·0.1 ≪ порога — в чистой природе выздоравливаешь). */
    private static final double DOSE_CLEAR_FLOOR = 100.0;

    // ── чанк ↔ предметы/игрок ──
    /** Предметы фонят от чанка только если он ОЧЕНЬ горячий (п.4: >30mZt). */
    private static final double ITEM_FROM_CHUNK_MIN = 30.0 * RadUnits.MILLI;
    /** Доля эмиссии ПРЕСЕТНОГО источника в инвентаре, уходящая в чанк ежесекундно
     *  (логистика к полной сумме инвентаря — анти-дюп: предмет никогда не
     *  заражает округу сильнее, чем фонит сам, автор 21.09). */
    private static final double INVENTORY_TO_CHUNK_RATE = 0.01;
    /** Вклад содержимого контейнеров в чанк за секунду сканирования (логистика
     *  к сумме эмиссии содержимого × экран контура каждого горячего контейнера). */
    private static final double CHEST_TO_CHUNK_RATE = 0.0005;
    /** Наведённый фон предметов НЕ греет чанк по своему весу (помпа только
     *  пресетных источников) — иначе петля «предметы→чанк→предметы» дюпала фон. */

    // ── естественное восстановление шкалы ──
    /** Доля шкалы, спадающая в секунду (точно 0.2%/с от текущего, автор: «теряет
     *  шкалу 0.2% от текущего заполнения в сек»; период полуспада ~5.8 мин). */
    private static final double RECOVERY_RATE = 0.002;
    /** Спадающая доза сбрасывается в чанк (п.4): 20%, с мягким потолком 100mZt —
     *  иначе петля «чанк→шкала→спад→чанк» саморазгонялась. */
    private static final double SHED_TO_CHUNK = 0.2;
    private static final double SHED_CEILING = 100.0 * RadUnits.MILLI;

    /** Обслуживание чанков — каждые 20 секунд (п.4). */
    private static final int CHUNK_MAINT_PERIOD_TICKS = 20 * 20;
    /** Тик игрока — каждую секунду. */
    private static final int PLAYER_PERIOD_TICKS = 20;

    /** Накопители дробной дозы/спада по игрокам (permille дискретен). */
    private static final Map<UUID, Double> DOSE_ACC = new HashMap<>();
    private static final Map<UUID, Double> SHED_ACC = new HashMap<>();
    /** Последний swing pulse: mining не является обычным isUsingItem(). */
    private static final Map<UUID, Integer> LAST_SWING = new HashMap<>();
    /** Виртуальная радиация активного предмета: не трогаем carried NBT во время действия. */
    private static final Map<HeldKey, HeldRadState> HELD_RAD = new HashMap<>();
    private record HeldKey(UUID player, InteractionHand hand) {}
    private static final int USE_SOFT_CD_TICKS = 40;
    private static final int HELD_FLUSH_TICKS = 20 * 35;

    private static final class HeldRadState {
        private ItemStack stack;
        private double value;
        private int lastUseTick;
        private int lastFlushTick;

        private HeldRadState(ItemStack stack, double value, int tick) {
            this.stack = stack;
            this.value = value;
            this.lastUseTick = tick;
            this.lastFlushTick = tick;
        }
    }

    private RadiationSystem() {
    }

    // ═══════════════════════ 1 сек: игрок ═══════════════════════

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // LivingEntity does not expose a public isSwinging() in 1.21.4.
        // Attack animation is the mapped public signal for the same pulse.
        if (player.getAttackAnim(0.0F) > 0.0F) {
            LAST_SWING.put(player.getUUID(), player.tickCount);
        }
        // Воздух некроза держим КАЖДЫЙ тик: ваниль восстанавливает 4 пузырька в тик,
        // поэтому вычитать раз в секунду бесполезно (автор 22.09.2026 — полоска мигала).
        Necrosis.tickAir(player);
        if (player.tickCount % PLAYER_PERIOD_TICKS != 0) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        ChunkRadiationData data = ChunkRadiationData.get(level);
        if (hasVisualInstrument(player)) {
            List<RadiationVisualPayload.Source> visual = data.visualSources(level, player.blockPosition(), 18).stream()
                    .map(s -> new RadiationVisualPayload.Source(s.pos().asLong(), (float) s.emission()))
                    .toList();
            PacketDistributor.sendToPlayer(player, new RadiationVisualPayload(visual));
        }
        long chunkKey = new ChunkPos(player.blockPosition()).toLong();

        // Скан инвентаря: пресетная эмиссия + самый горячий стак (цель логистики фона).
        List<List<ItemStack>> compartments = List.of(
                player.getInventory().items, player.getInventory().armor, player.getInventory().offhand);
        // 0.3.83: суммы — с рекурсивным обходом вложенных хранилищ (шалкер/
        // связка/мод-контейнер фонят как россыпь; экран только у носителей).
        double intrinsic = 0.0;
        double maxStackEmission = 0.0;     // сильнейший стак…
        double secondStackEmission = 0.0;  // …и сильнейший ДРУГОЙ (с кратностями)
        for (List<ItemStack> part : compartments) {
            for (ItemStack stack : part) {
                double e = RadSources.emissionDeep(stack);
                intrinsic += e;
                if (e > maxStackEmission) {
                    secondStackEmission = maxStackEmission;
                    maxStackEmission = e;
                } else if (e > secondStackEmission) {
                    secondStackEmission = e;
                }
            }
        }

        double chunkNzt = data.value(level, chunkKey);
        // Источники наведённого фона: собственные радио-предметы или очень горячий чанк (п.4).
        boolean hasSourceContext = intrinsic > 0.0 || chunkNzt > ITEM_FROM_CHUNK_MIN;
        // Цель наведённого фона = эмиссия СИЛЬНЕЙШЕГО локального источника (100%,
        // автор 21.09: «предмет не может облучить другой сильнее, чем выдаёт сам»).
        // 0.3.83: уровень считается БЕЗ самого стака (иначе носитель наводился бы
        // собственным содержимым, а empty-щипцы не росли бы от чужих источников).
        double chunkSource = chunkNzt > ITEM_FROM_CHUNK_MIN ? chunkNzt : 0.0;

        double induced = 0.0;
        for (List<ItemStack> part : compartments) {
            for (ItemStack stack : part) {
                if (stack.isEmpty()) {
                    continue;
                }
                boolean activeHand = stack == player.getMainHandItem()
                        || stack == player.getOffhandItem();
                boolean hardUse = activeHand && isHardUsing(player, stack);
                // Пер-стековый уровень источника: эмиссия этого стака исключена
                // (второй максимум), чанк-фон общий.
                double ownEmission = RadSources.emissionDeep(stack);
                double stackSource = Math.max(
                        ownEmission >= maxStackEmission ? secondStackEmission : maxStackEmission,
                        chunkSource);
                double perItem;
                if (activeHand) {
                    perItem = tickHeldRadiation(player, stack, hardUse, hasSourceContext,
                            stackSource, RadMaterials.itemFactor(stack));
                } else {
                    ItemRadioactivity.tickInduced(stack, hasSourceContext, stackSource,
                            RadMaterials.itemFactor(stack));
                    perItem = ItemRadioactivity.getInduced(stack);
                }
                induced += perItem * Math.max(1, stack.getCount());
            }
        }

        double totalNzt = intrinsic + induced;

        // Доза шкалы: инвентарь полным весом + фон чанка с весом 1/10.
        // Хазмат I (автор 22.09) режет входящую дозу, но пробивается горячим
        // источником: множитель считается от дозы/сек (см. Hazmat.factor).
        // The enclosure protects the outside chunk, not somebody standing beside
        // its sources. Add 40% of actual block emission in this same closed cavity;
        // this does not feed chunk contamination or inventory-induced radiation.
        double rawDose = totalNzt + chunkNzt * CHUNK_DOSE_WEIGHT
                + Containment.insideDose(level, BlockPos.containing(player.getEyePosition()));
        // «Зуд III» (заражение > 69 %) — +20 % к получению дозы (автор 22.09.2026).
        rawDose *= PsycheChemical.doseMultiplier(player);
        double suitFactor = Hazmat.factor(player, rawDose);
        // «Абсорбция дозы» (Цистамин/ДТПА, спека 24.09): срезает получаемую игроком
        // дозу на (30 + уровень²) %: уровень 1 → 31 %, уровень 2 → 34 %.
        var absorption = player.getEffect(com.gonzotech.core.registry.ModEffects.DOSE_ABSORPTION);
        if (absorption != null) {
            int absLevel = absorption.getAmplifier() + 1;
            int cut = Math.min(100, 30 + absLevel * absLevel);
            suitFactor *= (100 - cut) / 100.0;
        }
        double acc = DOSE_ACC.getOrDefault(player.getUUID(), 0.0) + rawDose * suitFactor;
        int gainPermille = (int) (acc / NZT_PER_PERMILLE);
        acc -= gainPermille * NZT_PER_PERMILLE;
        DOSE_ACC.put(player.getUUID(), acc);

        // Восстановление: ТОЛЬКО когда облучения в этот тик нет (автор 21.09:
        // «теряет 0.2% от текущего в сек» — иначе за час с ураном в руках шкала
        // уходила бы в плато вместо линейных ~8%/ч). Спад стекает в чанк (п.4).
        PlayerPsyche psyche = player.getData(ModPsycheAttachments.PSYCHE);
        int scale = psyche.getRadiation();
        int shedPermille = 0;
        double doseThisTick = rawDose * suitFactor;
        if (scale > 0 && doseThisTick < DOSE_CLEAR_FLOOR) {
            double shedAcc = SHED_ACC.getOrDefault(player.getUUID(), 0.0) + scale * RECOVERY_RATE;
            shedPermille = (int) shedAcc;
            shedAcc -= shedPermille;
            SHED_ACC.put(player.getUUID(), shedAcc);
        }

        int newScale = scale + gainPermille - shedPermille;
        if (newScale != scale) {
            psyche.setRadiation(newScale);
            PsycheNetwork.sendToPlayer(player);
        }

        // Химическое заражение: 2 % дозы уходит в шестую шкалу (автор 22.09.2026):
        // «шкалы радиации и заражения имеют одинаковую размерность».
        if (doseThisTick > 0.0) {
            PsycheChemical.addFromDose(player, doseThisTick / NZT_PER_PERMILLE);
        }

        // Антирадиновый абсорбент (автор 22.09): «Очищение» плавно выводит долю
        // дозы; выведенное уходит в чанк так же, как обычный спад.
        int cleansed = RadCleanse.tick(player);

        // Некроз (автор 22.09): спринт жжёт воздух, моб-агр растёт (вечный эффект
        // выдаёт RadSickness случайно при высокой дозе).
        Necrosis.tick(player);

        // Последствия дозы: эффекты/вспышки/смерть по категориям RadDose.
        // Считаются здесь же, чтобы не заводить второй тик на игрока.
        RadSickness.tick(player, psyche.getRadiation());

        // Игрок → чанк: ТОЛЬКО пресетная эмиссия (наведённый фон не греет местность),
        // логистика к ПОЛНОЙ сумме инвентаря: ближе к уровню источника — медленнее,
        // выше — никогда («источник не может заразить больше, чем имеет сам»).
        double contam = data.contaminationOf(chunkKey);
        if (intrinsic > 0.0 && contam < intrinsic) {
            data.addContamination(chunkKey,
                    intrinsic * INVENTORY_TO_CHUNK_RATE * (1.0 - contam / intrinsic));
        }
        if (shedPermille > 0 && contam < SHED_CEILING) {
            data.addContamination(chunkKey,
                    shedPermille * NZT_PER_PERMILLE * SHED_TO_CHUNK * (1.0 - contam / SHED_CEILING));
        }
        if (cleansed > 0 && contam < SHED_CEILING) {
            data.addContamination(chunkKey,
                    cleansed * NZT_PER_PERMILLE * SHED_TO_CHUNK * (1.0 - contam / SHED_CEILING));
        }

        // Скан содержимого контейнеров своего чанка (сундуки/бочки с ураном греют чанк).
        scanContainersInto(level, chunkKey, data);
    }

    private static boolean hasVisualInstrument(ServerPlayer player) {
        return player.getMainHandItem().is(ModItems.DOSIMETER.get())
                || player.getMainHandItem().is(ModItems.TELIFON.get())
                || player.getOffhandItem().is(ModItems.DOSIMETER.get())
                || player.getOffhandItem().is(ModItems.TELIFON.get());
    }

    private static boolean isHardUsing(ServerPlayer player, ItemStack stack) {
        if (player.isUsingItem() && player.getUseItem() == stack) {
            return true;
        }
        // Mining is not a normal ItemStack use action. Record swing pulses on
        // every player tick, not only on the once-per-second radiation tick.
        // The window bridges gaps between consecutive vanilla swings.
        int lastSwing = LAST_SWING.getOrDefault(player.getUUID(), Integer.MIN_VALUE);
        return stack == player.getMainHandItem() && player.tickCount - lastSwing <= 10;
    }

    private static double tickHeldRadiation(ServerPlayer player, ItemStack stack, boolean hardUse,
                                            boolean hasSource, double sourceNzt, double factor) {
        InteractionHand hand = stack == player.getOffhandItem()
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        HeldKey key = new HeldKey(player.getUUID(), hand);
        int now = player.tickCount;
        HeldRadState state = HELD_RAD.get(key);
        if (state == null && !hardUse) {
            ItemRadioactivity.tickInduced(stack, hasSource, sourceNzt, factor);
            return ItemRadioactivity.getInduced(stack);
        }
        if (state == null || state.stack != stack) {
            if (state != null) ItemRadioactivity.setInduced(state.stack, state.value);
            state = new HeldRadState(stack, ItemRadioactivity.getInduced(stack), now);
            HELD_RAD.put(key, state);
        }
        if (hardUse) state.lastUseTick = now;
        state.value = ItemRadioactivity.nextInduced(state.value, stack, hasSource, sourceNzt, factor);

        boolean inSoftCd = now - state.lastUseTick < USE_SOFT_CD_TICKS;
        boolean periodicFlush = now - state.lastFlushTick >= HELD_FLUSH_TICKS;
        if (!hardUse && !inSoftCd || periodicFlush) {
            ItemRadioactivity.setInduced(stack, state.value);
            HELD_RAD.remove(key);
            return ItemRadioactivity.getInduced(stack);
        }
        return state.value;
    }

    /** Контейнеры активного чанка: логистика к сумме эмиссии содержимого,
     *  на каждый горячий контейнер — свой экран контура (свинцовый купол над
     *  сундуком с ураном гасит вклад, автор 21.09). */
    private static void scanContainersInto(ServerLevel level, long chunkKey, ChunkRadiationData data) {
        LevelChunk chunk = level.getChunk(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
        double found = 0.0;
        double[] drops = scanDroppedItemsInto(level, chunkKey); // [россыпь, блоки]
        found += drops[0];
        double placedLike = drops[1];
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof Container container) {
                double mine = 0.0;
                for (int i = 0; i < container.getContainerSize(); i++) {
                    mine += RadSources.emissionDeep(container.getItem(i));
                }
                BlockPos pos = be.getBlockPos();
                // 0.3.111: содержимое свинцового ящика фонит на 80% слабее
                if (be.getBlockState().getBlock() instanceof com.gonzotech.machines.block.ThirdLeadChestBlock) {
                    mine *= com.gonzotech.machines.block.ThirdLeadChestBlock.CONTENT_CHUNK_FACTOR;
                }
                if (mine > 0.0 && pos != null) {
                    mine *= Containment.factor(level, pos); // экран контура контейнера
                }
                found += mine;
            }
        }
        // 0.3.108: блоки на полу/в рамках — полной ставкой (как поставленные),
        // россыпь — ставкой CHEST_TO_CHUNK_RATE; корriesha (1 − contam/target)
        // сохранена для обеих частей.
        double target = found * CHEST_TO_CHUNK_RATE + placedLike;
        if (target > 0.0) {
            double contam = data.contaminationOf(chunkKey);
            if (contam < target) {
                data.addContamination(chunkKey, target * (1.0 - contam / target));
            }
        }
    }

    /**
     * 0.3.96 (автор): россыпь на полу и предметы в рамках тоже заражали чанк —
     * сканировались только BlockEntity-контейнеры. ItemEntity и ItemFrame —
     * сущности, поэтому добираем их отдельным проходом по чанку (раз в секунду,
     * вместе со сканом контейнеров). Ставка та же, что у россыпи в сундуке
     * (CHEST_TO_CHUNK_RATE): экранирования у лежащего предмета нет.
     */
    private static double[] scanDroppedItemsInto(ServerLevel level, long chunkKey) {
        int cx = ChunkPos.getX(chunkKey);
        int cz = ChunkPos.getZ(chunkKey);
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
            cx << 4, level.getMinY(), cz << 4,
            (cx << 4) + 16, level.getMinY() + level.getHeight(), (cz << 4) + 16);
        double[] mine = new double[2]; // [россыпь, блоки-как-поставленные]
        for (net.minecraft.world.entity.item.ItemEntity drop
                : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box,
                    e -> !e.getItem().isEmpty())) {
            classifyFloorStack(drop.getItem(), mine);
        }
        for (net.minecraft.world.entity.decoration.ItemFrame frame
                : level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ItemFrame.class, box,
                    e -> !e.getItem().isEmpty())) {
            classifyFloorStack(frame.getItem(), mine);
        }
        return mine;
    }

    /**
     * 0.3.108 (автор): блочный предмет радиоактивного блока (радий и т.п.),
     * лежащий на полу или вставленный в рамку, фонит на чанк КАК ПОСТАВЛЕННЫЙ
     * БЛОК — полной эмиссией блока, без ставки россыпи («нужно поведение
     * именно второго варианта»). Неблочные предметы (пыль/слитки) — как
     * раньше: emissionDeep и ставка россыпи у вызывающего.
     */
    private static void classifyFloorStack(net.minecraft.world.item.ItemStack stack, double[] out) {
        if (stack.getItem() instanceof net.minecraft.world.item.BlockItem bi) {
            double be = blockEmission(bi.getBlock());
            if (be > 0.0) {
                out[1] += be * stack.getCount();
                return;
            }
        }
        out[0] += RadSources.emissionDeep(stack);
    }

    // ═══════════════════════ выход из игры: чистим карты ═══════════════════════

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DOSE_ACC.remove(player.getUUID());
            SHED_ACC.remove(player.getUUID());
            LAST_SWING.remove(player.getUUID());
            HELD_RAD.entrySet().removeIf(entry -> entry.getKey().player().equals(player.getUUID()));
            RadSickness.forget(player.getUUID());
            RadCleanse.forget(player.getUUID());
            Necrosis.forget(player.getUUID());
            PsycheUltraviolet.forget(player.getUUID());
            PsycheChemical.forget(player.getUUID());
        }
    }

    // ═══════════════════════ смерть: шкала в ноль ═══════════════════════

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        // Автор 21.09: после смерти шкала облучения сбрасывается (attachment
        // помечен copyOnDeath для прочих шкал — облучение перезатираем в 0).
        if (!event.isWasDeath()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerPsyche psyche = player.getData(ModPsycheAttachments.PSYCHE);
            if (psyche.getRadiation() != 0) {
                psyche.setRadiation(0);
            }
            DOSE_ACC.remove(player.getUUID());
            SHED_ACC.remove(player.getUUID());
            RadSickness.forget(player.getUUID());
            RadCleanse.forget(player.getUUID());
            Necrosis.forget(player.getUUID());
            PsycheNetwork.sendToPlayer(player);
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
        if (event.getLevel() instanceof ServerLevel level) {
            trackPlacedBlock(level, event.getPos(), event.getPlacedBlock());
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        double emission = RadSources.blockEmission(event.getState());
        if (emission > 0.0) {
            ChunkRadiationData.get(level).onBlockRemoved(event.getPos(), emission);
        }
    }

    /** Track direct source placements that do not produce BlockEvent.EntityPlaceEvent (buckets, thermal hazards). */
    public static void trackPlacedBlock(ServerLevel level, BlockPos pos,
                                        net.minecraft.world.level.block.state.BlockState state) {
        double emission = RadSources.blockEmission(state);
        if (emission > 0.0) {
            ChunkRadiationData.get(level).onBlockPlaced(pos, emission);
        }
    }

    /** Keep the placed-source index accurate when a direct setBlock replaces an existing source. */
    public static void trackReplacedBlock(ServerLevel level, BlockPos pos,
                                          net.minecraft.world.level.block.state.BlockState oldState,
                                          net.minecraft.world.level.block.state.BlockState newState) {
        double oldEmission = RadSources.blockEmission(oldState);
        double newEmission = RadSources.blockEmission(newState);
        if (oldEmission <= 0.0 && newEmission <= 0.0) return;
        ChunkRadiationData data = ChunkRadiationData.get(level);
        if (oldEmission > 0.0) data.onBlockRemoved(pos, oldEmission);
        if (newEmission > 0.0) data.onBlockPlaced(pos, newEmission);
    }

    private static double blockEmission(net.minecraft.world.level.block.Block block) {
        return RadSources.blockEmission(block.defaultBlockState());
    }
}
