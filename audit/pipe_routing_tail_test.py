#!/usr/bin/env python3
"""Regression contract for the capacity-induced zero-step pipe-routing tail.

A bounded integer model checks behavior against the former leveling + rotated-tail
semantics. Source pins ensure PipeRouting uses the bounded zero-step recovery. The
allocator is resource-agnostic: WIRE, HEAT, WATER, STEAM, and other fluids share it.
"""
from pathlib import Path
import random

ROOT = Path(__file__).resolve().parent.parent
SOURCE = (ROOT / "src/main/java/com/gonzotech/machines/network/PipeRouting.java").read_text()


def reference_allocate(budget, paths, capacities, rotation=0):
    """Bounded replacement: water-fill, then one rotated unit round per zero step."""
    count = len(paths)
    given = [0] * count
    active = [True] * count
    used = {segment: 0 for segment in capacities}
    remaining = budget
    bulk_rounds = 0
    zero_step_rounds = 0

    def freeze_full_lanes():
        frozen = 0
        for segment, initial in capacities.items():
            if initial - used[segment] > 0:
                continue
            for lane, path in enumerate(paths):
                if active[lane] and segment in path:
                    active[lane] = False
                    frozen += 1
        return frozen

    def has_room(path):
        return all(capacities[segment] - used[segment] > 0 for segment in path)

    while remaining > 0 and any(active):
        active_count = sum(active)
        step = remaining // active_count
        for segment, initial in capacities.items():
            crossing = sum(active[lane] and segment in paths[lane] for lane in range(count))
            if crossing:
                step = min(step, (initial - used[segment]) // crossing)

        if step <= 0:
            if freeze_full_lanes():
                continue

            zero_step_rounds += 1
            before = remaining
            start = rotation % count
            for offset in range(count):
                lane = (start + offset) % count
                if not active[lane] or not has_room(paths[lane]):
                    continue
                given[lane] += 1
                remaining -= 1
                for segment in paths[lane]:
                    used[segment] += 1
                if remaining == 0:
                    break
            frozen_after = freeze_full_lanes()
            if remaining == before and not frozen_after:
                break
            continue

        bulk_rounds += 1
        for lane, path in enumerate(paths):
            if not active[lane]:
                continue
            given[lane] += step
            remaining -= step
            for segment in path:
                used[segment] += step
        freeze_full_lanes()

    return given, {
        "remaining": remaining,
        "bulk_rounds": bulk_rounds,
        "zero_step_rounds": zero_step_rounds,
    }


def legacy_allocate(budget, paths, capacities, rotation=0):
    """Original leveler and one-unit tail, retained as a bounded behavior oracle."""
    count = len(paths)
    given = [0] * count
    active = [True] * count
    used = {segment: 0 for segment in capacities}
    remaining = budget

    def has_room(path):
        return all(capacities[segment] - used[segment] > 0 for segment in path)

    def freeze_full_lanes():
        for segment, initial in capacities.items():
            if initial - used[segment] <= 0:
                for lane, path in enumerate(paths):
                    if active[lane] and segment in path:
                        active[lane] = False

    while remaining > 0 and any(active):
        active_count = sum(active)
        step = remaining // active_count
        for segment, initial in capacities.items():
            crossing = sum(active[lane] and segment in paths[lane] for lane in range(count))
            if crossing:
                step = min(step, (initial - used[segment]) // crossing)
        if step <= 0:
            break
        for lane, path in enumerate(paths):
            if active[lane]:
                given[lane] += step
                remaining -= step
                for segment in path:
                    used[segment] += step
        freeze_full_lanes()

    start = rotation % count
    while remaining > 0:
        before = remaining
        for offset in range(count):
            lane = (start + offset) % count
            if not active[lane] or not has_room(paths[lane]):
                continue
            given[lane] += 1
            remaining -= 1
            for segment in paths[lane]:
                used[segment] += 1
            if remaining == 0:
                break
        if remaining == before:
            break
    return given, remaining


def method_region(start, end):
    start_at = SOURCE.index(start)
    return SOURCE[start_at:SOURCE.index(end, start_at)]


# Production wiring: full segments are frozen and re-leveled; every remaining
# zero-step case gets one bounded unit round. There is no per-resource-unit tail loop.
distributor = method_region(
    "private static long distributeLanes(",
    "private static int freezeSaturatedLanes(",
)
assert distributor.index("if (x <= 0)") < distributor.index("freezeSaturatedLanes(")
assert distributor.index("freezeSaturatedLanes(") < distributor.index("distributeZeroStepUnitRound(")
assert "activeCount -= freezeSaturatedLanes(crossers, rem0, usage, active);" in distributor
assert distributor.count("while (") == 1, "only the batch-leveling loop may be unbounded"
assert "diagnostics.tailPass()" not in distributor
assert "boolean tailStalled = remaining > 0;" in distributor

unit_round = method_region(
    "private static long distributeZeroStepUnitRound(",
    "private static boolean pathHasCapacityForUnit(",
)
assert "for (int k = 0; k < n && remaining > 0; k++)" in unit_round
assert "pathHasCapacityForUnit(level, path, initialCapacity, usage)" in unit_round
assert "given[laneIndex]++" in unit_round and "remaining--" in unit_round
assert "while (" not in unit_round, "zero-step recovery must not loop by resource-budget units"
assert "initialCapacity.get(key) - usage.getOrDefault(key, 0L)" in SOURCE

# Historical worst-shape: a blocked route beside a huge-capacity free route.
# Same planned amounts as the old tail, but the solver work is independent of
# the 562,001-unit remainder.
for resource in ("WIRE", "HEAT", "WATER", "STEAM", "MASH", "WORT", "DISTILLATE"):
    allocation, work = reference_allocate(
        1_000_000, [[0], [1]], {0: 0, 1: 562_001}, rotation=0
    )
    assert allocation == [0, 562_001], (resource, allocation)
    assert work["remaining"] == 437_999, (resource, work)
    assert work["bulk_rounds"] == 1, (resource, work)
    assert work["zero_step_rounds"] == 0, (resource, work)

# Positive shared remainder below the number of crossing lanes: one lane gets
# the last resource unit, the blocked peers freeze, and independent routes resume in bulk.
allocation, work = reference_allocate(
    200_000, [[0], [0], [1], [1]], {0: 1, 1: 100_000}, rotation=0
)
assert allocation == [1, 0, 50_000, 50_000], allocation
assert work["zero_step_rounds"] == 1, work

# Small source budget still uses the same stable rotated remainder order.
allocation, work = reference_allocate(3, [[], [], [], []], {}, rotation=2)
assert allocation == [1, 0, 1, 1], allocation
assert work["remaining"] == 0, work

# Verify the new event rounds preserve old per-lane allocations for a reproducible
# spread of small networks, including direct lanes, shared segments, and zero caps.
rng = random.Random(0x60A20)
for case in range(10_000):
    lane_count = rng.randint(1, 8)
    segment_count = rng.randint(0, 6)
    capacities = {i: rng.randint(0, 12) for i in range(segment_count)}
    paths = []
    for _ in range(lane_count):
        path_length = rng.randint(0, min(segment_count, 3))
        paths.append(sorted(rng.sample(list(capacities), path_length)) if segment_count else [])
    budget = rng.randint(1, 80)
    rotation = rng.randint(0, 31)
    new_allocation, new_state = reference_allocate(budget, paths, capacities, rotation)
    old_allocation, old_remaining = legacy_allocate(budget, paths, capacities, rotation)
    assert (new_allocation, new_state["remaining"]) == (old_allocation, old_remaining), (
        case, budget, rotation, paths, capacities,
        (new_allocation, new_state["remaining"]), (old_allocation, old_remaining),
    )

print("pipe routing tail contract passed (resource coverage, bounded zero-step, rotation, 10,000 legacy-equivalence cases)")
