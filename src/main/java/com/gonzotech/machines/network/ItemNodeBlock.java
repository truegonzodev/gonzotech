package com.gonzotech.machines.network;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Предметный УЗЕЛ ({@link PipeType#ITEM}) — как {@link NodeBlock} (открыт во все
 * 6 сторон, полный куб, точка ветвления), но АКТИВНЫЙ: сам тянет предметы из
 * прилегающих контейнеров и раскидывает по сети ({@link ItemRouting}).
 * <p>
 * Именно узел — обычная точка «забора»: ставишь его вплотную к сундуку, ключом
 * переключаешь грань в ЗАБОР/АВТО. Тикинг — тот же, что у {@link ItemPipeBlock}
 * (запланированный самоперепланирующийся тик, без {@code BlockEntity}); логика
 * дублируется, т.к. Java без множественного наследования.
 */
public class ItemNodeBlock extends NodeBlock {

    public ItemNodeBlock(Properties properties) {
        super(properties, PipeType.ITEM);
    }

    @Override
    protected MapCodec<? extends PipeBlock> makeCodec() {
        return simpleCodec(ItemNodeBlock::new);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide()) {
            level.scheduleTick(pos, this, ItemPipeBlock.TICK_INTERVAL);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        ItemRouting.tickExtract(level, pos, state);
        level.scheduleTick(pos, this, ItemPipeBlock.TICK_INTERVAL);
    }
}
