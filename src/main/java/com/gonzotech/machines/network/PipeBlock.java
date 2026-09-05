package com.gonzotech.machines.network;

import com.gonzotech.machines.item.WrenchItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

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
public class PipeBlock extends RotatedPillarBlock {

    public static final EnumProperty<PipeMode> MODE = EnumProperty.create("mode", PipeMode.class);

    // Вытянутый хитбокс 4×4 в сечении, 16 в длину — совпадает с тонкой моделью
    // (а не полный куб, как у котла). По одной форме на каждую ось.
    private static final VoxelShape SHAPE_Y = Block.box(6, 0, 6, 10, 16, 10);
    private static final VoxelShape SHAPE_Z = Block.box(6, 6, 0, 10, 10, 16);
    private static final VoxelShape SHAPE_X = Block.box(0, 6, 6, 16, 10, 10);

    private final PipeType pipeType;
    /** Кодек, захватывающий тип трубы (у провода и теплотрубы он разный). */
    private final MapCodec<? extends PipeBlock> codec;

    public PipeBlock(Properties properties, PipeType pipeType) {
        super(properties);
        this.pipeType = pipeType;
        this.codec = makeCodec();
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(AXIS, Direction.Axis.Y)
            .setValue(MODE, PipeMode.AUTO));
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

    @Override
    public MapCodec<? extends RotatedPillarBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, MODE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
            .setValue(AXIS, context.getClickedFace().getAxis())
            .setValue(MODE, PipeMode.AUTO);
    }

    // ─────────────────────────── форма (хитбокс) ───────────────────────────

    private static VoxelShape shapeFor(BlockState state) {
        return switch (state.getValue(AXIS)) {
            case X -> SHAPE_X;
            case Z -> SHAPE_Z;
            default -> SHAPE_Y;
        };
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
        if (!(stack.getItem() instanceof WrenchItem)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            PipeMode nextMode = state.getValue(MODE).next();
            level.setBlock(pos, state.setValue(MODE, nextMode), Block.UPDATE_ALL);
            player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable(
                    "message.gonzotech.pipe_mode." + nextMode.getSerializedName()),
                true);
        }
        return InteractionResult.SUCCESS;
    }
}
