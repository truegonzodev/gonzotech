import com.gonzotech.machines.blastfurnace.BlastFurnaceLayout;
import com.gonzotech.machines.blastfurnace.BlastFurnaceLayout.Role;

/** Раскладка доменной печи 3×3×3 — сверка со спецификацией автора (0.3.65). */
public final class BlastFurnaceSelfTest {
    private static int checks = 0;

    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
        checks++;
    }

    public static void main(String[] args) {
        // Слой 3 (низ, dy=-1): 9× шамот — фундамент.
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                check(BlastFurnaceLayout.roleAt(dx, -1, dz) == Role.FIRECLAY,
                    "bottom layer all fireclay " + dx + "," + dz);

        // Слой 2 (середина, dy=0): K в центре, D на серединах рёбер, F по углам.
        check(BlastFurnaceLayout.roleAt(0, 0, 0) == Role.FIREBOX, "middle center is firebox");
        int[][] edges = {{1,0},{-1,0},{0,1},{0,-1}};
        for (int[] e : edges)
            check(BlastFurnaceLayout.roleAt(e[0], 0, e[1]) == Role.HEAT_NODE, "edge node " + e[0] + "," + e[1]);
        int[][] corners = {{1,1},{1,-1},{-1,1},{-1,-1}};
        for (int[] c : corners)
            check(BlastFurnaceLayout.roleAt(c[0], 0, c[1]) == Role.FIRECLAY, "middle corner fireclay");

        // Слой 1 (верх, dy=+1): C (котёл) в центре, 8× шамот вокруг.
        check(BlastFurnaceLayout.roleAt(0, 1, 0) == Role.CAULDRON, "top center is cauldron");
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                if (dx != 0 || dz != 0)
                    check(BlastFurnaceLayout.roleAt(dx, 1, dz) == Role.FIRECLAY,
                        "top ring fireclay " + dx + "," + dz);

        // Вне куба — OUTSIDE.
        check(BlastFurnaceLayout.roleAt(2, 0, 0) == Role.OUTSIDE, "outside x");
        check(BlastFurnaceLayout.roleAt(0, 2, 0) == Role.OUTSIDE, "outside y");
        check(BlastFurnaceLayout.roleAt(0, 0, -2) == Role.OUTSIDE, "outside z");

        // Сводка: 27 ячеек, 21 шамотных (9 фундамент + 4 угла + 8 верх), 4 узла.
        check(BlastFurnaceLayout.cellCount() == 27, "27 cells");
        check(BlastFurnaceLayout.fireclayCount() == 21, "21 fireclay");
        int nodes = 0;
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    if (BlastFurnaceLayout.isNodeCell(dx, dy, dz)) nodes++;
        check(nodes == 4, "4 heat nodes");

        System.out.println(checks + " checks passed");
    }
}
