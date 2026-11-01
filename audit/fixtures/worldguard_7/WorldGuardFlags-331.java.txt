package com.gonzotech.machines.network;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Мост к флагам WorldGuard (0.3.306, требование автора 25.10, подтверждение
 * 26.10: «проверять нужно затрагиваемую клетку; владелец и ближайший игрок не
 * нужны»). Машины мода СПРОСАТ разрешение по трём флагам региона именно для
 * редактируемой клетки — без актора, без событий от «ближайшего игрока».
 *
 * <p>WorldGuard — чужая библиотека вне classpath сборки, поэтому весь путь
 * идёт рефлексией по именам. Правила честного.fail-open: если плагина нет
 * (одиночная игра, голый сервер) или цепочка методов не разворачивается,
 * {@link #decision} возвращает {@code null} — решение остаётся за серверными
 * событиями, как раньше. Один неудачный разбор запоминается и не повторяется
 * каждый тик; сбой — отладочной строкой в лог.</p>
 *
 * <p>Формула автора: {@code piston_allowed = true, block_place = true,
 * block_break = true}. Разрешающий смысл WG-флагов считывается так: если
 * ХОТЬ ОДИН применимый регион выставил флаг в DENY — клетка закрыта;
 * NOT_SET/ABSTAIN/ALLOW не мешают. Имена флагов перебираем и по авторским
 * id (пользовательские регионы), и по каноническим id WG
 * ({@code block-place}, {@code block-break}, {@code piston-protection}).</p>
 */
public final class WorldGuardFlags {

    public enum Action { PLACE, BREAK }

    /** Кандидаты id флагов: авторские имена — первыми (у автора на сервере они живые). */
    private static final String[] PLACE_FLAGS = {"block_place", "block-place"};
    private static final String[] BREAK_FLAGS = {"block_break", "block-break"};
    /** Общие для обоих действий: «разрешить механическое движение блоков». */
    private static final String[] PISTON_FLAGS = {"piston_allowed", "piston-protection"};

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    private static final Map<String, Object> RESOLVED = new ConcurrentHashMap<>();
    private static volatile boolean unavailable;

    /** true — разрешено, false — запрещено хотя бы одним регионом, null — WG недоступен. */
    public static Boolean decision(ServerLevel level, BlockPos pos, Action action) {
        if (unavailable) return null;
        try {
            return query(level, pos, action);
        } catch (Throwable ignored) {
            unavailable = true;
            LOGGER.debug("WorldGuardFlags: мост не поднялся — откат к серверным событиям", ignored);
            return null;
        }
    }

    private static Boolean query(ServerLevel level, BlockPos pos, Action action) throws Exception {
        Object query = regionQuery(level);
        if (query == null) {
            unavailable = true;
            return null;
        }
        Object vector = blockVector(pos);
        Object regions = invoke(query, "getRegions", vector);
        if (regions == null) return null; // нет ни одного региона — свобода
        boolean[] denied = {false};
        int[] seen = {0};
        for (String flagId : flagIds(action)) {
            Object flag = flag(flagId);
            if (flag == null) continue;
            for (Object region : (Iterable<Object>) regions) {
                seen[0]++;
                Object state = invoke(region, "getFlagState", flag);
                if (state == null) continue;
                String name = String.valueOf(state);
                if (name.contains("DENY")) {
                    denied[0] = true;
                }
            }
        }
        if (seen[0] == 0) return null; // API другой масти — решают события
        return !denied[0];
    }

    private static String[] flagIds(Action action) {
        return switch (action) {
            case PLACE -> concat(PLACE_FLAGS, PISTON_FLAGS);
            case BREAK -> concat(BREAK_FLAGS, PISTON_FLAGS);
        };
    }

    private static String[] concat(String[] a, String[] b) {
        String[] r = new String[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    // ───────────────────────────── рефлексия WG ─────────────────────────────

    /** com.sk89q.worldguard.WorldGuard.getInstance().getPlatform().getRegionContainer().get(wgWorld). */
    private static Object regionQuery(ServerLevel level) throws Exception {
        Object cached = RESOLVED.get("query:" + level.dimension().location());
        if (cached != null) return cached == NULL ? null : cached;
        Object guard = staticCall("com.sk89q.worldguard.WorldGuard", "getInstance");
        if (guard == null) { unavailable = true; return null; }
        Object platform = call(guard, "getPlatform");
        Object regionContainer = platform == null ? null : call(platform, "getRegionContainer");
        if (regionContainer == null) { unavailable = true; return null; }
        Object bukkitWorld = bukkitWorld(level);
        if (bukkitWorld == null) { unavailable = true; return null; }
        Object query = call(regionContainer, "get", bukkitWorld);
        RESOLVED.put("query:" + level.dimension().location(), query == null ? NULL : query);
        return query;
    }

    /** org.bukkit.Bukkit.getWorld(имя-в-скобках-ключа-измерения) — как именует CraftBukkit. */
    private static Object bukkitWorld(ServerLevel level) throws Exception {
        Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
        Method getWorld = bukkit.getMethod("getWorld", String.class);
        String key = level.dimension().location().getPath();
        String name = "overworld".equals(key) ? "world" : key;
        Object world = getWorld.invoke(null, name);
        if (world == null && !"world".equals(name)) world = getWorld.invoke(null, "world_" + name);
        return world;
    }

    private static Object flag(String id) throws Exception {
        Object cached = RESOLVED.get("flag:" + id);
        if (cached != null) return cached == NULL ? null : cached;
        Object registry = null;
        try {
            Class<?> defaultFlag = Class.forName("com.sk89q.worldguard.protection.flags.DefaultFlag");
            registry = defaultFlag.getField("REGISTRY").get(null);
        } catch (ClassNotFoundException ignored) {
            try {
                Class<?> flags = Class.forName("com.sk89q.worldguard.protection.flags.Flags");
                registry = flags.getField("REGISTRY").get(null);
            } catch (ClassNotFoundException alsoIgnored) {
                // нет реестра — нет и моста
            }
        }
        Object flag = registry == null ? null : call(registry, "get", id);
        RESOLVED.put("flag:" + id, flag == null ? NULL : flag);
        return flag;
    }

    private static Object blockVector(BlockPos pos) throws Exception {
        Class<?> vector = Class.forName("com.sk89q.worldedit.math.BlockVector3");
        return vector.getMethod("at", int.class, int.class, int.class)
            .invoke(null, pos.getX(), pos.getY(), pos.getZ());
    }

    /** Первый метод нужного имени, принимающий ровно этот один аргумент. */
    private static Object invoke(Object target, String name, Object arg) throws Exception {
        for (Method m : target.getClass().getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != 1) continue;
            if (m.getParameterTypes()[0].isInstance(arg)) return m.invoke(target, arg);
        }
        return null;
    }

    private static Object call(Object target, String name, Object... args) {
        try {
            outer:
            for (Method m : target.getClass().getMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
                for (int i = 0; i < args.length; i++) {
                    if (args[i] != null && !m.getParameterTypes()[i].isInstance(args[i])) continue outer;
                }
                return m.invoke(target, args);
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private static Object staticCall(String className, String name) throws Exception {
        Class<?> c = Class.forName(className);
        return c.getMethod(name).invoke(null);
    }

    /** Отличник «кэшированно-нулевое» значение (ConcurrentHashMap null не любит). */
    private static final Object NULL = new Object();

    private WorldGuardFlags() {
    }
}
