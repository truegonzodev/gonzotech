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
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Отладочные команды мода GonzoTech:
 * <ul>
 *   <li>{@code /gonzotech tp <dimension>} — телепорт между измерениями.</li>
 *   <li>{@code /gonzotech debug sun default|dyson|gone|blackhole|blackhole_dyson} — смена состояния Солнца.</li>
 *   <li>{@code /gonzotech debug alpha_centauri default|dyson} — сфера Дайсона для Альфы Центавра.</li>
 *   <li>{@code /gonzotech debug yx989 default|dyson} (алиасы y989, yx989_k2) — кольцо Дайсона Yx989-k2.</li>
 *   <li>{@code /gonzotech debug zangler default|dyson} (алиас zangler_11) — кольцо Дайсона Zangler-11.</li>
 * </ul>
 */
public final class SpaceCommand {

    private SpaceCommand() {
    }

    /** overworld/nether/end — чтобы можно было вернуться домой этой же командой. */
    private static final Map<String, ResourceKey<Level>> VANILLA = Map.of(
        "overworld", Level.OVERWORLD,
        "nether", Level.NETHER,
        "end", Level.END);

    /** 5 измерений Солнечной системы, зависящих от состояния Солнца. */
    private static final List<ResourceKey<Level>> SOLAR_DIMENSIONS = List.of(
        Level.OVERWORLD,
        SpaceDimensions.SOLAR_ORBIT,
        SpaceDimensions.MOON,
        SpaceDimensions.MARS,
        SpaceDimensions.EUROPA
    );

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

        // /gonzotech debug sun <state>
        LiteralArgumentBuilder<CommandSourceStack> sunDebug = Commands.literal("sun")
            .then(Commands.literal("default").executes(c -> setSun(c, SunState.DEFAULT)))
            .then(Commands.literal("dyson").executes(c -> setSun(c, SunState.DYSON)))
            .then(Commands.literal("gone").executes(c -> setSun(c, SunState.GONE)))
            .then(Commands.literal("blackhole").executes(c -> setSun(c, SunState.BLACKHOLE)))
            .then(Commands.literal("blackhole_dyson").executes(c -> setSun(c, SunState.BLACKHOLE_DYSON)))
            .then(Commands.literal("blackhole-dyson").executes(c -> setSun(c, SunState.BLACKHOLE_DYSON)));

        // /gonzotech debug alpha_centauri default|dyson
        LiteralArgumentBuilder<CommandSourceStack> alphaDebug = Commands.literal("alpha_centauri")
            .then(Commands.literal("default").executes(c -> setStar(c, "alpha_centauri", false, "Альфа Центавра")))
            .then(Commands.literal("dyson").executes(c -> setStar(c, "alpha_centauri", true, "Альфа Центавра")));

        LiteralArgumentBuilder<CommandSourceStack> alphaHyphenDebug = Commands.literal("alpha-centauri")
            .then(Commands.literal("default").executes(c -> setStar(c, "alpha_centauri", false, "Альфа Центавра")))
            .then(Commands.literal("dyson").executes(c -> setStar(c, "alpha_centauri", true, "Альфа Центавра")));

        // /gonzotech debug y989 / yx989 / yx989_k2 default|dyson
        LiteralArgumentBuilder<CommandSourceStack> y989Debug = Commands.literal("y989")
            .then(Commands.literal("default").executes(c -> setStar(c, "yx989", false, "Чёрная Дыра Yx989-k2")))
            .then(Commands.literal("dyson").executes(c -> setStar(c, "yx989", true, "Чёрная Дыра Yx989-k2")));

        LiteralArgumentBuilder<CommandSourceStack> yx989Debug = Commands.literal("yx989")
            .then(Commands.literal("default").executes(c -> setStar(c, "yx989", false, "Чёрная Дыра Yx989-k2")))
            .then(Commands.literal("dyson").executes(c -> setStar(c, "yx989", true, "Чёрная Дыра Yx989-k2")));

        LiteralArgumentBuilder<CommandSourceStack> yx989K2Debug = Commands.literal("yx989_k2")
            .then(Commands.literal("default").executes(c -> setStar(c, "yx989", false, "Чёрная Дыра Yx989-k2")))
            .then(Commands.literal("dyson").executes(c -> setStar(c, "yx989", true, "Чёрная Дыра Yx989-k2")));

        // /gonzotech debug zangler / zangler_11 default|dyson
        LiteralArgumentBuilder<CommandSourceStack> zanglerDebug = Commands.literal("zangler")
            .then(Commands.literal("default").executes(c -> setStar(c, "zangler", false, "Чёрная Дыра Zangler-11")))
            .then(Commands.literal("dyson").executes(c -> setStar(c, "zangler", true, "Чёрная Дыра Zangler-11")));

        LiteralArgumentBuilder<CommandSourceStack> zangler11Debug = Commands.literal("zangler_11")
            .then(Commands.literal("default").executes(c -> setStar(c, "zangler", false, "Чёрная Дыра Zangler-11")))
            .then(Commands.literal("dyson").executes(c -> setStar(c, "zangler", true, "Чёрная Дыра Zangler-11")));

        LiteralArgumentBuilder<CommandSourceStack> debug = Commands.literal("debug")
            .then(sunDebug)
            .then(alphaDebug)
            .then(alphaHyphenDebug)
            .then(y989Debug)
            .then(yx989Debug)
            .then(yx989K2Debug)
            .then(zanglerDebug)
            .then(zangler11Debug);

        dispatcher.register(
            Commands.literal("gonzotech")
                .requires(s -> s.hasPermission(2))
                .then(tp)
                .then(debug));
    }

    /** Установить состояние Солнца для всех игроков сервера. */
    private static int setSun(CommandContext<CommandSourceStack> ctx, SunState state) {
        CommandSourceStack source = ctx.getSource();
        var server = source.getServer();
        if (server == null) {
            return 0;
        }

        SpaceSkyNetwork.sendSunStateToAll(server, state);

        if (state == SunState.GONE) {
            // При исчезновении солнца — вечная тьма: time set 18000 + doDaylightCycle false
            for (ResourceKey<Level> dimKey : SOLAR_DIMENSIONS) {
                ServerLevel lvl = server.getLevel(dimKey);
                if (lvl != null) {
                    lvl.setDayTime(18000L);
                    lvl.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                }
            }
        } else {
            // При возвращении солнца/ЧД — возобновление цикла: doDaylightCycle true + time set 6000
            for (ResourceKey<Level> dimKey : SOLAR_DIMENSIONS) {
                ServerLevel lvl = server.getLevel(dimKey);
                if (lvl != null) {
                    lvl.setDayTime(6000L);
                    lvl.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true, server);
                }
            }
        }

        String label = switch (state) {
            case DEFAULT -> "Обычное (sun.png)";
            case DYSON -> "Сфера Дайсона (sun_dyson.png)";
            case GONE -> "Взорвалось / Погасло (sun_gone.png + вечная ночь)";
            case BLACKHOLE -> "Чёрная Дыра (sun_blackhole.png)";
            case BLACKHOLE_DYSON -> "Чёрная Дыра со Сферой Дайсона (sun_blackhole_dyson.png)";
        };
        source.sendSuccess(() -> Component.literal(
            "§a[GonzoTech] Состояние Солнца: §e" + label), true);
        return 1;
    }

    /** Переключить сферу/кольцо Дайсона для звезды или чёрной дыры. */
    private static int setStar(CommandContext<CommandSourceStack> ctx, String target, boolean dyson, String displayName) {
        CommandSourceStack source = ctx.getSource();
        if (source.getServer() == null) {
            return 0;
        }
        SpaceSkyNetwork.sendStarModeToAll(source.getServer(), target, dyson);
        String stateName = dyson ? "Кольцо/Сфера Дайсона (АКТИВНО)" : "Обычное состояние (ОТКЛЮЧЕНО)";
        source.sendSuccess(() -> Component.literal(
            "§a[GonzoTech] " + displayName + ": §e" + stateName), true);
        return 1;
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
            String normalized = name.toLowerCase(java.util.Locale.ROOT).replace('-', '_');
            key = SpaceDimensions.DIMENSIONS.get(normalized);
        }
        if (key == null) {
            key = VANILLA.get(name.toLowerCase(java.util.Locale.ROOT));
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

        int x = 0;
        int z = 0;
        level.getChunk(x >> 4, z >> 4);
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        int y = Math.max(surface + 1, level.getMinY() + 2);

        if (surface <= level.getMinY()) {
            y = 128;
            net.minecraft.world.level.block.state.BlockState platform =
                com.gonzotech.core.registry.ModBlocks.METEOR.get().defaultBlockState();
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    p.set(x + dx, y - 1, z + dz);
                    if (level.getBlockState(p).isAir()) {
                        level.setBlock(p, platform, 3);
                    }
                }
            }
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
