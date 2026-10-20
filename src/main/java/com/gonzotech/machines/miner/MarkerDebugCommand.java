package com.gonzotech.machines.miner;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * /gonzotech debug marker (0.3.256, автор): смотрит колонну блоков 1×1 под
 * игроком вниз до самого низа мира, собирает руды и создаёт тестовую метку,
 * которая исчезает сама через 20 секунд.
 */
public final class MarkerDebugCommand {

    /** Тестовая метка живёт 20 секунд (автор). */
    public static final int TEST_MARKER_TTL_TICKS = 20 * 20;

    private MarkerDebugCommand() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
            Commands.literal("gonzotech")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("debug")
                    .then(Commands.literal("marker")
                        .executes(MarkerDebugCommand::spawnTestMarker))));
    }

    private static int spawnTestMarker(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("[GonzoTech] Команда требует игрока."));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        BlockPos feet = player.blockPosition();

        // Колонна 1×1 под игроком вниз до самого низа мира.
        Set<Item> found = new LinkedHashSet<>();
        int minY = level.getMinY();
        for (int y = feet.getY() - 1; y >= minY; y--) {
            BlockState state = level.getBlockState(new BlockPos(feet.getX(), y, feet.getZ()));
            if (!state.isAir() && MinerOres.isOre(state)) {
                found.add(MinerOres.displayItem(state.getBlock()));
            }
        }

        // Метка «втыкается» в блок поверхности колонны игрока.
        BlockPos surface = level.getHeightmapPos(
            net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, feet);
        BlockPos plantAt = surface.below();

        List<Item> ores = new ArrayList<>(found);
        MarkerService.get(level).add(level, plantAt, MinerMarker.Kind.TEST,
            TEST_MARKER_TTL_TICKS, ores);
        source.sendSuccess(() -> Component.literal(
            "[GonzoTech] Тестовая метка: руд в колонне " + ores.size()
                + (ores.isEmpty() ? " (флажок)" : "") + ", живёт 20 секунд."), true);
        return 1;
    }
}
