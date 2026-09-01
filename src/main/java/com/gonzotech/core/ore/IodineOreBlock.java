package com.gonzotech.core.ore;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Йодистая руда: опыт как у обычной руды (DropExperienceBlock) плюс партиклы
 * «как у редстоуна» при взаимодействии — фиолетовая пыль (reddust) #7f1aab,
 * size 1.5, без свечения самого блока. Логика частиц скопирована с ванильного
 * {@code RedStoneOreBlock.spawnParticles} (частицы вылетают у открытых граней),
 * но без LIT-состояния.
 * <p>
 * Триггеры:
 * <ul>
 *   <li>ПКМ (в т.ч. удержание) — {@code useItemOn}/{@code useWithoutItem};</li>
 *   <li>ЛКМ — {@code getDestroyProgress}: вызывается каждый тик, пока держишь
 *       ЛКМ (и при переводе прицела на руду), поэтому удержание ЛКМ тоже
 *       сыплет частицы, как и удержание ПКМ.</li>
 * </ul>
 */
public class IodineOreBlock extends DropExperienceBlock {

    /** #7f1aab, size 1.5 — как просили для йодных руд (в 1.21.4 цвет — int 0xRRGGBB). */
    private static final DustParticleOptions PARTICLE = new DustParticleOptions(0x7F1AAB, 1.5F);

    public IodineOreBlock(IntProvider xpRange, BlockBehaviour.Properties properties) {
        super(xpRange, properties);
    }

    /** ПКМ с предметом в руке. PASS — не мешаем размещению блоков и прочему. */
    @Override
    protected InteractionResult useItemOn(
        ItemStack stack,
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        InteractionHand hand,
        BlockHitResult hitResult
    ) {
        spawnParticles(level, pos);
        return InteractionResult.PASS;
    }

    /** ПКМ с пустой рукой. */
    @Override
    protected InteractionResult useWithoutItem(
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        BlockHitResult hitResult
    ) {
        spawnParticles(level, pos);
        return InteractionResult.PASS;
    }

    /**
     * Хук, вызываемый каждый тик, пока игрок держит ЛКМ и ломает этот блок
     * (в т.ч. если прицел перевели на него, не отпуская кнопку). Частицы —
     * чисто визуал, поэтому достаточно клиентской стороны.
     */
    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (level instanceof Level lvl && lvl.isClientSide) {
            spawnParticles(lvl, pos);
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    /**
     * По одному облачку у каждой грани, у которой нет непрозрачного соседа —
     * ровно как ванильный редстоун (0.5625 = середина + ~половина блока наружу).
     */
    private static void spawnParticles(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (!level.getBlockState(neighbor).isSolidRender()) {
                Direction.Axis axis = direction.getAxis();
                double dx = axis == Direction.Axis.X ? 0.5 + 0.5625 * direction.getStepX() : level.random.nextFloat();
                double dy = axis == Direction.Axis.Y ? 0.5 + 0.5625 * direction.getStepY() : level.random.nextFloat();
                double dz = axis == Direction.Axis.Z ? 0.5 + 0.5625 * direction.getStepZ() : level.random.nextFloat();
                level.addParticle(PARTICLE, pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz, 0.0, 0.0, 0.0);
            }
        }
    }
}