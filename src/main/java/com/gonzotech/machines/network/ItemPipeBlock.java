package com.gonzotech.machines.network;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Предметная труба ({@link PipeType#ITEM}) — как обычная {@link PipeBlock}, но
 * АКТИВНАЯ: сама тянет предметы из прилегающих контейнеров и мгновенно
 * раскидывает по сети ({@link ItemRouting}). Остаётся БЕЗ {@code BlockEntity} —
 * работу выполняет запланированный тик блока ({@link #tick}), который сам себя
 * перепланирует, пока труба существует.
 * <p>
 * Так предметная труба сохраняет всё поведение обычной трубы (бандл в пучок,
 * геометрия угла ITEM, HUD ключа, дроп только самой трубы), но добавляет
 * забор/раздачу предметов без хранения — единый принцип с жидкостями.
 */
public class ItemPipeBlock extends PipeBlock {

    /**
     * Период забора (тиков). {@code 1} = каждый тик, поэтому лимит
     * {@link PipeType#maxThroughput()} = 5 читается как «5 предметов/тик».
     * Трубы без прилегающего контейнера отрабатывают тик почти мгновенно
     * (6 дешёвых проверок), тяжёлый BFS запускается только при наличии источника.
     */
    public static final int TICK_INTERVAL = 1;

    public ItemPipeBlock(Properties properties) {
        super(properties, PipeType.ITEM);
    }

    @Override
    protected MapCodec<? extends PipeBlock> makeCodec() {
        return simpleCodec(ItemPipeBlock::new);
    }

    // ─────────────────────────── запланированный тик ───────────────────────────

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide()) {
            level.scheduleTick(pos, this, TICK_INTERVAL);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Один проход забора предметов из прилегающих контейнеров.
        ItemRouting.tickExtract(level, pos, state);
        // Перепланируем себя, пока труба на месте.
        level.scheduleTick(pos, this, TICK_INTERVAL);
    }
}
