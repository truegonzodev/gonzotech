package com.gonzotech.core.tooltip;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Aggregate by actual recipe OUTPUT, retaining different rules for alternate recipes. */
public final class RecipeGateIndex<K> {
    private final Map<K, Set<GateRequirement>> gates = new HashMap<>();

    public void add(K output, GateRequirement gate) {
        gates.computeIfAbsent(output, ignored -> new TreeSet<>()).add(gate);
    }

    /** Ungated-only outputs need no wire entry; NONE is retained alongside gated alternatives. */
    public Map<K, List<GateRequirement>> snapshot() {
        Map<K, List<GateRequirement>> result = new HashMap<>();
        gates.forEach((output, rules) -> {
            if (rules.stream().anyMatch(rule -> !rule.equals(GateRequirement.NONE))) {
                result.put(output, List.copyOf(rules));
            }
        });
        return Map.copyOf(result);
    }
}
