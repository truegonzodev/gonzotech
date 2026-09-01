package com.gonzotech.chalkboard;

import com.gonzotech.chalkboard.client.ChalkboardClientHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Блок доски резонанса — ПКМ (пустой рукой или с любым предметом, не
 * перехватывающим клик на себя) открывает {@link ChalkboardClientHandler}.
 *
 * Фаза 1 (см. info/gonzo_tech_chalkboard_design.md): экран открывается «как
 * есть» — случайная решаемая загадка от портированного движка, состояние
 * эфемерно. Не Menu/Container — открытие чисто клиентское, поэтому этот
 * класс НЕ импортирует Minecraft/Screen напрямую (они бы потащились на
 * дедик-сервер) — вся клиентская часть изолирована в
 * {@link ChalkboardClientHandler}, вызывается только под isClientSide().
 *
 * Фаза 2-3 добавят сюда MenuProvider (сид-лок задачи + прогресс игрока +
 * серверную валидацию решения) — тогда просто открытие экрана здесь
 * заменится на player.openMenu(...).
 */
public class ChalkboardBlock extends Block {

    public ChalkboardBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        BlockHitResult hitResult
    ) {
        if (level.isClientSide()) {
            ChalkboardClientHandler.openScreen();
        }
        return InteractionResult.SUCCESS;
    }
}
