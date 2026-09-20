package com.gonzotech.radiation;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Динамическая радиоактивность чанков — SavedData <b>по измерению</b>
 * (п.4 спеки автора 20.09). Три слоя значений (nZt/с):
 * <ul>
 *   <li><b>baseline</b> — природный фон чанка, вычисляется ЛЕНИВО один раз и
 *       кэшируется: обычный чанк 1–10nZt; с урановой рудой 10–80nZt
 *       (по числу рудных блоков); биом {@code gonzotech:desolation} 1–5mZt;</li>
 *   <li><b>contamination</b> — динамическое заражение поверх baseline
 *       (занесено игроком/сундуками/поставленными блоками);</li>
 *   <li><b>placed</b> — учёт поставленных радио-блоков (uranium_block и т.п.),
 *       чтобы уметь вычитать при их разрушении.</li>
 * </ul>
 * Храним ТОЛЬКО «интересные» чанки (baseline-кэш + заражённые).
 *
 * <p>Первое железное правило спеки: «в сундуке — ОК, в руки — ай-ай-ай» —
 * сундуки заражают чанк лишь чуть-чуть через периодический скан содержимого,
 * а инвентарь игрока бьёт по шкале напрямую (см. {@code RadiationSystem}).</p>
 */
public class ChunkRadiationData extends SavedData {

    private static final String DATA_NAME = "gonzotech_chunk_radiation";

    /** Под этим значением заражения чанк «чистый»: запись вычитается затуханием и удаляется. */
    private static final double MUST_MAINTAIN = 50.0; // nZt/с

    /** Ключ биома «Пустошь» (data/gonzotech/worldgen/biome/desolation.json). */
    private static final ResourceLocation DESOLATION =
            ResourceLocation.fromNamespaceAndPath("gonzotech", "desolation");

    /** baseline-кэш: chunkKey → природный фон (nZt/с). -1 = ещё не вычисляли. */
    private final Long2DoubleOpenHashMap baseline = new Long2DoubleOpenHashMap();
    /** Заражение поверх baseline: chunkKey → nZt/с (только «горячие» чанки). */
    private final Long2DoubleOpenHashMap contamination = new Long2DoubleOpenHashMap();
    /** Эмиссия поставленных радио-блоков по чанкам (для вычитания при сломе). */
    private final Long2DoubleOpenHashMap placed = new Long2DoubleOpenHashMap();

    public ChunkRadiationData() {
        baseline.defaultReturnValue(-1.0);
        contamination.defaultReturnValue(0.0);
        placed.defaultReturnValue(0.0);
    }

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

    /** Поставлен радио-блок (BlockEvent.EntityPlaceEvent): учитываем вклад в чанк. */
    public void onBlockPlaced(BlockPos pos, double emissionNzt) {
        long key = new ChunkPos(pos).toLong();
        placed.put(key, placed.get(key) + emissionNzt);
        setDirty();
    }

    /** Сломан радио-блок (BlockEvent.BreakEvent): вычитаем вклад (взрывы/поршни — апроксимация, см. README-примечание). */
    public void onBlockRemoved(BlockPos pos, double emissionNzt) {
        long key = new ChunkPos(pos).toLong();
        placed.put(key, Math.max(0.0, placed.get(key) - emissionNzt));
        setDirty();
    }

    // ───────────────────────── обслуживание (раз в 20 с) ─────────────────────────

    /**
     * Один проход обслуживания: затухание 1%, диффузия к равновесию с 8 соседями
     * (попарно и консервативно — суммарная радиация сохраняется). Работает
     * только по «горячим» записям (п.4: «надо подумать над оптимизацией»).
     */
    public void maintain(ServerLevel level) {
        // 1) Затухание: «если в чанке чисто — снижает фон на 1% каждые 20 сек».
        for (var it = contamination.long2DoubleEntrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            double v = e.getDoubleValue() * 0.99;
            if (v < 0.1 * MUST_MAINTAIN && placed.get(e.getLongKey()) <= 0.0) {
                it.remove();
            } else {
                e.setValue(v);
            }
        }

        // 2) Диффузия: нельзя «в одном чанке чисто, в соседнем миллиард» —
        //    попарный обмен с каждым из 8 соседей (×2.5% разницы за проход):
        //    горячий чанк отдаёт, холодный принимает, сумма не меняется.
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

    // ───────────────────────── baseline ─────────────────────────

    /** Природный фон чанка: дефолт / урановая руда / «Пустошь». */
    private static double computeBaseline(ServerLevel level, long chunkKey) {
        ChunkPos cp = new ChunkPos(chunkKey);
        RandomSource rnd = RandomSource.create(level.getSeed() ^ chunkKey * 0x9E3779B97F4A7C15L);

        // Биом «Пустошь»: 1–5 mZt (перекрывает руду/дефолт — автор п.6).
        BlockPos center = cp.getMiddleBlockPosition(64);
        boolean desolation = level.getBiome(center).unwrapKey()
                .map(k -> k.location().equals(DESOLATION)).orElse(false);
        if (desolation) {
            return (1.0 + rnd.nextDouble() * 4.0) * RadUnits.MILLI;
        }

        // Подсчёт урановой руды: разовый скан при ПЕРВОМ обращении к чанку
        // (инициализация случается только когда в чанке есть игрок, кэшируется навсегда).
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
            return Math.min(80.0, 10.0 + ores * 5.0);   // 10–80 nZt (автор п.6)
        }
        return 1.0 + rnd.nextDouble() * 9.0;           // 1–10 nZt (автор п.6)
    }

    // ───────────────────────── персист ─────────────────────────

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag b = new CompoundTag(), c = new CompoundTag(), p = new CompoundTag();
        baseline.long2DoubleEntrySet().forEach(e -> b.putDouble(Long.toString(e.getLongKey()), e.getDoubleValue()));
        contamination.long2DoubleEntrySet().forEach(e -> c.putDouble(Long.toString(e.getLongKey()), e.getDoubleValue()));
        placed.long2DoubleEntrySet().forEach(e -> p.putDouble(Long.toString(e.getLongKey()), e.getDoubleValue()));
        tag.put("b", b);
        tag.put("c", c);
        tag.put("p", p);
        return tag;
    }

    private static ChunkRadiationData load(CompoundTag tag) {
        ChunkRadiationData d = new ChunkRadiationData();
        fill(d.baseline, tag.getCompound("b"));
        fill(d.contamination, tag.getCompound("c"));
        fill(d.placed, tag.getCompound("p"));
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
