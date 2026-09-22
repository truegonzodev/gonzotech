package com.gonzotech.chalkboard;

import com.gonzotech.chalkboard.core.ChalkboardWorldData;
import com.gonzotech.chalkboard.core.GameSolver;
import com.gonzotech.chalkboard.core.Quantities;
import com.gonzotech.chalkboard.core.Quantity;
import com.gonzotech.chalkboard.network.ChalkboardNetwork;
import com.gonzotech.chalkboard.progress.ModAttachments;
import com.gonzotech.chalkboard.progress.PlayerChalkboardProgress;
import com.gonzotech.core.registry.ModBlocks;
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
 * <p><b>Единственная точка входа.</b> Сейчас подсказку зовёт только
 * {@code /gonzotech debug clue}; в будущем так же будут звать револьвер и водка
 * (автор 22.09.2026). Если подсказка будет стоить стресса/кризиса — это тоже
 * добавится здесь, одной строкой, чтобы все источники вели себя одинаково.</p>
 */
public final class ResonanceClue {

    private ResonanceClue() {
    }

    /** Радиус поиска доски резонанса для партиклов. */
    public static final int BOARD_PARTICLE_RADIUS = 16;
    /** Сколько партиклов {@code happy_villager} вспыхивает у доски. */
    public static final int BOARD_PARTICLE_COUNT = 12;

    /**
     * Выдать подсказку игроку.
     *
     * @return подсказанный блок или {@code null}, если подсказывать нечего
     *         (у задачи пустое решение) — тогда вызывающий сам решает, что сказать.
     */
    public static Quantity give(ServerPlayer player) {
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
        spawnBoardParticles(level, player);
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

    /** Партиклы {@code happy_villager} вокруг ближайшей доски резонанса. */
    private static void spawnBoardParticles(ServerLevel level, ServerPlayer player) {
        BlockPos origin = player.blockPosition();
        int r = BOARD_PARTICLE_RADIUS;
        BlockPos nearest = null;
        double best = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -r, -r), origin.offset(r, r, r))) {
            if (pos.distSqr(origin) >= best) continue;
            if (!level.isLoaded(pos)) continue;
            if (!level.getBlockState(pos).is(ModBlocks.CHALKBOARD.get())) continue;
            best = pos.distSqr(origin);
            nearest = pos.immutable();
        }
        if (nearest == null) return;
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                nearest.getX() + 0.5D, nearest.getY() + 1.0D, nearest.getZ() + 0.5D,
                BOARD_PARTICLE_COUNT, 0.6D, 0.6D, 0.6D, 0.0D);
    }
}
