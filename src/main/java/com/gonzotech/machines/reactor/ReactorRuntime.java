package com.gonzotech.machines.reactor;

import com.gonzotech.machines.block.entity.ReactorControlPanelBlockEntity;
import com.gonzotech.machines.energy.GtBuffer;
import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.ResourceBuffer;
import com.gonzotech.machines.energy.Sinks;
import com.gonzotech.machines.network.PipeRouting;
import com.gonzotech.machines.network.PipeType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Persistent water/GTH buffers and ReaCalc 1.7 server-time simulation for one reactor. */
public final class ReactorRuntime {

    public static final int MENU_DATA_COUNT = 16;
    private static final int ENERGY_SYNC_BASE = 10_000;
    private static final int MAX_SYNC_PART = Short.MAX_VALUE;
    private static final long MAX_SYNC_TEMPERATURE_TENTHS =
        (long) MAX_SYNC_PART * ENERGY_SYNC_BASE + ENERGY_SYNC_BASE - 1L;

    private ResourceBuffer water = new ResourceBuffer(0);
    private GtBuffer gth = new GtBuffer(0);
    private int zoneSide;
    private long[] heatPorts = new long[0];
    private long[] waterPorts = new long[0];
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

    public record Metrics(double hotTemperature, double meanTemperature,
                          double generationRate, double usefulGenerationRate,
                          ReactorNeutronMetrics.Values neutrons, int loadedTvels,
                          String phase) {
    }

    public int zoneSide() {
        return zoneSide;
    }

    /** Called only after structure validation; buffers retain their contents when capacity changes. */
    public void configure(int side, long[] heatPorts, long[] waterPorts) {
        int nextSide = Math.max(0, side);
        long[] nextHeatPorts = heatPorts == null ? new long[0] : heatPorts.clone();
        long[] nextWaterPorts = waterPorts == null ? new long[0] : waterPorts.clone();
        boolean changed = zoneSide != nextSide
            || !Arrays.equals(this.heatPorts, nextHeatPorts)
            || !Arrays.equals(this.waterPorts, nextWaterPorts);

        if (zoneSide != nextSide) resizeBuffers(nextSide);
        zoneSide = nextSide;
        this.heatPorts = nextHeatPorts;
        this.waterPorts = nextWaterPorts;
        heatPortSet = positionsOf(nextHeatPorts);
        waterPortSet = positionsOf(nextWaterPorts);
        if (changed || lastSnapshot == null) snapshotDirty = true;
    }

    /** Stop I/O immediately when the shell becomes invalid; preserve stored resources and thermal state. */
    public void deconfigure() {
        heatPorts = new long[0];
        waterPorts = new long[0];
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
        return new Metrics(heat.temperature(), heat.meanTemperature(), generationRate,
            usefulGenerationRate, neutronMetrics, Math.max(0, lastLoadedTvels), heat.phase());
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
        ReactorHeatOutputCurve.Output output = heat.output();
        generationRate = output.exchange();
        usefulGenerationRate = output.useful();
        if (accumulateGeneration(generationRate)) changed = true;
        if (pushGth(level, gameTime)) changed = true;
        if (changed) panel.setChanged();
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

    /** Values synchronized by ContainerData; split long-like values into safe short-sized parts. */
    public int menuData(int index) {
        int waterAmount = water.amount();
        int waterCap = water.capacity();
        int gthAmount = gth.amountUnitsInt();
        int gthCap = gth.capacityUnitsInt();
        long hotTenths = temperatureTenths(heat.temperature());
        long meanTenths = temperatureTenths(heat.meanTemperature());
        return switch (index) {
            case 0 -> waterAmount;
            case 1 -> waterCap;
            case 2 -> gthAmount % ENERGY_SYNC_BASE;
            case 3 -> gthAmount / ENERGY_SYNC_BASE;
            case 4 -> gthCap % ENERGY_SYNC_BASE;
            case 5 -> gthCap / ENERGY_SYNC_BASE;
            case 6 -> (int) (hotTenths % ENERGY_SYNC_BASE);
            case 7 -> (int) Math.min(MAX_SYNC_PART, hotTenths / ENERGY_SYNC_BASE);
            case 8 -> (int) (meanTenths % ENERGY_SYNC_BASE);
            case 9 -> (int) Math.min(MAX_SYNC_PART, meanTenths / ENERGY_SYNC_BASE);
            case 10 -> percentTenths(neutronMetrics.strongestFlow());
            case 11 -> percentTenths(neutronMetrics.averageFlow());
            case 12 -> percentTenths(neutronMetrics.strongestLeak());
            case 13 -> percentTenths(neutronMetrics.averageLeak());
            case 14 -> rateUnits(generationRate);
            case 15 -> Math.max(0, lastLoadedTvels);
            default -> 0;
        };
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

    /** Display GTH/t as a rounded whole value so it fits one signed-short menu slot. */
    private static int rateUnits(double rate) {
        if (!Double.isFinite(rate) || rate <= 0.0) return 0;
        return (int) Math.min(MAX_SYNC_PART, Math.round(rate));
    }

    public void save(CompoundTag tag) {
        tag.putInt("ReactorZoneSide", zoneSide);
        water.save(tag, "ReactorWater");
        gth.save(tag, "ReactorGth");
        tag.putDouble("ReactorGthRemainderMilli", gthRemainderMilli);
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

        heatPorts = new long[0];
        waterPorts = new long[0];
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
        outputPortCursor = 0;
    }
}
