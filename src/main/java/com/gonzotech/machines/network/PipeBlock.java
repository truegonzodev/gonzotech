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
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Труба энергосети (провод/теплотруба). Скелет Фазы «логистика».
 * <p>
 * <b>Форма/постановка.</b> Труба — это axis-блок (как бревно): наследует
 * {@link RotatedPillarBlock}, ставится вдоль оси, куда смотрит игрок. «Перёд/зад»
 * не важны, важна только ось — этого достаточно для скелета (умный
 * авто-коннект-рендер с тройниками/уголками добавим позже).
 * <p>
 * <b>Связность/передача.</b> Определяется НЕ осью, а соседством: трубы одного
 * {@link PipeType} вплотную образуют общий контур ({@link EnergyNetwork}). Тикает
 * не труба, а сеть целиком — раз в тик собирает ресурс со «входов» и раздаёт на
 * «выходы» независимо от длины трассы («телепорт»). Сами трубы НЕ тикают.
 * <p>
 * <b>Режим.</b> Свойство блокстейта {@link #MODE} (AUTO/PULL/PUSH), переключается
 * ПКМ гаечным ключом ({@link WrenchItem}). Влияет на то, как труба на конце
 * контура взаимодействует с примыкающей машиной. Хранение режима в блокстейте
 * избавляет трубу от BlockEntity.
 */
public class PipeBlock extends RotatedPillarBlock {

    public static final EnumProperty<PipeMode> MODE = EnumProperty.create("mode", PipeMode.class);

    private final PipeType pipeType;
    /** Кодек, захватывающий тип трубы (у провода и теплотрубы он разный). */
    private final MapCodec<PipeBlock> codec;

    public PipeBlock(Properties properties, PipeType pipeType) {
        super(properties);
        this.pipeType = pipeType;
        this.codec = simpleCodec(props -> new PipeBlock(props, pipeType));
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(AXIS, Direction.Axis.Y)
            .setValue(MODE, PipeMode.AUTO));
    }

    public PipeType pipeType() {
        return pipeType;
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
            // Режим сменился → пересчитать порты сети (членство НЕ меняется).
            NetworkManager.get(level).markDirty(pos, pipeType);
        }
        return InteractionResult.SUCCESS;
    }

    // ─────────────────── членство в сети (слияние/распад) ───────────────────

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && !oldState.is(this)) {
            NetworkManager.get(level).onPipePlaced(level, pos, pipeType);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide() && !newState.is(this)) {
            NetworkManager.get(level).onPipeRemoved(level, pos, pipeType);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Сосед изменился (машину поставили/убрали рядом) → пересчитать порты сети. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
        if (!level.isClientSide()) {
            NetworkManager.get(level).markDirty(pos, pipeType);
        }
    }
}
