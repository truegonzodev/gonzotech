package com.gonzotech.radiation;

import com.gonzotech.radiation.engine.RadiationCluster;
import com.gonzotech.radiation.engine.RadiationOwners;
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
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
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

/** Shared per-item cluster physics; player dose, world sources and outgoing shielding
 * remain separate consumers. Physical owners are processed once per 20 game ticks,
 * independently of menus. All rates use nZt/s; ItemStacks are never mutated here. */
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
    private static final double INVENTORY_TO_CHUNK_RATE = 0.01;
    private static final double INVENTORY_TRANSFER_EFFICIENCY = 0.95;
    private static final double CHEST_TO_CHUNK_RATE = 0.01;

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
    /**
     * (0.3.277) Инвентарный журнал против «все всё дюпают»: Σ предметов
     * открытого меню (+курсор), посчитанная сервером на проходе, против суммы
     * того же меню, посчитанной КЛИЕНТОМ и присланной раз в секунду. Серверная
     * растёт = физический дюп (кто-то добавляет предметы серверу). Расхождение
     * клиент>сервер без роста серверной = призрак картинки. Пары:
     * [сервер, клиент].
     */
    private static final Map<java.util.UUID, long[]> LEDGER = new HashMap<>();
    private static final Map<java.util.UUID, Long> LEDGER_CLIENT = new HashMap<>();

    /** Значение клиентской суммы из {@link InventoryLedgerPayload}. */
    public static void acceptLedger(ServerPlayer player, long clientSum) {
        LEDGER_CLIENT.put(player.getUUID(), clientSum);
    }

    /** Сумма предметов меню по единой для сервера и клиента формуле. */
    public static long menuItemCount(AbstractContainerMenu menu) {
        long sum = menu.getCarried().getCount();
        for (Slot slot : menu.slots) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) sum += stack.getCount();
        }
        return sum;
    }

    /** Один скан содержимого на чанк, общий для всех игроков в нём. */
    private static final Map<ServerLevel, Map<Long, Long>> LAST_CONTENT_SCAN =
            Collections.synchronizedMap(new WeakHashMap<>());
    /** Время прошлого активного скана для темпа поставленного источника 5% за 20 с. */
    private static final Map<ServerLevel, Map<Long, Long>> LAST_FLOOR_BLOCK_SCAN =
            Collections.synchronizedMap(new WeakHashMap<>());
    private record FloorScan(double ordinaryTransfer, double blockLikeTarget, double maxTarget) {}

    private RadiationSystem() {
    }

    // ═══════════════════════ 1 сек: игрок ═══════════════════════

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // Воздух некроза держим КАЖДЫЙ тик: ваниль восстанавливает 4 пузырька в тик,
        // поэтому вычитать раз в секунду бесполезно (автор 22.09.2026 — полоска мигала).
        Necrosis.tickAir(player);
        if (player.tickCount % PLAYER_PERIOD_TICKS != 0) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        long nowTick = level.getGameTime();
        ChunkRadiationData data = ChunkRadiationData.get(level);
        boolean tracing = RadiationTrace.isActive(level, player.blockPosition());
        if (hasVisualInstrument(player)) {
            List<RadiationVisualPayload.Source> visual = data.visualSources(level, player.blockPosition(), 18).stream()
                    .map(s -> new RadiationVisualPayload.Source(s.pos().asLong(), (float) s.emission()))
                    .toList();
            PacketDistributor.sendToPlayer(player, new RadiationVisualPayload(visual));
        }
        long chunkKey = new ChunkPos(player.blockPosition()).toLong();
        // Dynamic item radiation is external; inventory interactions never pause it.
        boolean debugInv = GonzoServerConfig.RADIATION_INVENTORY_DEBUG.get();
        int debugTouched = 0;
        int debugItemCount = 0;
        // 0.3.306: radiation lives outside ItemStack; no interaction pauses or flush.

        List<ItemStack> roots = playerSlots(player);
        RadiationCluster.Summary before = RadiationCluster.inspect(roots);
        double intrinsic = before.total(), inducedBefore = before.induced();
        double chunkNzt = data.value(level, chunkKey);
        double chunkContamination = data.contaminationOf(chunkKey);
        double chunkBaseline = chunkNzt - chunkContamination;
        RadiationCluster.Summary result = stepCluster(level, player.blockPosition(), roots,
                chunkNzt, RadiationCluster.Policy.NORMAL, nowTick);
        double induced = result.induced();
        debugTouched = result.slots();
        debugItemCount = (int)Math.min(Integer.MAX_VALUE, result.count());

        // (0.3.277) [LEDGER] — арбитр «дюп vs призрак» по контейнеру. 0.3.278:
        // сеанс автора показал, что меню-Σ недостаточно — Q-дюп живёт НА ЗЕМЛЕ
        // (выбросил из меню Δ0, подобрал обратно — рост утонет в легитимном
        // пикапе). Поэтому строка расширена до МИРА: меню + ItemEntity в чанке
        // игрока. Рост мировой Σ — единственное, что доказывает физический дюп;
        // он же ловит и возвраты carried на землю (отрицательные Δ земли при
        // drag-end). Легитимный рост (крафт, печь, лут) никуда не девается —
        // строки читаются в паре с действиями в логе.
        long serverSum = menuItemCount(player.containerMenu);
        Long clientBox = LEDGER_CLIENT.get(player.getUUID());
        long clientSum = clientBox == null ? serverSum : clientBox;
        int pcx = (int) Math.floor(player.getX()) >> 4;
        int pcz = (int) Math.floor(player.getZ()) >> 4;
        net.minecraft.world.phys.AABB groundBox = new net.minecraft.world.phys.AABB(
                pcx << 4, level.getMinY(), pcz << 4,
                (pcx << 4) + 16, level.getMinY() + level.getHeight(), (pcz << 4) + 16);
        long groundSum = 0L;
        int groundCount = 0;
        for (net.minecraft.world.entity.item.ItemEntity drop
                : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    groundBox, entity -> !entity.getItem().isEmpty())) {
            groundSum += drop.getItem().getCount();
            groundCount++;
        }
        long worldSum = serverSum + groundSum;
        long[] prev = LEDGER.get(player.getUUID());
        long prevGround = prev == null || prev.length < 3 ? (prev == null ? 0L : groundSum) : prev[2];
        boolean ledgerChanged = prev == null || prev[0] != serverSum || prev[1] != clientSum
                || prevGround != groundSum;
        if (ledgerChanged) {
            long dSrv = prev == null ? 0 : serverSum - prev[0];
            long dCli = prev == null ? 0 : clientSum - prev[1];
            long dGround = prev == null ? 0 : groundSum - prevGround;
            long dWorld = dSrv + dGround;
            if (serverSum != clientSum || dWorld != 0L) {
                RAD_INV_LOG.warn("[LEDGER] {}: меню Σ={} (Δ{}) | клиент Σ={} (Δ{}) | расхождение {} | земля Σ={} ({} сущ., Δ{}) | мир Σ={} (Δ{})",
                        player.getGameProfile().getName(), serverSum,
                        (dSrv >= 0 ? "+" : "") + dSrv, clientSum, (dCli >= 0 ? "+" : "") + dCli,
                        clientSum - serverSum, groundSum, groundCount,
                        (dGround >= 0 ? "+" : "") + dGround, worldSum,
                        (dWorld >= 0 ? "+" : "") + dWorld);
            } else if (debugInv) {
                RAD_INV_LOG.info("[LEDGER] {}: меню Σ={} | клиент Σ={} (совпадают) | земля Σ={} ({} сущ.) | мир Σ={}",
                        player.getGameProfile().getName(), serverSum, clientSum,
                        groundSum, groundCount, worldSum);
            }
            LEDGER.put(player.getUUID(), new long[]{serverSum, clientSum, groundSum});
        }

        if (debugInv) {
            // (0.3.273) Реалтаймовый шлюз 1 строка/с на игрока: на tickRate 2000
            // лог с периодом 20 тиков давал 100 строк/с и сам кормил
            // «Can't keep up» — данные от этого плотнее не становятся.
            long logNowNs = System.nanoTime();
            Long logPrevNs = LAST_PASS_LOG_NS.get(player.getUUID());
            if (logPrevNs == null || logNowNs - logPrevNs > 950_000_000L) {
                LAST_PASS_LOG_NS.put(player.getUUID(), logNowNs);
                RAD_INV_LOG.info("[RAD-INV] {}: проход инвентаря t={} — {} стак(ов), {} шт, запись выполнена",
                    player.getGameProfile().getName(), nowTick, debugTouched, debugItemCount);
            }
        }
        double totalNzt = result.total();

        // Доза шкалы: инвентарь полным весом + фон чанка с весом 1/10.
        // Хазмат I (автор 22.09) режет входящую дозу, но пробивается горячим
        // источником: множитель считается от дозы/сек (см. Hazmat.factor).
        // The enclosure protects the outside chunk, not somebody standing beside
        // its sources. Add 5% of physical source emission in this same closed cavity;
        // this does not feed chunk contamination or inventory-induced radiation.
        double containmentDose = Containment.insideDose(level, BlockPos.containing(player.getEyePosition()), player);
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
                            + "; insideSources=" + RadiationTrace.rate(containmentDose)
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

        // Outgoing shielding never reduces the chunk's incoming item source.
        double contam = data.contaminationOf(chunkKey);
        double inventoryTarget = result.chunkTarget();
        double inventoryContourFactor = inventoryTarget > contam || shedPermille > 0 || cleansed > 0 || tracing
                ? Containment.factor(level, player.blockPosition()) : 1.0;
        double inventoryTransfer = transferTowardChunk(
                contam, inventoryTarget,
                INVENTORY_TO_CHUNK_RATE * inventoryContourFactor * INVENTORY_TRANSFER_EFFICIENCY);
        if (inventoryTransfer > 0.0) {
            data.addContamination(level, chunkKey, inventoryTransfer, "радиоактивный инвентарь игрока → чанк");
        }
        if (tracing) {
            RadiationTrace.log(level, player.blockPosition(), "ИНВЕНТАРЬ ИГРОКА → ЧАНК",
                    "emission=" + RadiationTrace.rate(totalNzt)
                            + "; target=" + RadiationTrace.rate(inventoryTarget)
                            + "; strongestItem=" + RadiationTrace.rate(result.strongestItem())
                            + "; contamination=" + RadiationTrace.rate(contam)
                            + "; contourFactor=" + RadiationTrace.number(inventoryContourFactor)
                            + "; rate=" + RadiationTrace.number(INVENTORY_TO_CHUNK_RATE)
                            + "; transfer=" + RadiationTrace.rate(inventoryTransfer));
        }
        if (shedPermille > 0) {
            double shedContamination = data.contaminationOf(chunkKey);
            if (shedContamination < SHED_CEILING) {
                double transfer = shedPermille * NZT_PER_PERMILLE * SHED_TO_CHUNK
                        * (1.0 - shedContamination / SHED_CEILING) * inventoryContourFactor;
                data.addContamination(level, chunkKey, transfer, "радиационный спад игрока → чанк");
            }
        }
        if (cleansed > 0) {
            double cleanseContamination = data.contaminationOf(chunkKey);
            if (cleanseContamination < SHED_CEILING) {
                double transfer = cleansed * NZT_PER_PERMILLE * SHED_TO_CHUNK
                        * (1.0 - cleanseContamination / SHED_CEILING) * inventoryContourFactor;
                data.addContamination(level, chunkKey, transfer, "Очищение/RadCleanse игрока → чанк");
            }
        }

        // World owners are scheduled independently of players and open menus.
    }

    /** Physical inventory only: neither open-menu slots, cursor nor ender storage. */
    public static List<ItemStack> playerSlots(ServerPlayer player) {
        List<ItemStack> roots = new java.util.ArrayList<>();
        roots.addAll(player.getInventory().items);
        roots.addAll(player.getInventory().armor);
        roots.addAll(player.getInventory().offhand);
        return roots;
    }

    private static RadiationCluster.Summary stepCluster(ServerLevel level, BlockPos pos,
            List<ItemStack> roots, double chunk, RadiationCluster.Policy policy, long now) {
        boolean trace = RadiationTrace.isActive(level, pos);
        return RadiationCluster.step(roots, chunk, policy, now, trace ? step ->
                RadiationTrace.log(level, pos, "КЛАСТЕР: ПРЕДМЕТ ↔ ИСТОЧНИК",
                        "path=" + step.path() + "; item=" + BuiltInRegistries.ITEM.getKey(step.stack().getItem())
                        + "; count=" + step.stack().getCount()
                        + "; induced/item=" + RadiationTrace.rate(step.before()) + "→" + RadiationTrace.rate(step.after())
                        + "; strongestOther/item=" + RadiationTrace.rate(step.otherSource())
                        + "; chunk=" + RadiationTrace.rate(step.chunkSource())
                        + "; target/item=" + RadiationTrace.rate(step.target())
                        + "; material=" + step.factor() + "; internal=" + policy.internalTransfer()) : null);
    }

    private static boolean hasVisualInstrument(ServerPlayer player) {
        return player.getMainHandItem().is(ModItems.DOSIMETER.get())
                || player.getMainHandItem().is(ModItems.TELIFON.get())
                || player.getOffhandItem().is(ModItems.DOSIMETER.get())
                || player.getOffhandItem().is(ModItems.TELIFON.get());
    }

    /** Loaded ticking chunks only; no menu-derived ownership and no chunk generation. */
    private static void scanContainersInto(ServerLevel level, long chunkKey,
                                          ChunkRadiationData data, long nowTick) {
        if (!claimContentScan(level, chunkKey, nowTick)) return;
        LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
        if (chunk == null) return;
        double contamination = data.contaminationOf(chunkKey);
        double chunkEmission = data.value(level, chunkKey);
        double ordinaryTransfer = 0, maxTarget = contamination;
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (!(be instanceof Container) && !(be instanceof com.gonzotech.swag.PetBowlBlockEntity)) continue;
            // The two halves of a double chest are one owner for interaction.
            List<BlockEntity> owners = chestOwners(level, be);
            BlockPos origin = owners.get(0).getBlockPos();
            if (!origin.equals(be.getBlockPos())) continue;
            List<ItemStack> roots = new java.util.ArrayList<>();
            for (BlockEntity owner : owners) roots.addAll(RadiationOwners.slots(owner));
            boolean lead = be.getBlockState().getBlock() instanceof com.gonzotech.machines.block.ThirdLeadChestBlock;
            RadiationCluster.Summary result = stepCluster(level, origin, roots, chunkEmission,
                    lead ? RadiationCluster.Policy.LEAD : RadiationCluster.Policy.NORMAL, nowTick);
            if (result.changed()) for (BlockEntity owner : owners) owner.setChanged();
            double target = result.chunkTarget();
            if (target <= contamination && !RadiationTrace.isActive(level, origin)) continue;
            double factor = Containment.factor(level, origin);
            double transfer = lead
                    ? transferTowardChunk(contamination, target * com.gonzotech.machines.block.ThirdLeadChestBlock.CONTENT_CHUNK_FACTOR * factor, CHEST_TO_CHUNK_RATE)
                    : transferTowardChunk(contamination, target, CHEST_TO_CHUNK_RATE * factor);
            ordinaryTransfer += transfer;
            if (factor > 0) maxTarget = Math.max(maxTarget, lead
                    ? target * com.gonzotech.machines.block.ThirdLeadChestBlock.CONTENT_CHUNK_FACTOR * factor : target);
            RadiationTrace.log(level, origin, "КЛАСТЕР → ЧАНК",
                    "block=" + BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock())
                    + "; slots=" + result.slots() + "; total=" + RadiationTrace.rate(result.total())
                    + "; strongestItem=" + RadiationTrace.rate(result.strongestItem())
                    + "; target=" + RadiationTrace.rate(target) + "; lead=" + lead
                    + "; contour=" + factor + "; transfer=" + RadiationTrace.rate(transfer));
        }
        FloorScan floor = scanDroppedItemsInto(level, chunkKey, contamination, nowTick);
        // Many clusters share one chunk. Their rates may add, but cannot overshoot
        // every source's target and then feed that overshoot back to all items.
        maxTarget = Math.max(maxTarget, floor.maxTarget());
        ordinaryTransfer = Math.min(ordinaryTransfer + floor.ordinaryTransfer(), Math.max(0, maxTarget - contamination));
        if (ordinaryTransfer > 0) data.addContamination(level, chunkKey, ordinaryTransfer, "кластеры предметов → чанк");
        long elapsed = elapsedFloorBlockScanTicks(level, chunkKey, nowTick);
        if (floor.blockLikeTarget() > 0 && elapsed > 0)
            data.convergePlacedLikeEntitySource(level, chunkKey, floor.blockLikeTarget(), elapsed);
    }

    public static List<BlockEntity> chestOwners(ServerLevel level, BlockEntity be) {
        var state = be.getBlockState();
        if (!(be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity)
                || !(state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock)
                || state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE) == net.minecraft.world.level.block.state.properties.ChestType.SINGLE)
            return List.of(be);
        BlockPos otherPos = be.getBlockPos().relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(state));
        LevelChunk otherChunk = level.getChunkSource().getChunkNow(otherPos.getX() >> 4, otherPos.getZ() >> 4);
        if (otherChunk == null || !level.shouldTickBlocksAt(new ChunkPos(otherPos).toLong())) return List.of(be);
        BlockEntity other = otherChunk.getBlockEntities().get(otherPos);
        if (!(other instanceof net.minecraft.world.level.block.entity.ChestBlockEntity)
                || !other.getBlockState().is(state.getBlock())
                || other.getBlockState().getValue(net.minecraft.world.level.block.ChestBlock.FACING) != state.getValue(net.minecraft.world.level.block.ChestBlock.FACING)
                || other.getBlockState().getValue(net.minecraft.world.level.block.ChestBlock.TYPE) == state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE)
                || other.getBlockState().getValue(net.minecraft.world.level.block.ChestBlock.TYPE) == net.minecraft.world.level.block.state.properties.ChestType.SINGLE
                || !otherPos.relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(other.getBlockState())).equals(be.getBlockPos())) return List.of(be);
        return be.getBlockPos().compareTo(otherPos) < 0 ? List.of(be, other) : List.of(other, be);
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
            // Chunk unload removes timestamps; no full-map pruning on each scan.
            return true;
        }
    }

    /** Первый скан запускает таймер, следующие воспроизводят 5% сближения за 20 с. */
    private static long elapsedFloorBlockScanTicks(ServerLevel level, long chunkKey, long nowTick) {
        synchronized (LAST_FLOOR_BLOCK_SCAN) {
            Map<Long, Long> byChunk = LAST_FLOOR_BLOCK_SCAN.computeIfAbsent(level, ignored -> new HashMap<>());
            Long last = byChunk.put(chunkKey, nowTick);
            // Chunk unload removes timestamps; no full-map pruning on each scan.
            if (last == null || nowTick <= last) return 0L;
            return Math.min(nowTick - last, CHUNK_MAINT_PERIOD_TICKS);
        }
    }

    /** Physical entity clusters; infinite block items keep their placed-source path. */
    private static FloorScan scanDroppedItemsInto(ServerLevel level, long chunkKey,
                                                  double contamination, long nowTick) {
        int cx = ChunkPos.getX(chunkKey);
        int cz = ChunkPos.getZ(chunkKey);
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                cx << 4, level.getMinY(), cz << 4,
                (cx << 4) + 16, level.getMinY() + level.getHeight(), (cz << 4) + 16);
        double[] totals = new double[3]; // [ordinary transfer, block target, strongest ordinary target]
        double incoming = ChunkRadiationData.get(level).value(level, chunkKey);
        for (net.minecraft.world.entity.Entity entity : level.getEntities((net.minecraft.world.entity.Entity)null, box,
                e -> e.isAlive() && e.chunkPosition().toLong() == chunkKey
                        && !(e instanceof net.minecraft.world.entity.player.Player)
                        && (e instanceof net.minecraft.world.entity.item.ItemEntity
                        || e instanceof net.minecraft.world.entity.decoration.ItemFrame
                        || e instanceof net.minecraft.world.entity.LivingEntity || e instanceof Container))) {
            List<ItemStack> roots = RadiationOwners.slots(entity);
            RadiationCluster.Summary result = stepCluster(level, entity.blockPosition(), roots, incoming,
                    RadiationCluster.Policy.NORMAL, nowTick);
            if (entity instanceof net.minecraft.world.entity.item.ItemEntity drop) {
                classifyFloorStack(level, entity.blockPosition(), "предмет на земле", drop.getItem(), contamination, totals);
            } else if (entity instanceof net.minecraft.world.entity.decoration.ItemFrame frame) {
                classifyFloorStack(level, entity.blockPosition(), "предмет в рамке", frame.getItem(), contamination, totals);
            } else {
                if (result.chunkTarget() > contamination) {
                    double factor = Containment.factor(level, entity.blockPosition());
                    totals[0] += transferTowardChunk(contamination, result.chunkTarget(), CHEST_TO_CHUNK_RATE * factor);
                    if (factor > 0) totals[2] = Math.max(totals[2], result.chunkTarget());
                }
            }
        }
        return new FloorScan(totals[0], totals[1], totals[2]);
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
        double emission = RadiationCluster.inspect(List.of(stack)).chunkTarget();
        if (emission <= contamination) return;
        double contourFactor = Containment.factor(level, pos);
        double transfer = transferTowardChunk(
                contamination, emission, CHEST_TO_CHUNK_RATE * contourFactor);
        out[0] += transfer;
        if (contourFactor > 0) out[2] = Math.max(out[2], emission);
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
            LEDGER.remove(player.getUUID());
            LEDGER_CLIENT.remove(player.getUUID());
            LAST_PASS_LOG_NS.remove(player.getUUID());
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

    private static final Map<ServerLevel, List<java.util.Set<Long>>> ACTIVE_CHUNKS = new WeakHashMap<>();

    @SubscribeEvent
    public static void onChunkLoad(net.neoforged.neoforge.event.level.ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk)) return;
        var buckets = ACTIVE_CHUNKS.computeIfAbsent(level, ignored -> {
            List<java.util.Set<Long>> out = new java.util.ArrayList<>();
            for (int i=0;i<PLAYER_PERIOD_TICKS;i++) out.add(new java.util.HashSet<>());
            return out;
        });
        long key = event.getChunk().getPos().toLong();
        buckets.get(Math.floorMod(Long.hashCode(key), PLAYER_PERIOD_TICKS)).add(key);
    }

    @SubscribeEvent
    public static void onChunkUnload(net.neoforged.neoforge.event.level.ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        long key = event.getChunk().getPos().toLong();
        var buckets = ACTIVE_CHUNKS.get(level);
        if (buckets != null) buckets.get(Math.floorMod(Long.hashCode(key), PLAYER_PERIOD_TICKS)).remove(key);
        var scans = LAST_CONTENT_SCAN.get(level); if (scans != null) scans.remove(key);
        var floor = LAST_FLOOR_BLOCK_SCAN.get(level); if (floor != null) floor.remove(key);
    }

    // ═══════════════════════ 20 сек: чанки ═══════════════════════

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        RadiationTrace.tick(event.getServer());
        for (ServerLevel level : event.getServer().getAllLevels()) {
            var buckets = ACTIVE_CHUNKS.get(level);
            if (buckets == null) continue;
            int phase = Math.floorMod(level.getGameTime(), PLAYER_PERIOD_TICKS);
            for (long key : List.copyOf(buckets.get(phase))) {
                if (level.shouldTickBlocksAt(key)) scanContainersInto(level, key, ChunkRadiationData.get(level), level.getGameTime());
            }
        }
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
        ACTIVE_CHUNKS.clear();
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
