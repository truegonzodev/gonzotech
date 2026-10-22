package com.gonzotech.machines.block;

import com.gonzotech.machines.block.entity.PlotnickBlockEntity;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Плотник (0.3.256, автор) — контроллер сектора автошахтера 3×3 чанка:
 * разметка площадок метками-вымпелами, хранилище v3 (9 классических слотов
 * по 64 + виртуаль 54 типа × 4 стопки: «добытое» и депо), сейсмика, буры,
 * сеть рабочих блоков, анабиоз соседей и гейт приватных правил.
 */
public final class PlotnickBlock extends MachineBlock {

    public static final MapCodec<PlotnickBlock> CODEC = simpleCodec(PlotnickBlock::new);

    public PlotnickBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<PlotnickBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlotnickBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        Level level, BlockState state, BlockEntityType<T> type
    ) {
        if (level.isClientSide()) return null;
        return FireboxBlock.createTickerHelper(type, ModBlockEntities.PLOTNICK.get(),
            PlotnickBlockEntity::serverTick);
    }

    /**
     * Спящий в анабиозе Плотник GUI не открывает (автор, 0.3.264): сектор
     * занят другим, хозяин — он. Ответ SUCCESS — диалог не всплывает,
     * игрок получает только сообщение в чат.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof PlotnickBlockEntity plotnick
            && plotnick.isHibernating()) {
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(
                    Component.translatable("message.gonzotech.plotnick.hibernating"));
            }
            return InteractionResult.SUCCESS;
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())
            && level.getBlockEntity(pos) instanceof PlotnickBlockEntity plotnick) {
            plotnick.onBroken();
        }
        // Слом Плотника (любой путь: рука, ТНТ, команды) будит анабиозных
        // соседей — перевыборы лидера сектора.
        if (!level.isClientSide && level instanceof net.minecraft.server.level.ServerLevel server) {
            PlotnickBlockEntity.notifySectorChanged(server, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
