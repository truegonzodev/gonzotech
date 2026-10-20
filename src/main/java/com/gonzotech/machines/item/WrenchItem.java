package com.gonzotech.machines.item;

import com.gonzotech.chalkboard.progress.ModAttachments;
import com.gonzotech.chalkboard.progress.PlayerChalkboardProgress;
import com.gonzotech.chalkboard.notes.ScholarNoteFlags;
import com.gonzotech.chalkboard.advancement.ModAdvancements;
import com.gonzotech.machines.network.CompositePipeBlock;
import com.gonzotech.machines.network.PipeFlowNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Гаечный ключ Gonzo Tech. Инструмент-модификатор труб/машин.
 * <ul>
 *   <li><b>ПКМ</b> по трубе — прокрутка режима (AUTO → PULL → PUSH). Эта логика
 *       живёт в {@code PipeBlock#useItemOn} / {@code CompositePipeBlock#useItemOn},
 *       потому что при обычном (не сидя) ПКМ ванилла зовёт блок.</li>
 *   <li><b>Присесть + ПКМ</b> по пучку труб — поворот СЛОЯ наведённой трубы
 *       (X↔Z). Эта логика ОБЯЗАНА жить здесь ({@link #useOn}): когда игрок сидит
 *       и держит предмет, Minecraft НЕ вызывает {@code BlockState#useItemOn} у
 *       блока, а сразу отдаёт клик предмету ({@code Item#useOn}). Реагируем на
 *       сам факт «игрок сидит», а не на конкретную клавишу.</li>
 * </ul>
 */
public class WrenchItem extends Item {

    /** Съесть ключ: три минуты (3600 тиков) при зажатом ПКМ «в никуда». */
    public static final int USE_TICKS = 3600;

    /**
     * Мастерство ключа (0.3.253, автор): после съедения ключа в сейве навсегда
     * доступны функции ключа без него в руке — потоки труб, режимы и поворот
     * пучков. Сервер проверяет флаг заметки учёного, клиент — синкаемый статус.
     */
    private static boolean clientMastery;

    /** Синк пейлоадом {@link PipeFlowNetwork.WrenchMasteryPayload} при входе и поедании. */
    public static void setClientMastery(boolean mastered) {
        clientMastery = mastered;
    }

    /** Серверная проверка — по флагу заметки в прогрессе chalkboard. */
    public static boolean hasMastery(Player player) {
        if (player == null) return clientMastery;
        if (player.level().isClientSide()) return clientMastery;
        PlayerChalkboardProgress progress = player.getData(ModAttachments.CHALKBOARD_PROGRESS.get());
        return progress.hasNoteFlag(ScholarNoteFlags.WRENCH_EATEN);
    }

    /** Ключ в руке ИЛИ съеденный однажды ключ (0.3.253). */
    public static boolean isActive(Player player) {
        return hasMastery(player)
            || player.getMainHandItem().getItem() instanceof WrenchItem
            || player.getOffhandItem().getItem() instanceof WrenchItem;
    }

    public WrenchItem(Properties properties) {
        super(properties);
    }

    /** ПКМ «в никуда» — начать трапезу. С мастерством есть больше нечего. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (hasMastery(player)) {
            return InteractionResult.PASS;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_TICKS;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.EAT;
    }

    /**
     * Конец трапезы: ключ съедается, навсегда включается «мастерство ключа» и
     * выдаётся ачивмент «Собаку съел в автоматике...».
     */
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        if (!(level instanceof ServerLevel serverLevel) || !(livingEntity instanceof Player player)) {
            return stack;
        }
        PlayerChalkboardProgress progress = player.getData(ModAttachments.CHALKBOARD_PROGRESS.get());
        progress.unlockNoteFlag(ScholarNoteFlags.WRENCH_EATEN);
        if (player instanceof ServerPlayer serverPlayer) {
            ModAdvancements.awardWrenchEaten(serverPlayer);
            PipeFlowNetwork.sendWrenchMastery(serverPlayer);
        }
        serverLevel.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EAT.value(),
            SoundSource.PLAYERS, 1.0F, 0.8F);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return stack;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        // Только «присесть + ПКМ»: обычный ПКМ обрабатывает сам блок в useItemOn.
        if (context.getPlayer() == null || !context.getPlayer().isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof CompositePipeBlock) {
            boolean handled = CompositePipeBlock.rotateLayerAt(
                level, pos, state, context.getClickLocation());
            if (handled) {
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }
}
