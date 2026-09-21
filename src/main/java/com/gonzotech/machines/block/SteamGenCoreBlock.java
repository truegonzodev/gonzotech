package com.gonzotech.machines.block;

import com.gonzotech.machines.block.entity.SteamGenCoreBlockEntity;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Ядро продвинутого парогенератора.
 *
 * <p>BlockEntity существует у каждого ядра, но тикер получает только одно
 * отмеченное {@link #CONTROLLER} ядро — в углу внутреннего объёма
 * {@code (minX+1, minY+1, minZ+1)}.</p>
 */
public final class SteamGenCoreBlock extends SteamGenPartBlock implements EntityBlock {

    public static final MapCodec<SteamGenCoreBlock> CODEC = simpleCodec(SteamGenCoreBlock::new);
    public static final BooleanProperty CONTROLLER = BooleanProperty.create("controller");

    public SteamGenCoreBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FORMED, false)
            .setValue(CONTROLLER, false));
    }

    @Override
    protected MapCodec<SteamGenCoreBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CONTROLLER);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SteamGenCoreBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide() || !state.getValue(CONTROLLER)) return null;
        return FireboxBlock.createTickerHelper(type, ModBlockEntities.SECOND_STEAMGEN_CORE.get(),
            SteamGenCoreBlockEntity::serverTick);
    }
}
