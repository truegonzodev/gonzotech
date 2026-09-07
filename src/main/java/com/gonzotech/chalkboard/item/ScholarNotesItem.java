package com.gonzotech.chalkboard.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * «Заметки учёного» — предмет-руководство (аналог лексикона Botania). ПКМ
 * открывает GUI-буклет со страницами и вкладками-главами. Не расходуется.
 *
 * <p>Открытие экрана делает клиент; данные прогресса (для разблокировки глав)
 * приходят тем же sync-пакетом, что и у доски резонанса
 * ({@code ChalkboardNetwork.SyncRequestPayload} → {@code SyncDataPayload}),
 * который кэшируется в {@code ChalkboardNetwork.CLIENT_DATA}.
 */
public class ScholarNotesItem extends Item {

    public ScholarNotesItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            // Клиентский вход в отдельном классе, чтобы не тянуть client-only типы на сервер.
            com.gonzotech.chalkboard.client.ScholarNotesClientHandler.openScreen();
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
        tooltip.add(net.minecraft.network.chat.Component.literal(
                "\u00a77" + "Используйте (ПКМ), чтобы открыть руководство."));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
