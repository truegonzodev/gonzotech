package com.gonzotech.radiation;

import com.gonzotech.core.psyche.ModPsycheAttachments;
import com.gonzotech.core.psyche.PlayerPsyche;
import com.gonzotech.core.psyche.PsycheChemical;
import com.gonzotech.core.psyche.PsycheUltraviolet;
import com.gonzotech.core.psyche.PsycheNetwork;
import com.gonzotech.core.registry.ModEffects;
import com.gonzotech.core.registry.ModItems;
import com.gonzotech.core.config.GonzoServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Система радиации (автор, спека 20.09 + правки 21.09). Три контура:
 *
 * <p><b>1. Инвентарь (каждую секунду, на игрока).</b> Пресетные источники
 * ({@link RadSources}) плюс наведённый фон предметов ({@link ItemRadioactivity})
 * суммируются (п.3). Сумма идёт в дозу шкалы {@code PlayerPsyche.radiation}
 * (permille), облучает соседние предметы ЛОГИСТИЧЕСКИ к уровню сильнейшего
 * источника («источник не может заразить больше, чем имеет сам»; материал цели
 * замедляет накопление — {@link RadMaterials}) и передаёт чанку 1% разницы от
 * полной суммы без потолка по сильнейшему стаку. Невложенные предметы игрока
 * считаются в дозе один раз.</p>
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
    private static final org.slf4j.Logger RAD_INV_LOG =
        org.slf4j.LoggerFactory.getLogger("gonzotech/radiation-inventory");
    /** Шлюз дебаг-лога прохода: nanos последней строки на игрока (0.3.273). */
    private static final Map<UUID, Long> LAST_PASS_LOG_NS = new java.util.concurrent.ConcurrentHashMap<>();


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
    /** Доля текущей полной эмиссии инвентаря, уходящая в чанк за секунду как
     *  часть оставшейся разницы до ЦЕЛИ. С 0.3.249 цель переноса — не сумма
     *  инвентаря, а эмиссия СИЛЬНЕЙШЕГО стака: предметы инвентаря не могут
     *  заразить чанк сильнее значения, которое они выдают (идея автора:
     *  плато по самому сильному источнику, сильный источник не греет чанк
     *  выше себя). */
    private static final double INVENTORY_TO_CHUNK_RATE = 0.01;
    /** Погрешность передачи в меньшую сторону (0.3.249): в чанк уходит 95 % расчёта. */
    private static final double INVENTORY_TRANSFER_EFFICIENCY = 0.95;
    /** Ставка «контейнеры/обычные предметы в чанке → чанк»: 1% оставшейся
     *  разницы с суммарной эмиссией за каждую секунду сканирования. */
    private static final double CHEST_TO_CHUNK_RATE = 0.01;
    /** Наведённый компонент уже учтён в общей сумме инвентаря; отдельный
     *  повторный перенос не запускается, чтобы не создавать петлю
     *  «предметы→чанк→предметы». */

    // ── естественное восстановление шкалы ──
    /** Доля шкалы, спадающая в секунду (точно 0.2%/с от текущего, автор: «теряет
     *  шкалу 0.2% от текущего заполнения в сек»; период полуспада ~5.8 мин). */
    private static final double RECOVERY_RATE = 0.002;
    /** Спадающая доза сбрасывается в чанк (п.4): 20%, с мягким потолком 100mZt —
     *  иначе петля «чанк→шкала→спад→чанк» саморазгонялась. */
    private static final double SHED_TO_CHUNK = 0.2;
    private static final double SHED_CEILING = 100.0 * RadUnits.MILLI;

    /** Обслуживание чанков — каждые 20 секунд (п.4). */
    private static final int CHUNK_MAINT_PERIOD_TICKS = ChunkRadiationData.PLACED_UPDATE_PERIOD_TICKS;
    /** Тик игрока — каждую секунду. */
    private static final int PLAYER_PERIOD_TICKS = 20;

    /** Накопители дробной дозы/спада по игрокам (permille дискретен). */
    private static final Map<UUID, Double> DOSE_ACC = new HashMap<>();
    private static final Map<UUID, Double> SHED_ACC = new HashMap<>();
    /** Последний swing pulse: mining не является обычным isUsingItem(). */
    private static final Map<UUID, Integer> LAST_SWING = new HashMap<>();
    /** Виртуальная радиация активного предмета: не трогаем carried NBT во время действия. */
    private static final Map<HeldKey, HeldRadState> HELD_RAD = new HashMap<>();
    /** Один скан содержимого на чанк, общий для всех игроков в нём. */
    private static final Map<ServerLevel, Map<Long, Long>> LAST_CONTENT_SCAN =
            Collections.synchronizedMap(new WeakHashMap<>());
    /** Время прошлого активного скана для темпа поставленного источника 5% за 20 с. */
    private static final Map<ServerLevel, Map<Long, Long>> LAST_FLOOR_BLOCK_SCAN =
            Collections.synchronizedMap(new WeakHashMap<>());
    private record HeldKey(UUID player, InteractionHand hand) {}
    private record FloorScan(double ordinaryTransfer, double blockLikeTarget) {}
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
        long nowTick = level.getServer().getTickCount();
        ChunkRadiationData data = ChunkRadiationData.get(level);
        boolean tracing = RadiationTrace.isActive(level, player.blockPosition());
        if (hasVisualInstrument(player)) {
            List<RadiationVisualPayload.Source> visual = data.visualSources(level, player.blockPosition(), 18).stream()
                    .map(s -> new RadiationVisualPayload.Source(s.pos().asLong(), (float) s.emission()))
                    .toList();
            PacketDistributor.sendToPlayer(player, new RadiationVisualPayload(visual));
        }
        long chunkKey = new ChunkPos(player.blockPosition()).toLong();
        // (0.3.271, автор 23.10) Игрок кликает по GUI — стаки не пишем: запись в
        // окне между предсказанием клиента и обработкой клика = дюп через carried.
        boolean clickFrozen = RadiationClickWatch.recentlyClicked(player, nowTick);
        boolean debugInv = GonzoServerConfig.RADIATION_INVENTORY_DEBUG.get();
        int debugTouched = 0;

        // Скан полной эмиссии инвентаря; максимум и второй максимум используются
        // только для локальной логистики наведёнки предметов, не для переноса в чанк.
        // (0.3.273, раунд автора 23.10) Курсор участвует в проходе на равных:
        // предмет, взятый в carried, излучает и получает дозу. Раньше он был вне
        // прохода (документированное поведение N3), и автор зафиксировал это как
        // баг: «не принимает и не излучает». Физическая запись в курсор защищена
        // тем же clickFrozen, что и слоты: окно клика стаки не трогаем.
        ItemStack carriedStack = player.containerMenu.getCarried();
        List<List<ItemStack>> compartments = carriedStack.isEmpty()
                ? List.of(player.getInventory().items, player.getInventory().armor, player.getInventory().offhand)
                : List.of(player.getInventory().items, player.getInventory().armor, player.getInventory().offhand,
                        List.of(carriedStack));
        // 0.3.83: суммы — с рекурсивным обходом вложенных хранилищ (шалкер/
        // связка/мод-контейнер фонят как россыпь; экран только у носителей).
        double intrinsic = 0.0;
        double maxStackEmission = 0.0;     // сильнейший стак…
        double secondStackEmission = 0.0;  // …и сильнейший ДРУГОЙ (с кратностями)
        // (автор 22.10) параллельно — те же стаки в значении «на предмет»
        // (наведённая часть ×0.75). Разыгрыв «кто сильнее» остаётся на полной
        // эмиссии, иначе стак со своей наведёнкой выталкивал бы сам себя в
        // «вторые» нечестно.
        double maxStackTransfer = 0.0;
        double secondStackTransfer = 0.0;
        for (List<ItemStack> part : compartments) {
            for (ItemStack stack : part) {
                double e = RadSources.emissionDeep(stack);
                double et = RadSources.emissionTransfer(stack);
                intrinsic += e;
                if (e > maxStackEmission) {
                    secondStackEmission = maxStackEmission;
                    secondStackTransfer = maxStackTransfer;
                    maxStackEmission = e;
                    maxStackTransfer = et;
                } else if (e > secondStackEmission) {
                    secondStackEmission = e;
                    secondStackTransfer = et;
                }
            }
        }

        double chunkNzt = data.value(level, chunkKey);
        double chunkContamination = data.contaminationOf(chunkKey);
        double chunkBaseline = chunkNzt - chunkContamination;
        // Источники наведённого фона: собственные радио-предметы или очень горячий чанк (п.4).
        boolean hasSourceContext = intrinsic > 0.0 || chunkNzt > ITEM_FROM_CHUNK_MIN;
        // Цель наведённого фона = эмиссия СИЛЬНЕЙШЕГО локального источника (100%,
        // автор 21.09: «предмет не может облучить другой сильнее, чем выдаёт сам»).
        // 0.3.83: уровень считается БЕЗ самого стака (иначе носитель наводился бы
        // собственным содержимым, а empty-щипцы не росли бы от чужих источников).
        double chunkSource = chunkNzt > ITEM_FROM_CHUNK_MIN ? chunkNzt : 0.0;

        double inducedBefore = 0.0;
        double induced = 0.0;
        for (int compartmentIndex = 0; compartmentIndex < compartments.size(); compartmentIndex++) {
            List<ItemStack> part = compartments.get(compartmentIndex);
            for (int slot = 0; slot < part.size(); slot++) {
                ItemStack stack = part.get(slot);
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
                        ownEmission >= maxStackEmission ? secondStackTransfer : maxStackTransfer,
                        chunkSource);
                double factor = RadMaterials.itemFactor(stack);
                int count = Math.max(1, stack.getCount());
                // intrinsic уже содержит NBT-наведёнку до тика. Запоминаем ровно
                // этот верхний уровень, чтобы заменить его новым значением, а не
                // прибавить наведённую эмиссию к уже включённой второй раз.
                double storedInduced = ItemRadioactivity.getInduced(stack);
                inducedBefore += storedInduced * count;
                double oldInduced = tracing ? storedInduced : 0.0;
                if (tracing && activeHand) {
                    InteractionHand hand = stack == player.getOffhandItem()
                            ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                    HeldRadState heldState = HELD_RAD.get(new HeldKey(player.getUUID(), hand));
                    if (heldState != null && heldState.stack == stack) oldInduced = heldState.value;
                }
                double perItem;
                if (activeHand) {
                    perItem = tickHeldRadiation(player, stack, hardUse, hasSourceContext,
                            stackSource, factor, nowTick, clickFrozen);
                } else {
                    perItem = clickFrozen
                            ? ItemRadioactivity.peekInducedAt(
                                stack, hasSourceContext, stackSource, factor, nowTick)
                            : ItemRadioactivity.tickInducedAt(
                                stack, hasSourceContext, stackSource, factor, nowTick);
                }
                induced += perItem * count;
                if (debugInv) debugTouched++;
                if (tracing && (ownEmission > 0.0 || oldInduced > 0.0 || perItem > 0.0)) {
                    RadiationTrace.log(level, player.blockPosition(), "ПРЕДМЕТЫ ИНВЕНТАРЯ ↔ ДОЗА ИГРОКА",
                            inventoryCompartmentName(compartmentIndex) + " слот " + slot
                                    + "; item=" + BuiltInRegistries.ITEM.getKey(stack.getItem())
                                    + "; count=" + count
                                    + "; intrinsic=" + RadiationTrace.rate(ownEmission)
                                    + "; induced/item=" + RadiationTrace.rate(oldInduced)
                                    + "→" + RadiationTrace.rate(perItem)
                                    + "; inventoryContribution=" + RadiationTrace.rate(perItem * count)
                                    + "; strongestOtherSource=" + RadiationTrace.rate(stackSource)
                                    + "; materialFactor=" + RadiationTrace.number(factor)
                                    + (hardUse ? "; held under active use protection" : ""));
                }
            }
        }

        if (debugInv) {
            // (0.3.273) Реалтаймовый шлюз 1 строка/с на игрока: на tickRate 2000
            // лог с периодом 20 тиков давал 100 строк/с и сам кормил
            // «Can't keep up» — данные от этого плотнее не становятся.
            long logNowNs = System.nanoTime();
            Long logPrevNs = LAST_PASS_LOG_NS.get(player.getUUID());
            if (logPrevNs == null || logNowNs - logPrevNs > 950_000_000L) {
                LAST_PASS_LOG_NS.put(player.getUUID(), logNowNs);
                RAD_INV_LOG.info("[RAD-INV] {}: проход инвентаря t={} — {} стак(ов), carried {} шт, {}{} (frozen={})",
                    player.getGameProfile().getName(), nowTick, debugTouched,
                    carriedStack.getCount(),
                    clickFrozen ? "запись заморожена" : "запись выполнена",
                    "", clickFrozen);
            }
        }
        // Пересобираем полную эмиссию после обновления вложенных слотов. Каждый
        // верхний стак входит ровно раз; виртуальная наведёнка активной руки
        // подменяет старый NBT, не записываясь в carried stack во время use.
        double totalNzt = 0.0D;
        double strongestStackNzt = 0.0D;
        for (List<ItemStack> part : compartments) {
            for (ItemStack stack : part) {
                double stackEmission = RadSources.emissionDeep(stack);
                if (stack == player.getMainHandItem() || stack == player.getOffhandItem()) {
                    InteractionHand hand = stack == player.getOffhandItem()
                            ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                    HeldRadState heldState = HELD_RAD.get(new HeldKey(player.getUUID(), hand));
                    if (heldState != null && heldState.stack == stack) {
                        stackEmission += (heldState.value - ItemRadioactivity.getInduced(stack))
                                * Math.max(1, stack.getCount());
                    }
                }
                double clampedEmission = Math.max(0.0D, stackEmission);
                totalNzt += clampedEmission;
                if (clampedEmission > strongestStackNzt) strongestStackNzt = clampedEmission;
            }
        }

        // Доза шкалы: инвентарь полным весом + фон чанка с весом 1/10.
        // Хазмат I (автор 22.09) режет входящую дозу, но пробивается горячим
        // источником: множитель считается от дозы/сек (см. Hazmat.factor).
        // The enclosure protects the outside chunk, not somebody standing beside
        // its sources. Add 40% of actual block emission in this same closed cavity;
        // this does not feed chunk contamination or inventory-induced radiation.
        double containmentDose = Containment.insideDose(level, BlockPos.containing(player.getEyePosition()));
        double rawDose = totalNzt + chunkNzt * CHUNK_DOSE_WEIGHT + containmentDose;
        // «Зуд III» (заражение > 69 %) — +20 % к получению дозы (автор 22.09.2026).
        double psycheDoseMultiplier = PsycheChemical.doseMultiplier(player);
        rawDose *= psycheDoseMultiplier;
        double hazmatFactor = Hazmat.factor(player, rawDose);
        double suitFactor = hazmatFactor;
        double absorptionFactor = 1.0;
        // «Абсорбция дозы» (Цистамин/ДТПА, спека 24.09): срезает получаемую игроком
        // дозу на (30 + уровень²) %: уровень 1 → 31 %, уровень 2 → 34 %.
        var absorption = player.getEffect(com.gonzotech.core.registry.ModEffects.DOSE_ABSORPTION);
        if (absorption != null) {
            int absLevel = absorption.getAmplifier() + 1;
            int cut = Math.min(100, 30 + absLevel * absLevel);
            absorptionFactor = (100 - cut) / 100.0;
            suitFactor *= absorptionFactor;
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
        if (tracing) {
            RadiationTrace.log(level, player.blockPosition(), "ДОЗА ИГРОКА",
                    "inventoryEmissionBeforeTick=" + RadiationTrace.rate(intrinsic)
                            + "; inducedInventoryBeforeTick=" + RadiationTrace.rate(inducedBefore)
                            + "; inducedInventoryAfterTick=" + RadiationTrace.rate(induced)
                            + "; inventoryDoseEmission=" + RadiationTrace.rate(totalNzt)
                            + "; chunkBaseline=" + RadiationTrace.rate(chunkBaseline)
                            + "; chunkContamination=" + RadiationTrace.rate(chunkContamination)
                            + "; chunkTotal=" + RadiationTrace.rate(chunkNzt) + "×" + CHUNK_DOSE_WEIGHT
                            + "=" + RadiationTrace.rate(chunkNzt * CHUNK_DOSE_WEIGHT)
                            + "; insideBlockSources=" + RadiationTrace.rate(containmentDose)
                            + "; psycheMultiplier=" + RadiationTrace.number(psycheDoseMultiplier)
                            + "; hazmatFactor=" + RadiationTrace.number(hazmatFactor)
                            + "; absorptionFactor=" + RadiationTrace.number(absorptionFactor)
                            + "; delivered=" + RadiationTrace.rate(doseThisTick)
                            + "; doseRemainder=" + RadiationTrace.number(acc) + " nZt accumulator"
                            + "; gain=" + gainPermille + "‰; shed=" + shedPermille + "‰"
                            + "; radiationScale=" + scale + "→" + newScale);
        }
        if (newScale != scale) {
            psyche.setRadiation(newScale);
            PsycheNetwork.sendToPlayer(player);
        }

        // Химическое заражение: 2 % дозы уходит в шестую шкалу (автор 22.09.2026):
        // «шкалы радиации и заражения имеют одинаковую размерность».
        if (doseThisTick > 0.0) {
            double chemicalTransfer = doseThisTick / NZT_PER_PERMILLE;
            PsycheChemical.addFromDose(player, chemicalTransfer);
            if (tracing) {
                RadiationTrace.log(level, player.blockPosition(), "ДОЗА → ХИМИЧЕСКОЕ ЗАРАЖЕНИЕ ИГРОКА",
                        "transfer=" + RadiationTrace.number(chemicalTransfer)
                                + "; dose=" + RadiationTrace.rate(doseThisTick));
            }
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

        // Игрок → чанк: F позиции игрока × 1%×max(M−C,0), без потолка по
        // эмиссии сильнейшего стака. Верхнеуровневая наведёнка уже включена в M.
        double contam = data.contaminationOf(chunkKey);
        double inventoryContourFactor = tracing || totalNzt > contam
                ? Containment.factor(level, player.blockPosition()) : 1.0D;
        // 0.3.249: чанк не может быть заражён инвентарём сильнее значения,
        // которое выдаёт сильнейший стак (цель — не вся сумма), и передача
        // теряет 5 %: петля «предметы → чанк → предметы» больше не набирает силу.
        double inventoryTarget = Math.min(totalNzt, strongestStackNzt);
        double inventoryTransfer = transferTowardChunk(
                contam, inventoryTarget,
                INVENTORY_TO_CHUNK_RATE * inventoryContourFactor * INVENTORY_TRANSFER_EFFICIENCY);
        if (inventoryTransfer > 0.0) {
            data.addContamination(level, chunkKey, inventoryTransfer, "радиоактивный инвентарь игрока → чанк");
        }
        if (tracing) {
            RadiationTrace.log(level, player.blockPosition(), "ИНВЕНТАРЬ ИГРОКА → ЧАНК",
                    "emission=" + RadiationTrace.rate(totalNzt)
                            + "; contamination=" + RadiationTrace.rate(contam)
                            + "; contourFactor=" + RadiationTrace.number(inventoryContourFactor)
                            + "; rate=" + RadiationTrace.number(INVENTORY_TO_CHUNK_RATE)
                            + "; transfer=" + RadiationTrace.rate(inventoryTransfer));
        }
        if (shedPermille > 0) {
            double shedContamination = data.contaminationOf(chunkKey);
            if (shedContamination < SHED_CEILING) {
                double transfer = shedPermille * NZT_PER_PERMILLE * SHED_TO_CHUNK
                        * (1.0 - shedContamination / SHED_CEILING);
                data.addContamination(level, chunkKey, transfer, "радиационный спад игрока → чанк");
            }
        }
        if (cleansed > 0) {
            double cleanseContamination = data.contaminationOf(chunkKey);
            if (cleanseContamination < SHED_CEILING) {
                double transfer = cleansed * NZT_PER_PERMILLE * SHED_TO_CHUNK
                        * (1.0 - cleanseContamination / SHED_CEILING);
                data.addContamination(level, chunkKey, transfer, "Очищение/RadCleanse игрока → чанк");
            }
        }

        // Скан содержимого контейнеров своего чанка (сундуки/бочки с ураном греют чанк).
        scanContainersInto(level, chunkKey, data, nowTick);
    }

    private static String inventoryCompartmentName(int compartment) {
        return switch (compartment) {
            case 0 -> "инвентарь/горячая панель";
            case 1 -> "броня";
            case 2 -> "вторая рука";
            case 3 -> "курсор (в руке)";
            default -> "слот " + compartment;
        };
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
                                            boolean hasSource, double sourceNzt, double factor,
                                            long nowTick, boolean frozen) {
        InteractionHand hand = stack == player.getOffhandItem()
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        HeldKey key = new HeldKey(player.getUUID(), hand);
        int now = player.tickCount;
        HeldRadState state = HELD_RAD.get(key);
        if (state == null && !hardUse) {
            return frozen
                    ? ItemRadioactivity.peekInducedAt(stack, hasSource, sourceNzt, factor, nowTick)
                    : ItemRadioactivity.tickInducedAt(stack, hasSource, sourceNzt, factor, nowTick);
        }
        if (state == null || state.stack != stack) {
            if (state != null && !frozen) {
                ItemRadioactivity.setInduced(state.stack, state.value);
                ItemRadioactivity.markDecayTick(state.stack, nowTick);
                ItemRadioactivity.decayInducedDeepLazily(state.stack, nowTick);
            }
            state = new HeldRadState(stack,
                    ItemRadioactivity.inducedBeforeSourceStep(stack, nowTick), now);
            HELD_RAD.put(key, state);
        }
        if (hardUse) state.lastUseTick = now;
        state.value = ItemRadioactivity.nextInduced(state.value, stack, hasSource, sourceNzt, factor);

        boolean inSoftCd = now - state.lastUseTick < USE_SOFT_CD_TICKS;
        boolean periodicFlush = now - state.lastFlushTick >= HELD_FLUSH_TICKS;
        if (!frozen && (periodicFlush || !hardUse && !inSoftCd)) {
            ItemRadioactivity.setInduced(stack, state.value);
            ItemRadioactivity.markDecayTick(stack, nowTick);
            ItemRadioactivity.decayInducedDeepLazily(stack, nowTick);
            HELD_RAD.remove(key);
            return ItemRadioactivity.getInduced(stack);
        }
        return state.value;
    }

    /**
     * Сканирует содержимое контейнеров и сущностей только один раз за секунду на
     * чанк, даже если в нём стоят несколько игроков. Вложенные предметы получают
     * только обычное затухание наведёнки — они не облучают соседние слоты.
     */
    private static void scanContainersInto(ServerLevel level, long chunkKey,
                                           ChunkRadiationData data, long nowTick) {
        if (!claimContentScan(level, chunkKey, nowTick)) return;
        // (0.3.271) Пока кто-то из игроков кликает по контейнеру, содержимое BE
        // не переписываем (эмиссии считаются по текущим стекам, разряд догонит
        // лениво на следующем цикле) — окно drag&drop не рождает дюпы.
        boolean clickFrozen = RadiationClickWatch.worldFrozen(level, nowTick);
        LevelChunk chunk = level.getChunk(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
        double contamination = data.contaminationOf(chunkKey);
        double containerTransfer = 0.0D;
        double leadChestTransfer = 0.0D;
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (!(be instanceof Container container)) continue;
            boolean leadChest = be.getBlockState().getBlock()
                    instanceof com.gonzotech.machines.block.ThirdLeadChestBlock;
            double mine = 0.0D;
            boolean changed = false;
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (stack.isEmpty()) continue;
                if (!clickFrozen) changed |= ItemRadioactivity.decayInducedDeepLazily(stack, nowTick);
                mine += RadSources.emissionDeep(stack);
            }
            if (changed) container.setChanged();
            if (mine <= 0.0D) continue;

            BlockPos pos = be.getBlockPos();
            double contourFactor = Containment.factor(level, pos);
            if (leadChest) {
                // Табличная формула: 0.2×источник ДО контура, затем max(...−C,0) и 1%.
                double preContourEmission = mine
                        * com.gonzotech.machines.block.ThirdLeadChestBlock.CONTENT_CHUNK_FACTOR;
                double effectiveEmission = preContourEmission * contourFactor;
                double transfer = transferTowardChunk(
                        contamination, effectiveEmission, CHEST_TO_CHUNK_RATE);
                leadChestTransfer += transfer;
                RadiationTrace.log(level, pos, "СВИНЦОВЫЙ ЯЩИК → ЧАНК",
                        "contentEmission=" + RadiationTrace.rate(mine)
                                + "; sourceFactor=" + RadiationTrace.number(
                                        com.gonzotech.machines.block.ThirdLeadChestBlock.CONTENT_CHUNK_FACTOR)
                                + "; preContourEmission=" + RadiationTrace.rate(preContourEmission)
                                + "; contourFactor=" + RadiationTrace.number(contourFactor)
                                + "; effectiveEmission=" + RadiationTrace.rate(effectiveEmission)
                                + "; transfer=" + RadiationTrace.rate(transfer));
            } else {
                // Для обычных контейнеров контур ограничивает сам перенос.
                double transfer = transferTowardChunk(
                        contamination, mine, CHEST_TO_CHUNK_RATE * contourFactor);
                containerTransfer += transfer;
                RadiationTrace.log(level, pos, "КОНТЕЙНЕР → ЧАНК",
                        "block=" + BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock())
                                + "; contentEmission=" + RadiationTrace.rate(mine)
                                + "; contourFactor=" + RadiationTrace.number(contourFactor)
                                + "; chunkRate=" + RadiationTrace.number(CHEST_TO_CHUNK_RATE)
                                + "; transfer=" + RadiationTrace.rate(transfer));
            }
        }

        FloorScan floor = scanDroppedItemsInto(level, chunkKey, contamination, nowTick);
        double ordinaryTransfer = containerTransfer + leadChestTransfer + floor.ordinaryTransfer();
        if (ordinaryTransfer > 0.0D) {
            data.addContamination(level, chunkKey, ordinaryTransfer,
                    "контейнеры/конечные предметы в чанке → чанк");
        }
        long floorElapsed = elapsedFloorBlockScanTicks(level, chunkKey, nowTick);
        if (floor.blockLikeTarget() > 0.0D && floorElapsed > 0L) {
            data.convergePlacedLikeEntitySource(level, chunkKey, floor.blockLikeTarget(), floorElapsed);
        }
    }

    /** Перенос доли оставшейся разницы к эмиссии источника. */
    private static double transferTowardChunk(double contamination, double sourceEmission, double rate) {
        return sourceEmission > contamination ? (sourceEmission - contamination) * rate : 0.0;
    }

    /** Ограничить скан контейнеров и предметов одним разом в секунду на чанк. */
    private static boolean claimContentScan(ServerLevel level, long chunkKey, long nowTick) {
        synchronized (LAST_CONTENT_SCAN) {
            Map<Long, Long> byChunk = LAST_CONTENT_SCAN.computeIfAbsent(level, ignored -> new HashMap<>());
            Long last = byChunk.get(chunkKey);
            if (last != null && nowTick >= last && nowTick - last < PLAYER_PERIOD_TICKS) return false;
            byChunk.put(chunkKey, nowTick);
            if (byChunk.size() > 4096) {
                byChunk.entrySet().removeIf(entry -> nowTick - entry.getValue() > 20L * 60L * 10L);
                if (byChunk.size() > 8192) byChunk.clear();
            }
            return true;
        }
    }

    /** Первый скан запускает таймер, следующие воспроизводят 5% сближения за 20 с. */
    private static long elapsedFloorBlockScanTicks(ServerLevel level, long chunkKey, long nowTick) {
        synchronized (LAST_FLOOR_BLOCK_SCAN) {
            Map<Long, Long> byChunk = LAST_FLOOR_BLOCK_SCAN.computeIfAbsent(level, ignored -> new HashMap<>());
            Long last = byChunk.put(chunkKey, nowTick);
            if (byChunk.size() > 4096) {
                byChunk.entrySet().removeIf(entry -> nowTick - entry.getValue() > 20L * 60L * 10L);
                if (byChunk.size() > 8192) byChunk.clear();
            }
            if (last == null || nowTick <= last) return 0L;
            return Math.min(nowTick - last, CHUNK_MAINT_PERIOD_TICKS);
        }
    }

    /**
     * Конечные предметы на полу/в рамках передают 1%×F×max(M−C,0) и затухают
     * лениво. Бесконечный радиоактивный BlockItem исключается из этой ставки и
     * возвращается отдельной целью, питающей чанк как поставленный блок.
     */
    private static FloorScan scanDroppedItemsInto(ServerLevel level, long chunkKey,
                                                  double contamination, long nowTick) {
        int cx = ChunkPos.getX(chunkKey);
        int cz = ChunkPos.getZ(chunkKey);
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                cx << 4, level.getMinY(), cz << 4,
                (cx << 4) + 16, level.getMinY() + level.getHeight(), (cz << 4) + 16);
        double[] totals = new double[2]; // [конечные предметы → чанк, цель BlockItem]
        for (net.minecraft.world.entity.item.ItemEntity drop
                : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box,
                    entity -> !entity.getItem().isEmpty())) {
            ItemStack stack = drop.getItem();
            if (ItemRadioactivity.decayInducedDeepLazily(stack, nowTick)) drop.setItem(stack);
            classifyFloorStack(level, drop.blockPosition(), "предмет на земле", stack, contamination, totals);
        }
        for (net.minecraft.world.entity.decoration.ItemFrame frame
                : level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ItemFrame.class, box,
                    entity -> !entity.getItem().isEmpty())) {
            ItemStack stack = frame.getItem();
            if (ItemRadioactivity.decayInducedDeepLazily(stack, nowTick)) frame.setItem(stack, false);
            classifyFloorStack(level, frame.blockPosition(), "предмет в рамке", stack, contamination, totals);
        }
        return new FloorScan(totals[0], totals[1]);
    }

    /** Добавить в итоги скана [перенос конечных предметов, цель BlockItem]. */
    private static void classifyFloorStack(ServerLevel level, BlockPos pos, String source,
                                           ItemStack stack, double contamination, double[] out) {
        if (stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem) {
            double blockEmission = blockEmission(blockItem.getBlock());
            if (blockEmission > 0.0D) {
                double contourFactor = Containment.factor(level, pos);
                double target = blockEmission * stack.getCount() * contourFactor;
                out[1] += target;
                RadiationTrace.log(level, pos, "БЛОЧНЫЙ ПРЕДМЕТ → МОДЕЛЬ ПОСТАВЛЕННОГО БЛОКА",
                        source + "; item=" + BuiltInRegistries.ITEM.getKey(stack.getItem())
                                + "; count=" + stack.getCount()
                                + "; blockEmission=" + RadiationTrace.rate(blockEmission)
                                + "; contourFactor=" + RadiationTrace.number(contourFactor)
                                + "; placedLikeTarget=" + RadiationTrace.rate(target));
                return;
            }
        }
        double emission = RadSources.emissionDeep(stack);
        if (emission <= 0.0D) return;
        double contourFactor = Containment.factor(level, pos);
        double transfer = transferTowardChunk(
                contamination, emission, CHEST_TO_CHUNK_RATE * contourFactor);
        out[0] += transfer;
        RadiationTrace.log(level, pos, "КОНЕЧНЫЙ ПРЕДМЕТ → ЧАНК",
                source + "; item=" + BuiltInRegistries.ITEM.getKey(stack.getItem())
                        + "; count=" + stack.getCount()
                        + "; emission=" + RadiationTrace.rate(emission)
                        + "; contourFactor=" + RadiationTrace.number(contourFactor)
                        + "; transfer=" + RadiationTrace.rate(transfer));
    }

    // ═══════════════════════ выход из игры: чистим карты ═══════════════════════

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RadiationTrace.stop(player, "выход из игры");
            DOSE_ACC.remove(player.getUUID());
            SHED_ACC.remove(player.getUUID());
            LAST_SWING.remove(player.getUUID());
            LAST_PASS_LOG_NS.remove(player.getUUID());
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
                int before = psyche.getRadiation();
                psyche.setRadiation(0);
                RadiationTrace.playerDoseChange(player, before, psyche.getRadiation(), "смерть: сброс шкалы");
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
        RadiationTrace.tick(event.getServer());
        if (event.getServer().getTickCount() % CHUNK_MAINT_PERIOD_TICKS != 0) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            ChunkRadiationData.get(level).maintain(level);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RadiationTrace.clearAll();
        LAST_CONTENT_SCAN.clear();
        LAST_FLOOR_BLOCK_SCAN.clear();
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
            ChunkRadiationData.get(level).onBlockRemoved(level, event.getPos(), emission);
        }
    }

    /** Track direct source placements that do not produce BlockEvent.EntityPlaceEvent (buckets, thermal hazards). */
    public static void trackPlacedBlock(ServerLevel level, BlockPos pos,
                                        net.minecraft.world.level.block.state.BlockState state) {
        double emission = RadSources.blockEmission(state);
        if (emission > 0.0) {
            ChunkRadiationData.get(level).onBlockPlaced(level, pos, emission);
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
        if (oldEmission > 0.0) data.onBlockRemoved(level, pos, oldEmission);
        if (newEmission > 0.0) data.onBlockPlaced(level, pos, newEmission);
    }

    private static double blockEmission(net.minecraft.world.level.block.Block block) {
        return RadSources.blockEmission(block.defaultBlockState());
    }
}
