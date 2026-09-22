package com.gonzotech.core.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Тяжёлая свинцовая дверь (автор 22.09.2026) — 1 блок в ширину и 2 в высоту, раздвижная,
 * без анимации: закрытая — сплошная стенка («как стеклянная панель», автор), ПКМ или редстоун
 * открывают проём — центральная створка модели исчезает (модель {@code opened}), проход свободен.
 *
 * <p>Пара блоков связана ровно так, как у ванильной двери (разбор — {@code docs/HEAVY-DOOR-2026-09-22.md}):
 * {@code HALF = lower/upper} — это соглашение об адресе, а не ссылка, и BlockEntity не нужен.
 * {@link #setPlacedBy} ставит верхнюю половину, {@link #updateShape} зеркалит состояние (или
 * выдаёт AIR, если пара пропала), {@link #neighborChanged} читает редстоун у обеих половин,
 * {@link #playerWillDestroy} глушит двойной дроп.</p>
 *
 * <p>Хитбокс: автор разрешил «округлить до параллелепипеда, охватывающего модель» — закрытая
 * дверь ровно габарит модели (16×16×6), открытая — только рама (порог, притолока и две стойки),
 * иначе открытая дверь оставалась бы преградой. Автор 22.09: проём 12 px игрока устраивает
 * («сам игрок же имеет ширину 0.6 блока»). Открытая дверь = дырка в контуре радиации
 * (см. {@code radiation/Containment}: заслонкой считается только {@code open=false}).</p>
 *
 * <p>Звук (автор 22.09): ванильный металлический набор (железная дверь), но питч
 * {@link #SOUND_PITCH_MIN}–{@link #SOUND_PITCH_MAX} — «тяжелее» обычной железной.</p>
 */
public class HeavyDoorBlock extends Block {

    public static final MapCodec<HeavyDoorBlock> CODEC = simpleCodec(HeavyDoorBlock::new);

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    /** Питч открывания/закрывания (автор 22.09): «сделай 0.3–0.5». */
    private static final float SOUND_PITCH_MIN = 0.3F;
    private static final float SOUND_PITCH_RANGE = 0.2F;

    /** Закрытая дверь: сплошная стенка 6 пикселей — габарит модели (автор: «6×16×16»). */
    private static final VoxelShape CLOSED_Z = Block.box(0.0, 0.0, 5.0, 16.0, 16.0, 11.0);
    private static final VoxelShape CLOSED_X = Block.box(5.0, 0.0, 0.0, 11.0, 16.0, 16.0);

    /**
     * Открытая дверь: створка ушла, осталась рама — порог (или притолока) во всю ширину и две
     * стойки по краям. Проём между стойками 12 пикселей — игрок (0.6 блока) проходит.
     */
    private static final VoxelShape OPEN_LOWER_Z = Shapes.or(
            Block.box(0.0, 0.0, 5.0, 16.0, 1.0, 11.0),
            Block.box(0.0, 1.0, 6.0, 2.0, 16.0, 10.0),
            Block.box(14.0, 1.0, 6.0, 16.0, 16.0, 10.0));
    private static final VoxelShape OPEN_UPPER_Z = Shapes.or(
            Block.box(0.0, 15.0, 5.0, 16.0, 16.0, 11.0),
            Block.box(0.0, 0.0, 6.0, 2.0, 15.0, 10.0),
            Block.box(14.0, 0.0, 6.0, 16.0, 15.0, 10.0));
    private static final VoxelShape OPEN_LOWER_X = Shapes.or(
            Block.box(5.0, 0.0, 0.0, 11.0, 1.0, 16.0),
            Block.box(6.0, 1.0, 0.0, 10.0, 16.0, 2.0),
            Block.box(6.0, 1.0, 14.0, 10.0, 16.0, 16.0));
    private static final VoxelShape OPEN_UPPER_X = Shapes.or(
            Block.box(5.0, 15.0, 0.0, 11.0, 16.0, 16.0),
            Block.box(6.0, 0.0, 0.0, 10.0, 15.0, 2.0),
            Block.box(6.0, 0.0, 14.0, 10.0, 15.0, 16.0));

    public HeavyDoorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, Boolean.FALSE)
                .setValue(POWERED, Boolean.FALSE)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected MapCodec<HeavyDoorBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, POWERED, HALF);
    }

    // ─────────────────────────── формы ───────────────────────────

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return shapeFor(state);
    }

    private static VoxelShape shapeFor(BlockState state) {
        boolean alongZ = state.getValue(FACING).getAxis() == Direction.Axis.Z;
        if (!state.getValue(OPEN)) {
            return alongZ ? CLOSED_Z : CLOSED_X;
        }
        boolean lower = state.getValue(HALF) == DoubleBlockHalf.LOWER;
        if (alongZ) {
            return lower ? OPEN_LOWER_Z : OPEN_UPPER_Z;
        }
        return lower ? OPEN_LOWER_X : OPEN_UPPER_X;
    }

    // ─────────────────────────── постановка ───────────────────────────

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxY() - 1
                || !level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null; // двери нужны две клетки по высоте
        }
        boolean powered = level.hasNeighborSignal(pos) || level.hasNeighborSignal(pos.above());
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection())
                .setValue(POWERED, powered)
                .setValue(OPEN, powered)
                .setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        return state.getValue(HALF) == DoubleBlockHalf.LOWER
                ? belowState.isFaceSturdy(level, below, Direction.UP)
                : belowState.is(this);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess,
                                     BlockPos pos, Direction direction, BlockPos neighborPos,
                                     BlockState neighborState, RandomSource random) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (direction.getAxis() != Direction.Axis.Y || half == DoubleBlockHalf.LOWER != (direction == Direction.UP)) {
            return half == DoubleBlockHalf.LOWER && direction == Direction.DOWN && !state.canSurvive(level, pos)
                    ? Blocks.AIR.defaultBlockState()
                    : super.updateShape(state, level, tickAccess, pos, direction, neighborPos, neighborState, random);
        }
        // Сосед по вертикали — вторая половина: состояние зеркалится (как у ванильной двери).
        return neighborState.is(this) && neighborState.getValue(HALF) != half
                ? neighborState.setValue(HALF, half)
                : Blocks.AIR.defaultBlockState();
    }

    // ─────────────────────────── открывание ───────────────────────────

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        state = state.cycle(OPEN);           // рука работает наравне с редстоуном (автор 22.09)
        level.setBlock(pos, state, 10);
        playSound(player, level, pos, state.getValue(OPEN));
        level.gameEvent(player, state.getValue(OPEN) ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   @Nullable Orientation orientation, boolean isMoving) {
        boolean powered = level.hasNeighborSignal(pos)
                || level.hasNeighborSignal(pos.relative(
                        state.getValue(HALF) == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN));
        if (!this.defaultBlockState().is(block) && powered != state.getValue(POWERED)) {
            if (powered != state.getValue(OPEN)) {
                playSound(null, level, pos, powered);
            }
            level.setBlock(pos, state.setValue(POWERED, powered).setValue(OPEN, powered), 2);
        }
    }

    private static void playSound(@Nullable Player player, Level level, BlockPos pos, boolean opening) {
        level.playSound(player, pos, opening ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE,
                SoundSource.BLOCKS, 1.0F,
                SOUND_PITCH_MIN + level.getRandom().nextFloat() * SOUND_PITCH_RANGE);
    }

    // ─────────────────────────── ломание ───────────────────────────

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && (player.isCreative() || !player.hasCorrectToolForDrops(state))) {
            removeOtherHalfSilently(level, pos, state, player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * Ванильная {@code DoublePlantBlock.preventDropFromBottomPart} недоступна из чужого пакета
     * (protected), поэтому повторяем её здесь: при разрушении ВЕРХНЕЙ половины нижняя снимается
     * молча (флаг 32 = без дропа), иначе дверь выпадала бы двумя предметами.
     */
    private static void removeOtherHalfSilently(Level level, BlockPos pos, BlockState state, Player player) {
        if (state.getValue(HALF) != DoubleBlockHalf.UPPER) {
            return;
        }
        BlockPos below = pos.below();
        BlockState lower = level.getBlockState(below);
        if (lower.is(state.getBlock()) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
            BlockState replacement = lower.getFluidState().is(Fluids.WATER)
                    ? Blocks.WATER.defaultBlockState()
                    : Blocks.AIR.defaultBlockState();
            level.setBlock(below, replacement, 35);              // 1 | 2 | 32: соседи + клиенты, без дропа
            level.levelEvent(player, 2001, below, Block.getId(lower));
        }
    }

    // ─────────────────────────── мелочи ───────────────────────────

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return switch (type) {
            case LAND, AIR -> state.getValue(OPEN);
            case WATER -> false;
        };
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }
}
