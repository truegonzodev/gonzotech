package com.gonzotech.machines.network;

import com.gonzotech.machines.item.WrenchItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.ScheduledTickAccess;

/**
 * Труба энергосети (провод — GTU / теплотруба — GTH). Полностью ПАССИВНЫЙ блок:
 * без BlockEntity, без тика, без хранения.
 * <p>
 * <b>Идея.</b> Провод — это «вынос слива за пределы блока». Он ничего не хранит и
 * ничего ни из кого не «высасывает». Когда машина хочет добровольно слить свою
 * выходную шкалу и рядом нет прямого приёмника, она через {@link PipeRouting}
 * обходит связную цепь труб и телепортирует ресурс приёмникам за тот же тик.
 * Логику «что можно слить» держит сама машина (см. её {@code push*}).
 * <p>
 * <b>Форма/постановка.</b> Axis-блок (как бревно): ставится вдоль оси, куда
 * смотрит игрок. Хитбокс и модель — тонкий брусок 4×4×16 (не полный куб).
 * Уголков/тройников нет — соединять разветвления будет отдельный блок-узел.
 * <p>
 * <b>Режим.</b> Свойство блокстейта {@link #MODE} (AUTO/PULL/PUSH), переключается
 * ПКМ гаечным ключом ({@link WrenchItem}); влияет только на грань труба↔машина
 * (вход/выход). Хранение режима в блокстейте избавляет трубу от BlockEntity.
 */
public class PipeBlock extends RotatedPillarBlock implements PipeCarrier, SimpleWaterloggedBlock {

    public static final EnumProperty<PipeMode> MODE = EnumProperty.create("mode", PipeMode.class);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private final PipeType pipeType;
    /** Кодек, захватывающий тип трубы (у провода и теплотрубы он разный). */
    private final MapCodec<? extends PipeBlock> codec;

    public PipeBlock(Properties properties, PipeType pipeType) {
        super(properties);
        this.pipeType = pipeType;
        this.codec = makeCodec();
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(AXIS, Direction.Axis.Y)
            .setValue(MODE, PipeMode.AUTO)
            .setValue(WATERLOGGED, false));
    }

    /**
     * Строит кодек, воссоздающий именно ЭТОТ подкласс трубы (важно для узла —
     * {@link NodeBlock}). Вызывается из конструктора {@link PipeBlock}, когда
     * {@link #pipeType} уже проставлен.
     */
    protected MapCodec<? extends PipeBlock> makeCodec() {
        return simpleCodec(props -> new PipeBlock(props, pipeType()));
    }

    public PipeType pipeType() {
        return pipeType;
    }

    /**
     * Соединяется ли этот блок со всех 6 сторон. Обычная труба — нет (только два
     * конца по оси); блок-узел ({@link NodeBlock}) — да. Маршрутизатор
     * ({@link PipeRouting}) по этому флагу решает, какие грани трубы «открыты».
     */
    public boolean connectsAllSides() {
        return false;
    }

    // ─────────────────────────── PipeCarrier ───────────────────────────

    @Override
    public boolean carries(BlockState state, PipeType type) {
        return this.pipeType == type;
    }

    @Override
    public boolean opensToward(BlockState state, PipeType type, Direction dir) {
        if (this.pipeType != type) return false;
        if (connectsAllSides()) return true;
        return dir.getAxis() == state.getValue(AXIS);
    }

    @Override
    public PipeMode modeFor(BlockState state, PipeType type) {
        return state.getValue(MODE);
    }

    @Override
    public MapCodec<? extends RotatedPillarBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, MODE, WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
        return this.defaultBlockState()
            .setValue(AXIS, context.getClickedFace().getAxis())
            .setValue(MODE, PipeMode.AUTO)
            .setValue(WATERLOGGED, fluid.getType() == Fluids.WATER);
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

    // ─────────────────────────── форма (хитбокс) ───────────────────────────

    private VoxelShape shapeFor(BlockState state) {
        // Труба сидит в СВОЁМ углу сечения (не по центру) — так одиночная труба и
        // связка выглядят одинаково, а тип всегда на своём месте.
        return PipeGeometry.cornerBox(state.getValue(AXIS), pipeType);
    }

    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                                  BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                                           BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    // ─────────────────────────── гаечный ключ ───────────────────────────

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        // Ключ — прокрутить режим этой трубы. Никаких сообщений в action-bar:
        // тип/режим/поток и так живьём висят над прицелом (WrenchHud).
        if (stack.getItem() instanceof WrenchItem) {
            if (!level.isClientSide()) {
                PipeMode nextMode = state.getValue(MODE).next();
                level.setBlock(pos, state.setValue(MODE, nextMode), Block.UPDATE_ALL);
            }
            return InteractionResult.SUCCESS;
        }

        // Труба ДРУГОГО типа в руке → собрать связку (составной блок): сохраняем
        // ось этой трубы, добавляем оба типа. Узлы (connectsAllSides) не стакаем.
        // Жидкостные типы делят один угол — воду и пар вместе в пучок нельзя.
        if (!connectsAllSides()) {
            PipeType adding = CompositePipeBlock.pipeTypeOf(stack);
            boolean fluidClash = adding != null && adding.isFluid() && this.pipeType.isFluid();
            if (adding != null && adding != this.pipeType && !fluidClash && ModCompositeAccess.get() != null) {
                if (!level.isClientSide()) {
                    Direction.Axis axis = state.getValue(AXIS);
                    BlockState composite = ModCompositeAccess.get().defaultBlockState()
                        .setValue(AXIS, axis)
                        .setValue(CompositePipeBlock.WATERLOGGED, state.getValue(WATERLOGGED))
                        .setValue(CompositePipeBlock.PRESENT.get(this.pipeType), true)
                        .setValue(CompositePipeBlock.MODE.get(this.pipeType), state.getValue(MODE))
                        .setValue(CompositePipeBlock.PRESENT.get(adding), true);
                    level.setBlock(pos, composite, Block.UPDATE_ALL);
                    if (!player.getAbilities().instabuild) stack.shrink(1);
                }
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }
}
