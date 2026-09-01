package com.gonzotech.core.ore;

import com.gonzotech.core.network.CesiumBlastRequestPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Цезиевая руда: «закрытая» руда безопасна, но если хотя бы к одной грани
 * прилегает воздух/вода, то каждое взаимодействие (удар или ПКМ) вызывает
 * маленький взрыв: урон/отбрасывание/частицы/звук — как у классического
 * взрыва (чем ближе — тем больнее, за препятствием — меньше), но без
 * разрушения блоков.
 * <p>
 * Реализация: ванильный {@code ServerLevel.explode(...)} с
 * {@code Level.ExplosionInteraction.NONE} — сам считает урон по дистанции
 * с лучами через {@code Explosion.getSeenPercent} (стены экранируют),
 * отбрасывает сущности и проигрывает частицы/звук, а {@code BlockInteraction}
 * остаётся KEEP — блоки не ломаются.
 * <p>
 * Триггеры:
 * <ul>
 *   <li>ПКМ (в т.ч. удержание) — {@code useItemOn}/{@code useWithoutItem}, движок
 *       вызывает каждый тик при зажатой ПКМ;</li>
 *   <li>ЛКМ — {@code attack} (один раз) плюс {@code getDestroyProgress}: этот
 *       хук вызывается КАЖДЫЙ ТИК, пока игрок держит ЛКМ (и при переводе
 *       прицела на руду), поэтому удержание ЛКМ тоже даёт повторные взрывы;
 *       с клиента уходит {@link CesiumBlastRequestPayload}, сервер выполняет
 *       взрыв под собственным КД.</li>
 * </ul>
 * КД — 8 тиков, ОТДЕЛЬНО для каждого блока: хранится последний тик взрыва
 * по позиции блока.
 */
public class CesiumOreBlock extends Block {

    private static final float EXPLOSION_RADIUS = 2.0F;
    private static final int COOLDOWN_TICKS = 8;

    /** pos (immutable) -> последний тик взрыва; per-block КД. */
    private static final Map<BlockPos, Long> LAST_BLAST_TICK = new ConcurrentHashMap<>();

    public CesiumOreBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /** Удар по блоку (начало ломания ЛКМ). */
    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        tryBlast(level, pos);
        super.attack(state, level, pos, player);
    }

    /** ПКМ с предметом — не блокируем размещение и прочие действия. */
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
        tryBlast(level, pos);
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
        tryBlast(level, pos);
        return InteractionResult.PASS;
    }

    /**
     * Хук, вызываемый каждый тик, пока игрок держит ЛКМ и ломает блок
     * (в т.ч. если прицел перевели на этот блок, не отпуская кнопку).
     */
    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (level instanceof Level lvl && lvl.isClientSide) {
            PacketDistributor.sendToServer(new CesiumBlastRequestPayload(pos.immutable()));
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    /** Вызывается с сервера (клиент шлёт запрос). Проверки повторяются сервером. */
    public static void tryBlast(Level level, BlockPos pos) {
        if (level.isClientSide || !(level instanceof ServerLevel server)) {
            return;
        }
        if (!isExposed(level, pos)) {
            return;
        }
        long now = level.getGameTime();
        BlockPos key = pos.immutable();
        Long last = LAST_BLAST_TICK.get(key);
        if (last != null && now - last < COOLDOWN_TICKS) {
            return;
        }
        LAST_BLAST_TICK.put(key, now);

        Vec3 center = Vec3.atCenterOf(pos);
        // NONE: урон/отбрасывание/частицы/звук как у взрыва, разрушение блоков выключено.
        server.explode(null, center.x, center.y, center.z, EXPLOSION_RADIUS, false, Level.ExplosionInteraction.NONE);
    }

    /** true, если хотя бы к одной грани прилегает воздух или вода. */
    private static boolean isExposed(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockState neighbor = level.getBlockState(pos.relative(direction));
            if (neighbor.isAir() || neighbor.getFluidState().is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }
}