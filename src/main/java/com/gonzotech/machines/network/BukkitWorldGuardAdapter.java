package com.gonzotech.machines.network;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import com.gonzotech.machines.network.PlotnickRegionPolicy.State;

/** Optional Bukkit/CraftWorld bridge. No Bukkit/WE/WG classes on the mod's linkage path.
 * Methods are bound to public API types and the actual plugins' class loaders.
 * Native support requires a hybrid exposing CraftWorld#getHandle() == ServerLevel.
 */
public final class BukkitWorldGuardAdapter {
    public enum FailureKind { DISCOVERY, PLUGINS, API, WORLD, REGIONS }
    public static final class Failure extends Exception {
        private final FailureKind kind;
        Failure(FailureKind kind, String message) { super(message); this.kind = kind; }
        Failure(FailureKind kind, String message, Throwable cause) { super(message, cause); this.kind = kind; }
        public FailureKind kind() { return kind; }
    }
    private final Object pluginManager;
    private final Method getPlugin, isEnabled, getWorlds;
    private final Class<?> bukkitWorldType;
    private final Map<Object, WeakReference<Object>> worlds = new WeakHashMap<>();
    private Object guardPlugin, editPlugin;
    private Bindings bindings;
    private Failure bindingFailure;

    /** Null means genuinely no running Bukkit server / no WorldGuard plugin, not a broken WG API. */
    public static BukkitWorldGuardAdapter discover() throws Failure {
        Class<?> bukkit;
        try {
            try { bukkit = Class.forName("org.bukkit.Bukkit", false, BukkitWorldGuardAdapter.class.getClassLoader()); }
            catch (ClassNotFoundException absent) {
                ClassLoader context = Thread.currentThread().getContextClassLoader();
                if (context == null || context == BukkitWorldGuardAdapter.class.getClassLoader()) return null;
                bukkit = Class.forName("org.bukkit.Bukkit", false, context);
            }
        } catch (ClassNotFoundException absent) { return null; }
        catch (LinkageError | RuntimeException failure) { throw new Failure(FailureKind.DISCOVERY, "Bukkit API linkage failed", failure); }
        try {
            if (bukkit.getMethod("getServer").invoke(null) == null) return null;
            ClassLoader loader = bukkit.getClassLoader();
            Class<?> managerType = Class.forName("org.bukkit.plugin.PluginManager", false, loader);
            Object manager = bukkit.getMethod("getPluginManager").invoke(null);
            Method find = managerType.getMethod("getPlugin", String.class);
            if (find.invoke(manager, "WorldGuard") == null) return null;
            return new BukkitWorldGuardAdapter(bukkit, manager, find);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            throw new Failure(FailureKind.DISCOVERY, "Cannot establish Bukkit plugin presence", failure);
        }
    }
    private BukkitWorldGuardAdapter(Class<?> bukkit, Object manager, Method find) throws ReflectiveOperationException {
        pluginManager = manager;
        getPlugin = find;
        ClassLoader loader = bukkit.getClassLoader();
        isEnabled = Class.forName("org.bukkit.plugin.Plugin", false, loader).getMethod("isEnabled");
        bukkitWorldType = Class.forName("org.bukkit.World", false, loader);
        getWorlds = bukkit.getMethod("getWorlds");
    }
    private Bindings liveBindings() throws Failure {
        try {
            Object guard = getPlugin.invoke(pluginManager, "WorldGuard");
            Object edit = getPlugin.invoke(pluginManager, "WorldEdit");
            if (guard == null || edit == null || !Boolean.TRUE.equals(isEnabled.invoke(guard))
                    || !Boolean.TRUE.equals(isEnabled.invoke(edit)))
                throw new Failure(FailureKind.PLUGINS, "Installed WorldGuard requires enabled WorldGuard AND WorldEdit plugins");
            if (guard != guardPlugin || edit != editPlugin) {
                guardPlugin = guard;
                editPlugin = edit;
                bindings = null;
                bindingFailure = null;
                worlds.clear();
                try { bindings = new Bindings(guard.getClass().getClassLoader(), edit.getClass().getClassLoader(), bukkitWorldType); }
                catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                    bindingFailure = new Failure(FailureKind.API, "Unsupported or inaccessible WE/WG API/class-loader combination", failure);
                }
            }
            if (bindingFailure != null) throw bindingFailure;
            return bindings;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            throw new Failure(FailureKind.PLUGINS, "Cannot check live plugin health", failure);
        }
    }
    private Object world(Object nativeLevel) throws Failure {
        try {
            WeakReference<Object> reference = worlds.get(nativeLevel);
            Object cached = reference == null ? null : reference.get();
            if (cached != null) {
                try { if (handle(cached) == nativeLevel) return cached; }
                catch (ReflectiveOperationException stale) { /* discard, try the current world list */ }
            }
            worlds.remove(nativeLevel);
            Object found = null;
            ReflectiveOperationException unsupported = null;
            for (Object world : (Iterable<?>) getWorlds.invoke(null)) {
                Object nativeHandle;
                try { nativeHandle = handle(world); }
                catch (ReflectiveOperationException failure) { unsupported = failure; continue; }
                if (nativeHandle != nativeLevel) continue;
                if (found != null && found != world)
                    throw new Failure(FailureKind.WORLD, "Multiple Bukkit worlds expose the same native level");
                found = world;
            }
            if (found == null) throw new Failure(FailureKind.WORLD,
                "No Bukkit world exposes this exact ServerLevel via getHandle(); configure a compatible hybrid bridge", unsupported);
            worlds.put(nativeLevel, new WeakReference<>(found));
            return found;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            throw new Failure(FailureKind.WORLD, "Cannot map native level to Bukkit world by handle identity", failure);
        }
    }
    private static Object handle(Object world) throws ReflectiveOperationException {
        // Public CraftWorld method, deliberately NOT a dimension-name/nearest-player fallback.
        return world.getClass().getMethod("getHandle").invoke(world);
    }
    public boolean allows(Object nativeLevel, int x, int y, int z) throws Failure {
        Bindings api = liveBindings();
        Object world = world(nativeLevel);
        try { return api.allows(world, x, y, z); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            throw new Failure(FailureKind.REGIONS, "Cannot query current WorldGuard region data", failure);
        }
    }
    public void unload(Object nativeLevel) { worlds.remove(nativeLevel); }

    private static final class Bindings {
        final Method instance, platform, container, adapt, manager, vector, applicable, virtual, regions, global;
        final Method queryValue, explicitValue, rawFlag, groupFlag, defaultValue, groupContains, priority, sortRegions, parent;
        final Object build, place, broken, pistons, passthrough, machine, nonMember;
        Bindings(ClassLoader guardLoader, ClassLoader editLoader, Class<?> bukkitWorld) throws ReflectiveOperationException {
            Class<?> wg = Class.forName("com.sk89q.worldguard.WorldGuard", true, guardLoader);
            Class<?> world = Class.forName("com.sk89q.worldedit.world.World", false, editLoader);
            Class<?> adapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter", true, editLoader);
            Class<?> regionContainer = Class.forName("com.sk89q.worldguard.protection.regions.RegionContainer", false, guardLoader);
            Class<?> regionManager = Class.forName("com.sk89q.worldguard.protection.managers.RegionManager", false, guardLoader);
            Class<?> result = Class.forName("com.sk89q.worldguard.protection.ApplicableRegionSet", false, guardLoader);
            Class<?> protectedRegion = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedRegion", false, guardLoader);
            Class<?> flag = Class.forName("com.sk89q.worldguard.protection.flags.Flag", false, guardLoader);
            Class<?> flags = Class.forName("com.sk89q.worldguard.protection.flags.Flags", true, guardLoader);
            Class<?> subject = Class.forName("com.sk89q.worldguard.protection.association.RegionAssociable", false, guardLoader);
            Class<?> association = Class.forName("com.sk89q.worldguard.domains.Association", false, guardLoader);
            Class<?> calculator = Class.forName("com.sk89q.worldguard.protection.FlagValueCalculator", false, guardLoader);
            Class<?> blockVector = Class.forName("com.sk89q.worldedit.math.BlockVector3", false, editLoader);
            nonMember = association.getField("NON_MEMBER").get(null);
            machine = Proxy.newProxyInstance(subject.getClassLoader(), new Class<?>[]{subject}, (proxy, method, args) -> {
                return switch (method.getName()) {
                    case "getAssociation" -> nonMember;
                    case "toString" -> "GonzoTech ownerless Plotnick";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.toString());
                };
            });
            build = flags.getField("BUILD").get(null);
            place = flags.getField("BLOCK_PLACE").get(null);
            broken = flags.getField("BLOCK_BREAK").get(null);
            pistons = flags.getField("PISTONS").get(null);
            passthrough = flags.getField("PASSTHROUGH").get(null);
            rawFlag = protectedRegion.getMethod("getFlag", flag);
            groupFlag = flag.getMethod("getRegionGroupFlag");
            defaultValue = flag.getMethod("getDefault");
            groupContains = Class.forName("com.sk89q.worldguard.protection.flags.RegionGroup", false, guardLoader)
                .getMethod("contains", association);
            priority = calculator.getMethod("getPriorityOf", protectedRegion);
            parent = protectedRegion.getMethod("getParent");
            sortRegions = Class.forName("com.sk89q.worldguard.protection.util.NormativeOrders", false, guardLoader)
                .getMethod("sort", List.class);
            instance = wg.getMethod("getInstance");
            platform = wg.getMethod("getPlatform");
            container = platform.getReturnType().getMethod("getRegionContainer");
            adapt = adapter.getMethod("adapt", bukkitWorld);
            manager = regionContainer.getMethod("get", world);
            vector = blockVector.getMethod("at", int.class, int.class, int.class);
            applicable = regionManager.getMethod("getApplicableRegions", blockVector);
            virtual = result.getMethod("isVirtual");
            regions = result.getMethod("getRegions");
            global = regionManager.getMethod("getRegion", String.class);
            queryValue = result.getMethod("queryValue", subject, flag);
            explicitValue = calculator.getMethod("getEffectiveFlagOf", protectedRegion, flag, subject);
        }
        boolean allows(Object bukkitWorld, int x, int y, int z) throws ReflectiveOperationException {
            Object wg = instance.invoke(null);
            Object regionContainer = container.invoke(platform.invoke(wg));
            Object currentManager = manager.invoke(regionContainer, adapt.invoke(null, bukkitWorld));
            if (currentManager == null) throw new IllegalStateException("WorldGuard region manager unavailable/disabled for mapped world");
            Object set = applicable.invoke(currentManager, vector.invoke(null, x, y, z));
            if (set == null || !Boolean.FALSE.equals(virtual.invoke(set)))
                throw new IllegalStateException("Unavailable or virtual WorldGuard region result");
            List<Object> candidates = new ArrayList<>((java.util.Collection<?>) regions.invoke(set));
            Object globalRegion = global.invoke(currentManager, "__global__");
            if (globalRegion != null && !candidates.contains(globalRegion)) candidates.add(globalRegion);
            if (candidates.isEmpty()) return true;
            // BUILD needs a non-null RegionAssociable. This proxy is ALWAYS NON_MEMBER, never a player.
            State effectiveBuild = state(set, candidates, build);
            if (effectiveBuild == State.UNSET && globalBuildAllows(globalRegion, candidates)) effectiveBuild = State.ALLOW;
            return PlotnickRegionPolicy.allows(effectiveBuild, state(set, candidates, place),
                state(set, candidates, broken), state(set, candidates, pistons));
        }
        // Author-approved deviation from WG's legacy rule: explicit global BUILD=ALLOW
        // may supply the machine fallback. It must NOT jump a local BUILD/implicit-build
        // priority barrier; other flags and native resolved DENY are never overridden.
        boolean globalBuildAllows(Object globalRegion, List<Object> candidates) throws ReflectiveOperationException {
            if (globalRegion == null || decode(rawFlag.invoke(globalRegion, build)) != State.ALLOW) return false;
            Object groupType = groupFlag.invoke(build);
            if (groupType != null) {
                Object group = rawFlag.invoke(globalRegion, groupType);
                if (group == null) group = defaultValue.invoke(groupType);
                if (group == null || !Boolean.TRUE.equals(groupContains.invoke(group, nonMember))) return false;
            }
            List<Object> localRegions = new ArrayList<>(candidates);
            localRegions.remove(globalRegion);
            sortRegions.invoke(null, localRegions); // same ordering as native FlagValueCalculator
            java.util.Set<Object> ignoredParents = new java.util.HashSet<>();
            for (Object region : localRegions) {
                if (ignoredParents.contains(region)) continue;
                if (((Integer) priority.invoke(null, region)) != Integer.MIN_VALUE
                    && decode(explicitValue.invoke(null, region, passthrough, machine)) != State.ALLOW) return false;
                for (Object ancestor = parent.invoke(region); ancestor != null; ancestor = parent.invoke(ancestor))
                    ignoredParents.add(ancestor);
            }
            return true;
        }
        State state(Object set, List<Object> candidates, Object flag) throws ReflectiveOperationException {
            boolean explicit = false;
            for (Object region : candidates) {
                // WG resolves parents + region groups here. Never mistake pistons' default ALLOW for an explicit flag.
                if (explicitValue.invoke(null, region, flag, machine) != null) { explicit = true; break; }
            }
            if (!explicit) return State.UNSET;
            Object value = queryValue.invoke(set, machine, flag); // actual WG priorities/ties/inheritance
            return decode(value);
        }
        State decode(Object value) {
            if (value == null) return State.UNSET;
            if (!(value instanceof Enum<?> state)) throw new IllegalStateException("Invalid WG state type");
            return switch (state.name()) {
                case "ALLOW" -> State.ALLOW;
                case "DENY" -> State.DENY;
                default -> throw new IllegalStateException("Unknown WG state: " + state.name());
            };
        }
    }
}
