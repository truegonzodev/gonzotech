package com.gonzotech.radiation;

import com.gonzotech.core.psyche.ModPsycheAttachments;
import com.gonzotech.core.psyche.PlayerPsyche;
import com.gonzotech.core.psyche.PsycheChemical;
import com.gonzotech.core.psyche.PsycheUltraviolet;
import com.gonzotech.core.psyche.PsycheNetwork;
import com.gonzotech.core.registry.ModEffects;
import com.gonzotech.core.registry.ModItems;
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
 * Обычные контейнеры не тикаем; у свинцового ящика наведёнка содержимого только
 * затухает с обычной секундной скоростью, без роста от внешних источников.</p>
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
    /** Доля текущей полной эмиссии инвентаря, уходящая в чанк за секунду как
     *  часть оставшейся разницы до суммы инвентаря. Цель не ограничивается
     *  эмиссией сильнейшего стака. */
    private static final double INVENTORY_TO_CHUNK_RATE = 0.01;
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
        boolean tracing = RadiationTrace.isActive(level, player.blockPosition());
        if (hasVisualInstrument(player)) {
            List<RadiationVisualPayload.Source> visual = data.visualSources(level, player.blockPosition(), 18).stream()
                    .map(s -> new RadiationVisualPayload.Source(s.pos().asLong(), (float) s.emission()))
                    .toList();
            PacketDistributor.sendToPlayer(player, new RadiationVisualPayload(visual));
        }
        long chunkKey = new ChunkPos(player.blockPosition()).toLong();

        // Скан полной эмиссии инвентаря; максимум и второй максимум используются
        // только для локальной логистики наведёнки предметов, не для переноса в чанк.
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
                        ownEmission >= maxStackEmission ? secondStackEmission : maxStackEmission,
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
                            stackSource, factor);
                } else {
                    ItemRadioactivity.tickInduced(stack, hasSourceContext, stackSource, factor);
                    perItem = ItemRadioactivity.getInduced(stack);
                }
                induced += perItem * count;
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

        // intrinsic — полный снимок эмиссии до обновления наведёнки. Убираем из
        // него старую наведёнку верхнего уровня и добавляем обновлённую ровно раз;
        // вложенные контейнеры остаются в intrinsic без изменений.
        double totalNzt = Math.max(0.0, intrinsic + induced - inducedBefore);

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

        // Игрок → чанк: 1% оставшейся разницы до полной эмиссии инвентаря;
        // цель — сумма, без потолка по сильнейшему стаку. Эта логистика сохранена.
        double contam = data.contaminationOf(chunkKey);
        if (intrinsic > 0.0 && contam < intrinsic) {
            double transfer = intrinsic * INVENTORY_TO_CHUNK_RATE * (1.0 - contam / intrinsic);
            data.addContamination(level, chunkKey, transfer, "радиоактивный инвентарь игрока → чанк");
        }
        if (shedPermille > 0 && contam < SHED_CEILING) {
            double transfer = shedPermille * NZT_PER_PERMILLE * SHED_TO_CHUNK * (1.0 - contam / SHED_CEILING);
            data.addContamination(level, chunkKey, transfer, "радиационный спад игрока → чанк");
        }
        if (cleansed > 0 && contam < SHED_CEILING) {
            double transfer = cleansed * NZT_PER_PERMILLE * SHED_TO_CHUNK * (1.0 - contam / SHED_CEILING);
            data.addContamination(level, chunkKey, transfer, "очищение/сброс игрока → чанк");
        }

        // Скан содержимого контейнеров своего чанка (сундуки/бочки с ураном греют чанк).
        scanContainersInto(level, chunkKey, data);
    }

    private static String inventoryCompartmentName(int compartment) {
        return switch (compartment) {
            case 0 -> "инвентарь/горячая панель";
            case 1 -> "броня";
            case 2 -> "вторая рука";
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

    /** Контейнеры активного чанка: обычное содержимое и предметная россыпь
     *  передают 1% оставшейся разницы; у свинцового ящика эмиссия сначала
     *  умножается на 0,2, затем на экран контура, но ставка остаётся 1%. Наведёнка
     *  предметов в свинцовом ящике отдельно затухает с обычной скоростью; блочные
     *  радиоактивные предметы остаются постоянными источниками уровня блока. */
    private static void scanContainersInto(ServerLevel level, long chunkKey, ChunkRadiationData data) {
        LevelChunk chunk = level.getChunk(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
        double chestEmission = 0.0;
        double leadChestEmission = 0.0;
        double[] drops = scanDroppedItemsInto(level, chunkKey); // [обычная россыпь, блоки-источники]
        chestEmission += drops[0];
        double placedLike = drops[1];
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof Container container) {
                boolean leadChest = be.getBlockState().getBlock()
                        instanceof com.gonzotech.machines.block.ThirdLeadChestBlock;
                double mine = 0.0;
                for (int i = 0; i < container.getContainerSize(); i++) {
                    mine += RadSources.emissionDeep(container.getItem(i));
                }
                BlockPos pos = be.getBlockPos();
                if (mine > 0.0 && pos != null) {
                    double sourceFactor = leadChest
                            ? com.gonzotech.machines.block.ThirdLeadChestBlock.CONTENT_CHUNK_FACTOR
                            : 1.0;
                    double preContourEmission = mine * sourceFactor;
                    double contourFactor = Containment.factor(level, pos);
                    double effectiveMine = preContourEmission * contourFactor;
                    if (leadChest) {
                        leadChestEmission += effectiveMine;
                    } else {
                        chestEmission += effectiveMine;
                    }
                    RadiationTrace.log(level, pos, "КОНТЕЙНЕР → ЧАНК",
                            "block=" + BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock())
                                    + "; contentEmission=" + RadiationTrace.rate(mine)
                                    + "; sourceFactor=" + RadiationTrace.number(sourceFactor)
                                    + "; preContourEmission=" + RadiationTrace.rate(preContourEmission)
                                    + "; contourFactor=" + RadiationTrace.number(contourFactor)
                                    + "; chunkRate=" + RadiationTrace.number(CHEST_TO_CHUNK_RATE)
                                    + "; effectiveEmission=" + RadiationTrace.rate(effectiveMine)
                                    + "; chunkTargetContribution="
                                    + RadiationTrace.rate(effectiveMine * CHEST_TO_CHUNK_RATE));
                }
            }
        }

        double contam = data.contaminationOf(chunkKey);
        double ordinaryTransfer = transferTowardChunk(contam, chestEmission, CHEST_TO_CHUNK_RATE);
        double leadTransfer = transferTowardChunk(contam, leadChestEmission, CHEST_TO_CHUNK_RATE);
        // Блочный радиоактивный предмет в мире ведёт себя как поставленный блок:
        // его постоянный вклад не получает ставку предметной наведёнки.
        double placedBlockTransfer = Math.max(0.0, placedLike - contam);
        double transfer = ordinaryTransfer + leadTransfer + placedBlockTransfer;
        if (transfer > 0.0) {
            data.addContamination(level, chunkKey, transfer, "контейнеры/предметы в чанке → чанк");
        }
    }

    /** Перенос доли оставшейся разницы к эмиссии источника. */
    private static double transferTowardChunk(double contamination, double sourceEmission, double rate) {
        return sourceEmission > contamination ? (sourceEmission - contamination) * rate : 0.0;
    }

    /**
     * 0.3.96 (автор): россыпь на полу и предметы в рамках тоже заражали чанк —
     * сканировались только BlockEntity-контейнеры. ItemEntity и ItemFrame —
     * сущности, поэтому добираем их отдельным проходом по чанку (раз в секунду,
     * вместе со сканом контейнеров). Обычная эмиссия передаётся по 1% остатка
     * (CHEST_TO_CHUNK_RATE); экранирования у лежащего предмета нет.
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
            classifyFloorStack(level, drop.blockPosition(), "предмет на земле", drop.getItem(), mine);
        }
        for (net.minecraft.world.entity.decoration.ItemFrame frame
                : level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ItemFrame.class, box,
                    e -> !e.getItem().isEmpty())) {
            classifyFloorStack(level, frame.blockPosition(), "предмет в рамке", frame.getItem(), mine);
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
    private static void classifyFloorStack(ServerLevel level, BlockPos pos, String source,
                                           ItemStack stack, double[] out) {
        if (stack.getItem() instanceof net.minecraft.world.item.BlockItem bi) {
            double be = blockEmission(bi.getBlock());
            if (be > 0.0) {
                double transfer = be * stack.getCount();
                out[1] += transfer;
                RadiationTrace.log(level, pos, "БЛОЧНЫЙ ПРЕДМЕТ → МОДЕЛЬ ЗАРАЖЕНИЯ ЧАНКА",
                        source + "; item=" + BuiltInRegistries.ITEM.getKey(stack.getItem())
                                + "; count=" + stack.getCount()
                                + "; emission=" + RadiationTrace.rate(be)
                                + "; targetContribution=" + RadiationTrace.rate(transfer));
                return;
            }
        }
        double emission = RadSources.emissionDeep(stack);
        out[0] += emission;
        if (emission > 0.0) {
            RadiationTrace.log(level, pos, "ПРЕДМЕТ → ЧАНК",
                    source + "; item=" + BuiltInRegistries.ITEM.getKey(stack.getItem())
                            + "; count=" + stack.getCount()
                            + "; emission=" + RadiationTrace.rate(emission)
                            + "; chunkRate=" + RadiationTrace.number(CHEST_TO_CHUNK_RATE)
                            + "; targetContribution="
                            + RadiationTrace.rate(emission * CHEST_TO_CHUNK_RATE));
        }
    }

    // ═══════════════════════ выход из игры: чистим карты ═══════════════════════

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RadiationTrace.stop(player, "выход из игры");
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
