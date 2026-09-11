package com.gonzotech.machines.network;

import com.gonzotech.machines.item.WrenchItem;
import com.gonzotech.machines.turbine.TurbineStructure;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.ScheduledTickAccess;

/**
 * УНИВЕРСАЛЬНЫЙ УЗЕЛ (first-tier) — один полный куб, который несёт СРАЗУ все типы
 * труб первого уровня: провод (WIRE), теплотрубу (HEAT), универсальную
 * жидкостную (вода+пар) и предметную (ITEM). По сути это «пучок из четырёх труб»,
 * но открытый во все 6 сторон, как узел — точка ветвления для любого ресурса.
 * <p>
 * <b>Штраф.</b> Пропускная способность узла на 10% хуже труб, из которых он
 * «состоит»: {@link #throughputFactor} возвращает {@code 0.9} для каждого типа.
 * Маршрутизаторы ({@link PipeRouting}, {@link ItemRouting}) умножают лимит слива
 * за тик на этот коэффициент.
 * <p>
 * Пассивен для энергии/жидкостей (слив дотягивает {@link PipeRouting}); для
 * предметов АКТИВЕН — тикает забор ({@link ItemRouting}), как предметный узел.
 * Реализует {@link PipeCarrier} напрямую (не наследует {@link PipeBlock}), т.к.
 * несёт несколько типов сразу и не привязан к оси.
 */
public class UniversalNodeBlock extends RotatedPillarBlock implements PipeCarrier, SimpleWaterloggedBlock {

    public static final EnumProperty<PipeMode> MODE = EnumProperty.create("mode", PipeMode.class);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    /** Штраф универсального узла к пропускной способности каждого типа. */
    public static final double THROUGHPUT_FACTOR = 0.9;

    private final MapCodec<UniversalNodeBlock> codec;

    public UniversalNodeBlock(Properties properties) {
        super(properties);
        this.codec = simpleCodec(UniversalNodeBlock::new);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(AXIS, Direction.Axis.Y)
            .setValue(MODE, PipeMode.AUTO)
            .setValue(WATERLOGGED, false));
    }

    @Override
    public MapCodec<? extends RotatedPillarBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, MODE, WATERLOGGED);
    }

    // ─────────────────────────── PipeCarrier ───────────────────────────

    /** Несёт все типы первого тира: провод, теплотрубу, жидкости (вода+пар), предметы. */
    @Override
    public boolean carries(BlockState state, PipeType type) {
        return true;
    }

    @Override
    public boolean opensToward(BlockState state, PipeType type, Direction dir) {
        return true; // узел открыт во все 6 сторон для любого типа
    }

    @Override
    public PipeMode modeFor(BlockState state, PipeType type) {
        return state.getValue(MODE);
    }

    @Override
    public double throughputFactor(BlockState state, PipeType type) {
        return THROUGHPUT_FACTOR;
    }

    // ─────────────────────────── постановка/форма ───────────────────────────

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
        return this.defaultBlockState()
            .setValue(MODE, PipeMode.AUTO)
            .setValue(WATERLOGGED, fluid.getType() == Fluids.WATER);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
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

    // ─────────── активный тик: забор предметов (как предметный узел) ───────────

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!state.is(oldState.getBlock())) TurbineStructure.portPlaced(level, pos);
        if (!level.isClientSide()) {
            level.scheduleTick(pos, this, ItemPipeBlock.TICK_INTERVAL);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) TurbineStructure.portRemoved(level, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        ItemRouting.tickExtract(level, pos, state);
        level.scheduleTick(pos, this, ItemPipeBlock.TICK_INTERVAL);
    }

    // ─────────────────────────── ПКМ / гаечный ключ ───────────────────────────

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide() && TurbineStructure.openMenu(level, pos, player)) {
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide() && TurbineStructure.openMenu(level, pos, player)) {
            return InteractionResult.SUCCESS;
        }
        if (stack.getItem() instanceof WrenchItem) {
            if (!level.isClientSide()) {
                PipeMode nextMode = state.getValue(MODE).next();
                level.setBlock(pos, state.setValue(MODE, nextMode), Block.UPDATE_ALL);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
