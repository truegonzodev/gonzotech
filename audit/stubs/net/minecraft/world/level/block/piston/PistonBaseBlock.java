package net.minecraft.world.level.block.piston;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
/** Стаб ванильного поршня (сигнатуры 1.21.4, dino939: ctor(sticky, props)). */
public class PistonBaseBlock extends Block {
    public PistonBaseBlock(boolean sticky, BlockBehaviour.Properties properties) { super(properties); }
    @Override
    public MapCodec<? extends Block> codec() { return null; }
}
