package com.gonzotech.chalkboard.core;

import java.util.List;
import java.util.Set;

/**
 * Static definitions for the 16 seed-deterministic discoveries in GonzoTech.
 */
public record DiscoveryDef(
        int index,               // 0..15 (Corresponding to "Открытие 1" .. "Открытие 16")
        String titleRu,
        String titleEn,
        Set<Integer> allowedLhsTiers,
        Set<Integer> allowedRhsTiers,
        int minNodes,
        int maxNodes,
        List<String> targetPoolIds,
        String themeBoostTargetId,
        int minTier2Req,
        int minTier3Req,
        int minTier4Req,
        int minTier99Req
) {

    public static final List<DiscoveryDef> ALL_DISCOVERIES = List.of(
            new DiscoveryDef(0, "ТЭС (Тепловая электростанция)", "Thermal Power Plant",
                    Set.of(1), Set.of(0), 2, 3, List.of("energy", "power", "force"), null, 0, 0, 0, 0),

            new DiscoveryDef(1, "Атомная энергетика", "Nuclear Power",
                    Set.of(1, 2), Set.of(0, 1), 2, 3, List.of(), null, 0, 0, 0, 0),

            new DiscoveryDef(2, "Микропроцессорная архитектура", "Microprocessor Architecture",
                    Set.of(1, 2), Set.of(0, 1, 2), 2, 4, List.of(), null, 0, 0, 0, 0),

            new DiscoveryDef(3, "Солнечная панель", "Solar Panel",
                    Set.of(1, 2), Set.of(0, 1, 2), 3, 4, List.of(), null, 0, 0, 0, 0),

            new DiscoveryDef(4, "Термоядерный синтез", "Thermonuclear Fusion",
                    Set.of(1, 2), Set.of(0, 1, 2, 3), 4, 5, List.of(), null, 0, 0, 0, 0),

            new DiscoveryDef(5, "Токамак и магнитное удержание", "Tokamak Magnetic Confinement",
                    Set.of(1, 2), Set.of(0, 1, 2, 3), 4, 5, List.of(), "b_field", 0, 0, 0, 0),

            new DiscoveryDef(6, "Программа «Икар»", "Icarus Aerospace",
                    Set.of(1, 2, 3), Set.of(0, 1, 2, 3), 5, 6, List.of(), null, 0, 0, 0, 0),

            new DiscoveryDef(7, "Квантовый процессор", "Quantum Processor",
                    Set.of(1, 2, 3), Set.of(0, 1, 2, 3), 5, 7, List.of(), null, 1, 0, 0, 0),

            new DiscoveryDef(8, "Нейросетевой синапс", "Neural Network Synapse",
                    Set.of(1, 2, 3), Set.of(0, 1, 2, 3), 5, 7, List.of(), null, 0, 1, 0, 0),

            new DiscoveryDef(9, "Орбитальный хаб", "Orbital Station Hub",
                    Set.of(2, 3), Set.of(0, 1, 2, 3), 6, 7, List.of(), null, 0, 2, 0, 0),

            new DiscoveryDef(10, "Рой Дайсона", "Dyson Swarm",
                    Set.of(2, 3), Set.of(0, 1, 2, 3, 4), 6, 7, List.of(), null, 0, 0, 1, 0),

            new DiscoveryDef(11, "Молекулярные сборщики", "Molecular Assemblers",
                    Set.of(2, 3), Set.of(0, 1, 2, 3, 4), 6, 7, List.of(), null, 0, 0, 2, 0),

            new DiscoveryDef(12, "Аннигиляционный реактор", "Annihilation Reactor",
                    Set.of(2, 3), Set.of(0, 1, 2, 3, 4), 6, 8, List.of(), null, 0, 0, 2, 0),

            new DiscoveryDef(13, "Кольцо Всевластия", "Ring of Power",
                    Set.of(3, 4), Set.of(0, 1, 2, 3, 4, 99), 8, 9, List.of(), null, 0, 0, 1, 1),

            new DiscoveryDef(14, "Губка Менгера", "Menger Sponge",
                    Set.of(2, 3, 4, 99), Set.of(0, 1, 2, 3, 4, 99), 8, 10, List.of(), null, 0, 0, 2, 1),

            new DiscoveryDef(15, "Пространственные червоточины", "Spatial Wormholes",
                    Set.of(3, 4, 99), Set.of(0, 1, 2, 3, 4, 99), 10, 13, List.of(), null, 0, 0, 1, 2)
    );

    public static DiscoveryDef get(int index) {
        int idx = Math.max(0, Math.min(15, index));
        return ALL_DISCOVERIES.get(idx);
    }
}
