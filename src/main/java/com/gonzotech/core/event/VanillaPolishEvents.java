package com.gonzotech.core.event;

import com.gonzotech.core.world.CalciteWatchData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * «Улучшение ванили» (пакет 0.3.254, автор) — модовые механики, выглядящие как
 * честное улучшение классики Minecraft:
 *
 * <ul>
 *   <li><b>Генератор туфа.</b> Туф в ванили невозобновляем. Теперь лава,
 *       соприкасающаяся с ВСПЕНЕННОЙ водой (пузырьковый столб над песком душ —
 *       те самые подбрасывающие пузырьки), даёт туф вместо булыжника/камня.
 *       Обычная вода — всё как ванили.</li>
 *   <li><b>Кальцит из костного блока.</b> Костный блок, контактирующий с водой
 *       {@value #CALCITE_DELAY_SECONDS} секунд, превращается в кальцит. Тот же
 *       результат добывается в наполнителе (вода + GTH) и на химзаводе.</li>
 * </ul>
 */
public final class VanillaPolishEvents {

    /** 240 секунд контакта костного блока с водой (автор). */
    public static final int CALCITE_DELAY_SECONDS = 240;
    public static final int CALCITE_DELAY_TICKS = CALCITE_DELAY_SECONDS * 20;

    private VanillaPolishEvents() {
    }

    // ─────────────────────────── Генератор туфа ───────────────────────────

    /**
     * Лава + вода → булыжник/камень. Если контакт с вспененной водой
     * (bubble column или вода над песком душ) — результат меняется на туф.
     */
    @SubscribeEvent
    public static void onFluidPlaceBlock(BlockEvent.FluidPlaceBlockEvent event) {
        BlockState placed = event.getNewState();
        if (!placed.is(Blocks.COBBLESTONE) && !placed.is(Blocks.STONE)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        BlockPos pos = event.getPos();
        if (isFoamWater(level, pos, event.getOriginalState())) {
            event.setNewState(Blocks.TUFF.defaultBlockState());
            return;
        }
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (isFoamWater(level, neighbor, level.getBlockState(neighbor))) {
                event.setNewState(Blocks.TUFF.defaultBlockState());
                return;
            }
        }
    }

    /**
     * Вспененная вода: пузырьковый столб (как над песком душ) или вода,
     * над которой только что поднялся такой столб (над самим песком душ).
     */
    private static boolean isFoamWater(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.is(Blocks.BUBBLE_COLUMN)) return true;
        return state.getFluidState().is(FluidTags.WATER)
            && level.getBlockState(pos.below()).is(Blocks.SOUL_SAND);
    }

    // ─────────────────────── Кальцит из костного блока ───────────────────────

    /** Вода коснулась соседа — возможно, рядом костный блок: ставим наблюдение. */
    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        registerBoneWatch(level, pos);
        for (Direction direction : Direction.values()) {
            registerBoneWatch(level, pos.relative(direction));
        }
    }

    /** Костный блок поставлен в воду — наблюдение с первого касания. */
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        registerBoneWatch(level, event.getPos());
    }

    /** Наблюдаемые костные блоки отсчитывают 240 секунд контакта. */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        CalciteWatchData.get(level).advance(level);
    }

    private static void registerBoneWatch(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).is(Blocks.BONE_BLOCK)) return;
        if (!hasWaterNeighbor(level, pos)) return;
        CalciteWatchData.get(level).watch(pos, level.getGameTime() + CALCITE_DELAY_TICKS);
    }

    /** Публично: {@link CalciteWatchData} перепроверяет контакт при срабатывании. */
    public static boolean hasWaterNeighbor(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockState state = level.getBlockState(pos.relative(direction));
            if (state.is(Blocks.BUBBLE_COLUMN) || state.getFluidState().is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }
}
