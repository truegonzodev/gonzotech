package com.gonzotech.machines.item;

import com.gonzotech.machines.network.CompositePipeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
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

    public WrenchItem(Properties properties) {
        super(properties);
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
