package com.gonzotech.radiation;

import com.gonzotech.core.psyche.ModPsycheAttachments;
import com.gonzotech.core.psyche.PlayerPsyche;
import com.gonzotech.core.text.GtUnits;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Locale;

/** Small, server-side diagnostics for radiation calibration and desync checks. */
public final class RadiationDebugCommand {
    private RadiationDebugCommand() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("gonzotech")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("debug")
                                .then(Commands.literal("purge")
                                        .executes(RadiationDebugCommand::purge))
                                .then(Commands.literal("getdose")
                                        .executes(RadiationDebugCommand::getDose))));
    }

    private static int purge(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("[GonzoTech] Команда требует игрока."));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        ChunkPos center = new ChunkPos(player.blockPosition());
        ChunkRadiationData.get(level).purgeAround(center, 8);
        context.getSource().sendSuccess(() -> Component.literal(
                "[GonzoTech] Радиация очищена в радиусе 8 чанков вокруг чанка "
                        + center.x + ", " + center.z + ". Все значения установлены в 0 nZt/s."), true);
        return 1;
    }

    private static int getDose(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("[GonzoTech] Команда требует игрока."));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        PlayerPsyche psyche = player.getData(ModPsycheAttachments.PSYCHE);
        double percent = RadDose.percent(psyche.getRadiation());
        ChunkPos chunk = new ChunkPos(player.blockPosition());
        ChunkRadiationData data = ChunkRadiationData.get(level);
        double chunkNzt = data.value(level, chunk.toLong());

        player.sendSystemMessage(Component.literal("Доза: ")
                .append(Component.literal(String.format(Locale.ROOT, "%.1f%%", percent)))
                .append(Component.literal(" (" + category(percent) + ")"))
                .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("Чанк [" + chunk.x + ", " + chunk.z + "]: ")
                .append(Component.literal(GtUnits.zt(chunkNzt)))
                .append(Component.literal("/s"))
                .withStyle(ChatFormatting.GRAY));

        var sources = data.sourcesInChunk(level, chunk.toLong());
        if (sources.isEmpty()) {
            player.sendSystemMessage(Component.literal("Источники в чанке: не обнаружены")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            for (var source : sources) {
                player.sendSystemMessage(Component.literal(
                        "Источник радиации в чанке " + chunk.x + "," + chunk.z
                                + " точке " + source.pos().getX() + " " + source.pos().getY() + " "
                                + source.pos().getZ() + " — " + GtUnits.zt(source.emission()) + "/s")
                        .withStyle(ChatFormatting.YELLOW));
            }
        }
        return 1;
    }

    private static String category(double percent) {
        return switch (RadDose.category((int) Math.round(percent * 10.0))) {
            case FINE -> "норма";
            case ELEVATED -> "повышенная";
            case DANGEROUS -> "опасная";
            case CRITICAL -> "критическая";
            case LETHAL -> "летальная";
        };
    }
}
