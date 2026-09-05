package com.gonzotech.machines.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Единая геометрия труб: у каждого {@link PipeType} — свой ФИКСИРОВАННЫЙ угол
 * сечения 4×4. И одиночная труба ({@link PipeBlock}), и связка
 * ({@link CompositePipeBlock}) используют одни и те же координаты, поэтому:
 * <ul>
 *   <li>тип всегда на своём месте (ток — верх-лево, тепло — низ-лево, …), даже
 *       если труба в блоке одна;</li>
 *   <li>соседи одной оси совпадают по под-решётке сами собой;</li>
 *   <li>по точке наведения можно понять, в КАКУЮ трубу пучка смотрит игрок
 *       ({@link #partAt}) — это и даёт «выбрать/сломать/настроить по отдельности».</li>
 * </ul>
 */
public final class PipeGeometry {

    private PipeGeometry() {
    }

    /**
     * Угол сечения {@code {u, v}} для типа: {@code u} — горизонталь, {@code v} —
     * вертикаль (в «клетках» 0..16, левый-нижний угол бокса 4×4).
     * <pre>
     *   [ ]E[ ][ ]F[ ]   E = WIRE (ток)      верх-лево   {2,10}
     *   [ ][ ][ ][ ][ ]   F = FLUID (жидк.)   верх-право  {10,10}
     *   [ ]H[ ][ ]I[ ]   H = HEAT (тепло)    низ-лево    {2,2}
     *                     I = ITEM (предметы) низ-право   {10,2}
     * </pre>
     */
    public static int[] corner(PipeType type) {
        return switch (type) {
            case WIRE -> new int[]{2, 10};
            case HEAT -> new int[]{2, 2};
            // FLUID -> {10, 10};  ITEM -> {10, 2};  (углы зарезервированы)
        };
    }

    /** Бокс трубы типа {@code type} вдоль оси {@code axis}, в его углу сечения. */
    public static VoxelShape cornerBox(Direction.Axis axis, PipeType type) {
        int[] c = corner(type);
        int u = c[0];
        int v = c[1];
        return switch (axis) {
            case Z -> Block.box(u, v, 0, u + 4, v + 4, 16);   // сечение X(u)×Y(v)
            case X -> Block.box(0, v, u, 16, v + 4, u + 4);   // сечение Z(u)×Y(v)
            case Y -> Block.box(u, 0, v, u + 4, 16, v + 4);   // сечение X(u)×Z(v)
        };
    }

    /**
     * Какая труба пучка ближе всего к точке наведения. {@code hitLocation} —
     * мировые координаты точки попадания луча; {@code candidates} — присутствующие
     * типы. Возвращает ближайший по сечению тип или {@code null}, если кандидатов
     * нет.
     */
    public static PipeType partAt(Direction.Axis axis, BlockPos pos, Vec3 hitLocation, Iterable<PipeType> candidates) {
        // Локальные координаты 0..16 внутри блока.
        double lx = (hitLocation.x - pos.getX()) * 16.0;
        double ly = (hitLocation.y - pos.getY()) * 16.0;
        double lz = (hitLocation.z - pos.getZ()) * 16.0;

        // Горизонталь/вертикаль сечения зависят от оси прогона.
        double h;
        double vv;
        switch (axis) {
            case Z -> { h = lx; vv = ly; }
            case X -> { h = lz; vv = ly; }
            default -> { h = lx; vv = lz; } // Y
        }

        PipeType best = null;
        double bestDist = Double.MAX_VALUE;
        for (PipeType t : candidates) {
            int[] c = corner(t);
            double cu = c[0] + 2.0; // центр бокса 4×4
            double cv = c[1] + 2.0;
            double d = (h - cu) * (h - cu) + (vv - cv) * (vv - cv);
            if (d < bestDist) {
                bestDist = d;
                best = t;
            }
        }
        return best;
    }
}
