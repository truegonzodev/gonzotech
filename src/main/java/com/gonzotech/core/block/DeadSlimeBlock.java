package com.gonzotech.core.block;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Optional;

/**
 * Мёртвый слизневый блок дезоляции (автор, 2026-09-19): гибрид поведений —
 * <b>зыбкость</b> рыхлого снега и <b>пружинность</b> блока слизи одновременно:
 * <ul>
 *   <li>твёрдого коллизионного объёма нет ({@link Shapes#empty()}) — сущность
 *       проваливается ВНУТРЬ блока, как в рыхлый снег;</li>
 *   <li>внутри (каждый тик {@code entityInside}): горизонтальное движение
 *       гасится ×0.4 (ползёшь как в топи), падение замедляется до ≈−0.1 б/тик —
 *       тонешь медленно; вертикаль подпрыгнуть позволяет слабо;</li>
 *   <li>влёт снизу быстро (|dy| выше порога): скорость инвертируется вверх —
 *       «отскок» как у слизи, урон падения обнуляется. Быстрый нырок отскакивает,
 *       шествие по поверхности — засасывает и даёт проползти дальше;</li>
 *   <li>набирается пустым ведром, как рыхлый снег ({@link BucketPickup}) →
 *       {@code dead_slime_bucket}; обратно ставится тем же ведром
 *       ({@code SolidBucketItem}).</li>
 * </ul>
 */
public class DeadSlimeBlock extends SlimeBlock implements BucketPickup {

    /** Минимальная скорость падения, при которой срабатывает отскок. */
    private static final double BOUNCE_THRESHOLD = -0.35D;
    /** Коэффициент отскока (как у ванильного слайма ~0.8). */
    private static final double BOUNCE_FACTOR = 0.8D;
    /** Максимальная скорость утону́тия внутри блока. */
    private static final double MAX_SINK_SPEED = -0.10D;

    public DeadSlimeBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // Мёртвая жижа засасывает всех — ботинки из кожи не помогают.
        return Shapes.empty();
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        Vec3 dm = entity.getDeltaMovement();
        // Зыбкость: горизонталь ×0.4, тонение не быстрее порога.
        double dx = dm.x * 0.4D;
        double dz = dm.z * 0.4D;
        double dy = dm.y;
        if (dy < MAX_SINK_SPEED && dy > BOUNCE_THRESHOLD) {
            dy = MAX_SINK_SPEED;
        } else if (dy <= BOUNCE_THRESHOLD) {
            // Пружинность: быстрый влёт отражается вверх, урон падения съедается жижей.
            dy = -dy * BOUNCE_FACTOR;
            entity.resetFallDistance();
            if (entity instanceof LivingEntity living && level instanceof net.minecraft.server.level.ServerLevel) {
                level.playSound(null, pos, SoundEvents.SLIME_BLOCK_HIT,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 0.8F);
            }
        }
        entity.setDeltaMovement(dx, dy, dz);
        entity.resetFallDistance();
    }

    // ─────────────────────────── ведро ───────────────────────────

    @Override
    public ItemStack pickupBlock(net.minecraft.world.entity.player.Player player,
                                 LevelAccessor level, BlockPos pos, BlockState state) {
        // Как ванильный рыхлый снег: стереть блок и кинуть частицы разрушения.
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 11);
        level.levelEvent(player, 2001, pos, Block.getId(state)); // 2001 = частицы разрушения блока
        return new ItemStack(com.gonzotech.core.registry.ModItems.DEAD_SLIME_BUCKET.get());
    }

    @Override
    public Optional<SoundEvent> getPickupSound() {
        return Optional.of(SoundEvents.BUCKET_FILL_POWDER_SNOW);
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return false;
    }
}
