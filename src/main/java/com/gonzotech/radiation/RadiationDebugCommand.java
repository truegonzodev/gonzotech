package com.gonzotech.radiation;

import com.gonzotech.core.psyche.ModPsycheAttachments;
import com.gonzotech.core.psyche.PlayerPsyche;
import com.gonzotech.core.text.GtUnits;
import com.gonzotech.music.MusicDebugNetwork;
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
                                        .executes(RadiationDebugCommand::getDose))
                                .then(Commands.literal("trigger")
                                        .then(Commands.literal("music")
                                                .executes(RadiationDebugCommand::triggerMusic)))));
    }

    private static int triggerMusic(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal(
                    "[GonzoTech] Для проверки музыки нужен игрок в качестве отправителя."));
            return 0;
        }
        // Проверка текущего трека и клиентского cooldown возможна только на клиенте;
        // ответ о шансах/пуле он выведет в чат после фактического броска.
        MusicDebugNetwork.requestDebugRoll(player);
        return 1;
    }

    private static int purge(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("[GonzoTech] Команда требует игрока."));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        ChunkPos center = new ChunkPos(player.blockPosition());
        ChunkRadiationData.get(level).purgeAround(level, center, 8);
        context.getSource().sendSuccess(() -> Component.literal(
                "[GonzoTech] Радиация очищена в радиусе 8 чанков вокруг чанка "
                        + center.x + ", " + center.z + ". Загрязнение чанков установлено в 0 nZt/s; предметы и шкала игрока не очищаются."), true);
        return 1;
    }

    private static int getDose(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("[GonzoTech] Команда требует игрока."));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        RadiationTrace.start(player);
        PlayerPsyche psyche = player.getData(ModPsycheAttachments.PSYCHE);
        double percent = RadDose.percent(psyche.getRadiation());
        ChunkPos chunk = new ChunkPos(player.blockPosition());
        ChunkRadiationData data = ChunkRadiationData.get(level);
        data.tracePlacedSources(level, chunk, RadiationTrace.CHUNK_RADIUS);
        double chunkNzt = data.value(level, chunk.toLong());
        double insideDose = Containment.insideDose(level,
                net.minecraft.core.BlockPos.containing(player.getEyePosition()), player);
        RadiationTrace.log(level, player.blockPosition(), "ИСХОДНЫЙ СНИМОК GETDOSE",
                "radiationScale=" + psyche.getRadiation() + "; percent="
                        + String.format(Locale.ROOT, "%.1f%%", percent)
                        + "; chunk=" + chunk.x + "," + chunk.z
                        + "; chunkDose=" + RadiationTrace.rate(chunkNzt)
                        + "; insideSources=" + RadiationTrace.rate(insideDose));

        player.sendSystemMessage(Component.literal("Доза: ")
                .append(Component.literal(String.format(Locale.ROOT, "%.1f%%", percent)))
                .append(Component.literal(" (" + category(percent) + ")"))
                .withStyle(ChatFormatting.GRAY));
        // 0.3.303: ручной «/s» снят — суффикс несёт GtUnits.zt (единый канал).
        player.sendSystemMessage(Component.literal("Чанк [" + chunk.x + ", " + chunk.z + "]: ")
                .append(GtUnits.zt(chunkNzt))
                .withStyle(ChatFormatting.GRAY));

        var inventory = com.gonzotech.radiation.engine.RadiationCluster.inspect(RadiationSystem.playerSlots(player));
        player.sendSystemMessage(Component.literal("Предметный кластер: " + inventory.slots() + " занятых слотов, "
                + inventory.count() + " предметов; суммарно ").append(GtUnits.zt(inventory.total())));
        player.sendSystemMessage(Component.literal("Сильнейший предмет: ").append(GtUnits.zt(inventory.strongestItem()))
                .append(Component.literal("; цель чанка: ")).append(GtUnits.zt(inventory.chunkTarget())));
        RadiationTrace.log(level, player.blockPosition(), "GETDOSE: ВНЕШНИЙ ДВИЖОК",
                "presetSum=" + RadiationTrace.rate(inventory.preset()) + "; inducedSum=" + RadiationTrace.rate(inventory.induced())
                + "; strongestInduced/item=" + RadiationTrace.rate(inventory.strongestInduced())
                + "; chunkTarget=" + RadiationTrace.rate(inventory.chunkTarget()));

        var observer = net.minecraft.core.BlockPos.containing(player.getEyePosition());
        var contour = Containment.result(level, observer);
        player.sendSystemMessage(Component.translatable("command.gonzotech.radiation.contour",
                Component.translatable(contour.enclosed() ? "gui.yes" : "gui.no"),
                String.format(Locale.ROOT, "%.6f", contour.factor())).withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.translatable("command.gonzotech.radiation.inside_dose",
                GtUnits.zt(insideDose)).withStyle(ChatFormatting.GRAY));

        var sources = data.sourcesInChunk(level, chunk.toLong());
        if (sources.isEmpty()) {
            player.sendSystemMessage(Component.literal("Источники в чанке: не обнаружены")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            for (var source : sources) {
                player.sendSystemMessage(Component.literal(
                        "Источник радиации в чанке " + chunk.x + "," + chunk.z
                                + " точке " + source.pos().getX() + " " + source.pos().getY() + " "
                                + source.pos().getZ() + " — ")
                        .append(GtUnits.zt(source.emission()))
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
