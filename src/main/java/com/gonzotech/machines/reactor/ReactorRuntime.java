package com.gonzotech.machines.reactor;

import com.gonzotech.machines.block.entity.ReactorControlPanelBlockEntity;
import com.gonzotech.machines.energy.GtBuffer;
import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.ResourceBuffer;
import com.gonzotech.machines.energy.Sinks;
import com.gonzotech.machines.network.PipeRouting;
import com.gonzotech.machines.network.PipeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Persistent water/GTH buffers and ReaCalc 1.7 server-time simulation for one reactor. */
public final class ReactorRuntime {

    public static final int MENU_DATA_COUNT = 20;
    /** GTH-шкала выше 97% добавляет выработку/2000 °C за тик отдельным слоем. */
    public static final double GTH_BACKPRESSURE_FILL = 0.97;
    public static final double GTH_BACKPRESSURE_DIVISOR = 2000.0;
    /** Слой перегрева не постоянный: при снятии условия полностью увядает за 20 сек. */
    public static final int GTH_BACKPRESSURE_FADE_TICKS = 400;
    /** Запитанная панель + GTH >99.9% открывает клапан: гейзер и сброс 20% за 1.75 сек. */
    public static final double VALVE_TRIGGER_FILL = 0.999;
    public static final int VALVE_VENT_TICKS = 35;
    public static final int VALVE_VENT_FIFTHS = 5;
    private static final int ENERGY_SYNC_BASE = 10_000;
    private static final int MAX_SYNC_PART = Short.MAX_VALUE;
    private static final long MAX_SYNC_TEMPERATURE_TENTHS =
        (long) MAX_SYNC_PART * ENERGY_SYNC_BASE + ENERGY_SYNC_BASE - 1L;
    private static final long MAX_SYNC_RATE_TENTHS =
        (long) MAX_SYNC_PART * ENERGY_SYNC_BASE + ENERGY_SYNC_BASE - 1L;

    private ResourceBuffer water = new ResourceBuffer(0);
    private GtBuffer gth = new GtBuffer(0);
    private int zoneSide;
    private long[] heatPorts = new long[0];
    private long[] waterPorts = new long[0];
    private long[] valvePorts = new long[0];
    private Set<Long> heatPortSet = Set.of();
    private Set<Long> waterPortSet = Set.of();

    private final ReactorHeatDynamics.State heat = new ReactorHeatDynamics.State();
    private double gthRemainderMilli;
    private int outputPortCursor;

    private MatrixCoefficients.Analysis lastAnalysis;
    private ReactorMatrix.OperatingSnapshot lastSnapshot;
    private ReactorHeatOutputCurve outputCurve;
    private ReactorNeutronMetrics.Values neutronMetrics = ReactorNeutronMetrics.Values.ZERO;
    private int lastLoadedTvels = -1;
    private long lastSnapshotTick = Long.MIN_VALUE;
    private boolean snapshotDirty = true;
    private double generationRate;
    private double usefulGenerationRate;
    private double modelWaterTemperature = ReactorOperatingMetrics.AMBIENT;
    private double modelWaterPressureIndex = 1.0;
    private double backpressureBonus;
    private double backpressureFadePerTick;
    private int fuelAgeTicks;
    private int ventTicksRemaining;
    private long ventRemainingMilli;

    public record Metrics(double hotTemperature, double meanTemperature,
                          double generationRate, double usefulGenerationRate,
                          ReactorNeutronMetrics.Values neutrons, int loadedTvels,
                          String phase) {
    }

    public int zoneSide() {
        return zoneSide;
    }

    /** Called only after structure validation; buffers retain their contents when capacity changes. */
    public void configure(int side, long[] heatPorts, long[] waterPorts, long[] valvePorts) {
        int nextSide = Math.max(0, side);
        long[] nextHeatPorts = heatPorts == null ? new long[0] : heatPorts.clone();
        long[] nextWaterPorts = waterPorts == null ? new long[0] : waterPorts.clone();
        long[] nextValvePorts = valvePorts == null ? new long[0] : valvePorts.clone();
        boolean changed = zoneSide != nextSide
            || !Arrays.equals(this.heatPorts, nextHeatPorts)
            || !Arrays.equals(this.waterPorts, nextWaterPorts)
            || !Arrays.equals(this.valvePorts, nextValvePorts);

        if (zoneSide != nextSide) resizeBuffers(nextSide);
        zoneSide = nextSide;
        this.heatPorts = nextHeatPorts;
        this.waterPorts = nextWaterPorts;
        this.valvePorts = nextValvePorts;
        heatPortSet = positionsOf(nextHeatPorts);
        waterPortSet = positionsOf(nextWaterPorts);
        if (changed || lastSnapshot == null) snapshotDirty = true;
    }

    /** Stop I/O immediately when the shell becomes invalid; preserve stored resources and thermal state. */
    public void deconfigure() {
        heatPorts = new long[0];
        waterPorts = new long[0];
        valvePorts = new long[0];
        heatPortSet = Set.of();
        waterPortSet = Set.of();
        lastAnalysis = null;
        lastSnapshot = null;
        outputCurve = null;
        lastLoadedTvels = -1;
        lastSnapshotTick = Long.MIN_VALUE;
        snapshotDirty = true;
        outputPortCursor = 0;
    }

    private void resizeBuffers(int side) {
        long oldGth = gth.amountAsLong();
        int oldWater = water.amount();
        water = new ResourceBuffer(waterCapacity(side));
        gth = new GtBuffer(gthCapacityMilli(side));
        water.receive(oldWater, false);
        gth.receive(oldGth, false);
        if (gth.amountAsLong() < oldGth || water.amount() < oldWater) {
            gthRemainderMilli = 0.0;
        }
    }

    public static int waterCapacity(int side) {
        return Math.max(0, side) * MachineDefs.REACTOR_WATER_CAPACITY_PER_ZONE_SIDE;
    }

    public static long gthCapacityMilli(int side) {
        return (long) Math.max(0, side)
            * MachineDefs.REACTOR_GTH_CAPACITY_PER_ZONE_SIDE * MachineDefs.MILLI;
    }

    private static Set<Long> positionsOf(long[] positions) {
        if (positions.length == 0) return Set.of();
        Set<Long> result = new HashSet<>(positions.length * 2);
        for (long packed : positions) result.add(packed);
        return Set.copyOf(result);
    }

    public boolean isWaterPort(BlockPos pos) {
        return pos != null && waterPortSet.contains(pos.asLong());
    }

    public long receiveWater(BlockPos port, long amount, boolean simulate) {
        if (port == null || amount <= 0 || !waterPortSet.contains(port.asLong())) return 0L;
        int accepted = water.receive(amount, simulate);
        return accepted;
    }

    public Metrics metrics() {
        double hot = hotWithBackpressure();
        return new Metrics(hot, heat.meanTemperature(hot), generationRate,
            usefulGenerationRate, neutronMetrics, Math.max(0, lastLoadedTvels), heat.phase());
    }

    /** Integrator reading plus the GTH back-pressure layer shown on the temperature gauge. */
    private double hotWithBackpressure() {
        return heat.temperature() + Math.max(0.0, backpressureBonus);
    }

    /** One server tick: sample fuel/geometry, integrate temperature, generate GTH, then route it. */
    public void serverTick(ServerLevel level, ReactorControlPanelBlockEntity panel) {
        if (!panel.isRuntimeReady()) return;
        long gameTime = level.getGameTime();
        if (snapshotDirty || lastSnapshot == null || lastSnapshotTick == Long.MIN_VALUE
            || gameTime - lastSnapshotTick >= MachineDefs.REACTOR_FUEL_SNAPSHOT_INTERVAL) {
            refreshSnapshot(level, panel, gameTime);
        }
        if (lastSnapshot == null) return;

        boolean changed = heat.advance(1.0);
        ReactorHeatOutputCurve.Output output = heat.output(Math.max(0.0, backpressureBonus));
        generationRate = output.exchange();
        usefulGenerationRate = output.useful();
        applyModelBath(output);
        if (updateBackpressureLayer()) changed = true;
        if (accumulateGeneration(generationRate)) changed = true;
        if (pushGth(level, gameTime)) changed = true;
        if (valveVentTick(level, panel)) changed = true;
        if (++fuelAgeTicks >= 20) {
            fuelAgeTicks = 0;
            if (ReactorFuelStorage.ageLoadedFuel(level, panel)) changed = true;
        }
        if (changed) panel.setChanged();
    }

    /** The archived water bath readings of the current output point. */
    private void applyModelBath(ReactorHeatOutputCurve.Output output) {
        modelWaterTemperature = output.waterTemperature();
        modelWaterPressureIndex = output.pressureIndex();
    }

    /**
     * GTH-шкала выше 97% добавляет выработку/2000 °C за тик отдельным слоем
     * нагрева поверх плато (и тем самым поднимает выработку). Слой не
     * перманентный: при падении заполнения ниже 97% или после выемки ТВЭЛов
     * он за 20 секунд полностью увядает.
     */
    private boolean updateBackpressureLayer() {
        double before = backpressureBonus;
        if (backpressureFadePerTick > 0.0) {
            backpressureBonus = Math.max(0.0, backpressureBonus - backpressureFadePerTick);
            if (backpressureBonus <= 0.0) backpressureFadePerTick = 0.0;
        } else if (gthFillFraction() > GTH_BACKPRESSURE_FILL) {
            backpressureBonus += generationRate / GTH_BACKPRESSURE_DIVISOR;
        } else if (backpressureBonus > 0.0) {
            startBackpressureFade();
            backpressureBonus = Math.max(0.0, backpressureBonus - backpressureFadePerTick);
        }
        return backpressureBonus != before;
    }

    private void startBackpressureFade() {
        if (backpressureFadePerTick > 0.0 || backpressureBonus <= 0.0) return;
        backpressureFadePerTick = backpressureBonus / GTH_BACKPRESSURE_FADE_TICKS;
    }

    private double gthFillFraction() {
        long capacityMilli = gthCapacityMilli(zoneSide);
        return capacityMilli <= 0L ? 0.0
            : Math.min(1.0, (double) gth.amountAsLong() / (double) capacityMilli);
    }

    /**
     * Запитанная редстоуном панель + GTH >99.9% открывает клапаны: гейзер
     * poof-частиц наружу противоположно активной зоне (как у очистителя
     * воздуха, но быстрее) и за 1.75 сек сбрасывает 20% текущего GTH.
     */
    private boolean valveVentTick(ServerLevel level, ReactorControlPanelBlockEntity panel) {
        if (valvePorts.length == 0) return false;
        if (ventTicksRemaining <= 0) {
            if (!level.hasNeighborSignal(panel.getBlockPos())) return false;
            if (gthFillFraction() <= VALVE_TRIGGER_FILL) return false;
            long ventTotal = gth.amountAsLong() / VALVE_VENT_FIFTHS;
            if (ventTotal <= 0L) return false;
            ventRemainingMilli = ventTotal;
            ventTicksRemaining = VALVE_VENT_TICKS;
        }
        long take = ventRemainingMilli;
        if (ventTicksRemaining > 1) {
            take = (ventRemainingMilli + ventTicksRemaining - 1) / ventTicksRemaining;
        }
        gth.extract(take, false);
        ventRemainingMilli = Math.max(0L, ventRemainingMilli - take);
        ventTicksRemaining--;
        spawnValveGeysers(level, panel);
        return true;
    }

    private void spawnValveGeysers(ServerLevel level, ReactorControlPanelBlockEntity panel) {
        BlockPos min = panel.min();
        BlockPos max = panel.max();
        if (min == null || max == null) return;
        for (long packed : valvePorts) {
            BlockPos pos = BlockPos.of(packed);
            Direction outward = outwardDirection(pos, min, max);
            // count=0 — точная скорость (не гауссова), как у очистителя воздуха, но выше.
            double speed = 0.45;
            double vx = outward.getStepX() * speed;
            double vy = outward.getStepY() * speed;
            double vz = outward.getStepZ() * speed;
            for (int i = 0; i < 3; i++) {
                level.sendParticles(ParticleTypes.POOF,
                    pos.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 0.4
                        + outward.getStepX() * 0.55,
                    pos.getY() + 0.5 + (level.random.nextDouble() - 0.5) * 0.4
                        + outward.getStepY() * 0.55,
                    pos.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 0.4
                        + outward.getStepZ() * 0.55,
                    0, vx, vy, vz, 1.0);
            }
        }
    }

    /** Наружу из куба — противоположно активной зоне, мимо которой стоит клапан. */
    private static Direction outwardDirection(BlockPos pos, BlockPos min, BlockPos max) {
        if (pos.getX() == max.getX()) return Direction.EAST;
        if (pos.getX() == min.getX()) return Direction.WEST;
        if (pos.getY() == max.getY()) return Direction.UP;
        if (pos.getY() == min.getY()) return Direction.DOWN;
        if (pos.getZ() == max.getZ()) return Direction.SOUTH;
        return Direction.NORTH;
    }

    private void refreshSnapshot(ServerLevel level, ReactorControlPanelBlockEntity panel, long gameTime) {
        lastSnapshotTick = gameTime;
        snapshotDirty = false;
        ReactorMatrix.OperatingSnapshot snapshot = ReactorMatrix.operatingSnapshot(level, panel);
        MatrixCoefficients.Analysis analysis = snapshot.diagnostic().analysis();
        boolean matrixChanged = analysis != lastAnalysis;
        boolean fuelChanged = snapshot.loadedTvels() != lastLoadedTvels;
        boolean firstConfiguration = lastSnapshot == null;

        if (matrixChanged || fuelChanged || firstConfiguration) {
            if (matrixChanged || outputCurve == null) {
                outputCurve = new ReactorHeatOutputCurve(analysis);
            }
            if (fuelChanged && snapshot.loadedTvels() < lastLoadedTvels) {
                // Вынутые ТВЭЛы: плато падает, а слой от полной шкалы гаснет за 20 сек.
                startBackpressureFade();
            }
            heat.configure(ReactorHeatDynamics.parameters(analysis, snapshot.metrics(), outputCurve));
            neutronMetrics = ReactorNeutronMetrics.calculate(analysis, snapshot.metrics());
            lastAnalysis = analysis;
            lastLoadedTvels = snapshot.loadedTvels();
        }
        lastSnapshot = snapshot;
    }

    private boolean accumulateGeneration(double gthPerTick) {
        if (!Double.isFinite(gthPerTick) || gthPerTick <= 0.0) return false;
        if (gth.isFull()) {
            boolean changed = gthRemainderMilli != 0.0;
            gthRemainderMilli = 0.0;
            return changed;
        }

        double exactMilli = gthPerTick * MachineDefs.MILLI + gthRemainderMilli;
        if (!Double.isFinite(exactMilli) || exactMilli >= Long.MAX_VALUE) {
            gthRemainderMilli = 0.0;
            return gth.receive(Long.MAX_VALUE, false) > 0;
        }
        if (exactMilli <= 0.0) return false;
        long wholeMilli = (long) Math.floor(exactMilli);
        if (wholeMilli <= 0L) {
            boolean changed = exactMilli != gthRemainderMilli;
            gthRemainderMilli = exactMilli;
            return changed;
        }
        long accepted = gth.receive(wholeMilli, false);
        gthRemainderMilli = accepted < wholeMilli ? 0.0 : exactMilli - wholeMilli;
        return accepted > 0L;
    }

    private boolean pushGth(ServerLevel level, long gameTime) {
        if (gth.isEmpty() || heatPorts.length == 0) return false;
        long remaining = gth.amountAsLong();
        if (remaining <= 0L) return false;

        boolean movedAnything = false;
        int attempts = Math.min(heatPorts.length, MachineDefs.REACTOR_MAX_OUTPUT_ROUTE_ATTEMPTS);
        for (int tried = 0; tried < attempts && remaining > 0L; tried++) {
            int index = Math.floorMod(outputPortCursor++, heatPorts.length);
            BlockPos port = BlockPos.of(heatPorts[index]);
            long portLimit = ReactorStructure.gthPortLimit(level, port);
            if (portLimit <= 0L) continue;
            long budget = Math.min(remaining, portLimit);
            long moved = PipeRouting.drainFromMultiblockPort(level, port, PipeType.HEAT,
                budget, gameTime,
                (target, ignored) -> target instanceof Sinks.GthSink sink
                    ? sink::receiveGth : null,
                ReactorStructure::isMember);
            if (moved > 0L) {
                gth.extract(moved, false);
                remaining = Math.max(0L, remaining - moved);
                movedAnything = true;
            }
        }
        return movedAnything;
    }

    /** Values synchronized by ContainerData; long-like readings use signed-short-safe parts. */
    public int menuData(int index) {
        long gthAmountTenths = gth.amountAsLong() / 100L;
        long gthCapacityTenths = gthCapacityMilli(zoneSide) / 100L;
        double hot = hotWithBackpressure();
        long hotTenths = temperatureTenths(hot);
        long meanTenths = temperatureTenths(heat.meanTemperature(hot));
        long rateTenths = rateTenths(generationRate);
        long waterTemperatureTenths = temperatureTenths(modelWaterTemperature);
        return switch (index) {
            case 0 -> water.amount();
            case 1 -> water.capacity();
            case 2 -> (int) (gthAmountTenths % ENERGY_SYNC_BASE);
            case 3 -> shortPart(gthAmountTenths / ENERGY_SYNC_BASE);
            case 4 -> (int) (gthCapacityTenths % ENERGY_SYNC_BASE);
            case 5 -> shortPart(gthCapacityTenths / ENERGY_SYNC_BASE);
            case 6 -> (int) (hotTenths % ENERGY_SYNC_BASE);
            case 7 -> shortPart(hotTenths / ENERGY_SYNC_BASE);
            case 8 -> (int) (meanTenths % ENERGY_SYNC_BASE);
            case 9 -> shortPart(meanTenths / ENERGY_SYNC_BASE);
            case 10 -> percentTenths(neutronMetrics.strongestFlow());
            case 11 -> percentTenths(neutronMetrics.averageFlow());
            case 12 -> percentTenths(neutronMetrics.strongestLeak());
            case 13 -> percentTenths(neutronMetrics.averageLeak());
            case 14 -> (int) (rateTenths % ENERGY_SYNC_BASE);
            case 15 -> shortPart(rateTenths / ENERGY_SYNC_BASE);
            case 16 -> Math.max(0, lastLoadedTvels);
            case 17 -> (int) (waterTemperatureTenths % ENERGY_SYNC_BASE);
            case 18 -> shortPart(waterTemperatureTenths / ENERGY_SYNC_BASE);
            case 19 -> pressureTenthsMpa(modelWaterPressureIndex);
            default -> 0;
        };
    }

    private static int shortPart(long value) {
        return (int) Math.min(MAX_SYNC_PART, Math.max(0L, value));
    }

    private static long temperatureTenths(double temperature) {
        if (!Double.isFinite(temperature) || temperature <= 0.0) return 0L;
        double tenths = temperature * 10.0;
        if (tenths >= MAX_SYNC_TEMPERATURE_TENTHS) return MAX_SYNC_TEMPERATURE_TENTHS;
        return Math.max(0L, Math.round(tenths));
    }

    private static int percentTenths(double fraction) {
        if (!Double.isFinite(fraction) || fraction <= 0.0) return 0;
        return (int) Math.min(MAX_SYNC_PART, Math.round(fraction * 1_000.0));
    }

    private static long rateTenths(double rate) {
        if (!Double.isFinite(rate) || rate <= 0.0) return 0L;
        double tenths = rate * 10.0;
        return tenths >= MAX_SYNC_RATE_TENTHS
            ? MAX_SYNC_RATE_TENTHS : Math.max(0L, Math.round(tenths));
    }

    private static int pressureTenthsMpa(double pressureIndex) {
        if (!Double.isFinite(pressureIndex) || pressureIndex <= 0.0) return 0;
        // One ReaCalc conditional pressure unit equals 0.1 MPa, or one tenth-MPa.
        return (int) Math.min(MAX_SYNC_PART, Math.round(pressureIndex));
    }

    public void save(CompoundTag tag) {
        tag.putInt("ReactorZoneSide", zoneSide);
        water.save(tag, "ReactorWater");
        gth.save(tag, "ReactorGth");
        tag.putDouble("ReactorGthRemainderMilli", gthRemainderMilli);
        tag.putDouble("ReactorBackpressureBonus", backpressureBonus);
        tag.putDouble("ReactorBackpressureFade", backpressureFadePerTick);
        tag.putInt("ReactorFuelAgeTicks", fuelAgeTicks);
        tag.putInt("ReactorValveVentTicks", ventTicksRemaining);
        tag.putLong("ReactorValveVentRemaining", ventRemainingMilli);
        ReactorHeatDynamics.Saved saved = heat.save();
        tag.putBoolean("ReactorHeatConfigured", saved.configured());
        tag.putDouble("ReactorHeatH", saved.h());
        tag.putDouble("ReactorHeatBase", saved.base());
        tag.putDouble("ReactorHeatExcess", saved.excess());
        tag.putDouble("ReactorHeatVelocity", saved.velocity());
        tag.putDouble("ReactorHeatHold", saved.hold());
        tag.putDouble("ReactorCoolingAge", saved.coolingAge());
    }

    public void load(CompoundTag tag) {
        zoneSide = Math.max(0, Math.min(ReactorStructure.MAX_SIZE - 2,
            tag.getInt("ReactorZoneSide")));
        water = new ResourceBuffer(waterCapacity(zoneSide));
        gth = new GtBuffer(gthCapacityMilli(zoneSide));
        water.load(tag, "ReactorWater");
        gth.load(tag, "ReactorGth");
        double remainder = tag.getDouble("ReactorGthRemainderMilli");
        gthRemainderMilli = Double.isFinite(remainder) ? Math.max(0.0, Math.min(0.999999999, remainder)) : 0.0;
        backpressureBonus = finiteOr(tag.getDouble("ReactorBackpressureBonus"), 0.0);
        backpressureFadePerTick = finiteOr(tag.getDouble("ReactorBackpressureFade"), 0.0);
        fuelAgeTicks = Math.max(0, Math.min(19, tag.getInt("ReactorFuelAgeTicks")));
        ventTicksRemaining = Math.max(0, tag.getInt("ReactorValveVentTicks"));
        ventRemainingMilli = Math.max(0L, tag.getLong("ReactorValveVentRemaining"));

        heatPorts = new long[0];
        waterPorts = new long[0];
        valvePorts = new long[0];
        heatPortSet = Set.of();
        waterPortSet = Set.of();
        boolean hasHeatState = tag.contains("ReactorHeatBase");
        heat.restore(hasHeatState
            ? new ReactorHeatDynamics.Saved(tag.getBoolean("ReactorHeatConfigured"),
                tag.getDouble("ReactorHeatH"), tag.getDouble("ReactorHeatBase"),
                tag.getDouble("ReactorHeatExcess"), tag.getDouble("ReactorHeatVelocity"),
                tag.getDouble("ReactorHeatHold"), tag.getDouble("ReactorCoolingAge"))
            : new ReactorHeatDynamics.Saved(false, 0.0,
                ReactorHeatDynamics.AMBIENT, 0.0, 0.0,
                ReactorHeatDynamics.DELAY, -1.0));

        lastAnalysis = null;
        lastSnapshot = null;
        outputCurve = null;
        neutronMetrics = ReactorNeutronMetrics.Values.ZERO;
        lastLoadedTvels = -1;
        lastSnapshotTick = Long.MIN_VALUE;
        snapshotDirty = true;
        generationRate = 0.0;
        usefulGenerationRate = 0.0;
        modelWaterTemperature = ReactorOperatingMetrics.AMBIENT;
        modelWaterPressureIndex = 1.0;
        outputPortCursor = 0;
    }

    private static double finiteOr(double value, double fallback) {
        return Double.isFinite(value) ? Math.max(0.0, value) : fallback;
    }
}
