package com.gonzotech.machines.network;

import com.gonzotech.machines.item.WrenchItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.ScheduledTickAccess;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Составной блок труб — несколько ТИПОВ труб в одном кубе, каждый в своём
 * фиксированном углу сечения, БЕЗ соединения между собой. По сути «пучок»
 * обособленных труб (аналогия: {@code pink_petals}/горшок с несколькими
 * стеблями). Пассивен, как и одиночная труба: без BlockEntity, без тика.
 * <p>
 * <b>Фиксированные углы = детерминизм.</b> Тип всегда сидит в одном и том же
 * углу сечения ({@link #corner}). Поэтому «ток» одного блока стыкуется с «током»
 * соседнего сам собой (под-решётки выровнены by design), модель просто
 * аддитивная, а пустой угол служит игроку подсказкой «сюда влезет ещё труба».
 * <p>
 * <b>Шаг 1 (эта версия): общая ось.</b> Все типы в блоке смотрят вдоль одной оси
 * ({@code AXIS}). Независимые попарные направления (ток на север, жидкость вверх
 * и т.п.) — запланированный апгрейд (Шаг 2), потребует per-type оси, multipart-
 * моделей и валидации непересечения.
 * <p>
 * <b>Угловые типы пучка ({@link #BUNDLE_TYPES}).</b> Физически связка имеет ровно 4
 * угла сечения (WIRE, HEAT, FLUID, ITEM). В пучок собираются только эти базовые типы;
 * специализированные жидкости Эпохи III текут через универсальный жидкостный угол
 * (когда в связке стоят оба флага WATER+STEAM) и не плодят комбинаторные блокстейты.
 */
public class CompositePipeBlock extends RotatedPillarBlock implements PipeCarrier, SimpleWaterloggedBlock {

    /** Физические типы одиночных труб, которые можно собрать в связку. */
    public static final List<PipeType> BUNDLE_TYPES = List.of(
        PipeType.WIRE, PipeType.HEAT, PipeType.WATER, PipeType.STEAM, PipeType.ITEM
    );

    /** Присутствует ли тип в блоке. Ключ — {@link PipeType}. */
    public static final Map<PipeType, BooleanProperty> PRESENT = new EnumMap<>(PipeType.class);
    /** Режим (AUTO/PULL/PUSH) типа. Ключ — {@link PipeType}. */
    public static final Map<PipeType, EnumProperty<PipeMode>> MODE = new EnumMap<>(PipeType.class);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    /**
     * Ось прогона НИЖНЕГО слоя (HEAT + ITEM). Верхний слой (WIRE + FLUID) использует
     * унаследованную {@link RotatedPillarBlock#AXIS}. Разделение по высоте сечения:
     * верх {@code v=10} (WIRE {2,10}, FLUID {10,10}), низ {@code v=2} (HEAT {2,2},
     * ITEM {10,2}). Так слои можно крутить попарно и независимо (X↔Z) ключом.
     */
    public static final EnumProperty<Direction.Axis> AXIS_LOWER =
        EnumProperty.create("axis_lower", Direction.Axis.class);

    static {
        for (PipeType t : BUNDLE_TYPES) {
            PRESENT.put(t, BooleanProperty.create("has_" + t.id()));
            MODE.put(t, EnumProperty.create("mode_" + t.id(), PipeMode.class));
        }
    }

    private final MapCodec<CompositePipeBlock> codec;

    public CompositePipeBlock(Properties properties) {
        super(properties);
        this.codec = simpleCodec(CompositePipeBlock::new);
        BlockState def = this.stateDefinition.any()
            .setValue(AXIS, Direction.Axis.Z)
            .setValue(AXIS_LOWER, Direction.Axis.Z)
            .setValue(WATERLOGGED, false);
        for (PipeType t : BUNDLE_TYPES) {
            def = def.setValue(PRESENT.get(t), false).setValue(MODE.get(t), PipeMode.AUTO);
        }
        this.registerDefaultState(def);
    }

    @Override
    public MapCodec<? extends RotatedPillarBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, AXIS_LOWER, WATERLOGGED);
        for (PipeType t : BUNDLE_TYPES) {
            builder.add(PRESENT.get(t));
            builder.add(MODE.get(t));
        }
    }

    // ─────────────────────────── waterlogging ───────────────────────────

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess,
                                     BlockPos pos, Direction direction, BlockPos neighborPos,
                                     BlockState neighborState, RandomSource random) {
        if (state.getValue(WATERLOGGED)) {
            tickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, level, tickAccess, pos, direction, neighborPos, neighborState, random);
    }

    // ─────────────────────────── PipeCarrier ───────────────────────────

    @Override
    public boolean carries(BlockState state, PipeType type) {
        if (BUNDLE_TYPES.contains(type)) {
            BooleanProperty p = PRESENT.get(type);
            return p != null && state.getValue(p);
        }
        // Жидкости Эпохи III переносятся пучком, если в нём смонтирован универсальный FLUID-угол.
        if (type.isFluid() && carriesUniversalFluid(state)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean opensToward(BlockState state, PipeType type, Direction dir) {
        if (!carries(state, type)) return false;
        // Тип открыт двумя торцами вдоль оси СВОЕГО слоя.
        return dir.getAxis() == axisOf(state, type);
    }

    /**
     * В нижнем ли слое сечения находится тип. Нижний слой — HEAT + ITEM (v=2);
     * верхний — WIRE + FLUID/жидкости (v=10). Разбивка совпадает с
     * {@link PipeGeometry#corner}.
     */
    private static boolean isLowerLayer(PipeType type) {
        return type == PipeType.HEAT || type == PipeType.ITEM;
    }

    /**
     * Ось прогона для типа: нижний слой (HEAT/ITEM) — {@link #AXIS_LOWER}, верхний
     * (WIRE/FLUID) — {@link #AXIS}. Публичный, чтобы HUD/поток-сеть читали ось «по
     * слою наведённого типа».
     */
    public static Direction.Axis axisOf(BlockState state, PipeType type) {
        return isLowerLayer(type) ? state.getValue(AXIS_LOWER) : state.getValue(AXIS);
    }

    @Override
    public PipeMode modeFor(BlockState state, PipeType type) {
        if (BUNDLE_TYPES.contains(type)) {
            EnumProperty<PipeMode> m = MODE.get(type);
            return m != null ? state.getValue(m) : PipeMode.AUTO;
        }
        if (type.isFluid() && carriesUniversalFluid(state)) {
            return state.getValue(MODE.get(PipeType.WATER));
        }
        return PipeMode.AUTO;
    }

    // ─────────────────────────── форма (хитбокс) ───────────────────────────

    private static VoxelShape shapeFor(BlockState state) {
        VoxelShape shape = Shapes.empty();
        for (PipeType t : BUNDLE_TYPES) {
            if (!state.getValue(PRESENT.get(t))) continue;
            shape = Shapes.join(shape, PipeGeometry.cornerBox(axisOf(state, t), t), BooleanOp.OR);
        }
        return shape.isEmpty() ? Shapes.block() : shape;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    // ─────────── активный тик, если в пучке есть предметная труба ───────────

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && state.getValue(PRESENT.get(PipeType.ITEM))) {
            level.scheduleTick(pos, this, ItemPipeBlock.TICK_INTERVAL);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(PRESENT.get(PipeType.ITEM))) return;
        ItemRouting.tickExtract(level, pos, state);
        level.scheduleTick(pos, this, ItemPipeBlock.TICK_INTERVAL);
    }

    // ─────────────────────────── взаимодействие ───────────────────────────

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof WrenchItem) {
            PipeType part = partAt(state, pos, hit);
            if (part == null) return InteractionResult.PASS;
            if (!level.isClientSide()) {
                PipeMode nextMode = state.getValue(MODE.get(part)).next();
                level.setBlock(pos, state.setValue(MODE.get(part), nextMode), Block.UPDATE_ALL);
            }
            return InteractionResult.SUCCESS;
        }

        // Добавление универсальной жидк.трубы: занимает весь FLUID-угол (вода+пар),
        // если он ещё свободен.
        if (isUniversalPipeItem(stack) && isSecondTierPipeItem(stack) == (this instanceof SecondTierPipe)
            && fluidCornerFree(state)) {
            if (!level.isClientSide()) {
                level.setBlock(pos, withUniversalFluid(state), Block.UPDATE_ALL);
                if (!player.getAbilities().instabuild) stack.shrink(1);
            }
            return InteractionResult.SUCCESS;
        }

        // Добавление ещё одной трубы в связку: используем предмет-трубу другого типа.
        PipeType adding = pipeTypeOf(stack);
        if (adding != null && isSecondTierPipeItem(stack) == (this instanceof SecondTierPipe)
            && !state.getValue(PRESENT.get(adding)) && canAdd(state, adding)) {
            if (!level.isClientSide()) {
                level.setBlock(pos, state.setValue(PRESENT.get(adding), true), Block.UPDATE_ALL);
                if (adding == PipeType.ITEM) {
                    level.scheduleTick(pos, this, ItemPipeBlock.TICK_INTERVAL);
                }
                if (!player.getAbilities().instabuild) stack.shrink(1);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    public static boolean canAdd(BlockState state, PipeType adding) {
        if (!adding.isFluid()) return true;
        for (PipeType t : BUNDLE_TYPES) {
            if (t.isFluid() && t != adding && state.getValue(PRESENT.get(t))) {
                return false;
            }
        }
        return true;
    }

    private static PipeType partAt(BlockState state, BlockPos pos, BlockHitResult hit) {
        return partAt(state, pos, hit.getLocation());
    }

    public static PipeType partAt(BlockState state, BlockPos pos, net.minecraft.world.phys.Vec3 hitLoc) {
        List<PipeType> present = new ArrayList<>();
        for (PipeType t : BUNDLE_TYPES) {
            if (state.getValue(PRESENT.get(t))) present.add(t);
        }
        if (present.isEmpty()) return null;
        return PipeGeometry.partAt(t -> axisOf(state, t), pos, hitLoc, present);
    }

    public static boolean rotateLayerAt(Level level, BlockPos pos, BlockState state, net.minecraft.world.phys.Vec3 hitLoc) {
        PipeType part = partAt(state, pos, hitLoc);
        if (part == null) return false;
        EnumProperty<Direction.Axis> axisProp = isLowerLayer(part) ? AXIS_LOWER : AXIS;
        Direction.Axis cur = state.getValue(axisProp);
        if (cur == Direction.Axis.Y) {
            return true;
        }
        if (!level.isClientSide()) {
            Direction.Axis next = (cur == Direction.Axis.X) ? Direction.Axis.Z : Direction.Axis.X;
            level.setBlock(pos, state.setValue(axisProp, next), Block.UPDATE_ALL);
        }
        return true;
    }

    static PipeType pipeTypeOf(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof PipeBlock pipe
            && !pipe.connectsAllSides()
            && !(pipe instanceof UniversalFluidPipeBlock)) {
            return pipe.pipeType();
        }
        return null;
    }

    static boolean isUniversalPipeItem(ItemStack stack) {
        return stack.getItem() instanceof BlockItem bi
            && bi.getBlock() instanceof UniversalFluidPipeBlock u
            && !u.connectsAllSides();
    }

    static boolean isSecondTierPipeItem(ItemStack stack) {
        return stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof SecondTierPipe;
    }

    public static boolean carriesUniversalFluid(BlockState state) {
        if (!(state.getBlock() instanceof CompositePipeBlock)) return false;
        return state.getValue(PRESENT.get(PipeType.WATER))
            && state.getValue(PRESENT.get(PipeType.STEAM));
    }

    public static boolean fluidCornerFree(BlockState state) {
        for (PipeType t : BUNDLE_TYPES) {
            if (t.isFluid() && state.getValue(PRESENT.get(t))) return false;
        }
        return true;
    }

    public static BlockState withUniversalFluid(BlockState state) {
        for (PipeType t : BUNDLE_TYPES) {
            if (t.isFluid()) {
                state = state.setValue(PRESENT.get(t), true).setValue(MODE.get(t), PipeMode.AUTO);
            }
        }
        return state;
    }
}
