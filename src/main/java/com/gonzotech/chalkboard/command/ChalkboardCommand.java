package com.gonzotech.chalkboard.command;

import com.gonzotech.chalkboard.network.ChalkboardNetwork;
import com.gonzotech.chalkboard.progress.ModAttachments;
import com.gonzotech.chalkboard.progress.PlayerChalkboardProgress;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Collection;

/**
 * Admin cheat command to set player chalkboard progression stage.
 * Usage: /chalkboard step <player> <1..100>
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
    }
}
