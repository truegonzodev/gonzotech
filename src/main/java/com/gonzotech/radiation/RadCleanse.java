package com.gonzotech.radiation;

import com.gonzotech.core.psyche.ModPsycheAttachments;
import com.gonzotech.core.psyche.PlayerPsyche;
import com.gonzotech.core.psyche.PsycheNetwork;
import com.gonzotech.core.registry.ModEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * «Очищение» — эффект антирадинового абсорбента (спека автора 22.09.2026).
 *
 * <p>Пока эффект висит, он <b>плавно</b> выводит долю текущей дозы: за 30 секунд
 * суммарно {@link RadDose#CLEANSE_PER_LEVEL_PERMILLE} % (больше со стеком уровня).
 * Долей, а не фиксированным числом — как просил автор: «не 50 – 20 = 30, а
 * 50 – (50·0.2)».</p>
 *
 * <p>Когда эффект кончается: голод I на 10 секунд и синие крапинки лазурита
 * («абсорбент, а не лекарство»). Выведенная доза, как и обычный спад,
 * уходит в загрязнение чанка (см. {@link RadiationSystem}).</p>
 *
 * <p>Тик — раз в секунду из цикла {@link RadiationSystem}: своего тика у эффекта
 * нет (и не нужно — так эффект-класс остаётся простым, без переопределений
 * {@code applyEffectTick}).</p>
 */
public final class RadCleanse {

    /** Остаток вывода для дробных долей (в тысячных). */
    private static final Map<UUID, Double> RESIDUE = new HashMap<>();
    /** У кого эффект был в прошлую секунду — чтобы поймать момент окончания. */
    private static final Set<UUID> ACTIVE = new HashSet<>();

    private static final int HUNGER_TICKS = 20 * 10;

    private RadCleanse() {
    }

    /**
     * Секундный тик для игрока.
     *
     * @return сколько тысячных дозы реально выведено за этот тик (0 — эффекта нет)
     */
    public static int tick(ServerPlayer player) {
        MobEffectInstance cleanse = player.getEffect(ModEffects.RAD_CLEANSE);
        PlayerPsyche psyche = player.getData(ModPsycheAttachments.PSYCHE);
        UUID id = player.getUUID();

        if (cleanse == null) {
            if (ACTIVE.remove(id)) {           // эффект только что кончился
                after(player);
            }
            RESIDUE.remove(id);
            return 0;
        }
        ACTIVE.add(id);

        int dose = psyche.getRadiation();
        if (dose <= 0) {
            return 0;
        }

        // За секунду — (20 + 20·уровень)% дозы / 30 секунд.
        double exact = RadDose.cleansePerSecond(dose, cleanse.getAmplifier());
        double acc = RESIDUE.getOrDefault(id, 0.0) + exact;
        int shed = (int) acc;
        RESIDUE.put(id, acc - shed);
        if (shed <= 0) {
            return 0;
        }
        int applied = Math.min(shed, dose);
        psyche.setRadiation(dose - applied);
        PsycheNetwork.sendToPlayer(player);
        return applied;
    }

    /** Побочка после курса: голод I на 10 секунд и синие крапинки лазурита. */
    private static void after(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(
                net.minecraft.world.effect.MobEffects.HUNGER, HUNGER_TICKS, 0, true, true));
        if (player.level() instanceof net.minecraft.server.level.ServerLevel level) {
            level.sendParticles(
                    new net.minecraft.core.particles.BlockParticleOption(
                            net.minecraft.core.particles.ParticleTypes.FALLING_DUST,
                            net.minecraft.world.level.block.Blocks.LAPIS_BLOCK.defaultBlockState()),
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    24, 0.4, 0.6, 0.4, 0.05);
        }
    }

    /** Забыть игрока (выход/смерть) — карты не должны течь. */
    public static void forget(UUID playerId) {
        RESIDUE.remove(playerId);
        ACTIVE.remove(playerId);
    }
}
