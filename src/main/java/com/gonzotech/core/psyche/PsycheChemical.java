package com.gonzotech.core.psyche;

import com.gonzotech.core.registry.ModEffects;
import com.gonzotech.radiation.ItemToxicity;
import com.gonzotech.radiation.RadiationSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Шкала «Химическое заражение» (спека автора 22.09.2026) — шестая шкала HUD,
 * та же размерность, что у облучения (тысячные, {@code 1000 = 100 %}).
 *
 * <h2>Как наполняется</h2>
 * <ul>
 *   <li><b>Доза радиации делится с этой шкалой:</b> пока идёт облучение, 2 % дозы
 *       уходит в заражение ({@value #DOSE_SHARE} от темпа дозы);</li>
 *   <li><b>Токсичность предметов</b> ({@link com.gonzotech.radiation.ItemToxicity}):
 *       предметы со параметром «Токсичность: Tx/s» постепенно творят то же самое —
 *       но проще, чем радиация: не делятся друг с другом, не заражают чанк,
 *       заражают только игрока;</li>
 *   <li><b>Антирадин</b> (абсорбент): применение добавляет +{@value #ABSORBENT_ADD_PERMILLE}
 *       тысячных (1 %) — «лекарство само пачкает» (автор).</li>
 * </ul>
 *
 * <h2>Как спадает</h2>
 * <p>Сама тает на {@value #DECAY_PERCENT_PER_SECOND} % ОТ ТЕКУЩЕГО значения ежесекундно
 * (как радиация: «−0.2 % от текущего в секунду»). Смерть чистит
 * {@value #DEATH_CLEAR_PERCENT} % от текущего; препараты-очистители — потом (автор).</p>
 *
 * <h2>Эффект «Зуд»</h2>
 * <p>Пороги {@value #ITCH1_PERMILLE} / {@value #ITCH2_PERMILLE} / {@value #ITCH3_PERMILLE}
 * тысячных (38 / 52 / 69 %). Работает только если на игроке <b>хотя бы одна часть брони</b>,
 * иначе тело «дышит» и ничего не чешется (условие автора). Урон — как от отравления:
 * только на движении, не может добить (при HP ≤ 1 урона нет), кулдаун 3 / 2 / 1 секунды.</p>
 */
public final class PsycheChemical {

    // ── шкала ──
    /** Сколько дозы (в долях) уходит в заражение: 2 % (автор). */
    public static final double DOSE_SHARE = 0.02;
    /** Таяние: 0.3 % от текущего значения в секунду. */
    public static final double DECAY_PERCENT_PER_SECOND = 0.3;
    /** Смерть: чистится 30 % от текущего значения. */
    public static final int DEATH_CLEAR_PERCENT = 30;
    /** Антирадин (абсорбент): +1 % к шкале за применение. */
    public static final int ABSORBENT_ADD_PERMILLE = 10;

    // ── «Зуд» ──
    public static final int ITCH1_PERMILLE = 380;
    public static final int ITCH2_PERMILLE = 520;
    public static final int ITCH3_PERMILLE = 690;
    /** Кулдаун урона по уровням (секунды): 3 / 2 / 1. */
    public static final int[] ITCH_DAMAGE_COOLDOWN_SECONDS = {3, 2, 1};
    /** Бонус к получению стресса по уровням: +10 / +20 / +40 %. */
    public static final double[] ITCH_STRESS_BONUS = {0.10, 0.20, 0.40};
    /** Бонус к получению дозы на третьем уровне: +20 %. */
    public static final double ITCH3_DOSE_BONUS = 0.20;
    /** Как долго держится эффект «Зуд» без обновления (перевыдаётся раз в секунду). */
    private static final int ITCH_REFRESH_TICKS = 40;
    /** Урон «как отравление»: 1 HP и только если игрок выше 1 HP (добить нельзя). */
    private static final float ITCH_DAMAGE = 1.0F;
    /** «Движение» за секунду: 5 см (стоит на месте — смещения нет вообще). */
    private static final double MOVED_MIN_SQR = 0.05 * 0.05;
    /** Рывки дальше 32 блоков — телепорт (каскад-чекпойнт), движением не считаем. */
    private static final double TELEPORT_IGNORE_SQR = 32.0 * 32.0;

    private static final Map<UUID, State> STATES = new HashMap<>();

    private PsycheChemical() {
    }

    /** Состояние игрока: накопители дробной шкалы и таймеры зудового урона. */
    private static final class State {
        private double decayAcc;
        private double incomeAcc;
        /** Накопитель доли дозы (2 % от дозы радиации — темп дробный). */
        private double doseAcc;
        private int itchCooldown;
        /** Прошлая позиция игрока: «двигался ли» считаем по ней (см. {@link #movedSince}). */
        private double lastX;
        private double lastY;
        private double lastZ;
        private boolean hasLastPos;
    }

    // ═══════════════════════ раз в секунду ═══════════════════════

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % 20 != 0) {
            return;
        }
        if (player.isDeadOrDying()) {
            return;
        }
        // Автор 22.09.2026: «металлы не заражают игрока» — в креативе шкала не двигалась
        // вообще (ранний выход), а тестирует автор именно в креативе. Теперь шкала
        // наполняется и тает в ЛЮБОМ режиме (как доза радиации: RadiationSystem её тоже
        // не гейтит по креативу), а вот последствия — урон/вытаптывание — только в
        // выживании, как эффекты дозы в RadSickness.
        boolean consequences = !player.isCreative() && !player.isSpectator();
        PlayerPsyche psyche = player.getData(ModPsycheAttachments.PSYCHE);
        int before = psyche.getChemical();
        State state = STATES.computeIfAbsent(player.getUUID(), key -> new State());

        // 1. Токсичность предметов инвентаря (проще радиации: только игрок).
        //    Темпы дробные (nTx/с и 0.3 % от текущего), поэтому копим остатки:
        //    без накопителей целочисленное усечение «замораживало» шкалу ниже ~333‰.
        state.incomeAcc += ItemToxicity.inventoryTotal(player) / RadiationSystem.NZT_PER_PERMILLE;
        int income = (int) state.incomeAcc;
        state.incomeAcc -= income;

        // 2. Таяние 0.3 % от ТЕКУЩЕГО значения в секунду.
        state.decayAcc += before * (DECAY_PERCENT_PER_SECOND / 100.0);
        int decay = (int) state.decayAcc;
        state.decayAcc -= decay;

        int value = Math.max(0, Math.min(PlayerPsyche.MAX, before + income - decay));
        if (value != before) {
            psyche.setChemical(value);
            player.setData(ModPsycheAttachments.PSYCHE, psyche);
            PsycheNetwork.sendToPlayer(player);
        }

        // 3. Эффект «Зуд» и его последствия (эффект виден всегда — для проверки глазами).
        tickItch(player, value, consequences);
    }

    /**
     * Добавить заражение напрямую (тысячные шкалы). Источники: доля дозы радиации,
     * антирадин.
     */
    public static void addPermille(ServerPlayer player, double permille) {
        if (permille <= 0.0) {
            return;
        }
        PlayerPsyche psyche = player.getData(ModPsycheAttachments.PSYCHE);
        int value = Math.min(PlayerPsyche.MAX, psyche.getChemical() + (int) Math.round(permille));
        psyche.setChemical(value);
        player.setData(ModPsycheAttachments.PSYCHE, psyche);
        PsycheNetwork.sendToPlayer(player);
    }

    /**
     * Доля дозы, ушедшая в заражение за секунду темпом {@code dosePermillePerSecond}
     * (то есть 2 % от того, что получила шкала радиации). Темп дробный (обычно меньше
     * одной тысячной в секунду), поэтому копим остаток — иначе шкала не двигалась бы.
     */
    public static void addFromDose(ServerPlayer player, double dosePermillePerSecond) {
        if (dosePermillePerSecond <= 0.0) {
            return;
        }
        State state = STATES.computeIfAbsent(player.getUUID(), key -> new State());
        state.doseAcc += dosePermillePerSecond * DOSE_SHARE;
        int whole = (int) state.doseAcc;
        if (whole <= 0) {
            return;
        }
        state.doseAcc -= whole;
        addPermille(player, whole);
    }

    /** Смерть: чистится {@value #DEATH_CLEAR_PERCENT} % от ТЕКУЩЕГО значения. */
    public static void onDeath(ServerPlayer player) {
        PlayerPsyche psyche = player.getData(ModPsycheAttachments.PSYCHE);
        int value = psyche.getChemical();
        psyche.setChemical(value - value * DEATH_CLEAR_PERCENT / 100);
        STATES.remove(player.getUUID());
        player.setData(ModPsycheAttachments.PSYCHE, psyche);
        PsycheNetwork.sendToPlayer(player);
    }

    // ═══════════════════════ эффект «Зуд» ═══════════════════════

    /** Уровень зудa «как видно в игре» (0 — нет) по текущему заражению, с учётом брони. */
    public static int itchLevel(ServerPlayer player, int chemical) {
        if (!wearsArmor(player)) {
            return 0;
        }
        if (chemical > ITCH3_PERMILLE) {
            return 3;
        }
        if (chemical > ITCH2_PERMILLE) {
            return 2;
        }
        return chemical > ITCH1_PERMILLE ? 1 : 0;
    }

    /** «Хотя бы одна часть брони» — иначе зуд не появляется (условие автора). */
    private static boolean wearsArmor(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().armor) {
            if (!stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Множитель стресса от зуда: 1.0 / 1.1 / 1.2 / 1.4. */
    public static double stressMultiplier(ServerPlayer player) {
        int level = itchLevel(player, player.getData(ModPsycheAttachments.PSYCHE).getChemical());
        return level == 0 ? 1.0 : 1.0 + ITCH_STRESS_BONUS[level - 1];
    }

    /** Множитель получаемой дозы: +20 % на третьем уровне (см. RadiationSystem). */
    public static double doseMultiplier(ServerPlayer player) {
        int level = itchLevel(player, player.getData(ModPsycheAttachments.PSYCHE).getChemical());
        return level >= 3 ? 1.0 + ITCH3_DOSE_BONUS : 1.0;
    }

    private static void tickItch(ServerPlayer player, int chemical, boolean consequences) {
        int level = itchLevel(player, chemical);
        State state = STATES.computeIfAbsent(player.getUUID(), key -> new State());
        if (level == 0) {
            state.itchCooldown = 0;
            movedSince(state, player);   // запомнить позицию, пока зуд не активен
            return;
        }

        // Эффект держим на игроке, пока уровень не упадёт (обновляем раз в секунду).
        player.addEffect(new MobEffectInstance(ModEffects.ITCH,
                ITCH_REFRESH_TICKS, level - 1, false, true));

        // 1. Урон «как отравление»: только на движении, не добивает, свой кулдаун.
        boolean moved = movedSince(state, player);
        if (state.itchCooldown > 0) {
            state.itchCooldown--;
        }
        if (consequences && moved && state.itchCooldown <= 0 && player.getHealth() > 1.0F) {
            player.hurt(player.damageSources().magic(), ITCH_DAMAGE);
            state.itchCooldown = ITCH_DAMAGE_COOLDOWN_SECONDS[level - 1];
        }

        // 2. Блок под ногами «вытаптывается» (уровни 2 и 3 — по спеке);
        //    в креативе/наблюдателе мир не трогаем — там обычно строят.
        if (consequences && level >= 2) {
            stompGround(player, level);
        }
    }

    /**
     * Двигался ли игрок с прошлого вызова (раз в секунду). Считаем по СМЕЩЕНИЮ ПОЗИЦИИ,
     * а не по {@code walkDist}: в Mojang-маппингах 1.21.4 такого поля нет (это Yarn-имя,
     * на нём падала сборка автора) — есть {@code walkAnimation}. Смещение надёжнее:
     * телепорт (например, каскад-чекпойнт) движением не считаем, иначе игрок «пробегал»
     * бы полмира и получал урон за перенос.
     */
    private static boolean movedSince(State state, ServerPlayer player) {
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        if (!state.hasLastPos) {
            state.lastX = x;
            state.lastY = y;
            state.lastZ = z;
            state.hasLastPos = true;
            return false;
        }
        double dx = x - state.lastX;
        double dy = y - state.lastY;
        double dz = z - state.lastZ;
        state.lastX = x;
        state.lastY = y;
        state.lastZ = z;
        double distSqr = dx * dx + dy * dy + dz * dz;
        if (distSqr > TELEPORT_IGNORE_SQR) {
            return false;
        }
        return distSqr > MOVED_MIN_SQR;
    }

    /**
     * Трава/пашня под ногами → земля (уровень 2); трава, земля, пашня, подзол и мицелий →
     * грубая земля (уровень 3).
     */
    private static void stompGround(ServerPlayer player, int level) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockPos pos = player.blockPosition().below();
        BlockState state = serverLevel.getBlockState(pos);
        BlockState replacement = null;
        if (level >= 3) {
            if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.FARMLAND)
                    || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)) {
                replacement = Blocks.COARSE_DIRT.defaultBlockState();
            }
        } else if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.FARMLAND)) {
            replacement = Blocks.DIRT.defaultBlockState();
        }
        if (replacement != null) {
            serverLevel.setBlockAndUpdate(pos, replacement);
        }
    }

    /** Забыть игрока (выход). */
    public static void forget(UUID playerId) {
        STATES.remove(playerId);
    }

    /** Для отладки: текущий уровень зудa. */
    public static int debugLevel(ServerPlayer player) {
        return itchLevel(player, player.getData(ModPsycheAttachments.PSYCHE).getChemical());
    }

    /**
     * Отладочный «удар зудом»: выдать эффект текущего уровня и провести один удар по
     * правилам (движение игнорируем — админ хочет увидеть эффект сразу).
     */
    public static void itchDebug(ServerPlayer player) {
        int chemical = player.getData(ModPsycheAttachments.PSYCHE).getChemical();
        int level = Math.max(1, itchLevel(player, chemical));
        player.addEffect(new MobEffectInstance(ModEffects.ITCH, ITCH_REFRESH_TICKS, level - 1, false, true));
        if (player.getHealth() > 1.0F) {
            player.hurt(player.damageSources().magic(), ITCH_DAMAGE);
        }
        stompGround(player, level);
    }
}
