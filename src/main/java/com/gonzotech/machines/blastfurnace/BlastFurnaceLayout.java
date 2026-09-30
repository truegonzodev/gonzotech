package com.gonzotech.machines.blastfurnace;

/**
 * Раскладка доменной печи 3×3×3 (0.3.65), чистый класс — без Minecraft-типов.
 * <p>
 * Центр структуры — топка ({@code K}) в среднем слое. Слои считаются сверху
 * вниз, как в спецификации автора:
 * <ul>
 *   <li>слой 1 (верх, {@code dy=+1}): 8× шамотный кирпич + котёл в центре;</li>
 *   <li>слой 2 (середина, {@code dy=0}): 4× шамот по углам, 4× узел теплотрубы
 *       (T1/T2 или универсальный T1/T2) по серединам рёбер, топка в центре;</li>
 *   <li>слой 3 (низ, {@code dy=-1}): 9× шамотный кирпич — фундамент.</li>
 * </ul>
 */
public final class BlastFurnaceLayout {

    /** Роль ячейки структуры относительно центра (топки). */
    public enum Role { OUTSIDE, FIRECLAY, HEAT_NODE, FIREBOX, CAULDRON }

    private BlastFurnaceLayout() {
    }

    /**
     * Роль ячейки со смещением {@code (dx, dy, dz)} от топки.
     * {@code dy=-1} — нижний слой (фундамент), {@code dy=0} — средний,
     * {@code dy=+1} — верхний (котёл в центре).
     */
    public static Role roleAt(int dx, int dy, int dz) {
        if (dy < -1 || dy > 1 || dx < -1 || dx > 1 || dz < -1 || dz > 1) return Role.OUTSIDE;
        boolean edge = (Math.abs(dx) + Math.abs(dz)) == 1;
        if (dy == -1) return Role.FIRECLAY;                 // слой 3: 9× шамот
        if (dy == 0) {
            if (dx == 0 && dz == 0) return Role.FIREBOX;    // K — центр
            return edge ? Role.HEAT_NODE : Role.FIRECLAY;   // D — середины рёбер
        }
        // dy == +1: слой 1
        if (dx == 0 && dz == 0) return Role.CAULDRON;       // C — котёл над топкой
        return Role.FIRECLAY;
    }

    /** Ячейка шва структуры (для флага FORMED): 17 шамотных блоков. */
    public static boolean isFireclayCell(int dx, int dy, int dz) {
        return roleAt(dx, dy, dz) == Role.FIRECLAY;
    }

    /** Узлы вывода GTH: середины рёбер среднего слоя — 4 позиции. */
    public static boolean isNodeCell(int dx, int dy, int dz) {
        return roleAt(dx, dy, dz) == Role.HEAT_NODE;
    }

    /** Всего ячеек структуры (3×3×3). */
    public static int cellCount() {
        return 27;
    }

    /** Шамотных блоков в структуре: 9 (низ) + 4 (углы середины) + 8 (верх). */
    public static int fireclayCount() {
        int n = 0;
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    if (isFireclayCell(dx, dy, dz)) n++;
        return n;
    }
}
