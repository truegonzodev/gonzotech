package com.gonzotech.machines.reactor;

import com.gonzotech.core.registry.ModBlocks;
import com.gonzotech.machines.registry.ModMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * Сценарий аварии реактора при 10 000 °C (0.3.246). Место пролома и самая
 * горячая точка локализуются по сборке в момент создания аварии (argmax
 * локального тепловыделения матрицы → ближайший блок обшивки). Последовательность
 * живёт на уровне ({@link ReactorAccidentData}) и переживает разрушение
 * структуры и панели управления:
 *
 * <ul>
 *   <li>T0 — суб-тик перед аварией: показания GTH/воды/температуры/потока
 *       обнуляются («шкала уже 100 %, но ещё ничего не произошло»), чанк
 *       получает разовую дозу +5 mZt;</li>
 *   <li>T1 — вода в радиусе 4 от пролома → мёртвая слизь 80 % / радиоактивная
 *       слизь 20 %;</li>
 *   <li>T2 — хаотичная сфера радиуса 2 от самой горячей точки: не вода и не
 *       воздух → кориум/лава (чехлы → кориум, графит → расплавленный кориум,
 *       барий и бор → лава); самая горячая точка всегда → расплавленный кориум;
 *       обшивка, узлы и т.д. у пролома → лава 30 % в радиусе 2; сам пролом →
 *       кориум 100 %; из пролома — explosion_emitter и взрыв силой 2 (как TNT);</li>
 *   <li>T3 — из пролома: гейзер как у клапана, но в 2–3 раза быстрее, плюс
 *       lava-частицы (как выстреливает из лавы), плюс хаотичная дробь из
 *       падающих блоков мёртвой слизи (летят 3–10 блоков и приземляются
 *       блоками; на неподходящей поверхности дропа нет);</li>
 *   <li>T1..T5 — белая dust-пыль в радиусе 0–3 от пролома: 1–5 частиц за тик,
 *       ближе к пролому — плотнее; размер 0.5–3 блока, жизнь 3–5 с по размеру.</li>
 * </ul>
 */
public final class ReactorAccident {

    /** Разовая доза чанка при аварии: +5 mZt. */
    public static final double ACCIDENT_DOSE_NZT = 5.0 * com.gonzotech.radiation.RadUnits.MILLI;
    /** T1: вода в радиусе 4 от пролома заменяется слизью (80 % мёртвая). */
    public static final int WATER_SLIME_RADIUS = 4;
    public static final double DEAD_SLIME_CHANCE = 0.8;
    /** T2: хаотичная сфера вокруг горячей точки (радиус ±0.4..+1.2). */
    public static final double CORE_SPHERE_RADIUS = 2.0;
    /** T2: обшивка/узлы и т.д. у пролома → лава 30 % в радиусе 2. */
    public static final int SHELL_LAVA_RADIUS = 2;
    public static final double SHELL_LAVA_CHANCE = 0.3;
    /** T2: взрыв как от тнт силой 2. */
    public static final float EXPLOSION_POWER = 2.0F;
    /** T3: гейзер в 2–3 раза быстрее клапанного (0.45). */
    public static final double GEYSER_SPEED = 1.15;
    public static final int GEYSER_POOF_COUNT = 8;
    public static final int GEYSER_LAVA_COUNT = 8;
    /** T3: дробь из падающих блоков мёртвой слизи, летят 3–10 блоков. */
    public static final int FALLING_SLIME_MIN = 8;
    public static final int FALLING_SLIME_MAX = 16;
    public static final double FALLING_SPEED_MIN = 0.2;
    public static final double FALLING_SPEED_MAX = 0.7;
    /** Пыль: 1–5 частиц/тик в радиусе 0–3 от пролома (ближе — плотнее). */
    public static final int DUST_MIN_PER_TICK = 1;
    public static final int DUST_MAX_PER_TICK = 5;
    public static final int DUST_RADIUS = 3;
    /** Пыль крутится все тики аварии (3) и ещё 2 после. */
    public static final int DUST_TICKS = 5;

    private final long breachPos;
    private final long hottestPos;
    private final long minPos;
    private final long maxPos;
    private final long startTick;
    private int fired;

    ReactorAccident(BlockPos breach, BlockPos hottest, BlockPos min, BlockPos max, long startTick) {
        this.breachPos = breach.immutable().asLong();
        this.hottestPos = hottest.immutable().asLong();
        this.minPos = min.immutable().asLong();
        this.maxPos = max.immutable().asLong();
        this.startTick = startTick;
    }

    public BlockPos breachPos() {
        return BlockPos.of(breachPos);
    }

    /** Драйвер уровня: переживает разрушение панели и структуры. */
    @SubscribeEvent
    public static void onLevelTickPost(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ReactorAccidentData.get(level).tickAccidents(level, level.getGameTime());
    }

    /** Ближайший блок обшивки (внешний слой куба) к точке — место пролома. */
    static BlockPos nearestShellBlock(BlockPos target, BlockPos min, BlockPos max) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!onShell(pos, min, max)) continue;
            BlockPos candidate = pos.immutable();
            double dist = candidate.distSqr(target);
            if (dist < bestDist) {
                bestDist = dist;
                best = candidate;
            }
        }
        return best == null ? target : best;
    }

    private static boolean onShell(BlockPos pos, BlockPos min, BlockPos max) {
        return pos.getX() == min.getX() || pos.getX() == max.getX()
            || pos.getY() == min.getY() || pos.getY() == max.getY()
            || pos.getZ() == min.getZ() || pos.getZ() == max.getZ();
    }

    void tick(ServerLevel level, long gameTime) {
        long elapsed = gameTime - startTick;
        if (elapsed < 1) return;
        if ((fired & 1) == 0) {
            stageWaterToSlime(level);
            fired |= 1;
        }
        if (elapsed >= 2 && (fired & 2) == 0) {
            stageMeltdown(level);
            fired |= 2;
        }
        if (elapsed >= 3 && (fired & 4) == 0) {
            stageEruption(level);
            fired |= 4;
        }
        if (elapsed <= DUST_TICKS) {
            spawnDust(level);
        }
    }

    boolean finished(long gameTime) {
        return gameTime - startTick > DUST_TICKS && (fired & 7) == 7;
    }

    /** T1: вода в радиусе 4 от пролома → мёртвая слизь 80 % / радиоактивная 20 %. */
    private void stageWaterToSlime(ServerLevel level) {
        BlockPos center = breachPos();
        RandomSource random = level.random;
        int radius = WATER_SLIME_RADIUS;
        for (BlockPos pos : BlockPos.betweenClosed(
            center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            BlockPos target = pos.immutable();
            if (target.distSqr(center) > (double) radius * radius + 0.25) continue;
            if (!level.getBlockState(target).is(Blocks.WATER)) continue;
            BlockState slime = random.nextDouble() < DEAD_SLIME_CHANCE
                ? ModBlocks.DEAD_SLIME_BLOCK.get().defaultBlockState()
                : ModBlocks.RADIOACTIVE_SLIME_BLOCK.get().defaultBlockState();
            level.setBlock(target, slime, 3);
        }
    }

    /** T2: плавится ядро, обшивка у пролома, сам пролом и взрыв силой 2. */
    private void stageMeltdown(ServerLevel level) {
        BlockPos hot = BlockPos.of(hottestPos);
        BlockPos breach = breachPos();
        RandomSource random = level.random;
        Set<Long> touched = new HashSet<>();

        // Ядро: хаотичная сфера радиуса 2 от самой горячей точки.
        for (BlockPos pos : BlockPos.betweenClosed(hot.offset(-4, -4, -4), hot.offset(4, 4, 4))) {
            BlockPos target = pos.immutable();
            double dist = Math.sqrt(target.distSqr(hot));
            double radius = CORE_SPHERE_RADIUS - 0.4 + random.nextDouble() * 1.6;
            if (dist > radius) continue;
            BlockState state = level.getBlockState(target);
            if (state.isAir() || state.is(Blocks.WATER)) continue;
            if (level.setBlock(target, coreReplacement(state.getBlock(), random), 3)) {
                touched.add(target.asLong());
            }
        }

        // Самая горячая точка — всегда расплавленный кориум (плавится первой).
        if (level.setBlock(hot, ModBlocks.MOLTEN_CORIUM.get().defaultBlockState(), 3)) {
            touched.add(hot.asLong());
        }

        // Обшивка, узлы и т.д. у пролома: лава 30 % (слой T1 не трогаем).
        for (BlockPos pos : BlockPos.betweenClosed(
            breach.offset(-SHELL_LAVA_RADIUS, -SHELL_LAVA_RADIUS, -SHELL_LAVA_RADIUS),
            breach.offset(SHELL_LAVA_RADIUS, SHELL_LAVA_RADIUS, SHELL_LAVA_RADIUS))) {
            BlockPos target = pos.immutable();
            if (target.distSqr(breach) > (double) SHELL_LAVA_RADIUS * SHELL_LAVA_RADIUS + 0.25) continue;
            if (touched.contains(target.asLong())) continue;
            BlockState state = level.getBlockState(target);
            if (state.isAir() || state.is(Blocks.WATER)
                || state.is(ModBlocks.DEAD_SLIME_BLOCK.get())
                || state.is(ModBlocks.RADIOACTIVE_SLIME_BLOCK.get())) continue;
            if (random.nextDouble() < SHELL_LAVA_CHANCE) {
                level.setBlock(target, Blocks.LAVA.defaultBlockState(), 3);
            }
        }

        // Самый горячий блок обшивки — кориум 100 %.
        level.setBlock(breach, ModBlocks.CORIUM.get().defaultBlockState(), 3);

        // Из места пролома: explosion_emitter и взрыв как от тнт силой 2.
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
            breach.getX() + 0.5, breach.getY() + 0.5, breach.getZ() + 0.5,
            1, 0.0, 0.0, 0.0, 0.0);
        level.explode(null,
            breach.getX() + 0.5, breach.getY() + 0.5, breach.getZ() + 0.5,
            EXPLOSION_POWER, Level.ExplosionInteraction.BLOCK);
    }

    /** Чехлы → кориум; графит → расплавленный кориум; барий и бор → лава; прочее → кориум/лава. */
    private static BlockState coreReplacement(Block block, RandomSource random) {
        if (block == ModMachines.THIRD_URANIUM_REACTOR_FUEL_SHEATH.get()) {
            return ModBlocks.CORIUM.get().defaultBlockState();
        }
        if (block == ModBlocks.GRAPHITE_BLOCK.get()) {
            return ModBlocks.MOLTEN_CORIUM.get().defaultBlockState();
        }
        if (ReactorMatrix.isMetalBlock(block, "boron_block")
            || ReactorMatrix.isMetalBlock(block, "barium_block")) {
            return Blocks.LAVA.defaultBlockState();
        }
        return random.nextBoolean()
            ? ModBlocks.CORIUM.get().defaultBlockState()
            : Blocks.LAVA.defaultBlockState();
    }

    /** T3: гейзер наружу (в 2–3 раза быстрее клапанного), lava-частицы и дробь из падающей слизи. */
    private void stageEruption(ServerLevel level) {
        BlockPos breach = breachPos();
        RandomSource random = level.random;
        Direction outward = ReactorRuntime.outwardDirection(
            breach, BlockPos.of(minPos), BlockPos.of(maxPos));
        double ox = outward.getStepX();
        double oy = outward.getStepY();
        double oz = outward.getStepZ();

        for (int i = 0; i < GEYSER_POOF_COUNT; i++) {
            level.sendParticles(ParticleTypes.POOF,
                breach.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.4 + ox * 0.55,
                breach.getY() + 0.5 + (random.nextDouble() - 0.5) * 0.4 + oy * 0.55,
                breach.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4 + oz * 0.55,
                0,
                (ox + (random.nextDouble() - 0.5) * 0.35) * GEYSER_SPEED,
                (oy + (random.nextDouble() - 0.5) * 0.35) * GEYSER_SPEED,
                (oz + (random.nextDouble() - 0.5) * 0.35) * GEYSER_SPEED,
                1.0);
        }
        for (int i = 0; i < GEYSER_LAVA_COUNT; i++) {
            level.sendParticles(ParticleTypes.LAVA,
                breach.getX() + 0.5, breach.getY() + 0.5, breach.getZ() + 0.5,
                0,
                (ox + (random.nextDouble() - 0.5) * 0.9) * GEYSER_SPEED,
                (oy + random.nextDouble() * 0.8) * GEYSER_SPEED,
                (oz + (random.nextDouble() - 0.5) * 0.9) * GEYSER_SPEED,
                1.0);
        }

        int count = FALLING_SLIME_MIN + random.nextInt(FALLING_SLIME_MAX - FALLING_SLIME_MIN + 1);
        BlockPos spawn = level.getBlockState(breach).isAir() ? breach : breach.relative(outward);
        for (int i = 0; i < count; i++) {
            FallingBlockEntity falling = FallingBlockEntity.fall(
                level, spawn, ModBlocks.DEAD_SLIME_BLOCK.get().defaultBlockState());
            if (falling == null) continue;
            // На неподходящей поверхности слизь не дропается (ни блоком, ни ведром).
            falling.dropItem = false;
            double speed = FALLING_SPEED_MIN
                + random.nextDouble() * (FALLING_SPEED_MAX - FALLING_SPEED_MIN);
            falling.setDeltaMovement(
                (ox + (random.nextDouble() - 0.5) * 1.6) * speed,
                (0.25 + random.nextDouble() * 0.75) + Math.max(0, oy) * speed,
                (oz + (random.nextDouble() - 0.5) * 1.6) * speed);
        }
    }

    /** Пыль T1..T5: ближе к пролому — плотнее; частицы живут 10–15 с. */
    private void spawnDust(ServerLevel level) {
        RandomSource random = level.random;
        BlockPos breach = breachPos();
        int count = DUST_MIN_PER_TICK + random.nextInt(DUST_MAX_PER_TICK - DUST_MIN_PER_TICK + 1);
        for (int i = 0; i < count; i++) {
            double radius = DUST_RADIUS * Math.pow(random.nextDouble(), 1.6);
            double theta = random.nextDouble() * Math.PI * 2.0;
            double vertical = random.nextDouble() * 2.0 - 1.0;
            double horizontal = Math.sqrt(Math.max(0.0, 1.0 - vertical * vertical));
            level.sendParticles(com.gonzotech.core.registry.ModParticles.REACTOR_DUST.get(),
                breach.getX() + 0.5 + radius * horizontal * Math.cos(theta),
                breach.getY() + 0.5 + radius * vertical * 0.7,
                breach.getZ() + 0.5 + radius * horizontal * Math.sin(theta),
                0,
                (random.nextDouble() - 0.5) * 0.02,
                -0.01 - random.nextDouble() * 0.02,
                (random.nextDouble() - 0.5) * 0.02,
                1.0);
        }
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Breach", breachPos);
        tag.putLong("Hottest", hottestPos);
        tag.putLong("Min", minPos);
        tag.putLong("Max", maxPos);
        tag.putLong("StartTick", startTick);
        tag.putInt("Fired", fired);
        return tag;
    }

    static ReactorAccident load(CompoundTag tag) {
        ReactorAccident accident = new ReactorAccident(
            BlockPos.of(tag.getLong("Breach")),
            BlockPos.of(tag.getLong("Hottest")),
            BlockPos.of(tag.getLong("Min")),
            BlockPos.of(tag.getLong("Max")),
            tag.getLong("StartTick"));
        accident.fired = tag.getInt("Fired");
        return accident;
    }
}
