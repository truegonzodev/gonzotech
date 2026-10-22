package com.gonzotech.radiation;

import com.gonzotech.core.registry.ModBlocks;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * Динамическая радиоактивность чанков — SavedData <b>по измерению</b>
 * (п.4 спеки автора 20.09, правки 21.09). Три слоя значений (nZt/с):
 * <ul>
 *   <li><b>baseline</b> — природный фон чанка, вычисляется ЛЕНИВО один раз и
 *       кэшируется: обычный чанк 1–10nZt; биом {@code gonzotech:desolation}
 *       1–5mZt; чанк с бочками отходов 1–10mZt (не «заражение» — не вымывается);</li>
 *   <li><b>contamination</b> — динамическое заражение поверх baseline.
 *       <b>Урановая руда больше НЕ является baseline</b> (автор 21.09): при
 *       первом обращении к чанку (≈генерация для игрока) руда ОДИН РАЗ сыплет
 *       10–80nZt в contamination — дальше только обычное затухание, чанк может
 *       полностью отмыться за долгую AFK-сессию;</li>
 *   <li><b>placed(+positions)</b> — поставленные радио-блоки (uranium_block и
 *       т.п.): эмиссия по чанку + координаты (для проверки свинцового/вольфрамового
 *       контура через {@link Containment}).</li>
 * </ul>
 * Храним ТОЛЬКО «интересные» чанки (baseline-кэш + заражённые + учёты блоков).
 *
 * <p>Обслуживание (20 с): поставленные блоки ЛОГИСТИЧЕСКИ подтягивают заражение
 * к своей эмиссии × экран контура (ближе к уровню источника — медленнее), затем
 * ЛЮБОЙ чанк теряет 1% фона (даже питаемый — стационар чуть ниже источника),
 * затем диффузия к равновесию с соседями.</p>
 */
public class ChunkRadiationData extends SavedData {

    private static final String DATA_NAME = "gonzotech_chunk_radiation";

    /** Под этим значением заражения чанк «чистый»: запись вычитается затуханием и удаляется (если нет placed-блоков). */
    private static final double MUST_MAINTAIN = 50.0; // nZt/с

    /** Скорость логистического сближения с уровнем поставленных блоков за проход обслуживания. */
    private static final double PLACED_CONVERGE = 0.05;
    /** Шаг обслуживания поставленных радиоактивных блоков — 20 секунд. */
    public static final int PLACED_UPDATE_PERIOD_TICKS = 20 * 20;

    /** Затухание заражения за обслуживание (применяется ВСЕГДА, автор 21.09). */
    private static final double DECAY = 0.99;

    /** Ключ биома «Пустошь» (data/gonzotech/worldgen/biome/desolation.json). */
    private static final ResourceLocation DESOLATION =
            ResourceLocation.fromNamespaceAndPath("gonzotech", "desolation");

    /** Максимум новых обходов контура за проход чанка; источники в уже закэшированной полости лимит не тратят. */
    private static final int MAX_PROBES_PER_CHUNK = 16;

    /** baseline-кэш: chunkKey → природный фон (nZt/с). -1 = ещё не вычисляли. */
    private final Long2DoubleOpenHashMap baseline = new Long2DoubleOpenHashMap();
    /** Заражение поверх baseline: chunkKey → nZt/с (только «горячие» чанки). */
    private final Long2DoubleOpenHashMap contamination = new Long2DoubleOpenHashMap();
    /** Эмиссия поставленных радио-блоков по чанкам (для вычитания при сломе). */
    private final Long2DoubleOpenHashMap placed = new Long2DoubleOpenHashMap();
    /** Позиции поставленных радио-блоков по чанкам (для экрана контура). */
    private final Long2ObjectOpenHashMap<LongOpenHashSet> placedPos = new Long2ObjectOpenHashMap<>();
    /** (0.3.269 #25) Последняя подтверждённая эмиссия источника (posKey→nZt/с):
     *  выгруженный чанк не «забывает» индекс и не грузится ради чтения. */
    private final Long2DoubleOpenHashMap placedEmission = new Long2DoubleOpenHashMap();
    /** (0.3.269 #26) Замороженный фактор контура на источник (posKey→factor). */
    private final Long2DoubleOpenHashMap screenFactorCache = new Long2DoubleOpenHashMap();
    /** (0.3.269 #26) Каретка ротации бюджета проб на чанк. */
    private final Long2IntOpenHashMap screenProbeCursor = new Long2IntOpenHashMap();

    public ChunkRadiationData() {
        baseline.defaultReturnValue(-1.0);
        contamination.defaultReturnValue(0.0);
        screenFactorCache.defaultReturnValue(-1.0);
        placed.defaultReturnValue(0.0);
    }

    public record VisualSource(BlockPos pos, double emission) {}

    // ───────────────────────── доступ ─────────────────────────

    public static ChunkRadiationData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(ChunkRadiationData::new,
                        (tag, provider) -> load(tag), null),
                DATA_NAME);
    }

    /** Текущий фон чанка: природный baseline (инициализируется лениво) + заражение. */
    public double value(ServerLevel level, long chunkKey) {
        return baselineOf(level, chunkKey) + contamination.get(chunkKey);
    }

    /**
     * Полностью обнулить радиационные данные в квадрате чанков вокруг центра.
     * baseline фиксируется в нуле, чтобы ленивый природный фон не создался заново
     * при следующем запросе дозиметра.
     */
    public void purgeAround(ServerLevel level, ChunkPos center, int radius) {
        for (int cx = center.x - radius; cx <= center.x + radius; cx++) {
            for (int cz = center.z - radius; cz <= center.z + radius; cz++) {
                long key = ChunkPos.asLong(cx, cz);
                double oldBaseline = Math.max(0.0, baseline.get(key));
                double oldContamination = contamination.get(key);
                double oldPlaced = placed.get(key);
                int oldSources = placedPos.containsKey(key) ? placedPos.get(key).size() : 0;
                baseline.put(key, 0.0);
                contamination.remove(key);
                placed.remove(key);
                placedPos.remove(key);
                if (oldBaseline != 0.0 || oldContamination != 0.0 || oldPlaced != 0.0 || oldSources > 0) {
                    RadiationTrace.logChunk(level, cx, cz, "ЧАНК → ОЧИЩЕНИЕ",
                            "purge: чанк [" + cx + "," + cz + "]; baseline=" + RadiationTrace.rate(oldBaseline)
                                    + "; contamination=" + RadiationTrace.rate(oldContamination)
                                    + "; placed=" + RadiationTrace.rate(oldPlaced) + "; источников=" + oldSources);
                }
            }
        }
        setDirty();
    }

    /** Actual radioactive blocks currently present in one chunk, for diagnostics. */
    public List<VisualSource> sourcesInChunk(ServerLevel level, long chunkKey) {
        List<VisualSource> out = new ArrayList<>();
        ChunkPos cp = new ChunkPos(chunkKey);
        int minY = level.getMinY();
        int maxY = level.getMaxY();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = cp.getMinBlockX(); x < cp.getMinBlockX() + 16; x++) {
            for (int z = cp.getMinBlockZ(); z < cp.getMinBlockZ() + 16; z++) {
                // (0.3.269 #25) Диагностика не права выгружать чанки.
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                for (int y = minY; y < maxY; y++) {
                    pos.set(x, y, z);
                    var state = level.getBlockState(pos);
                    double emission = RadSources.blockEmission(state);
                    if (emission > 0.0) out.add(new VisualSource(pos.immutable(), emission));
                }
            }
        }
        out.sort((a, b) -> Double.compare(b.emission(), a.emission()));
        return out;
    }

    /** Только динамическое заражение (без baseline) — для мягких потолков помп. */
    public double contaminationOf(long chunkKey) {
        return contamination.get(chunkKey);
    }

    /** Прибавить заражение (дельта может быть отрицательной; клэмп снизу в 0). */
    public void addContamination(ServerLevel level, long chunkKey, double deltaNzt, String cause) {
        double before = contamination.get(chunkKey);
        double next = Math.max(0.0, before + deltaNzt);
        contamination.put(chunkKey, next);
        setDirty();
        double applied = next - before;
        if (applied != 0.0) {
            int chunkX = ChunkPos.getX(chunkKey);
            int chunkZ = ChunkPos.getZ(chunkKey);
            RadiationTrace.logChunk(level, chunkX, chunkZ, "ЗАРАЖЕНИЕ ЧАНКА",
                    "чанк [" + chunkX + "," + chunkZ + "]; причина=" + cause
                            + "; requested=" + RadiationTrace.rate(deltaNzt)
                            + "; actual=" + RadiationTrace.rate(applied)
                            + "; contamination=" + RadiationTrace.rate(before)
                            + "→" + RadiationTrace.rate(next));
        }
    }

    /**
     * Питать радиоактивный BlockItem на полу/в рамке с тем же сближением 5% за 20 с,
     * что и зарегистрированный поставленный блок. Цель уже умножена на фактор контура
     * в позиции сущности.
     */
    public void convergePlacedLikeEntitySource(ServerLevel level, long chunkKey,
                                               double targetNzt, long elapsedTicks) {
        if (targetNzt <= 0.0D || elapsedTicks <= 0L) return;
        long boundedTicks = Math.min(elapsedTicks, PLACED_UPDATE_PERIOD_TICKS);
        double rate = 1.0D - Math.pow(1.0D - PLACED_CONVERGE,
                boundedTicks / (double) PLACED_UPDATE_PERIOD_TICKS);
        double before = contamination.get(chunkKey);
        double transfer = (targetNzt - before) * rate;
        if (transfer != 0.0D) {
            addContamination(level, chunkKey, transfer,
                    "радиоактивный BlockItem на полу/в рамке → модель поставленного блока");
        }
    }

    /** Emit an immediate contour-to-chunk diagnostic for placed sources in the command's 3×3 area. */
    public void tracePlacedSources(ServerLevel level, ChunkPos center, int radius) {
        for (var entry : placedPos.long2ObjectEntrySet()) {
            long key = entry.getLongKey();
            int chunkX = ChunkPos.getX(key);
            int chunkZ = ChunkPos.getZ(key);
            if (Math.abs((long) chunkX - center.x) > radius
                    || Math.abs((long) chunkZ - center.z) > radius) continue;
            screenFactor(level, key);
        }
    }

    /** Read-only nearby source snapshot for the instrument visualizer. */
    public List<VisualSource> visualSources(ServerLevel level, BlockPos center, int radius) {
        List<VisualSource> out = new ArrayList<>();
        double max = radius * radius;
        for (var entry : placedPos.long2ObjectEntrySet()) {
            for (long raw : entry.getValue()) {
                BlockPos pos = BlockPos.of(raw);
                if (pos.distToCenterSqr(center.getX(), center.getY(), center.getZ()) > max) continue;
                // (0.3.269 #25) картинка не грузит мир: чанк выгружен — last known.
                double emission = level.hasChunkAt(pos)
                    ? RadSources.blockEmission(level.getBlockState(pos))
                    : placedEmission.get(raw);
                if (emission > 0.0) out.add(new VisualSource(pos, emission));
            }
        }
        int minChunkX = (center.getX() - radius) >> 4, maxChunkX = (center.getX() + radius) >> 4;
        int minChunkZ = (center.getZ() - radius) >> 4, maxChunkZ = (center.getZ() + radius) >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
            LevelChunk chunk = level.getChunkNow(new ChunkPos(cx, cz));
            if (chunk == null) continue; // #25: визуал не принудительная загрузка
            for (var be : chunk.getBlockEntities().values()) {
                if (!(be instanceof Container container)) continue;
                BlockPos pos = be.getBlockPos();
                if (pos.distToCenterSqr(center.getX(), center.getY(), center.getZ()) > max) continue;
                double emission = 0.0;
                for (int i = 0; i < container.getContainerSize(); i++) emission += RadSources.emissionDeep(container.getItem(i));
                // 0.3.111: свинцовый ящик — партиклы/визуал от содержимого на 80% слабее
                if (be.getBlockState().getBlock() instanceof com.gonzotech.machines.block.ThirdLeadChestBlock) {
                    emission *= com.gonzotech.machines.block.ThirdLeadChestBlock.CONTENT_CHUNK_FACTOR;
                }
                if (emission > 0.0) out.add(new VisualSource(pos, emission));
            }
        }
        out.sort((a, b) -> Double.compare(b.emission(), a.emission()));
        return out.size() <= 64 ? out : new ArrayList<>(out.subList(0, 64));
    }

    public double baselineOf(ServerLevel level, long chunkKey) {
        double v = baseline.get(chunkKey);
        if (v < 0.0) {
            v = computeBaseline(level, chunkKey);
            baseline.put(chunkKey, v);
            setDirty();
            RadiationTrace.logChunk(level, ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey), "ПРИРОДНЫЙ ФОН ЧАНКА",
                    "чанк [" + ChunkPos.getX(chunkKey) + "," + ChunkPos.getZ(chunkKey)
                            + "]; baseline=" + RadiationTrace.rate(v));
        }
        return v;
    }

    // ───────────────────────── поставленные блоки ─────────────────────────

    /** Поставлен радио-блок: индекс позиции идемпотентен для bucket-хуков и BlockEvent вместе. */
    public void onBlockPlaced(ServerLevel level, BlockPos pos, double emissionNzt) {
        long key = new ChunkPos(pos).toLong();
        LongOpenHashSet set = placedPos.computeIfAbsent(key, k -> new LongOpenHashSet());
        if (!set.add(pos.asLong())) return;
        double before = placed.get(key);
        double next = before + emissionNzt;
        placed.put(key, next);
        setDirty();
        RadiationTrace.log(level, pos, "РАДИОАКТИВНЫЙ БЛОК → ИНДЕКС ЧАНКА",
                "эмиссия=" + RadiationTrace.rate(emissionNzt)
                        + "; placed=" + RadiationTrace.rate(before) + "→" + RadiationTrace.rate(next));
    }

    /** Сломан радио-блок: вычитаем вклад только если позиция была учтена. */
    public void onBlockRemoved(ServerLevel level, BlockPos pos, double emissionNzt) {
        long key = new ChunkPos(pos).toLong();
        LongOpenHashSet set = placedPos.get(key);
        if (set == null || !set.remove(pos.asLong())) return;
        double before = placed.get(key);
        double next = Math.max(0.0, before - emissionNzt);
        placed.put(key, next);
        setDirty();
        RadiationTrace.log(level, pos, "ИНДЕКС ЧАНКА → УДАЛЕНИЕ ИСТОЧНИКА",
                "эмиссия=" + RadiationTrace.rate(emissionNzt)
                        + "; placed=" + RadiationTrace.rate(before) + "→" + RadiationTrace.rate(next));
    }

    // ───────────────────────── обслуживание (раз в 20 с) ─────────────────────────

    /**
     * Один проход обслуживания:
     * <ol>
     *   <li>поставленные блоки тянут заражение к своей эмиссии × экран контура
     *       (логистика «чем ближе к источнику, тем медленнее», автор 21.09);</li>
     *   <li>ЛЮБОЙ чанк теряет 1% заражения (если в чанке чисто — на самом деле
     *       примерно то же; с подпиткой — стационар чуть ниже источника);</li>
     *   <li>диффузия: попарный обмен с 8 соседями (консервативно, сумма не меняется).</li>
     * </ol>
     */
    public void maintain(ServerLevel level) {
        // Before feeding contamination, reconcile the registry with the world.
        // A piston can move a source without firing the player break/place path;
        // a bounded local search preserves that source at its new position.
        reconcilePlacedSources(level);

        // 1) Питание от поставленных блоков.
        for (var pe : placed.long2DoubleEntrySet()) {
            double emitted = pe.getDoubleValue();
            if (emitted <= 0.0) {
                continue;
            }
            long key = pe.getLongKey();
            double screen = screenFactor(level, key);
            double target = emitted * screen;
            double before = contamination.get(key);
            double c = Math.max(0.0, before + (target - before) * PLACED_CONVERGE);
            contamination.put(key, c);
            int chunkX = ChunkPos.getX(key);
            int chunkZ = ChunkPos.getZ(key);
            if (c != before && RadiationTrace.isActiveChunk(level, chunkX, chunkZ)) {
                LongOpenHashSet sources = placedPos.get(key);
                RadiationTrace.logChunk(level, chunkX, chunkZ, "ИСТОЧНИКИ БЛОКОВ → ЗАРАЖЕНИЕ ЧАНКА",
                        "чанк [" + chunkX + "," + chunkZ + "]; источников="
                                + (sources == null ? 0 : sources.size())
                                + "; emissions=" + RadiationTrace.rate(emitted)
                                + "; contourFactor=" + RadiationTrace.number(screen)
                                + "; target=" + RadiationTrace.rate(target)
                                + "; contamination=" + RadiationTrace.rate(before)
                                + "→" + RadiationTrace.rate(c));
            }
        }

        // 2) Затухание: ВСЕГДА (автор 21.09: «за долгую AFK сессию чанк может
        //    полностью очиститься») — запись вычищается, когда питания нет и фон стёрся.
        for (var it = contamination.long2DoubleEntrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            long key = e.getLongKey();
            double before = e.getDoubleValue();
            double v = before * DECAY;
            boolean removed = v < 0.1 * MUST_MAINTAIN && placed.get(key) <= 0.0;
            if (removed) {
                it.remove();
                v = 0.0;
            } else {
                e.setValue(v);
            }
            int chunkX = ChunkPos.getX(key);
            int chunkZ = ChunkPos.getZ(key);
            if (v != before && RadiationTrace.isActiveChunk(level, chunkX, chunkZ)) {
                RadiationTrace.logChunk(level, chunkX, chunkZ, "РАСПАД ЗАРАЖЕНИЯ ЧАНКА",
                        "чанк [" + chunkX + "," + chunkZ + "]; contamination="
                                + RadiationTrace.rate(before) + "→" + RadiationTrace.rate(v)
                                + (removed ? "; запись удалена после порога" : "; decay=" + RadiationTrace.number(DECAY)));
            }
        }

        // 3) Диффузия: нельзя «в одном чанке чисто, в соседнем миллиард» —
        //    попарный обмен с каждым из 8 соседей (×2.5% разницы за проход).
        Long2DoubleOpenHashMap snapshot = new Long2DoubleOpenHashMap(contamination);
        snapshot.defaultReturnValue(0.0);
        Long2DoubleOpenHashMap delta = new Long2DoubleOpenHashMap();
        delta.defaultReturnValue(0.0);
        for (var e : snapshot.long2DoubleEntrySet()) {
            long key = e.getLongKey();
            int x = ChunkPos.getX(key), z = ChunkPos.getZ(key);
            double self = e.getDoubleValue();
            if (self <= 0.0) {
                continue;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    long nk = ChunkPos.asLong(x + dx, z + dz);
                    double other = snapshot.get(nk);
                    double flow = (self - other) * 0.025;
                    if (flow > 0.0) { // только отдаём; «приём» посчитает сосед из своего снимка
                        delta.addTo(key, -flow);
                        delta.addTo(nk, flow);
                        if (RadiationTrace.isActiveChunkTransfer(level, x, z, x + dx, z + dz)) {
                            RadiationTrace.logChunkTransfer(level, x, z, x + dx, z + dz,
                                    "ДИФФУЗИЯ ЧАНК → ЧАНК",
                                    "[" + x + "," + z + "] → [" + (x + dx) + "," + (z + dz)
                                            + "]; transfer=" + RadiationTrace.rate(flow)
                                            + "; snapshot=" + RadiationTrace.rate(self)
                                            + "→" + RadiationTrace.rate(other));
                        }
                    }
                }
            }
        }
        delta.long2DoubleEntrySet().forEach(e -> {
            long key = e.getLongKey();
            double nv = contamination.get(key) + e.getDoubleValue();
            if (nv > 0.0) {
                contamination.put(key, nv);
            }
        });
        setDirty();
    }

    /**
     * Rebuild the placed-source index from its known coordinates. Missing sources
     * are removed; when a source disappeared from its old position, search only
     * the piston-sized neighbourhood for the same radioactive block and migrate
     * the registration if found. This never scans the world.
     */
    private void reconcilePlacedSources(ServerLevel level) {
        List<SourceRecord> known = new ArrayList<>();
        List<BlockPos> missing = new ArrayList<>();
        LongOpenHashSet claimed = new LongOpenHashSet();

        // Preserve every still-present source before looking for piston moves;
        // otherwise two stale positions could both claim the same nearby block.
        List<Long> heldFrozen = new ArrayList<>();
        for (var entry : placedPos.long2ObjectEntrySet()) {
            for (long raw : entry.getValue()) {
                BlockPos pos = BlockPos.of(raw);
                if (!level.hasChunkAt(pos)) {
                    // (0.3.269 #24/#25) UNKNOWN: чанк выгружен — это НЕ «пропал».
                    // Индекс держим, эмиссия — последнее известное (0 для легаси
                    // позиций без кэша — доживут до первой загрузки).
                    if (claimed.add(raw)) {
                        double last = placedEmission.get(raw);
                        if (last > 0.0) known.add(new SourceRecord(pos, last));
                        else heldFrozen.add(raw);
                    }
                    continue;
                }
                double expected = RadSources.blockEmission(level.getBlockState(pos));
                if (expected > 0.0) {
                    placedEmission.put(raw, expected);
                    if (claimed.add(raw)) known.add(new SourceRecord(pos, expected));
                } else {
                    missing.add(pos);
                }
            }
        }
        for (BlockPos origin : missing) {
            BlockPos moved = findNearbySource(level, origin, claimed);
            if (moved == null) {
                placedEmission.remove(origin.asLong());
                RadiationTrace.log(level, origin, "ИСТОЧНИК ПОКИНУЛ ПОЗИЦИЮ",
                        "радиоактивный блок больше не найден рядом; индекс будет удалён");
                continue;
            }
            double movedEmission = RadSources.blockEmission(level.getBlockState(moved));
            placedEmission.remove(origin.asLong());
            if (movedEmission > 0.0 && claimed.add(moved.asLong())) {
                placedEmission.put(moved.asLong(), movedEmission);
                known.add(new SourceRecord(moved, movedEmission));
                RadiationTrace.log(level, moved, "ПЕРЕНОС ИНДЕКСА ИСТОЧНИКА",
                        "старое положение=" + origin.getX() + "," + origin.getY() + "," + origin.getZ()
                                + "; новое положение=" + moved.getX() + "," + moved.getY() + "," + moved.getZ()
                                + "; emission=" + RadiationTrace.rate(movedEmission));
            }
        }

        placedPos.clear();
        placed.clear();
        for (SourceRecord source : known) {
            long key = new ChunkPos(source.pos()).toLong();
            placedPos.computeIfAbsent(key, ignored -> new LongOpenHashSet()).add(source.pos().asLong());
            placed.put(key, placed.get(key) + source.emission());
        }
        for (long raw : heldFrozen) {
            long key = new ChunkPos(BlockPos.of(raw)).toLong();
            placedPos.computeIfAbsent(key, ignored -> new LongOpenHashSet()).add(raw);
        }
        if (!known.isEmpty() || !placed.isEmpty() || !heldFrozen.isEmpty()) setDirty();
    }

    private record SourceRecord(BlockPos pos, double emission) {}

    /** Vanilla pistons can move a block up to twelve positions. */
    private BlockPos findNearbySource(ServerLevel level, BlockPos origin, LongOpenHashSet claimed) {
        for (int dx = -12; dx <= 12; dx++) {
            for (int dy = -12; dy <= 12; dy++) {
                for (int dz = -12; dz <= 12; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos candidate = origin.offset(dx, dy, dz);
                    if (!level.hasChunkAt(candidate) || claimed.contains(candidate.asLong())) continue;
                    double actual = RadSources.blockEmission(level.getBlockState(candidate));
                    if (actual > 0.0) return candidate;
                }
            }
        }
        return null;
    }

    /**
     * Средний (взвешенный по эмиссии блоков) фактор экранирования чанка:
     * у каждого поставленного радио-блока своя полость ({@link Containment}).
     * Без позиций (старые миры-снапшоты) — 1.0 (контур не учитываем, вреда нет).
     */
    private double screenFactor(ServerLevel level, long chunkKey) {
        LongOpenHashSet set = placedPos.get(chunkKey);
        if (set == null || set.isEmpty()) {
            return 1.0;
        }
        int chunkX = ChunkPos.getX(chunkKey);
        int chunkZ = ChunkPos.getZ(chunkKey);
        boolean tracingChunk = RadiationTrace.isActiveChunk(level, chunkX, chunkZ);
        double weighted = 0.0, weight = 0.0;
        int probed = 0;
        List<Long> stale = null;
        // (0.3.269 #26) Ротация: за проход бюджета 16 проб обслуживаются
        // СЛЕДУЮЩИЕ источники по каретке, а не первые — недообследованные не
        // голодают вечно. Источник вне бюджета получает замороженный прошлый
        // фактор (не занижаем и не завышаем защиту), никогда не меренный —
        // честную 1.0 до своей очереди.
        long[] cells = set.toLongArray();
        int n = cells.length;
        int cursor = n == 0 ? 0 : Math.floorMod(screenProbeCursor.getInt(chunkKey), n);
        screenProbeCursor.put(chunkKey, cursor + MAX_PROBES_PER_CHUNK);
        for (int i = 0; i < n; i++) {
            long packed = cells[Math.floorMod(cursor + i, n)];
            BlockPos pos = BlockPos.of(packed);
            var state = level.getBlockState(pos);
            double emission = RadSources.blockEmission(state);
            if (emission <= 0.0) {
                // Блок исчез в обход событий (взрыв/поршень/setblock) — забываем позицию.
                (stale == null ? stale = new ArrayList<>() : stale).add(packed);
                continue;
            }
            RadiationContour.Result contour = Containment.cachedResult(level, pos);
            if (contour == null && probed++ < MAX_PROBES_PER_CHUNK) {
                contour = Containment.result(level, pos);
                if (contour != null) screenFactorCache.put(packed, contour.factor());
            }
            double factor;
            if (contour != null) {
                factor = contour.factor();
            } else {
                double frozen = screenFactorCache.get(packed);
                factor = frozen < 0.0 ? 1.0 : frozen;
            }
            double contribution = emission * factor;
            weighted += contribution;
            weight += emission;

            if (tracingChunk) {
                String contourState = contour == null ? "not-probed"
                        : contour.enclosed() ? "closed" : "open/unsealed";
                RadiationTrace.logHighlighted(level, pos, "БЛОК В КОНТУРЕ → ЧАНК",
                        "block=" + BuiltInRegistries.BLOCK.getKey(state.getBlock())
                                + "; chunk=[" + chunkX + "," + chunkZ + "]"
                                + "; contour=" + contourState
                                + "; contourFactor=" + RadiationTrace.number(factor)
                                + "; blockEmission=" + RadiationTrace.rate(emission)
                                + "; sourceTargetContribution=" + RadiationTrace.rate(contribution)
                                + "; chunkContamination=" + RadiationTrace.rate(contamination.get(chunkKey)));
            }
        }
        if (stale != null) {
            for (long packed : stale) {
                set.remove(packed);
                screenFactorCache.remove(packed);
                placedEmission.remove(packed);
            }
            setDirty();
        }
        return weight <= 0.0 ? 1.0 : weighted / weight;
    }

    // ───────────────────────── baseline ─────────────────────────

    /**
     * Природный фон чанка: отходы 1–10mZt; иначе «Пустошь» 1–5mZt или
     * обычный чанк 1–10nZt. Урановая руда отдельно: находим её в том же
     * однократном скане и добавляем 10–80nZt в contamination.
     */
    private double computeBaseline(ServerLevel level, long chunkKey) {
        ChunkPos cp = new ChunkPos(chunkKey);
        RandomSource rnd = RandomSource.create(level.getSeed() ^ chunkKey * 0x9E3779B97F4A7C15L);

        BlockPos center = cp.getMiddleBlockPosition(64);
        boolean desolation = level.getBiome(center).unwrapKey()
                .map(k -> k.location().equals(DESOLATION)).orElse(false);
        ChunkAccess chunk = level.getChunk(cp.x, cp.z);
        int minY = level.getMinY(), maxY = level.getMaxY();
        int baseX = cp.getMinBlockX(), baseZ = cp.getMinBlockZ();
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();

        // Generated barrels are surface-only. Ignore leaves in this heightmap
        // so a barrel below a tree canopy is still found without a full scan.
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = baseX + x;
                int worldZ = baseZ + z;
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, worldX, worldZ);
                int fromY = Math.max(minY, surfaceY - 5);
                int toY = Math.min(maxY - 1, surfaceY + 1);
                for (int y = fromY; y <= toY; y++) {
                    mp.set(worldX, y, worldZ);
                    if (chunk.getBlockState(mp).is(ModBlocks.WASTE_BARREL.get())) {
                        return (1.0 + rnd.nextDouble() * 9.0) * RadUnits.MILLI;
                    }
                }
            }
        }
        if (desolation) {
            return (1.0 + rnd.nextDouble() * 4.0) * RadUnits.MILLI;
        }

        // Разовый скан урановой руды при первой инициализации baseline.
        int ores = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    mp.set(baseX + x, y, baseZ + z);
                    String path = BuiltInRegistries.BLOCK.getKey(chunk.getBlockState(mp).getBlock()).getPath();
                    if (path.contains("uranium") && path.endsWith("_ore")) {
                        ores++;
                    }
                }
            }
        }
        if (ores > 0) {
            double seed = Math.min(80.0, 10.0 + ores * 5.0); // 10–80 nZt один раз
            double before = contamination.get(chunkKey);
            contamination.addTo(chunkKey, seed);
            RadiationTrace.logChunk(level, cp.x, cp.z, "УРАНОВАЯ РУДА → ЗАРАЖЕНИЕ ЧАНКА",
                    "чанк [" + cp.x + "," + cp.z + "]; найдено руд=" + ores
                            + "; посев=" + RadiationTrace.rate(seed)
                            + "; contamination=" + RadiationTrace.rate(before)
                            + "→" + RadiationTrace.rate(contamination.get(chunkKey)));
        }
        return 1.0 + rnd.nextDouble() * 9.0; // природный фон обычного чанка
    }

    // ───────────────────────── персист ─────────────────────────

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag b = new CompoundTag(), c = new CompoundTag(), p = new CompoundTag(), q = new CompoundTag();
        baseline.long2DoubleEntrySet().forEach(e -> b.putDouble(Long.toString(e.getLongKey()), e.getDoubleValue()));
        contamination.long2DoubleEntrySet().forEach(e -> c.putDouble(Long.toString(e.getLongKey()), e.getDoubleValue()));
        placed.long2DoubleEntrySet().forEach(e -> p.putDouble(Long.toString(e.getLongKey()), e.getDoubleValue()));
        placedPos.long2ObjectEntrySet().forEach(e -> q.putLongArray(Long.toString(e.getLongKey()), e.getValue().toLongArray()));
        tag.put("b", b);
        tag.put("c", c);
        tag.put("p", p);
        tag.put("q", q);
        return tag;
    }

    private static ChunkRadiationData load(CompoundTag tag) {
        ChunkRadiationData d = new ChunkRadiationData();
        fill(d.baseline, tag.getCompound("b"));
        fill(d.contamination, tag.getCompound("c"));
        fill(d.placed, tag.getCompound("p"));
        CompoundTag q = tag.getCompound("q");
        for (String k : q.getAllKeys()) {
            try {
                d.placedPos.put(Long.parseLong(k), new LongOpenHashSet(q.getLongArray(k)));
            } catch (NumberFormatException ignored) {
            }
        }
        // Миграция v1 (автор 21.09): руда раньше жила в baseline (10–80, «вечная
        // подпитка»). Теперь baseline — только природный фон: обычные чанки ≤10,
        // «Пустошь»/отходы пересчитываются по биому и наличию бочек. Старые записи >10nZt забываем:
        // при следующем обращении чанк получит природный фон + РАЗОВЫЙ посев
        // руды в contamination (если руда жива), дальше — обычное затухание.
        var bit = d.baseline.long2DoubleEntrySet().iterator();
        while (bit.hasNext()) {
            if (bit.next().getDoubleValue() > 10.0) {
                bit.remove();
            }
        }
        return d;
    }

    private static void fill(Long2DoubleOpenHashMap map, CompoundTag tag) {
        for (String k : tag.getAllKeys()) {
            try {
                map.put(Long.parseLong(k), tag.getDouble(k));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    /** Сколько «горячих» чанков сейчас на учёте (диагностика в лог при отладке). */
    public int contaminatedCount() {
        return contamination.size();
    }
}
