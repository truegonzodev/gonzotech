package com.gonzotech.chalkboard.command;

import com.gonzotech.chalkboard.ResonanceClue;
import com.gonzotech.chalkboard.core.Quantity;
import com.gonzotech.chalkboard.network.ChalkboardNetwork;
import com.gonzotech.chalkboard.progress.ModAttachments;
import com.gonzotech.chalkboard.progress.PlayerChalkboardProgress;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Collection;

/**
 * Админ-команды доски резонанса:
 *
 * <ul>
 *   <li>{@code /chalkboard step <player> <1..100>} — поставить игроку номер задачи;</li>
 *   <li>{@code /gonzotech debug clue} — подсказка по доске резонанса (автор 22.09.2026):
 *       подсветить в лотке случайный блок из решения текущей задачи и выдать его
 *       игроку, если он ещё не открыт. Логика живёт в {@link ResonanceClue}: в будущем
 *       так же будут звать револьвер и водка.</li>
 * </ul>
 */
public class ChalkboardCommand {

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("chalkboard")
                        .requires(s -> s.hasPermission(2)) // Admin / Cheats required
                        .then(Commands.literal("step")
                                .then(Commands.argument("target", EntityArgument.players())
                                        .then(Commands.argument("stage", IntegerArgumentType.integer(1, 100))
                                                .executes(ctx -> {
                                                    CommandSourceStack source = ctx.getSource();
                                                    Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "target");
                                                    int stage = IntegerArgumentType.getInteger(ctx, "stage");
                                                    int discoveryIndex = stage - 1; // Convert 1-based stage to 0-based discovery index

                                                    for (ServerPlayer player : players) {
                                                        PlayerChalkboardProgress progress = player.getData(ModAttachments.CHALKBOARD_PROGRESS);
                                                        progress.setCurrentDiscoveryIndex(discoveryIndex);
                                                        player.setData(ModAttachments.CHALKBOARD_PROGRESS, progress);
                                                        ChalkboardNetwork.sendSyncToPlayer(player);
                                                    }

                                                    source.sendSuccess(() -> Component.literal("§a[GonzoTech] Перенос на задачу резонансной доски #" + stage), true);
                                                    return players.size();
                                                })
                                        )
                                )
                        )
        );

        // /gonzotech debug clue — Brigadier сшивает узел debug с ветками psyche и sun.
        dispatcher.register(
                Commands.literal("gonzotech")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.literal("debug")
                                .then(Commands.literal("clue")
                                        .executes(ChalkboardCommand::giveClue))));
    }

    /**
     * Подсказка по доске резонанса: блок из решения текущей задачи подсвечивается в
     * лотке доски (толстая белая обводка) и выдаётся игроку, если ещё не открыт.
     */
    private static int giveClue(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal(
                    "§c[GonzoTech] Подсказка выдаётся игроку — нужен игрок в качестве отправителя."));
            return 0;
        }
        Quantity hinted = ResonanceClue.give(player);
        if (hinted == null) {
            source.sendFailure(Component.literal(
                    "§c[GonzoTech] Подсказывать нечего: у текущей задачи пустое решение."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "§a[GonzoTech] Подсказка: §e" + hinted.symbol() + " §7— " + hinted.nameRu()
                        + " §8(подсвечен в лотке доски)"), false);
        return 1;
    }
}
