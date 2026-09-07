package com.gonzotech.machines.network;

import com.gonzotech.machines.item.WrenchItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import net.minecraft.world.level.storage.loot.LootParams;
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
 * <b>Генерик по {@link PipeType}.</b> Свойства и логика строятся из
 * {@code PipeType.values()}. Сейчас реально участвуют WIRE + HEAT; когда добавим
 * FLUID/ITEM — они автоматически получат свой угол и заработают в связке без
 * переделок.
 */
public class CompositePipeBlock extends RotatedPillarBlock implements PipeCarrier, SimpleWaterloggedBlock {

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
        for (PipeType t : PipeType.values()) {
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
        for (PipeType t : PipeType.values()) {
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
        for (PipeType t : PipeType.values()) {
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
        return state.getValue(PRESENT.get(type));
    }

    @Override
    public boolean opensToward(BlockState state, PipeType type, Direction dir) {
        if (!carries(state, type)) return false;
        // Тип открыт двумя торцами вдоль оси СВОЕГО слоя.
        return dir.getAxis() == axisOf(state, type);
    }

    /**
     * В нижнем ли слое сечения находится тип. Нижний слой — HEAT + ITEM (v=2);
     * верхний — WIRE + FLUID/вода/пар (v=10). Разбивка совпадает с
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
        return state.getValue(MODE.get(type));
    }

    // ─────────────────────────── форма (хитбокс) ───────────────────────────

    private static VoxelShape shapeFor(BlockState state) {
        VoxelShape shape = Shapes.empty();
        for (PipeType t : PipeType.values()) {
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
    // Пучок остаётся БЕЗ BlockEntity: забор предметов гоняем запланированным
    // тиком блока (как одиночная предметная труба). Тик заводим/перепланируем,
    // только пока в пучке присутствует тип ITEM.

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && state.getValue(PRESENT.get(PipeType.ITEM))) {
            level.scheduleTick(pos, this, ItemPipeBlock.TICK_INTERVAL);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(PRESENT.get(PipeType.ITEM))) return; // тип убрали — тик прекращаем
        ItemRouting.tickExtract(level, pos, state);
        level.scheduleTick(pos, this, ItemPipeBlock.TICK_INTERVAL);
    }

    // ─────────────────────────── взаимодействие ───────────────────────────

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        // Ключ работает по КОНКРЕТНОЙ трубе пучка — той, куда наведён прицел.
        //  • обычный ПКМ — прокрутка режима (AUTO/PULL/PUSH) наведённой трубы;
        //  • Shift/Alt+ПКМ — поворот СЛОЯ наведённой трубы (X↔Z). Слой = пара труб
        //    по высоте сечения: верх WIRE+FLUID (AXIS), низ HEAT+ITEM (AXIS_LOWER).
        //    Вертикальный слой (Y) не крутится — из-за зеркала модели Y-раскол
        //    невозможен; клик просто «съедается».
        if (stack.getItem() instanceof WrenchItem) {
            PipeType part = partAt(state, pos, hit);
            if (part == null) return InteractionResult.PASS;

            if (player.isSecondaryUseActive()) {
                // Поворот слоя наведённой трубы X↔Z.
                boolean lower = isLowerLayer(part);
                EnumProperty<Direction.Axis> axisProp = lower ? AXIS_LOWER : AXIS;
                Direction.Axis cur = state.getValue(axisProp);
                if (cur == Direction.Axis.Y) {
                    // Вертикальный слой не поворачиваем — но клик считаем обработанным,
                    // чтобы ключом случайно не поставить/сломать что-то.
                    return InteractionResult.SUCCESS;
                }
                if (!level.isClientSide()) {
                    Direction.Axis next = (cur == Direction.Axis.X) ? Direction.Axis.Z : Direction.Axis.X;
                    level.setBlock(pos, state.setValue(axisProp, next), Block.UPDATE_ALL);
                }
                return InteractionResult.SUCCESS;
            }

            if (!level.isClientSide()) {
                PipeMode nextMode = state.getValue(MODE.get(part)).next();
                level.setBlock(pos, state.setValue(MODE.get(part), nextMode), Block.UPDATE_ALL);
                // Ничего в action-bar: type/mode/поток трубы под прицелом уже
                // показывает WrenchHud.
            }
            return InteractionResult.SUCCESS;
        }

        // Добавление универсальной жидк.трубы: занимает весь FLUID-угол (вода+пар),
        // если он ещё свободен.
        if (isUniversalPipeItem(stack) && fluidCornerFree(state)) {
            if (!level.isClientSide()) {
                level.setBlock(pos, withUniversalFluid(state), Block.UPDATE_ALL);
                if (!player.getAbilities().instabuild) stack.shrink(1);
            }
            return InteractionResult.SUCCESS;
        }

        // Добавление ещё одной трубы в связку: используем предмет-трубу другого типа.
        PipeType adding = pipeTypeOf(stack);
        if (adding != null && !state.getValue(PRESENT.get(adding)) && canAdd(state, adding)) {
            if (!level.isClientSide()) {
                level.setBlock(pos, state.setValue(PRESENT.get(adding), true), Block.UPDATE_ALL);
                // Если в пучок добавили предметную трубу — запускаем забор-тик
                // (setBlock тем же блоком не всегда вызывает onPlace).
                if (adding == PipeType.ITEM) {
                    level.scheduleTick(pos, this, ItemPipeBlock.TICK_INTERVAL);
                }
                if (!player.getAbilities().instabuild) stack.shrink(1);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /**
     * Можно ли добавить тип {@code adding} в пучок {@code state}. Жидкостные типы
     * (вода/пар/…) делят один угол сечения FLUID — поэтому в пучке одновременно
     * допустима только ОДНА жидкостная труба. Прочие типы ограничены лишь тем,
     * что такого типа ещё нет (проверяется у места вызова).
     */
    public static boolean canAdd(BlockState state, PipeType adding) {
        if (!adding.isFluid()) return true;
        for (PipeType t : PipeType.values()) {
            if (t.isFluid() && t != adding && state.getValue(PRESENT.get(t))) {
                return false; // жидкостный угол уже занят другой жидкостью
            }
        }
        return true;
    }

    /** Тип трубы пучка, в которую сейчас смотрит игрок (по точке наведения). */
    private static PipeType partAt(BlockState state, BlockPos pos, BlockHitResult hit) {
        List<PipeType> present = new ArrayList<>();
        for (PipeType t : PipeType.values()) {
            if (state.getValue(PRESENT.get(t))) present.add(t);
        }
        if (present.isEmpty()) return null;
        return PipeGeometry.partAt(t -> axisOf(state, t), pos, hit.getLocation(), present);
    }

    // ─────────────────────────── дроп компонентов ───────────────────────────

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        // Дропаем по одной трубе каждого несомого типа (связку «разбираем»).
        // Особый случай: FLUID-угол занят универсальной трубой (вода+пар вместе) —
        // тогда вместо двух жидк.труб дропаем ОДНУ универсальную.
        List<ItemStack> drops = new ArrayList<>();
        boolean universalFluid = carriesUniversalFluid(state);
        if (universalFluid) {
            Item uni = BuiltInRegistries.ITEM.getValue(
                ResourceLocation.fromNamespaceAndPath("gonzotech", "first_universal_fluid_pipe"));
            if (uni != Items.AIR) drops.add(new ItemStack(uni));
        }
        for (PipeType t : PipeType.values()) {
            if (!state.getValue(PRESENT.get(t))) continue;
            if (universalFluid && t.isFluid()) continue; // уже выдали как универсальную
            Item item = BuiltInRegistries.ITEM.getValue(ResourceLocation.fromNamespaceAndPath("gonzotech", t.id()));
            if (item != Items.AIR) drops.add(new ItemStack(item));
        }
        return drops;
    }

    /**
     * {@link PipeType} предмета-ТРУБЫ, или {@code null} если это не обычная труба.
     * Узлы ({@link NodeBlock#connectsAllSides()}) в пучок не стакаются — для них
     * возвращаем {@code null}. Универсальная труба тоже {@code null} здесь — у неё
     * отдельный путь ({@link #isUniversalPipeItem}), т.к. она занимает FLUID-угол
     * СРАЗУ двумя ресурсами.
     */
    static PipeType pipeTypeOf(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof PipeBlock pipe
            && !pipe.connectsAllSides()
            && !(pipe instanceof UniversalFluidPipeBlock)) {
            return pipe.pipeType();
        }
        return null;
    }

    /**
     * Предмет ли это ОБЫЧНОЙ (не-узел) универсальной жидкостной трубы. В пучке она
     * представлена ОБОИМИ жидкостными флагами (вода+пар) в общем FLUID-углу.
     */
    static boolean isUniversalPipeItem(ItemStack stack) {
        return stack.getItem() instanceof BlockItem bi
            && bi.getBlock() instanceof UniversalFluidPipeBlock u
            && !u.connectsAllSides();
    }

    /** Заняты ли в пучке ОБА жидкостных флага (вода+пар) — т.е. FLUID-угол универсальный. */
    public static boolean carriesUniversalFluid(BlockState state) {
        if (!(state.getBlock() instanceof CompositePipeBlock)) return false;
        return state.getValue(PRESENT.get(PipeType.WATER))
            && state.getValue(PRESENT.get(PipeType.STEAM));
    }

    /** Свободен ли FLUID-угол пучка (нет ни воды, ни пара). */
    public static boolean fluidCornerFree(BlockState state) {
        for (PipeType t : PipeType.values()) {
            if (t.isFluid() && state.getValue(PRESENT.get(t))) return false;
        }
        return true;
    }

    /**
     * Состояние пучка с добавленной универсальной жидк.трубой: выставляет ОБА
     * жидкостных флага (вода+пар) в режиме AUTO. Вызывать, только если
     * {@link #fluidCornerFree} истинно.
     */
    public static BlockState withUniversalFluid(BlockState state) {
        for (PipeType t : PipeType.values()) {
            if (t.isFluid()) {
                state = state.setValue(PRESENT.get(t), true).setValue(MODE.get(t), PipeMode.AUTO);
            }
        }
        return state;
    }
}
