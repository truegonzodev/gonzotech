package net.minecraft.world.level.block.piston;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
/**
 * Стаб ванильного поршня (сигнатуры 1.21.4, dino939: ctor(sticky, props)).
 * 0.3.112: codec() возвращает ТОЧНЫЙ MapCodec<PistonBaseBlock> — не «? extends»!
 * Именно это ловила сборка автора в 0.3.110 (несовместимый ковариантный возврат).
 */
public class PistonBaseBlock extends net.minecraft.world.level.block.DirectionalBlock {
    public static final MapCodec<PistonBaseBlock> CODEC =
        simpleCodec(properties -> new PistonBaseBlock(false, properties));
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty EXTENDED =
        net.minecraft.world.level.block.state.properties.BlockStateProperties.EXTENDED;
    public PistonBaseBlock(boolean sticky, BlockBehaviour.Properties properties) { super(properties); }
    @Override
    public MapCodec<PistonBaseBlock> codec() { return CODEC; }
}
