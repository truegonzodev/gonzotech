package com.gonzotech.space;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Map;
import java.util.Set;

/**
 * Отладочный телепорт между космическими измерениями:
 * {@code /gonzotech tp <dimension>}.
 *
 * <p>Список {@code <dimension>} автодополняется из
 * {@link SpaceDimensions#DIMENSIONS} (плюс ванильные overworld/nether/end для
 * возврата домой). Телепорт использует ванильный кросс-мировой путь 1.21.4
 * {@code Entity.teleportTo(ServerLevel, x,y,z, relatives, yRot, xRot)} — тот же,
 * что и команда {@code /tp} — без порталов и эффектов смены измерения.
 */
public final class SpaceCommand {

    private SpaceCommand() {
    }

    /** overworld/nether/end — чтобы можно было вернуться домой этой же командой. */
    private static final Map<String, ResourceKey<Level>> VANILLA = Map.of(
        "overworld", Level.OVERWORLD,
        "nether", Level.NETHER,
        "end", Level.END);

    private static final SuggestionProvider<CommandSourceStack> DIMENSION_SUGGESTIONS =
        (ctx, builder) -> SharedSuggestionProvider.suggest(
            java.util.stream.Stream.concat(
                SpaceDimensions.DIMENSIONS.keySet().stream(),
                VANILLA.keySet().stream()),
            builder);

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        LiteralArgumentBuilder<CommandSourceStack> tp = Commands.literal("tp")
            .then(Commands.argument("dimension", StringArgumentType.string())
                .suggests(DIMENSION_SUGGESTIONS)
                .executes(SpaceCommand::teleport));

        dispatcher.register(
            Commands.literal("gonzotech")
                .requires(s -> s.hasPermission(2))
                .then(tp));
    }

    private static int teleport(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "dimension");

        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            source.sendFailure(Component.literal("§c[GonzoTech] Команду tp может выполнить только игрок."));
            return 0;
        }

        ResourceKey<Level> key = SpaceDimensions.DIMENSIONS.get(name);
        if (key == null) {
            key = VANILLA.get(name);
        }
        if (key == null) {
            source.sendFailure(Component.literal("§c[GonzoTech] Неизвестное измерение: " + name));
            return 0;
        }

        ServerLevel level = source.getServer().getLevel(key);
        if (level == null) {
            source.sendFailure(Component.literal(
                "§c[GonzoTech] Измерение не загружено: " + name
                + " (проверь data/gonzotech/dimension/" + name + ".json)"));
            return 0;
        }

        // Точка появления над (0,0). Форс-грузим чанк и берём высоту рельефа,
        // чтобы не воткнуть игрока в блок и не уронить в пустоту.
        int x = 0;
        int z = 0;
        level.getChunk(x >> 4, z >> 4); // синхронная загрузка/генерация чанка
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        int y = Math.max(surface + 1, level.getMinY() + 2);
        // Если рельефа нет (пустое измерение) — ставим на безопасную высоту.
        if (surface <= level.getMinY()) {
            y = 128;
        }

        double px = x + 0.5D;
        double py = y;
        double pz = z + 0.5D;

        player.teleportTo(level, px, py, pz, Set.<Relative>of(), player.getYRot(), player.getXRot(), true);

        final String fname = name;
        final BlockPos landed = BlockPos.containing(px, py, pz);
        source.sendSuccess(() -> Component.literal(
            "§a[GonzoTech] Переход в измерение §e" + fname + "§a → "
            + landed.getX() + " " + landed.getY() + " " + landed.getZ()), true);
        return 1;
    }
}
