package com.gonzotech.machines.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
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
            // Жидкостное семейство (вода/пар/…) делит ОДИН угол сечения FLUID —
            // потому в пучке одновременно возможна только одна жидкостная труба.
            case WATER, STEAM -> new int[]{10, 10};
            // ITEM -> {10, 2};  (угол зарезервирован)
        };
    }

    /**
     * Бокс трубы типа {@code type} вдоль оси {@code axis}, в его углу сечения.
     * <p>
     * Координаты выведены из того, КАК блокстейт крутит модель (авторская модель
     * лежит вдоль Z; ось X → {@code y:90}, ось Y → {@code x:90}). MC вращает через
     * {@code rotateYXZ(-y, -x, 0)} вокруг центра, поэтому для оси Y координата Z
     * зеркалится в {@code [12-v, 16-v]} — только так хитбокс совпадает с моделью
     * (без этого вертикальные трубы менялись местами с соседом).
     */
    public static VoxelShape cornerBox(Direction.Axis axis, PipeType type) {
        int[] c = corner(type);
        int u = c[0];
        int v = c[1];
        return switch (axis) {
            case Z -> Block.box(u, v, 0, u + 4, v + 4, 16);            // сечение X(u)×Y(v)
            case X -> Block.box(0, v, u, 16, v + 4, u + 4);            // сечение Z(u)×Y(v)
            case Y -> Block.box(u, 0, 12 - v, u + 4, 16, 16 - v);      // Z зеркалится поворотом
        };
    }

    /**
     * Какая труба пучка ближе всего к точке наведения. {@code hitLocation} —
     * мировые координаты точки попадания луча; {@code candidates} — присутствующие
     * типы. Возвращает ближайший по сечению тип или {@code null}.
     * <p>
     * Центр каждого кандидата берётся ПРЯМО из {@link #cornerBox} — так наведение
     * и хитбокс/модель гарантированно согласованы для любой оси.
     */
    public static PipeType partAt(Direction.Axis axis, BlockPos pos, Vec3 hitLocation, Iterable<PipeType> candidates) {
        double lx = (hitLocation.x - pos.getX()) * 16.0;
        double ly = (hitLocation.y - pos.getY()) * 16.0;
        double lz = (hitLocation.z - pos.getZ()) * 16.0;

        PipeType best = null;
        double bestDist = Double.MAX_VALUE;
        for (PipeType t : candidates) {
            AABB b = cornerBox(axis, t).bounds();
            double cx = (b.minX + b.maxX) * 0.5 * 16.0;
            double cy = (b.minY + b.maxY) * 0.5 * 16.0;
            double cz = (b.minZ + b.maxZ) * 0.5 * 16.0;
            // Сравниваем только в плоскости сечения (ось прогона игнорируем).
            double d = switch (axis) {
                case X -> sq(ly - cy) + sq(lz - cz);
                case Y -> sq(lx - cx) + sq(lz - cz);
                default -> sq(lx - cx) + sq(ly - cy); // Z
            };
            if (d < bestDist) {
                bestDist = d;
                best = t;
            }
        }
        return best;
    }

    private static double sq(double x) {
        return x * x;
    }
}
