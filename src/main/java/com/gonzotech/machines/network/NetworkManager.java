package com.gonzotech.machines.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Владелец всех энергосетей одного уровня (мира). Держит быстрый индекс
 * «позиция трубы → её сеть» и раз в тик прогоняет {@link EnergyNetwork#tick}.
 * <p>
 * Ключевая оптимизация (то, о чём просил автор): <b>тикают сети, а не провода</b>.
 * Отдельная труба не имеет BlockEntity и не тикает; передача идёт «телепортом»
 * внутри контура за один тик, независимо от длины трассы.
 * <p>
 * <b>Слияние/распад</b> — единственные «дорогие» операции, но случаются только
 * при постановке/сломе трубы (не каждый тик):
 * <ul>
 *   <li>ставим трубу → объединяем сети всех соседей того же типа (или создаём
 *       новую);</li>
 *   <li>ломаем трубу → возможен распад на несколько контуров: flood-fill по
 *       оставшимся соседям пересобирает компоненты связности.</li>
 * </ul>
 * <b>Хранение.</b> Класс — {@link SavedData}: граф труб (тип + позиции + транзитный
 * буфер) сохраняется в {@code data/gonzotech_networks.dat} и восстанавливается при
 * загрузке мира. Порты (примыкающие машины) НЕ сохраняются — они пересчитываются
 * из блоков мира в рантайме, т.к. полностью восстановимы.
 */
public final class NetworkManager extends SavedData {

    private static final String DATA_NAME = "gonzotech_networks";

    /** Уровень, которому принадлежит менеджер (устанавливается в {@link #get}). */
    private transient Level level;

    private final List<EnergyNetwork> networks = new ArrayList<>();
    private final transient Map<BlockPos, EnergyNetwork> index = new HashMap<>();
    /** Позиции, у которых сменились соседи/режим — пересчитать порты их сетей. */
    private final transient Set<BlockPos> dirtyPorts = new HashSet<>();

    public NetworkManager() {
    }

    /** Получить (или создать) менеджер сетей уровня. Только на сервере. */
    public static NetworkManager get(Level level) {
        if (!(level instanceof ServerLevel server)) {
            throw new IllegalStateException("NetworkManager доступен только на сервере");
        }
        NetworkManager mgr = server.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(NetworkManager::new, NetworkManager::load, null),
            DATA_NAME);
        mgr.level = level;
        return mgr;
    }

    // ─────────────────────────── тик ───────────────────────────

    public void tick() {
        if (!dirtyPorts.isEmpty()) {
            for (BlockPos pos : dirtyPorts) {
                EnergyNetwork net = index.get(pos);
                if (net != null) net.markPortsDirty();
            }
            dirtyPorts.clear();
        }
        long gameTime = level.getGameTime();
        for (EnergyNetwork net : networks) {
            net.tick(level, gameTime);
        }
    }

    /** Пометить, что у трубы в {@code pos} изменился сосед (или режим) — пересчитать порты. */
    public void markDirty(BlockPos pos, PipeType type) {
        dirtyPorts.add(pos.immutable());
    }

    // ─────────────────────── постановка/слом трубы ───────────────────────

    public void onPipePlaced(Level level, BlockPos pos, PipeType type) {
        pos = pos.immutable();

        // Собрать соседние сети того же типа.
        List<EnergyNetwork> adjacent = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            EnergyNetwork net = index.get(pos.relative(dir));
            if (net != null && net.type() == type && !adjacent.contains(net)) {
                adjacent.add(net);
            }
        }

        EnergyNetwork target;
        if (adjacent.isEmpty()) {
            target = new EnergyNetwork(type);
            networks.add(target);
        } else {
            target = adjacent.get(0);
            for (int i = 1; i < adjacent.size(); i++) {
                mergeInto(target, adjacent.get(i));
            }
        }
        target.addPipe(pos);
        index.put(pos, target);
        target.markPortsDirty();
        dirtyPorts.remove(pos);
        setDirty();
    }

    public void onPipeRemoved(Level level, BlockPos pos, PipeType type) {
        pos = pos.immutable();

        EnergyNetwork net = index.remove(pos);
        if (net == null) return;
        net.removePipe(pos);
        dirtyPorts.remove(pos);
        setDirty();

        if (net.pipes().isEmpty()) {
            networks.remove(net);
            return;
        }

        // Труба могла быть «мостом» → сеть распадается на компоненты связности.
        List<BlockPos> neighbors = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            BlockPos npos = pos.relative(dir);
            if (net.contains(npos)) neighbors.add(npos);
        }
        if (neighbors.size() <= 1) {
            net.markPortsDirty();
            return;
        }

        // Пересобрать компоненты: первая остаётся в исходной сети, прочие — новые.
        Set<BlockPos> remaining = new HashSet<>(net.pipes());
        boolean first = true;
        for (BlockPos seed : neighbors) {
            if (!remaining.contains(seed)) continue;
            Set<BlockPos> component = floodFill(seed, remaining);
            remaining.removeAll(component);
            if (first) {
                first = false;
                if (component.size() != net.pipes().size()) {
                    rebuildNetwork(net, component);
                }
            } else {
                EnergyNetwork split = new EnergyNetwork(net.type());
                networks.add(split);
                for (BlockPos p : component) {
                    split.addPipe(p);
                    index.put(p, split);
                }
                split.markPortsDirty();
            }
        }
    }

    // ─────────────────────────── helpers ───────────────────────────

    private void mergeInto(EnergyNetwork target, EnergyNetwork source) {
        for (BlockPos p : source.pipes()) {
            index.put(p, target);
            target.addPipe(p);
        }
        target.addBuffer(source.buffer());
        networks.remove(source);
    }

    private void rebuildNetwork(EnergyNetwork net, Set<BlockPos> keep) {
        Set<BlockPos> old = new HashSet<>(net.pipes());
        for (BlockPos p : old) {
            if (!keep.contains(p)) {
                net.removePipe(p);
            }
        }
        for (BlockPos p : keep) {
            index.put(p, net);
        }
        net.markPortsDirty();
    }

    private Set<BlockPos> floodFill(BlockPos start, Set<BlockPos> within) {
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            BlockPos cur = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos npos = cur.relative(dir);
                if (within.contains(npos) && !visited.contains(npos)) {
                    visited.add(npos);
                    queue.add(npos);
                }
            }
        }
        return visited;
    }

    // ─────────────────────────── SavedData ───────────────────────────

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag netList = new ListTag();
        for (EnergyNetwork net : networks) {
            if (net.pipes().isEmpty()) continue;
            CompoundTag netTag = new CompoundTag();
            netTag.putString("type", net.type().name());
            netTag.putInt("buffer", net.buffer());
            long[] positions = net.pipes().stream().mapToLong(BlockPos::asLong).toArray();
            netTag.putLongArray("pipes", positions);
            netList.add(netTag);
        }
        tag.put("networks", netList);
        return tag;
    }

    public static NetworkManager load(CompoundTag tag, HolderLookup.Provider registries) {
        NetworkManager mgr = new NetworkManager();
        ListTag netList = tag.getList("networks", Tag.TAG_COMPOUND);
        for (int i = 0; i < netList.size(); i++) {
            CompoundTag netTag = netList.getCompound(i);
            PipeType type;
            try {
                type = PipeType.valueOf(netTag.getString("type"));
            } catch (IllegalArgumentException ex) {
                continue; // неизвестный тип (напр. удалён из мода) — пропускаем
            }
            EnergyNetwork net = new EnergyNetwork(type);
            net.addBuffer(netTag.getInt("buffer"));
            for (long packed : netTag.getLongArray("pipes")) {
                BlockPos pos = BlockPos.of(packed);
                net.addPipe(pos);
                mgr.index.put(pos, net);
            }
            if (!net.pipes().isEmpty()) {
                mgr.networks.add(net);
            }
        }
        return mgr;
    }
}
