package com.gonzotech.radiation;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
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
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * Динамическая радиоактивность чанков — SavedData <b>по измерению</b>
 * (п.4 спеки автора 20.09, правки 21.09). Три слоя значений (nZt/с):
 * <ul>
 *   <li><b>baseline</b> — природный фон чанка, вычисляется ЛЕНИВО один раз и
 *       кэшируется: обычный чанк 1–10nZt; биом {@code gonzotech:desolation}
 *       1–5mZt (природный фон биома, не «заражение» — не вымывается);</li>
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

    /** Затухание заражения за обслуживание (применяется ВСЕГДА, автор 21.09). */
    private static final double DECAY = 0.99;

    /** Ключ биома «Пустошь» (data/gonzotech/worldgen/biome/desolation.json). */
    private static final ResourceLocation DESOLATION =
            ResourceLocation.fromNamespaceAndPath("gonzotech", "desolation");

    /** Максимум позиций радио-блоков, реально проверяемых контуром за проход (остальные считаются открытыми). */
    private static final int MAX_PROBES_PER_CHUNK = 16;

    /** baseline-кэш: chunkKey → природный фон (nZt/с). -1 = ещё не вычисляли. */
    private final Long2DoubleOpenHashMap baseline = new Long2DoubleOpenHashMap();
    /** Заражение поверх baseline: chunkKey → nZt/с (только «горячие» чанки). */
    private final Long2DoubleOpenHashMap contamination = new Long2DoubleOpenHashMap();
    /** Эмиссия поставленных радио-блоков по чанкам (для вычитания при сломе). */
    private final Long2DoubleOpenHashMap placed = new Long2DoubleOpenHashMap();
    /** Позиции поставленных радио-блоков по чанкам (для экрана контура). */
    private final Long2ObjectOpenHashMap<LongOpenHashSet> placedPos = new Long2ObjectOpenHashMap<>();

    public ChunkRadiationData() {
        baseline.defaultReturnValue(-1.0);
        contamination.defaultReturnValue(0.0);
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
    public void purgeAround(ChunkPos center, int radius) {
        for (int cx = center.x - radius; cx <= center.x + radius; cx++) {
            for (int cz = center.z - radius; cz <= center.z + radius; cz++) {
                long key = ChunkPos.asLong(cx, cz);
                baseline.put(key, 0.0);
                contamination.remove(key);
                placed.remove(key);
                placedPos.remove(key);
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
                for (int y = minY; y < maxY; y++) {
                    pos.set(x, y, z);
                    var state = level.getBlockState(pos);
                    double emission = RadSources.blockEmission(
                            BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath());
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
    public void addContamination(long chunkKey, double deltaNzt) {
        double next = Math.max(0.0, contamination.get(chunkKey) + deltaNzt);
        contamination.put(chunkKey, next);
        setDirty();
    }

    /** Read-only nearby source snapshot for the instrument visualizer. */
    public List<VisualSource> visualSources(ServerLevel level, BlockPos center, int radius) {
        List<VisualSource> out = new ArrayList<>();
        double max = radius * radius;
        for (var entry : placedPos.long2ObjectEntrySet()) {
            for (long raw : entry.getValue()) {
                BlockPos pos = BlockPos.of(raw);
                if (pos.distToCenterSqr(center.getX(), center.getY(), center.getZ()) > max) continue;
                double emission = RadSources.blockEmission(
                        BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath());
                if (emission > 0.0) out.add(new VisualSource(pos, emission));
            }
        }
        int minChunkX = (center.getX() - radius) >> 4, maxChunkX = (center.getX() + radius) >> 4;
        int minChunkZ = (center.getZ() - radius) >> 4, maxChunkZ = (center.getZ() + radius) >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
            LevelChunk chunk = level.getChunk(cx, cz);
            for (var be : chunk.getBlockEntities().values()) {
                if (!(be instanceof Container container)) continue;
                BlockPos pos = be.getBlockPos();
                if (pos.distToCenterSqr(center.getX(), center.getY(), center.getZ()) > max) continue;
                double emission = 0.0;
                for (int i = 0; i < container.getContainerSize(); i++) emission += RadSources.emissionOfStack(container.getItem(i));
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
        }
        return v;
    }

    // ───────────────────────── поставленные блоки ─────────────────────────

    /** Поставлен радио-блок (BlockEvent.EntityPlaceEvent): учитываем позицию и вклад в чанк. Живой помпы НЕТ — питание тянет чанк в {@link #maintain}. */
    public void onBlockPlaced(BlockPos pos, double emissionNzt) {
        long key = new ChunkPos(pos).toLong();
        placed.put(key, placed.get(key) + emissionNzt);
        placedPos.computeIfAbsent(key, k -> new LongOpenHashSet()).add(pos.asLong());
        Containment.invalidate(pos);
        setDirty();
    }

    /** Сломан радио-блок (BlockEvent.BreakEvent): вычитаем вклад; затухание догонит остаток (взрывы/поршни — апроксимация). */
    public void onBlockRemoved(BlockPos pos, double emissionNzt) {
        long key = new ChunkPos(pos).toLong();
        placed.put(key, Math.max(0.0, placed.get(key) - emissionNzt));
        LongOpenHashSet set = placedPos.get(key);
        if (set != null) {
            set.remove(pos.asLong());
        }
        Containment.invalidate(pos);
        setDirty();
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
            double target = emitted * screenFactor(level, key);
            double c = contamination.get(key);
            c = Math.max(0.0, c + (target - c) * PLACED_CONVERGE);
            contamination.put(key, c);
        }

        // 2) Затухание: ВСЕГДА (автор 21.09: «за долгую AFK сессию чанк может
        //    полностью очиститься») — запись вычищается, когда питания нет и фон стёрся.
        for (var it = contamination.long2DoubleEntrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            double v = e.getDoubleValue() * DECAY;
            if (v < 0.1 * MUST_MAINTAIN && placed.get(e.getLongKey()) <= 0.0) {
                it.remove();
            } else {
                e.setValue(v);
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
        for (var entry : placedPos.long2ObjectEntrySet()) {
            for (long raw : entry.getValue()) {
                BlockPos pos = BlockPos.of(raw);
                double expected = RadSources.blockEmission(
                        BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath());
                if (expected > 0.0) {
                    known.add(new SourceRecord(pos, expected));
                    continue;
                }
                BlockPos moved = findNearbySource(level, pos);
                if (moved != null) {
                    double movedEmission = RadSources.blockEmission(BuiltInRegistries.BLOCK.getKey(
                            level.getBlockState(moved).getBlock()).getPath());
                    known.add(new SourceRecord(moved, movedEmission));
                }
            }
        }

        placedPos.clear();
        placed.clear();
        for (SourceRecord source : known) {
            long key = new ChunkPos(source.pos()).toLong();
            placedPos.computeIfAbsent(key, ignored -> new LongOpenHashSet()).add(source.pos().asLong());
            placed.put(key, placed.get(key) + source.emission());
        }
        if (!known.isEmpty() || !placed.isEmpty()) setDirty();
    }

    private record SourceRecord(BlockPos pos, double emission) {}

    /** Vanilla pistons can move a block up to twelve positions. */
    private BlockPos findNearbySource(ServerLevel level, BlockPos origin) {
        for (int dx = -12; dx <= 12; dx++) {
            for (int dy = -12; dy <= 12; dy++) {
                for (int dz = -12; dz <= 12; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos candidate = origin.offset(dx, dy, dz);
                    if (!level.hasChunkAt(candidate)) continue;
                    double actual = RadSources.blockEmission(BuiltInRegistries.BLOCK.getKey(
                            level.getBlockState(candidate).getBlock()).getPath());
                    if (Math.abs(actual - emission) < 1.0e-9) return candidate;
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
        double weighted = 0.0, weight = 0.0;
        int probed = 0;
        List<Long> stale = null;
        for (long packed : set) {
            BlockPos pos = BlockPos.of(packed);
            double e = RadSources.blockEmission(
                    BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath());
            if (e <= 0.0) {
                // Блок исчез в обход событий (взрыв/поршень/setblock) — забываем позицию.
                (stale == null ? stale = new ArrayList<>() : stale).add(packed);
                continue;
            }
            double f = probed < MAX_PROBES_PER_CHUNK ? Containment.factor(level, pos) : 1.0;
            probed++;
            weighted += e * f;
            weight += e;
        }
        if (stale != null) {
            stale.forEach(set::remove);
            setDirty();
        }
        return weight <= 0.0 ? 1.0 : weighted / weight;
    }

    // ───────────────────────── baseline ─────────────────────────

    /**
     * Природный фон чанка: «Пустошь» 1–5mZt (перекрывает всё) или обычный 1–10nZt.
     * УРАНОВАЯ РУДА отдельно (автор 21.09): «не подпитывает чанк постоянно, а
     * ОДИН РАЗ даёт при генерации» — находим её в этом же скане и сыплем
     * 10–80nZt в contamination; выполняется только при первом обращении.
     */
    private double computeBaseline(ServerLevel level, long chunkKey) {
        ChunkPos cp = new ChunkPos(chunkKey);
        RandomSource rnd = RandomSource.create(level.getSeed() ^ chunkKey * 0x9E3779B97F4A7C15L);

        BlockPos center = cp.getMiddleBlockPosition(64);
        boolean desolation = level.getBiome(center).unwrapKey()
                .map(k -> k.location().equals(DESOLATION)).orElse(false);
        if (desolation) {
            return (1.0 + rnd.nextDouble() * 4.0) * RadUnits.MILLI;
        }

        // Разовый скан руды (ишем заодно с первой инициализацией baseline).
        int ores = 0;
        ChunkAccess chunk = level.getChunk(cp.x, cp.z);
        int minY = level.getMinY(), maxY = level.getMaxY();
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        int baseX = cp.getMinBlockX(), baseZ = cp.getMinBlockZ();
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
            contamination.addTo(chunkKey, Math.min(80.0, 10.0 + ores * 5.0)); // 10–80 nZt один раз
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
        // подпитка»). Теперь baseline — только природа (обычные ≤10, «Пустошь»
        // пересчитается по биому сама). Старые записи >10nZt просто забываем:
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
