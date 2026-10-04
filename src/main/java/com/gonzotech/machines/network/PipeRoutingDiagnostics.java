package com.gonzotech.machines.network;

import com.gonzotech.core.config.GonzoServerConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Detailed, opt-in diagnostics for route building, distribution and topology rebuilds. */
public final class PipeRoutingDiagnostics {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicLong NEXT_ROUTE_ID = new AtomicLong();
    private static final int MAX_LOGGED_ENTRIES = 64;
    private static final int MAX_LOGGED_LANES = 128;
    private static final int MAX_LOGGED_LIMITERS = 16;
    private static final int MAX_LOGGED_LEVEL_ROUNDS = 64;
    private static final int MAX_LOGGED_PIPE_STATES = 64;
    private static final int MAX_LOGGED_TAIL_BLOCKERS = 16;
    private static final int MAX_LOGGED_PATH_STEPS = 48;

    private PipeRoutingDiagnostics() {}

    static boolean isEnabled() {
        return GonzoServerConfig.SPEC.isLoaded() && GonzoServerConfig.PIPE_ROUTING_DIAGNOSTICS.get();
    }

    static Trace begin(Level level, BlockPos source, PipeType type, long budget, String operation) {
        if (!isEnabled()) return null;
        return new Trace(level, source, type, budget, operation);
    }

    /** One startup warning makes the cost and the reversible config switch visible in the console. */
    public static void warnIfEnabled() {
        if (isEnabled()) {
            LOGGER.warn("[GONZOTECH PIPE-DIAG] Detailed pipe diagnostics are enabled. Per-drain lane/path logs can be high-volume and may reduce TPS; disable pipeRoutingDiagnostics in the server config after the profiling session.");
        }
    }

    /** Separate topology rebuild cost from the steady-state routing records. */
    static void clumpRebuilt(Level level, BlockPos pos, String operation, String kind,
                             int oldMembers, int rebuiltMembers, int components,
                             String result, long elapsedNanos) {
        if (!isEnabled()) return;
        LOGGER.info("[GONZOTECH PIPE-DIAG][CLUMP] tick=" + level.getGameTime()
                + " dimension=" + level.dimension().location()
                + " operation=" + operation
                + " pos=" + pos(pos)
                + " kind=" + kind
                + " oldMembers=" + oldMembers
                + " rebuiltMembers=" + rebuiltMembers
                + " components=" + components
                + " result=" + result
                + " elapsedUs=" + micros(elapsedNanos));
    }

    static final class Trace {
        private final long id = NEXT_ROUTE_ID.incrementAndGet();
        private final long tick;
        private final String dimension;
        private final String source;
        private final String sourceBlock;
        private final String operation;
        private final PipeType type;
        private final long budget;
        private final long startedNanos = System.nanoTime();
        private final List<String> entries = new ArrayList<>();
        private final List<String> limitingPipes = new ArrayList<>();
        private final List<String> roundDetails = new ArrayList<>();
        private final List<PipeDetail> pipeDetails = new ArrayList<>();
        private final Map<Integer, LaneDetail> laneDetails = new LinkedHashMap<>();
        private final Map<Long, Long> tailBlockers = new LinkedHashMap<>();

        private int bfsVisited;
        private int bfsCapped;
        private int laneCount;
        private int routePipeCount;
        private int pipeStateCount;
        private long pathSteps;
        private long pipeLaneRefs;
        private int levelingRounds;
        private int limitingPipeCount;
        private String levelingStop = "not-run";
        private int activeAtTail;
        private long remainingAtTail;
        private long remainingAfterTail;
        private int tailPasses;
        private long tailLaneChecks;
        private long tailPipeChecks;
        private long tailBlockedLanes;
        private long tailAssigned;
        private boolean tailStalled;
        private long acceptedTotal;
        private long offeredTotal;
        private long chargedTotal;
        private int receiverCalls;
        private long crossingIndexNanos;
        private long levelingNanos;
        private long hotPipeMarkNanos;
        private long residualLoopNanos;
        private long receiverNanos;

        private Trace(Level level, BlockPos source, PipeType type, long budget, String operation) {
            this.tick = level.getGameTime();
            this.dimension = String.valueOf(level.dimension().location());
            this.source = pos(source);
            this.sourceBlock = level.getBlockState(source).getBlock().getClass().getSimpleName();
            this.type = type;
            this.budget = budget;
            this.operation = operation;
        }

        void addBfsEntry(BlockPos entry, int visited, boolean capped) {
            bfsVisited += visited;
            if (capped) bfsCapped++;
            if (entries.size() < MAX_LOGGED_ENTRIES) {
                entries.add(pos(entry) + ":" + visited + (capped ? "(cap)" : ""));
            }
        }

        void setTopology(int lanes, long totalPathSteps, int pipes, long crossingReferences) {
            laneCount = lanes;
            pathSteps = totalPathSteps;
            routePipeCount = pipes;
            pipeLaneRefs = crossingReferences;
        }

        void addPipeState(BlockPos pipe, long initialRemaining, long planned, int laneRefs) {
            pipeStateCount++;
            PipeDetail candidate = new PipeDetail(pipe.asLong(), initialRemaining, planned,
                initialRemaining - planned, laneRefs);
            if (pipeDetails.size() < MAX_LOGGED_PIPE_STATES) {
                pipeDetails.add(candidate);
                return;
            }
            int worstIndex = 0;
            for (int i = 1; i < pipeDetails.size(); i++) {
                if (comparePipeStates(pipeDetails.get(i), pipeDetails.get(worstIndex)) > 0) worstIndex = i;
            }
            if (comparePipeStates(candidate, pipeDetails.get(worstIndex)) < 0) {
                pipeDetails.set(worstIndex, candidate);
            }
        }

        void addLane(int laneIndex, String route) {
            if (laneIndex < MAX_LOGGED_LANES) {
                laneDetails.putIfAbsent(laneIndex, new LaneDetail(route));
            }
        }

        void laneAllocation(int laneIndex, long allocated) {
            LaneDetail detail = laneDetails.get(laneIndex);
            if (detail != null) detail.allocated = allocated;
        }

        void laneAccepted(int laneIndex, long offered, long accepted) {
            receiverCalls++;
            offeredTotal += offered;
            acceptedTotal += accepted;
            LaneDetail detail = laneDetails.get(laneIndex);
            if (detail != null) {
                detail.offered = offered;
                detail.accepted = accepted;
                detail.received = true;
            }
        }

        void laneCharged(int laneIndex, long charged) {
            chargedTotal += charged;
            LaneDetail detail = laneDetails.get(laneIndex);
            if (detail != null) detail.charged = charged;
        }

        void levelingRound(long stepSize, long remainingBefore, long remainingAfter,
                          int activeBefore, int activeAfter) {
            levelingRounds++;
            if (roundDetails.size() < MAX_LOGGED_LEVEL_ROUNDS) {
                roundDetails.add("x=" + stepSize + ",remaining=" + remainingBefore + "->" + remainingAfter
                    + ",active=" + activeBefore + "->" + activeAfter);
            }
        }

        void addLimitingPipe(BlockPos pipe, long remainingCapacity, int activeLanes) {
            limitingPipeCount++;
            if (limitingPipes.size() < MAX_LOGGED_LIMITERS) {
                limitingPipes.add(pos(pipe) + ":remaining=" + remainingCapacity + ",activeLanes=" + activeLanes);
            }
        }

        void levelingStopped(String reason, long remaining, int activeCount) {
            levelingStop = reason;
            activeAtTail = activeCount;
            remainingAtTail = remaining;
        }

        void tailPass() {
            tailPasses++;
        }

        void tailLaneCheck() {
            tailLaneChecks++;
        }

        void tailPipeCheck() {
            tailPipeChecks++;
        }

        void tailBlockedBy(BlockPos pipe) {
            tailBlockedLanes++;
            tailBlockers.merge(pipe.asLong(), 1L, Long::sum);
        }

        void tailFinished(long remaining, boolean stalled, long elapsedNanos) {
            remainingAfterTail = remaining;
            tailAssigned = Math.max(0L, remainingAtTail - remaining);
            tailStalled = stalled;
            residualLoopNanos = elapsedNanos;
        }

        void phaseTimes(long crossingIndexNanos, long levelingNanos, long hotPipeMarkNanos,
                        long residualLoopNanos, long receiverNanos) {
            this.crossingIndexNanos = crossingIndexNanos;
            this.levelingNanos = levelingNanos;
            this.hotPipeMarkNanos = hotPipeMarkNanos;
            this.residualLoopNanos = residualLoopNanos;
            this.receiverNanos = receiverNanos;
        }

        void finish(String result, long sourceTaken, long routeNanos, long distributionNanos) {
            long totalNanos = System.nanoTime() - startedNanos;
            StringBuilder line = new StringBuilder(1024);
            line.append("[GONZOTECH PIPE-DIAG][ROUTE] id=").append(id)
                    .append(" tick=").append(tick)
                    .append(" dimension=").append(dimension)
                    .append(" operation=").append(operation)
                    .append(" source=").append(source)
                    .append(" sourceBlock=").append(sourceBlock)
                    .append(" type=").append(type)
                    .append(" budgetMilli=").append(budget)
                    .append(" entries=").append(entries.size())
                    .append(" bfsVisited=").append(bfsVisited)
                    .append(" bfsCapped=").append(bfsCapped)
                    .append(" lanes=").append(laneCount)
                    .append(" routePipes=").append(routePipeCount)
                    .append(" pathSteps=").append(pathSteps)
                    .append(" pipeLaneRefs=").append(pipeLaneRefs)
                    .append(" levelRounds=").append(levelingRounds)
                    .append(" levelStop=").append(levelingStop)
                    .append(" activeAtTail=").append(activeAtTail)
                    .append(" remAtTail=").append(remainingAtTail)
                    .append(" remAfterTail=").append(remainingAfterTail)
                    .append(" tailPasses=").append(tailPasses)
                    .append(" tailAssigned=").append(tailAssigned)
                    .append(" tailLaneChecks=").append(tailLaneChecks)
                    .append(" tailPipeChecks=").append(tailPipeChecks)
                    .append(" tailBlockedLanes=").append(tailBlockedLanes)
                    .append(" tailStalled=").append(tailStalled)
                    .append(" receiverCalls=").append(receiverCalls)
                    .append(" offeredToReceiver=").append(offeredTotal)
                    .append(" accepted=").append(acceptedTotal)
                    .append(" sourceChargeTotal=").append(chargedTotal)
                    .append(" sourceTaken=").append(sourceTaken)
                    .append(" result=").append(result)
                    .append(" timingsUs{collect=").append(micros(routeNanos))
                    .append(",crossingIndex=").append(micros(crossingIndexNanos))
                    .append(",leveling=").append(micros(levelingNanos))
                    .append(",hotPipeMarks=").append(micros(hotPipeMarkNanos))
                    .append(",residual=").append(micros(residualLoopNanos))
                    .append(",receivers=").append(micros(receiverNanos))
                    .append(",distribution=").append(micros(distributionNanos))
                    .append(",total=").append(micros(totalNanos)).append('}')
                    .append(" entriesDetail=").append(entries)
                    .append(" levelRoundDetail=").append(roundDetails)
                    .append(" zeroLimiters=").append(limitingPipes);
            if (levelingRounds > roundDetails.size()) {
                line.append(" omittedLevelRounds=").append(levelingRounds - roundDetails.size());
            }

            line.append(" lanesDetail=[");
            boolean first = true;
            for (Map.Entry<Integer, LaneDetail> entry : laneDetails.entrySet()) {
                if (!first) line.append(';');
                first = false;
                line.append(entry.getKey()).append('{').append(entry.getValue()).append('}');
            }
            line.append(']');
            if (laneCount > laneDetails.size()) {
                line.append(" omittedLaneDetails=").append(laneCount - laneDetails.size());
            }
            if (limitingPipeCount > limitingPipes.size()) {
                line.append(" omittedZeroLimiters=").append(limitingPipeCount - limitingPipes.size());
            }

            pipeDetails.sort(PipeRoutingDiagnostics::comparePipeStates);
            line.append(" pipeCapacity=[");
            for (int i = 0; i < pipeDetails.size(); i++) {
                if (i > 0) line.append(';');
                PipeDetail pipe = pipeDetails.get(i);
                line.append(pos(BlockPos.of(pipe.packedPos())))
                    .append("{initial=").append(pipe.initialRemaining())
                    .append(",planned=").append(pipe.planned())
                    .append(",after=").append(pipe.remainingAfter())
                    .append(",laneRefs=").append(pipe.laneRefs()).append('}');
            }
            line.append(']');
            if (pipeStateCount > pipeDetails.size()) {
                line.append(" omittedPipeStates=").append(pipeStateCount - pipeDetails.size());
            }

            List<Map.Entry<Long, Long>> blockers = new ArrayList<>(tailBlockers.entrySet());
            blockers.sort((left, right) -> Long.compare(right.getValue(), left.getValue()));
            line.append(" tailBlockers=[");
            int count = Math.min(blockers.size(), MAX_LOGGED_TAIL_BLOCKERS);
            for (int i = 0; i < count; i++) {
                if (i > 0) line.append(';');
                Map.Entry<Long, Long> blocker = blockers.get(i);
                line.append(pos(BlockPos.of(blocker.getKey()))).append(':').append(blocker.getValue());
            }
            line.append(']');
            if (blockers.size() > count) {
                line.append(" omittedTailBlockers=").append(blockers.size() - count);
            }
            LOGGER.info(line.toString());
        }
    }

    private record PipeDetail(long packedPos, long initialRemaining, long planned,
                              long remainingAfter, int laneRefs) {}

    private static int comparePipeStates(PipeDetail left, PipeDetail right) {
        int remaining = Long.compare(left.remainingAfter(), right.remainingAfter());
        return remaining != 0 ? remaining : Long.compare(left.packedPos(), right.packedPos());
    }

    private static final class LaneDetail {
        private final String route;
        private long allocated;
        private long offered;
        private long accepted;
        private long charged;
        private boolean received;

        private LaneDetail(String route) {
            this.route = route;
        }

        @Override
        public String toString() {
            return route + ",allocated=" + allocated
                    + ",offered=" + offered
                    + ",accepted=" + accepted
                    + ",sourceCharge=" + charged
                    + (received ? "" : ",receiverNotCalled=true");
        }
    }

    static String routeDescription(BlockPos target, List<BlockPos> pipes, List<String> directions,
                                   long lossMilli, boolean direct) {
        StringBuilder route = new StringBuilder(96);
        route.append("target=").append(pos(target))
                .append(",kind=").append(direct ? "direct" : "pipe")
                .append(",lossMilli=").append(lossMilli);
        if (!direct) {
            route.append(",path=[");
            int shown = Math.min(pipes.size(), MAX_LOGGED_PATH_STEPS);
            for (int i = 0; i < shown; i++) {
                if (i > 0) route.append('>');
                route.append(pos(pipes.get(i))).append('/').append(directions.get(i));
            }
            if (pipes.size() > shown) route.append(">...+").append(pipes.size() - shown);
            route.append(']');
        }
        return route.toString();
    }

    private static String pos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static long micros(long nanos) {
        return Math.max(0L, nanos) / 1_000L;
    }
}
