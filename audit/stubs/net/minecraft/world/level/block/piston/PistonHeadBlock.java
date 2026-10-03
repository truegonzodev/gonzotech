package net.minecraft.world.level.block.piston;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.PistonType;

/**
 * Стаб ванильной головки поршня (1.21.4, dino939): FACING × TYPE × SHORT,
 * приватный isFittingBase (миксин), codec() ТОЧНОГО типа (урок 0.3.112).
 */
public class PistonHeadBlock extends DirectionalBlock {
    public static final EnumProperty<PistonType> TYPE = BlockStateProperties.PISTON_TYPE;
    public static final BooleanProperty SHORT = BlockStateProperties.SHORT;

    protected PistonHeadBlock(Properties properties) { super(properties); }

    @Override
    public MapCodec<PistonHeadBlock> codec() { return null; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING, TYPE, SHORT);
    }

    private boolean isFittingBase(BlockState head, BlockState base) { return false; }
}
