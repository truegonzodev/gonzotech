package com.gonzotech.machines.network;

import com.gonzotech.machines.steamgen.SteamGenStructure;
import com.gonzotech.machines.turbine.TurbineStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * СШИТЫЕ УЗЛЫ (0.3.98, проект автора «смежные узлы = единая структура»).
 * Смежные узлы ОДНОГО рода (тепло+тепло, провод+провод, уни+уни, предметы+
 * предметы, одного тира) образуют один кламп: маршрутизация не строит пути
 * внутри него — кламп агрегирует приём/выдачу по границам, внутри безлимитен
 * («перемещение ×N членов»), а транзит через него платит ПЛОСКУЮ потерю
 * сумме членов (потери × N) независимо от реального маршрута (нёрф автора:
 * 1×1×3 теплоузлов = 3 × 0.22 = 0.66 GTH; 5×5×5 = 125 × 0.22 = 27.5 GTH;
 * [исток][труба][кламп 1×1×3][труба][потребитель] = 0.22 + 0.66 + 0.22 = 1.1 GTH).
 *
 * <p><b>Инвалидация — по множеству членов, не по форме.</b> Поршень, двигающий
 * узел внутри того же множества, вызывает перефлуд компонента один раз
 * (O(N), микросекунды); пер-тик стоимость клампа от этого не меняется —
 * поршневой киллсвитч бесплатен. Матсимуляция: audit/node_clump_sim.py
 * (21x..18826x экономии против пер-тик BFS).</p>
 *
 * <p>Серверная static-карта: пишется только из onPlace/onRemove узлов
 * (в т.ч. поршневых), чистится в методе записи и на остановке сервера.</p>
 */
public final class NodeClumpIndex {

    /** level → (позиция члена → корень клампа). Одиночки не индексируются. */
    private static final Map<Level, Map<Long, Long>> MEMBER_ROOT = new IdentityHashMap<>();
    /** level → (корень → кламп). */
    private static final Map<Level, Map<Long, Clump>> BY_ROOT = new IdentityHashMap<>();

    /** Кламп: множество членов, плоская потеря транзита ({@code потери × N}). */
    public record Clump(long root, long lossMilli, Set<Long> members) {
    }

    private NodeClumpIndex() {
    }

    /** Род клампа по блоку: «U1»/«U2» (универсальные), «N1:HEAT»/«N2:HEAT»/… или null. */
    private static String kindOfBlock(BlockState state) {
        Block b = state.getBlock();
        if (b instanceof UniversalNodeBlock) {
            return b instanceof SecondTierPipe ? "U2" : "U1";
        }
        if (b instanceof NodeBlock node) {
            return (b instanceof SecondTierPipe ? "N2:" : "N1:") + node.pipeType().name();
        }
        return null;
    }

    /**
     * Род клампа позиции. 0.3.104: порты СФОРМИРОВАННЫХ мультиблоков (турбина,
     * парогенератор) — не узлы теплосети: не сшиваются ни с внешними узлами,
     * ни между собой (автор: «они должны просто служить как порты приёма/выдачи
     * для мультиблока и не взаимодействовать с внешними прикреплёнными узлами»).
     */
    private static String kindOf(Level level, BlockPos pos, BlockState state) {
        if (TurbineStructure.isMember(level, pos) || SteamGenStructure.isMember(level, pos)) return null;
        return kindOfBlock(state);
    }

    /** Плоская потеря транзита (milli) для рода/типа: только провода и теплотрубы теряют. */
    private static long lossMilliFor(String kind, int lossCells) {
        if (kind.startsWith("N")) {
            boolean second = kind.startsWith("N2");
            boolean heat = kind.endsWith("HEAT");
            boolean wire = kind.endsWith("WIRE");
            if (heat || wire) {
                return lossCells * PipeLoss.perCell(second, heat);
            }
        }
        return 0L; // универсальные и жидкости/предметы не теряют
    }

    // ─────────────────────────── запросы маршрутизатора ───────────────────────────

    /** Позиция — член сшитого клампа (не одиночки)? */
    public static boolean isMember(Level level, BlockPos pos) {
        Map<Long, Long> members = MEMBER_ROOT.get(level);
        return members != null && members.containsKey(pos.asLong());
    }

    /** Корень клампа позиции или 0 (не член). */
    public static long rootOf(Level level, BlockPos pos) {
        Map<Long, Long> members = MEMBER_ROOT.get(level);
        if (members == null) return 0L;
        Long root = members.get(pos.asLong());
        return root == null ? 0L : root;
    }

    /**
     * Члены клампа позиции (пусто — не член). Для hover ЛЮБОГО члена: клиент
     * запрашивает поток позиции-члена, сервер отвечает агрегатом клампа.
     */
    public static Set<BlockPos> membersOf(Level level, BlockPos pos) {
        Set<BlockPos> out = new HashSet<>();
        long root = rootOf(level, pos);
        if (root == 0L) return out;
        Map<Long, Clump> byRoot = BY_ROOT.get(level);
        Clump clump = byRoot == null ? null : byRoot.get(root);
        if (clump == null) return out;
        for (long key : clump.members) out.add(BlockPos.of(key));
        return out;
    }

    /** Размер клампа позиции (0 — одиночка/не узел); для HUD «сшито узлов: N». */
    public static int sizeAt(Level level, BlockPos pos) {
        long root = rootOf(level, pos);
        if (root == 0L) return 0;
        Map<Long, Clump> byRoot = BY_ROOT.get(level);
        Clump clump = byRoot == null ? null : byRoot.get(root);
        return clump == null ? 0 : clump.members.size();
    }

    /** Плоская потеря транзита клампа-корня (0 для не-клампа/безпотерьных родов). */
    public static long lossMilliOfRoot(Level level, long root) {
        Map<Long, Clump> byRoot = BY_ROOT.get(level);
        if (byRoot == null) return 0L;
        Clump clump = byRoot.get(root);
        return clump == null ? 0L : clump.lossMilli;
    }

    // ─────────────────────────── постановка/удаление узлов ───────────────────────────

    /**
     * Узел поставлен (в т.ч. поршнем): слияние с окружением.
     * <p>0.3.100: флуд по СОСТОЯНИЯМ мира (same-kind смежные узлы), а не по
     * индексу — индекс не содержит одиночек, поэтому версия 0.3.98 (union
     * только из проиндексированных клампов соседей) не могла сшить даже два
     * первых узла: сосед-одиночка был невидим, union = 1, register выходил
     * по size < 2.</p>
     */
    public static void onNodeChanged(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return;
        String kind = kindOf(level, pos, level.getBlockState(pos));
        if (kind == null) return;

        Set<Long> union = new HashSet<>();
        union.add(pos.asLong());
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(pos);
        while (!queue.isEmpty()) {
            BlockPos cur = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos next = cur.relative(dir);
                long key = next.asLong();
                if (union.contains(key) || !level.isLoaded(next)) continue;
                if (!kind.equals(kindOf(level, next, level.getBlockState(next)))) continue;
                union.add(key);
                queue.add(next);
            }
        }
        register(server, union, kind);
    }

    /** Узел удалён (в т.ч. поршнем): кламп без него может расколоться. */
    public static void onNodeRemoved(Level level, BlockPos pos, BlockState oldState) {
        if (!(level instanceof ServerLevel server)) return;
        String kind = kindOf(level, pos, oldState);
        if (kind == null) return;
        removeInternal(server, pos, kind);
    }

    /**
     * 0.3.104: порты сформированной структуры выкидываются из реестра клампов
     * (вызов из Turbine/SteamGenStructure.index ПОСЛЕ регистрации членов).
     * Оставшиеся соседи-узлы пере-регистрируются расколом как раньше.
     */
    public static void detachPorts(ServerLevel level, List<BlockPos> ports) {
        for (BlockPos pos : ports) {
            String kind = kindOfBlock(level.getBlockState(pos));
            if (kind == null) continue;
            removeInternal(level, pos, kind);
        }
    }

    private static void removeInternal(ServerLevel level, BlockPos pos, String kind) {
        Map<Long, Long> members = MEMBER_ROOT.get(level);
        if (members == null) return;
        Long root = members.remove(pos.asLong());
        if (root == null) return; // одиночка — не индексировался
        Map<Long, Clump> byRoot = BY_ROOT.get(level);
        Clump old = byRoot == null ? null : byRoot.remove(root);

        // Компоненты остатка (кламп мог расколоться на несколько).
        Set<Long> rest = new HashSet<>();
        if (old != null) rest.addAll(old.members());
        rest.remove(pos.asLong());
        // 0.3.105: протухшие маппинги остатка стираются ДО раскладки
        // компонентов. Иначе инвариант register у ВТОРОЙ компоненты читает
        // старый корень (общий с первой) и «съедает» только что
        // зарегистрированный кламп первой — её члены остаются сиротами.
        for (long key : rest) members.remove(key);
        while (!rest.isEmpty()) {
            long min = Long.MAX_VALUE;
            for (long key : rest) min = Math.min(min, key);
            Set<Long> component = floodWithin(level, min, rest);
            rest.removeAll(component);
            if (component.size() >= 2) {
                register(level, component, kind);
            } else if (component.size() == 1) {
                members.remove(component.iterator().next()); // одиночка не индексируется
            }
        }
        // 0.3.101: уборка устаревших корней (живой кламп отображает свой
        // корень сам в себя; пере-корневки поршнем оставляли мусор в BY_ROOT).
        if (byRoot != null && !byRoot.isEmpty()) {
            byRoot.keySet().removeIf(r -> {
                Long self = members.get(r);
                return self == null || self != r;
            });
        }
        if (byRoot != null && byRoot.isEmpty()) BY_ROOT.remove(level);
        if (members.isEmpty()) MEMBER_ROOT.remove(level);
    }

    /** Сброс на остановке сервера (карты держат ссылки на Level). */
    public static void clearAll() {
        MEMBER_ROOT.clear();
        BY_ROOT.clear();
    }

    // ─────────────────────────── формирование ───────────────────────────

    /**
     * Регистрирует кламп из множества членов.
     * <p>0.3.101: ИНВАРИАНТ НЕПЕРЕСЕЧЕНИЯ — старые клампы, члены которых вошли
     * в новый, удаляются из BY_ROOT, и каждый член отображается ровно в новый
     * корень. Ранее при пере-корневках (поршень унёс min-узел) в BY_ROOT
     * оставались устаревшие клампы с растущими множествами — HUD-счётчик у
     * автора рос «экспоненциально» (492023 на 5×5×5). Визуал (вспышка/HUD-
     * счётчик) убран по решению автора 02.10 — «главное чтобы не было
     * мёртвого кода»; кламп виден по числам потерь на ключе.</p>
     */
    private static void register(ServerLevel level, Set<Long> members, String kind) {
        if (members.size() < 2) return;

        long root = Long.MAX_VALUE;
        for (long key : members) root = Math.min(root, key);
        // Плоская потеря = perCell × N ВСЕХ членов (автор 02.10: «кламп влияет
        // на цепь как сумма членов»: 1×1×3 → 0.66 GTH, 5×5×5 → 27.5 GTH).
        long lossMilli = lossMilliFor(kind, members.size());

        Map<Long, Long> members2root = MEMBER_ROOT.computeIfAbsent(level, ignored -> new java.util.HashMap<>());
        Map<Long, Clump> byRoot = BY_ROOT.computeIfAbsent(level, ignored -> new java.util.HashMap<>());
        // Инвариант: пересекаемые старые клампы съедены новым.
        for (long key : members) {
            Long oldRoot = members2root.get(key);
            if (oldRoot != null && oldRoot != root) byRoot.remove(oldRoot);
        }
        for (long key : members) members2root.put(key, root);
        byRoot.put(root, new Clump(root, lossMilli, members));
    }

    /** Флуд по чужим/чужеродным блокам не идёт: шагаем только по членам набора. */
    private static Set<Long> floodWithin(Level level, long start, Set<Long> allowed) {
        Set<Long> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(BlockPos.of(start));
        while (!queue.isEmpty()) {
            BlockPos cur = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos next = cur.relative(dir);
                long key = next.asLong();
                // 0.3.105: порядок условий КРИТИЧЕН — сначала «это вообще
                // член?». Прежний порядок (!seen.add первым) вталкивал в
                // компонент ЛЮБОГО соседа (воздух, трубы, порты, только что
                // сломанный узел): register раздавал членство мусору, корнями
                // становились призраки, а живые клампы осиротевали — при
                // разборе клампа ключ показывал «ни сшито, ни потока»
                // (репорт автора 02.10).
                if (!allowed.contains(key) || !seen.add(key)) continue;
                if (!level.isLoaded(next)) continue; // не грузим чанки флудом
                queue.add(next);
            }
        }
        return seen;
    }
}
