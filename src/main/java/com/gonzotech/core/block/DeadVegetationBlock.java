package com.gonzotech.core.block;

import com.gonzotech.core.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/**
 * Опалённая растительность пустошей (автор, 2026-09-19): декоративные
 * #nonsolid блоки-пучки с cross-моделью — {@code charred_sapling} и три
 * {@code scorched_tuft*}. Как трава: без age, без роста, ставятся на
 * почву/»мёртвую» землю дезолейт-зон, срезаются ножницами (ванильное
 * поведение short grass), ломаются при убирании опоры (механика BushBlock).
 */
public class DeadVegetationBlock extends BushBlock {

    public static final MapCodec<DeadVegetationBlock> CODEC = simpleCodec(DeadVegetationBlock::new);

    /** Допустимые грунты: ванильные растительные + наши мёртвые почвы. Lazy-сет,
     * чтобы регистрация ModBlocks успела отработать до первого вопроса. */
    private static Set<Block> allowedSoil;

    public DeadVegetationBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        if (allowedSoil == null) {
            allowedSoil = Set.of(
                Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT,
                Blocks.PODZOL, Blocks.MYCELIUM,
                ModBlocks.DEAD_DIRT.get(), ModBlocks.DEAD_SAND.get(), ModBlocks.DEAD_STONE.get());
        }
        return allowedSoil.contains(state.getBlock());
    }
}
