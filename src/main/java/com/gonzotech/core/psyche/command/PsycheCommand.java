package com.gonzotech.core.psyche.command;

import com.gonzotech.core.psyche.ModPsycheAttachments;
import com.gonzotech.core.psyche.PlayerPsyche;
import com.gonzotech.core.psyche.PsycheNetwork;
import com.gonzotech.core.psyche.PsycheStressEffects;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;
import java.util.Locale;

/**
 * Админ-команды психики (автор 22.09.2026):
 *
 * <ul>
 *   <li>{@code /gonzotech debug psyche add <шкала> <проценты>} — прибавить к шкале;</li>
 *   <li>{@code /gonzotech debug psyche remove <шкала> <проценты>} — отнять;</li>
 *   <li>{@code /gonzotech debug psyche set <шкала> <проценты>} — поставить ровно;</li>
 *   <li>{@code /gonzotech debug psyche trigger <событие>} — вручную запустить событие шкалы
 *       (тремор, галлюцинация, сердечный приступ, каскад, ложный экран смерти…). Именно
 *       события шкал, а не эффекты зелий.</li>
 * </ul>
 *
 * <p><b>Единицы.</b> Везде в команде — <b>проценты</b> (0…100), как в HUD. Внутри зависимость,
 * стресс и кризис живут в очках ({@code 1 000 000 = 100 %}), а облучение/УФ/химия — в тысячных
 * ({@code 1000 = 100 %}); перевод делает команда, поэтому игроку не нужно помнить про очки.
 *
 * <p>Действует на того, кто ввёл команду (из консоли — ошибка с подсказкой).
 */
public final class PsycheCommand {

    private PsycheCommand() {
    }

    /** Шкалы «в очках» (0…1 000 000 = 100 %). */
    private static final List<String> POINT_SCALES = List.of("addiction", "stress", "crisis");
    /** Подсказки для {@code <шкала>}. */
    private static final SuggestionProvider<CommandSourceStack> SCALE_SUGGESTIONS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    List.of("addiction", "stress", "crisis", "radiation", "uv", "chemical"), builder);

    /** Подсказки для {@code <событие>} — тот же список, что и у ручного триггера. */
    private static final SuggestionProvider<CommandSourceStack> EVENT_SUGGESTIONS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(PsycheStressEffects.TRIGGER_IDS, builder);

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        LiteralArgumentBuilder<CommandSourceStack> add = scaleBranch("add", 1);
        LiteralArgumentBuilder<CommandSourceStack> remove = scaleBranch("remove", -1);
        LiteralArgumentBuilder<CommandSourceStack> set = scaleBranch("set", 0);

        LiteralArgumentBuilder<CommandSourceStack> trigger = Commands.literal("trigger")
                .then(Commands.argument("event", StringArgumentType.word())
                        .suggests(EVENT_SUGGESTIONS)
                        .executes(PsycheCommand::trigger));

        // Brigadier сшивает одинаковые узлы: этот /gonzotech debug psyche добавится к
        // /gonzotech debug sun|notes|… из SpaceCommand — корни не конфликтуют.
        dispatcher.register(
                Commands.literal("gonzotech")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.literal("debug")
                                .then(Commands.literal("psyche")
                                        .then(add)
                                        .then(remove)
                                        .then(set)
                                        .then(trigger))));
    }

    /** Ветка {@code add|remove|set <шкала> <проценты>}; {@code mode}: +1, −1 или 0. */
    private static LiteralArgumentBuilder<CommandSourceStack> scaleBranch(String name, int mode) {
        return Commands.literal(name)
                .then(Commands.argument("parameter", StringArgumentType.word())
                        .suggests(SCALE_SUGGESTIONS)
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0D, 100.0D))
                                .executes(ctx -> changeScale(ctx, mode))));
    }

    // ─────────────────────────── шкалы ───────────────────────────

    private static int changeScale(CommandContext<CommandSourceStack> ctx, int mode) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal(
                    "§c[GonzoTech] Команда меняет шкалы игрока — нужен игрок в качестве отправителя."));
            return 0;
        }
        String scale = canonical(StringArgumentType.getString(ctx, "parameter"));
        if (scale == null) {
            source.sendFailure(Component.literal("§c[GonzoTech] Неизвестная шкала: "
                    + StringArgumentType.getString(ctx, "parameter")
                    + " (addiction, stress, crisis, radiation, uv, chemical)"));
            return 0;
        }

        double value = DoubleArgumentType.getDouble(ctx, "value");
        PlayerPsyche psyche = player.getData(ModPsycheAttachments.PSYCHE);
        boolean points = POINT_SCALES.contains(scale);
        int perPercent = points ? PlayerPsyche.POINTS_PER_PERCENT : 10; // очки / тысячные
        int delta = (int) Math.round(value * perPercent);

        int before = raw(psyche, scale);
        int target = switch (mode) {
            case 1 -> before + delta;  // add
            case -1 -> before - delta; // remove
            default -> delta;          // set
        };
        int after = write(player, psyche, scale, target);

        source.sendSuccess(() -> Component.literal(
                "§a[GonzoTech] " + scale + ": §e" + fmt(percentOf(before, points))
                        + " % §a→ §e" + fmt(percentOf(after, points)) + " %"), true);
        return after;
    }

    /** Записать значение в шкалу и сразу синхронизировать HUD. */
    private static int write(ServerPlayer player, PlayerPsyche psyche, String scale, int target) {
        switch (scale) {
            case "addiction" -> psyche.setAddiction(clampPoints(target));
            case "stress" -> psyche.setStress(clampPoints(target));
            case "crisis" -> psyche.setCrisis(clampPoints(target));
            case "radiation" -> psyche.setRadiation(clampPermille(target));
            case "uv" -> psyche.setUv(clampPermille(target));
            default -> psyche.setChemical(clampPermille(target));
        }
        player.setData(ModPsycheAttachments.PSYCHE, psyche);
        PsycheNetwork.sendToPlayer(player);
        return raw(psyche, scale);
    }

    private static int raw(PlayerPsyche psyche, String scale) {
        return switch (scale) {
            case "addiction" -> psyche.getAddiction();
            case "stress" -> psyche.getStress();
            case "crisis" -> psyche.getCrisis();
            case "radiation" -> psyche.getRadiation();
            case "uv" -> psyche.getUv();
            default -> psyche.getChemical();
        };
    }

    /** Внутренние единицы → проценты (для вывода в чат). */
    private static double percentOf(int value, boolean points) {
        return points ? value / (double) PlayerPsyche.POINTS_PER_PERCENT : value / 10.0D;
    }

    /** Округление «как в игре»: стресс/зависимость/кризис — целые очки. */
    private static int clampPoints(int v) {
        return Math.max(0, Math.min(PlayerPsyche.POINT_MAX, v));
    }

    /** Облучение/УФ/химия — тысячные, 0…1000 (100 %). */
    private static int clampPermille(int v) {
        return Math.max(0, Math.min(1000, v));
    }

    private static String fmt(double percent) {
        return String.format(Locale.ROOT, "%.1f", percent);
    }

    /** Алиасы: «кортизол» — это стресс, «доза» — облучение, «химия» — химическое заражение. */
    private static String canonical(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "addiction", "зависимость" -> "addiction";
            case "stress", "cortisol", "кортизол", "стресс" -> "stress";
            case "crisis", "кризис" -> "crisis";
            case "radiation", "dose", "доза", "облучение" -> "radiation";
            case "uv", "уф" -> "uv";
            case "chemical", "chem", "химия" -> "chemical";
            default -> null;
        };
    }

    // ─────────────────────────── события шкал ───────────────────────────

    private static int trigger(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal(
                    "§c[GonzoTech] Триггер срабатывает на игроке — нужен игрок в качестве отправителя."));
            return 0;
        }
        String id = StringArgumentType.getString(ctx, "event").toLowerCase(Locale.ROOT);
        if (!PsycheStressEffects.trigger(player, id)) {
            source.sendFailure(Component.literal("§c[GonzoTech] Неизвестное событие: " + id
                    + "\n§7Доступно: §f" + String.join(", ", PsycheStressEffects.TRIGGER_IDS)));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "§a[GonzoTech] Триггер: §e" + id + " §a— событие шкалы выполнено."), true);
        return 1;
    }
}
