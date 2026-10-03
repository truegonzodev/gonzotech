package com.gonzotech.machines.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Головка свинцового поршня (0.3.115): своя модель/текстуры. Ставится ванильным
 * {@code PistonBaseBlock.moveBlocks} через редирект в {@code PistonBaseBlockMixin}
 * (ваниль жёстко создаёт {@code Blocks.PISTON_HEAD}). Свойства — как у ванили:
 * FACING × TYPE × SHORT, pushReaction DESTROY (головку не толкают, а ломают).
 */
public class ThirdPistonHeadBlock extends PistonHeadBlock {

    public static final BooleanProperty SHORT = BlockStateProperties.SHORT;

    public static final MapCodec<ThirdPistonHeadBlock> CODEC = simpleCodec(ThirdPistonHeadBlock::new);

    public ThirdPistonHeadBlock(Properties properties) {
        super(properties);
    }

    /**
     * Точный тип возврата (урок 0.3.112): совместим и с объявлением родителя
     * «MapCodec&lt;PistonHeadBlock&gt;», и с ковариантным «? extends».
     */
    @SuppressWarnings("unchecked")
    @Override
    public MapCodec<PistonHeadBlock> codec() {
        return (MapCodec<PistonHeadBlock>) (MapCodec<?>) CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING, TYPE, SHORT);
    }
}
