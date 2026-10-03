package net.minecraft.world.level.block.piston;

import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.PistonType;

/** Стаб ванильной головы поршня (1.21.4, dino939): TYPE, приватный isFittingBase. */
public class PistonHeadBlock extends DirectionalBlock {
    public static final EnumProperty<PistonType> TYPE = BlockStateProperties.PISTON_TYPE;

    protected PistonHeadBlock(Properties properties) { super(properties); }

    private boolean isFittingBase(BlockState head, BlockState base) { return false; }
}
