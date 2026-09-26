package com.gonzotech.chalkboard;

import com.gonzotech.chalkboard.core.ChalkboardWorldData;
import com.gonzotech.chalkboard.core.GameSolver;
import com.gonzotech.chalkboard.core.Quantities;
import com.gonzotech.chalkboard.core.Quantity;
import com.gonzotech.chalkboard.network.ChalkboardNetwork;
import com.gonzotech.chalkboard.progress.ModAttachments;
import com.gonzotech.chalkboard.progress.PlayerChalkboardProgress;
import com.gonzotech.core.registry.ModBlocks;
import com.gonzotech.core.psyche.AlcoholDose;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Подсказка по доске резонанса (автор 22.09.2026).
 *
 * <p><b>Что делает.</b> Берёт ТЕКУЩУЮ задачу игрока (никакого разбора его черновика —
 * «даже без текущего stance уравнения»), достаёт из сгенерированного решения один
 * случайный блок и подсвечивает его в лотке доски. Если блок игроку ещё не открыт —
 * подсказка его выдаёт: иначе сервер не принял бы решение (валидатор требует
 * {@code isQuantityUnlocked} для каждой плитки, поставленной игроком), и подсказка
 * была бы бесполезной.</p>
 *
 * <p><b>Визуал.</b> Клиент обводит плитку толстой белой рамкой (4–5 px) и снимает
 * обводку при первом использовании блока (нажал/перетащил). Плюс вокруг ближайшей
 * доски резонанса (радиус {@value #BOARD_PARTICLE_RADIUS} блоков) вспыхивают
 * {@code happy_villager} партиклы.</p>
 *
 * <p><b>Общий механизм.</b> Подсказку зовут {@code /gonzotech debug clue} и
 * напитки через {@link #giveNatural}. Револьвер остаётся будущим источником.
 * Отдельная цена самой подсказки в стрессе/кризисе пока не назначена.</p>
 */
public final class ResonanceClue {

    private ResonanceClue() {
    }

    /** Радиус поиска доски резонанса для партиклов. */
    public static final int BOARD_PARTICLE_RADIUS = (int) AlcoholDose.CLUE_RADIUS;
    /** Сколько партиклов {@code happy_villager} вспыхивает у доски. */
    public static final int BOARD_PARTICLE_COUNT = 12;

    /**
     * Выдать подсказку игроку.
     *
     * @return подсказанный блок или {@code null}, если подсказывать нечего
     *         (у задачи пустое решение) — тогда вызывающий сам решает, что сказать.
     */
    public static Quantity give(ServerPlayer player) {
        return give(player, nearestBoard(player)); // Debug remains usable even without a board.
    }

    /** Natural sources must actually be near a loaded board; failed/no-op clues say nothing. */
    public static Quantity giveNatural(ServerPlayer player) {
        BlockPos board = nearestBoard(player);
        if (board == null) return null;
        Quantity hinted = give(player, board);
        if (hinted != null) player.sendSystemMessage(Component.translatable("message.gonzotech.clue.elementary")
                .withStyle(ChatFormatting.RED));
        return hinted;
    }

    private static Quantity give(ServerPlayer player, BlockPos board) {
        ServerLevel level = player.serverLevel();
        PlayerChalkboardProgress progress = player.getData(ModAttachments.CHALKBOARD_PROGRESS);
        GameSolver.Puzzle puzzle = ChalkboardWorldData.get(level)
                .getPuzzle(progress.getCurrentDiscoveryIndex());
        if (puzzle == null) return null;

        Quantity hinted = pickHint(puzzle);
        if (hinted == null) return null;

        // Блок обязан стать доступным игроку: без этого подсказанный блок нельзя
        // поставить в формулу — серверная проверка решения отклонит его.
        if (!progress.isQuantityUnlocked(hinted)) {
            progress.unlockSecretQuantity(hinted.id());
            player.setData(ModAttachments.CHALKBOARD_PROGRESS, progress);
        }

        ChalkboardNetwork.sendClue(player, hinted.id());
        // Свежий синк: лоток и открытые блоки обновляются сразу, даже если доска
        // уже открыта на экране.
        ChalkboardNetwork.sendSyncToPlayer(player);
        spawnBoardParticles(level, board);
        return hinted;
    }

    /**
     * Случайный блок из решения текущей задачи. Цель ({@code puzzle.target()}) не
     * подсказывается: в лотке она заблокирована (это ответ уравнения, а не плитка).
     */
    private static Quantity pickHint(GameSolver.Puzzle puzzle) {
        List<String> ids = new ArrayList<>(puzzle.sampleSolution().values());
        ids.removeIf(id -> id == null
                || Quantities.get(id) == null
                || id.equals(puzzle.target().id()));
        if (ids.isEmpty()) return null;
        return Quantities.get(ids.get(ThreadLocalRandom.current().nextInt(ids.size())));
    }

    /** Loaded boards inside a true 16-block sphere, not the corners of a 33-block cube. */
    private static BlockPos nearestBoard(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos origin = player.blockPosition();
        int r = BOARD_PARTICLE_RADIUS;
        BlockPos nearest = null;
        double best = Double.POSITIVE_INFINITY;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -r, -r), origin.offset(r, r, r))) {
            double distance = pos.distToCenterSqr(player.getX(), player.getY(), player.getZ());
            if (!AlcoholDose.withinClueRadius(distance) || distance >= best) continue;
            if (!level.isLoaded(pos)) continue;
            if (!level.getBlockState(pos).is(ModBlocks.CHALKBOARD.get())) continue;
            best = distance;
            nearest = pos.immutable();
        }
        return nearest;
    }

    private static void spawnBoardParticles(ServerLevel level, BlockPos nearest) {
        if (nearest == null) return;
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                nearest.getX() + 0.5D, nearest.getY() + 1.0D, nearest.getZ() + 0.5D,
                BOARD_PARTICLE_COUNT, 0.6D, 0.6D, 0.6D, 0.0D);
    }
}
