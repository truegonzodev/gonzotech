package com.gonzotech.machines.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;
import java.util.function.Predicate;

/** Server-thread topology only. No inventories, receivers or resources are stored here.
 * Sparse 8^3 cells index dependencies; a search keeps its FIFO and parents between slices.
 * A pending/stale result is never a truncated route plan. */
public final class PipeNetworkService {
    private PipeNetworkService() {}
    public enum Kind { ITEM, FILTER, RESOURCE, PORT }
    public record Outlet(BlockPos pipe, BlockPos target, Direction face, boolean port) {}
    private record Key(BlockPos start, PipeType type, Kind kind) {}
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int QUANTUM = 64;
    private static final int NODES_PER_TICK = 4096;
    private static final long NANOS_PER_TICK = 3_000_000L;
    private static final int IDLE_TICKS = 200;
    private static final Map<Level, World> WORLDS = new IdentityHashMap<>();
    private static final LinkedHashSet<Query> RUNNABLE = new LinkedHashSet<>();
    private static int remaining = NODES_PER_TICK;
    private static long spent;
    private static boolean ticking;
    private static long epoch;

    private static final class Cell {
        long revision;
        final BlockState[] states = new BlockState[512];
        final Set<Query> readers = new HashSet<>();
    }
    private static final class World {
        final Level level;
        final Map<Key, Query> queries = new HashMap<>();
        final Map<BlockPos, Cell> cells = new HashMap<>();
        final Map<Long, Set<BlockPos>> chunks = new HashMap<>();
        long fallbackTick = Long.MIN_VALUE;
        World(Level level) { this.level = level; }
    }
    public static final class Plan {
        private boolean current = true;
        private final Map<Long, BlockPos> parents;
        private List<Outlet> outlets;
        private int visited;
        private final Map<BlockPos, List<Outlet>> emptyLeafOutlets;
        private final Map<BlockPos, List<BlockPos>> paths = new HashMap<>();
        private Plan(Query q) {
            parents = q.parents; outlets = Collections.unmodifiableList(q.outlets); visited = q.processed; emptyLeafOutlets = q.emptyLeafOutlets;
        }
        public boolean current() { return current; }
        public int visited() { return visited; }
        public List<Outlet> outlets() {
            if (emptyLeafOutlets.isEmpty()) return outlets;
            // Discovery refresh may materialize candidates. A leaf EDIT never scans this list.
            List<Outlet> result = new ArrayList<>();
            for (Outlet outlet : outlets) if (parents.containsKey(outlet.pipe.asLong())) result.add(outlet);
            return result;
        }
        public Map<Long, BlockPos> parents() { return parents; }
        /** Materialize only an actual receiver's route, preserving vanilla direction-order BFS. */
        public List<BlockPos> pathTo(BlockPos end) {
            return paths.computeIfAbsent(end, p -> {
                List<BlockPos> path = new ArrayList<>();
                for (BlockPos at = p; at != null; at = parents.get(at.asLong())) path.add(at);
                Collections.reverse(path); return List.copyOf(path);
            });
        }
    }
    private static final class Query {
        final World world;
        final Key key;
        final Set<BlockPos> dependencies = new HashSet<>();
        Map<Long, BlockPos> parents = new HashMap<>();
        final Set<BlockPos> seen = new HashSet<>();
        final ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
        List<Outlet> outlets = new ArrayList<>();
        List<Outlet> potentialItems = new ArrayList<>();
        List<Outlet> refreshOutlets;
        Set<Outlet> refreshSeen;
        Iterator<Outlet> refreshIterator;
        long refreshedTick;
        boolean refreshing;

        final Set<BlockPos> ports = new HashSet<>();
        final Set<BlockPos> endpointPipes = new HashSet<>();
        final Map<BlockPos, List<Outlet>> emptyLeafOutlets = new HashMap<>();
        Predicate<BlockPos> excluded;
        Predicate<BlockPos> virtualPort;
        Plan plan;
        boolean dirty = true;
        long lastUse, retryAt;
        int processed;
        Query(World world, Key key) { this.world = world; this.key = key; }
        void invalidate() {
            if (plan != null) plan.current = false;
            dirty = true;
            // One coalesced dirty state. Repeated piston updates cannot append jobs.
            retryAt = epoch + 1;
            RUNNABLE.add(this);
        }
        void release() {
            if (plan != null) plan.current = false;
            RUNNABLE.remove(this);
            for (BlockPos cellPos : dependencies) {
                Cell cell = world.cells.get(cellPos);
                if (cell != null) cell.readers.remove(this);
            }
            dependencies.clear();
        }
        void restart() {
            release(); plan = null; parents = new HashMap<>(); seen.clear(); frontier.clear(); outlets = new ArrayList<>(); potentialItems = new ArrayList<>(); refreshing = false; refreshIterator = null; refreshOutlets = null; ports.clear(); endpointPipes.clear(); emptyLeafOutlets.clear(); processed = 0;
            dirty = false;
            seen.add(key.start); parents.put(key.start.asLong(), null); frontier.add(key.start);
        }
        BlockState state(BlockPos pos) {
            BlockPos cellPos = cellOf(pos);
            Cell cell = world.cells.get(cellPos);
            if (cell == null) {
                cell = new Cell(); world.cells.put(cellPos, cell);
                world.chunks.computeIfAbsent(chunkOf(pos), k -> new HashSet<>()).add(cellPos);
            }
            if (dependencies.add(cellPos)) cell.readers.add(this);
            // Unknown boundaries are watched too; they are never read/force-loaded.
            if (!world.level.hasChunkAt(pos)) return null;
            BlockState cached = cell.states[cellIndex(pos)];
            if (cached != null) return cached;
            long revision = cell.revision;
            BlockState read = world.level.getBlockState(pos);
            if (cell.revision != revision || !world.level.hasChunkAt(pos)) return null;
            cell.states[cellIndex(pos)] = read;
            return read;
        }
        void visit(BlockPos pipe) {
            BlockState s = state(pipe);
            boolean filterRoot = key.kind == Kind.FILTER && pipe.equals(key.start);
            if (s == null || (!filterRoot && !carries(s, key.type))) return;
            if ((key.kind == Kind.RESOURCE || key.kind == Kind.PORT) && virtualPort.test(pipe)) {
                outlets.add(new Outlet(pipe, pipe, Direction.UP, true)); ports.add(pipe); endpointPipes.add(pipe);
            }
            for (Direction dir : DIRECTIONS) {
                if (!(filterRoot && !(s.getBlock() instanceof PipeCarrier)) && !opens(s, key.type, dir)
                    && !((key.kind == Kind.RESOURCE || key.kind == Kind.PORT) && opens(s, key.type, dir.getOpposite()))) continue;
                BlockPos next = pipe.relative(dir);
                // Watch excluded structure cells before querying membership as well.
                BlockState other = state(next);
                if (other == null || (key.kind == Kind.PORT && !next.equals(key.start) && excluded.test(next))) continue;
                if (carries(other, key.type)) {
                    boolean openHere = filterRoot && !(s.getBlock() instanceof PipeCarrier)
                        || opens(s, key.type, dir);
                    if (!openHere || !opens(other, key.type, dir.getOpposite())) continue;
                    if (seen.add(next)) {
                        parents.put(next.asLong(), filterRoot ? null : pipe); frontier.add(next);
                    }
                } else if (opens(s, key.type, key.kind == Kind.ITEM || key.kind == Kind.FILTER ? dir : dir.getOpposite()) && (filterRoot || mode(s, key.type).deliversToMachine())) {
                    Outlet outlet = new Outlet(pipe, next, dir.getOpposite(), false);
                    if (key.kind == Kind.ITEM || key.kind == Kind.FILTER) {
                        potentialItems.add(outlet);
                        if (ItemRouting.containerAt(world.level, next) != null) { outlets.add(outlet); endpointPipes.add(pipe); }
                    } else if (world.level.getBlockEntity(next) != null) { outlets.add(outlet); endpointPipes.add(pipe); }
                }
            }
        }
    }
    private static void startRefresh(Query q) {
        List<Outlet> extra = new ArrayList<>();
        for (List<Outlet> list : q.emptyLeafOutlets.values()) extra.addAll(list);
        Iterator<Outlet> base = q.potentialItems.iterator(), additions = extra.iterator();
        q.refreshIterator = new Iterator<>() {
            public boolean hasNext() { return base.hasNext() || additions.hasNext(); }
            public Outlet next() { return base.hasNext() ? base.next() : additions.next(); }
        };
        q.refreshOutlets = new ArrayList<>(); q.refreshSeen = new HashSet<>(); q.refreshing = true; RUNNABLE.add(q);
    }
    private static void refresh(Query q, int quantum, long begin) {
        int count = 0;
        while (q.refreshIterator.hasNext() && count < quantum && remaining > 0) {
            if (count > 0 && count % 16 == 0 && spent + System.nanoTime() - begin >= NANOS_PER_TICK) break;
            Outlet outlet = q.refreshIterator.next(); remaining--; count++;
            // A removed empty leaf is not a live route. New leaves are in the delta.
            if (!q.parents.containsKey(outlet.pipe.asLong())) continue;
            if (!q.refreshSeen.add(outlet)) continue;
            BlockState pipeState = q.state(outlet.pipe);
            if (pipeState == null || !opens(pipeState, q.key.type, outlet.face.getOpposite())) continue;
            if (q.dirty || !q.refreshing) { spent += System.nanoTime() - begin; return; }
            boolean present = ItemRouting.containerAt(q.world.level, outlet.target) != null;
            if (q.dirty || !q.refreshing) { spent += System.nanoTime() - begin; return; }
            if (present) q.refreshOutlets.add(outlet);
        }
        if (!q.dirty && !q.refreshIterator.hasNext()) {
            q.plan.current = false;
            q.outlets = q.refreshOutlets; q.endpointPipes.clear();
            for (Outlet outlet : q.outlets) q.endpointPipes.add(outlet.pipe);
            q.refreshOutlets = null; q.refreshIterator = null; q.refreshing = false; q.refreshSeen = null;
            q.plan = new Plan(q); q.refreshedTick = q.world.level.getGameTime(); RUNNABLE.remove(q);
        }
        spent += System.nanoTime() - begin;
    }
    private static int cellIndex(BlockPos p) { return (p.getX() & 7) | (p.getZ() & 7) << 3 | (p.getY() & 7) << 6; }
    private static BlockPos cellOf(BlockPos pos) { return new BlockPos(pos.getX() >> 3, pos.getY() >> 3, pos.getZ() >> 3); }
    private static long chunkOf(BlockPos pos) { return (long)(pos.getX() >> 4) << 32 ^ ((pos.getZ() >> 4) & 0xffffffffL); }
    private static boolean carries(BlockState s, PipeType t) { return s.getBlock() instanceof PipeCarrier c && c.carries(s, t); }
    private static boolean opens(BlockState s, PipeType t, Direction d) { return s.getBlock() instanceof PipeCarrier c && c.opensToward(s, t, d); }
    private static PipeMode mode(BlockState s, PipeType t) { return ((PipeCarrier)s.getBlock()).modeFor(s, t); }

    public static Plan request(Level level, BlockPos start, PipeType type, Kind kind,
                               Predicate<BlockPos> excluded, Predicate<BlockPos> virtualPort) {
        if (level.isClientSide() || !level.hasChunkAt(start)) return null;
        World world = WORLDS.computeIfAbsent(level, World::new);
        // Unit harnesses have no event bus. Production is reset only by SERVER ticks,
        // never by the independent clocks of dimensions.
        if (!ticking && world.fallbackTick != level.getGameTime()) {
            world.fallbackTick = level.getGameTime(); beginTick(); ticking = false;
        }
        Query q = world.queries.computeIfAbsent(new Key(start, type, kind), k -> new Query(world, k));
        q.lastUse = epoch; q.excluded = excluded; q.virtualPort = virtualPort;
        if (q.plan != null && q.plan.current && !q.dirty) {
            if ((kind == Kind.ITEM || kind == Kind.FILTER) && !q.refreshing && level.getGameTime() - q.refreshedTick >= 40)
                startRefresh(q);
            return q.plan; // old complete snapshot remains live-validated while discovery refreshes
        }
        RUNNABLE.add(q);
        // Synchronous callers share the same FIFO rotation as Post. Otherwise a
        // stable block-entity tick order can spend every tick's budget on early
        // sources, starving later searches before Post ever gets to run.
        if (RUNNABLE.iterator().next() == q && remaining > 0
            && spent < NANOS_PER_TICK && q.retryAt <= epoch) {
            RUNNABLE.remove(q); RUNNABLE.add(q);
            advance(q, QUANTUM);
        }
        return q.plan != null && q.plan.current && !q.dirty ? q.plan : null;
    }
    public static Plan requestItems(Level level, BlockPos start, boolean filter) {
        return request(level, start, PipeType.ITEM, filter ? Kind.FILTER : Kind.ITEM, p -> false, p -> false);
    }
    private static void advance(Query q, int quantum) {
        if (remaining <= 0 || spent >= NANOS_PER_TICK || q.retryAt > epoch) return;
        long begin = System.nanoTime();
        if (q.dirty) q.restart();
        if (q.refreshing) { refresh(q, quantum, begin); return; }
        int count = 0;
        while (!q.frontier.isEmpty() && count < quantum && remaining > 0) {
            if (count > 0 && count % 16 == 0 && spent + System.nanoTime() - begin >= NANOS_PER_TICK) break;
            q.visit(q.frontier.removeFirst()); q.processed++; remaining--; count++;
            if (q.dirty) break; // world callbacks invalidated this generation
        }
        if (!q.dirty && q.frontier.isEmpty()) {
            q.plan = new Plan(q); q.refreshedTick = q.world.level.getGameTime(); RUNNABLE.remove(q);
        } else RUNNABLE.add(q);
        spent += System.nanoTime() - begin;
    }
    public static void beginTick() { ticking = true; epoch++; remaining = NODES_PER_TICK; spent = 0; }
    public static void endTick() {
        // Rotate even unfinished work: a noisy world/source cannot monopolize the scheduler.
        while (!RUNNABLE.isEmpty() && remaining > 0 && spent < NANOS_PER_TICK) {
            int attempts = RUNNABLE.size(), before = remaining;
            while (!RUNNABLE.isEmpty() && remaining > 0 && spent < NANOS_PER_TICK && attempts-- > 0) {
                Query q = RUNNABLE.iterator().next(); RUNNABLE.remove(q);
                if (epoch - q.lastUse > IDLE_TICKS) { q.release(); q.world.queries.remove(q.key); continue; }
                RUNNABLE.add(q); advance(q, QUANTUM);
            }
            if (before == remaining) break; // all remaining jobs are in their coalescing cooldown
        }
        // Periodic idle maintenance; do not clear all warm plans at a size threshold.
        if (epoch % 40 == 0) for (World world : WORLDS.values()) {
            var it = world.queries.values().iterator();
            while (it.hasNext()) { Query q = it.next(); if (epoch - q.lastUse > IDLE_TICKS) { q.release(); it.remove(); } }
            var cells = world.cells.entrySet().iterator();
            while (cells.hasNext()) {
                var entry = cells.next(); if (!entry.getValue().readers.isEmpty()) continue;
                BlockPos p = entry.getKey(); long chunk = (long)(p.getX() >> 1) << 32 ^ ((p.getZ() >> 1) & 0xffffffffL);
                Set<BlockPos> indexed = world.chunks.get(chunk); if (indexed != null) { indexed.remove(p); if (indexed.isEmpty()) world.chunks.remove(chunk); }
                cells.remove();
            }
        }
    }
    /** Invalidate only cells that actually observed the changed position. No global version. */
    public static void changed(Level level, BlockPos pos) {
        World world = WORLDS.get(level); if (world == null) return;
        Cell cell = world.cells.get(cellOf(pos)); if (cell == null || cell.states[cellIndex(pos)] == null) return;
        cell.revision++; cell.states[cellIndex(pos)] = null;
        for (Query q : cell.readers) q.invalidate();
    }
    /** State edits that cannot change connectivity do not revoke route leases.
     * A single empty leaf can be attached/detached locally, without searching the component. */
    public static void changed(Level level, BlockPos pos, BlockState old, BlockState actual) {
        World world = WORLDS.get(level); if (world == null) return;
        Cell cell = world.cells.get(cellOf(pos)); if (cell == null || cell.states[cellIndex(pos)] == null) return;
        cell.revision++; cell.states[cellIndex(pos)] = actual;
        for (Query q : List.copyOf(cell.readers)) {
            if (sameTopology(old, actual, q.key.type)) continue;
            if (!patchEmptyLeaf(q, pos, old, actual)) q.invalidate();
        }
    }
    private static boolean sameTopology(BlockState a, BlockState b, PipeType type) {
        boolean ac = carries(a, type), bc = carries(b, type);
        if (ac != bc) return false;
        if (!ac) return a.hasBlockEntity() == b.hasBlockEntity();
        if (mode(a, type) != mode(b, type)) return false;
        for (Direction d : DIRECTIONS) if (opens(a, type, d) != opens(b, type, d)) return false;
        return true;
    }
    private static boolean patchEmptyLeaf(Query q, BlockPos pos, BlockState old, BlockState actual) {
        if (q.dirty || q.plan == null || !q.plan.current || q.key.kind == Kind.PORT || pos.equals(q.key.start)) return false;
        boolean adding = carries(actual, q.key.type), removing = carries(old, q.key.type);
        if (adding == removing || old.hasBlockEntity() || actual.hasBlockEntity()) return false;
        if (q.endpointPipes.contains(pos)) return false;
        if (adding == q.parents.containsKey(pos.asLong())) return false;
        BlockState leaf = adding ? actual : old;
        BlockPos neighbour = null;
        // Six local faces only. Unknown boundary or an actual endpoint => full dirty search.
        for (Direction d : DIRECTIONS) {
            BlockPos next = pos.relative(d);
            if (!q.world.level.hasChunkAt(next)) return false;
            BlockState state = q.world.level.getBlockState(next);
            if (carries(state, q.key.type)) {
                if (opens(leaf, q.key.type, d) && opens(state, q.key.type, d.getOpposite())) {
                    if (neighbour != null) return false; neighbour = next;
                }
            } else if (q.world.level.getBlockEntity(next) != null || ItemRouting.containerAt(q.world.level, next) != null) return false;
        }
        if (neighbour == null || !q.parents.containsKey(neighbour.asLong())) return false;
        if (adding && q.virtualPort.test(pos)) return false;
        List<Outlet> extras = new ArrayList<>();
        if (ItemRouting.containerAt(q.world.level, pos) != null) return false;
        if (adding) {
            q.parents.put(pos.asLong(), q.key.kind == Kind.FILTER && neighbour.equals(q.key.start) ? null : neighbour);
            q.seen.add(pos); q.processed++;
            for (Direction d : DIRECTIONS) {
                BlockPos next = pos.relative(d); BlockState state = q.state(next);
                if ((q.key.kind == Kind.ITEM || q.key.kind == Kind.FILTER) && state != null && !carries(state, q.key.type)
                    && opens(actual, q.key.type, d) && mode(actual, q.key.type).deliversToMachine())
                    extras.add(new Outlet(pos, next, d.getOpposite(), false));
            }
        } else {
            q.parents.remove(pos.asLong()); q.seen.remove(pos); q.processed--;
            q.plan.paths.remove(pos);
            BlockState neighbourState = q.state(neighbour);
            for (Direction d : DIRECTIONS) if (neighbour.relative(d).equals(pos)
                && (q.key.kind == Kind.ITEM || q.key.kind == Kind.FILTER) && neighbourState != null
                && opens(neighbourState, q.key.type, d) && mode(neighbourState, q.key.type).deliversToMachine())
                extras.add(new Outlet(neighbour, pos, d.getOpposite(), false));
        }
        q.emptyLeafOutlets.put(pos, extras); q.plan.visited = q.processed;
        if (q.refreshing) { q.refreshing = false; q.refreshIterator = null; q.refreshOutlets = null; RUNNABLE.remove(q); }
        return true;
    }
    public static void chunkChanged(Level level, int x, int z) {
        World world = WORLDS.get(level); if (world == null) return;
        Set<BlockPos> cells = world.chunks.get((long)x << 32 ^ (z & 0xffffffffL)); if (cells == null) return;
        for (BlockPos p : cells) {
            Cell cell = world.cells.get(p); if (cell == null) continue;
            cell.revision++; Arrays.fill(cell.states, null); for (Query q : cell.readers) q.invalidate();
        }
    }
    public static void invalidateAll(Level level) {
        World world = WORLDS.get(level); if (world == null) return;
        for (Cell cell : world.cells.values()) { cell.revision++; Arrays.fill(cell.states, null); }
        for (Query q : world.queries.values()) q.invalidate();
    }
    public static void closeLevel(Level level) {
        World world = WORLDS.remove(level); if (world != null) for (Query q : world.queries.values()) q.release();
    }
    public static void clearAll() { RUNNABLE.clear(); WORLDS.clear(); ticking = false; epoch = 0; remaining = NODES_PER_TICK; spent = 0; }
    /** Cheap diagnostic lookup; does not start a search or inspect the world. */
    public static int progress(Level level, BlockPos start, PipeType type, Kind kind) {
        World world = WORLDS.get(level); if (world == null) return 0;
        Query query = world.queries.get(new Key(start, type, kind));
        return query == null || query.dirty ? 0 : query.processed;
    }
    public static int pendingJobs() { return RUNNABLE.size(); }
}
