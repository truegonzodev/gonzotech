package com.gonzotech.core.psyche;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Эффекты экзистенциального кризиса (спека автора 22.09.2026). Настоящие галлюцинации —
 * «строка кризиса, начинается самое интересное».
 *
 * <h2>Пороги</h2>
 * <table>
 *   <tr><th>кризис</th><th>что происходит</th></tr>
 *   <tr><td>&gt; {@value #CASCADE_MIN_PERCENT} %</td>
 *       <td>{@value #CASCADE_CHANCE} шанс запустить <b>каскад-чекпойнт</b>: тихо сохраняется состояние
 *       игрока, через 2–30 с играет случайный звук (за секунду до переноса), затем игрок
 *       переносится на чекпойнт, и на 0.3 с взгляд фиксируется</td></tr>
 *   <tr><td>&gt; {@value #SWAP_MIN_PERCENT} %</td>
 *       <td>{@value #SWAP_CHANCE} на любое «использование предмета» (ЛКМ/ПКМ, даже по воздуху):
 *       предмет меняется местами со случайным предметом из инвентаря. Плюс попытка поспать
 *       требует динамит под кроватью</td></tr>
 *   <tr><td>&gt; {@value #MICROSTEP_MIN_PERCENT} %</td>
 *       <td>если камеру не двигали {@value #MICROSTEP_IDLE_SECONDS} с, то каждую секунду
 *       {@value #MICROSTEP_CHANCE} шанс микрошага вперёд (симуляция 1–2 тиков «W»)</td></tr>
 *   <tr><td>&gt; {@value #OVERLAY_MIN_PERCENT} %</td>
 *       <td>экранный эффект: 10 % непрозрачности, дальше +2 % за каждый процент кризиса
 *       (рисует клиент, см. {@code client.PsycheCrisisClient})</td></tr>
 *   <tr><td>&gt; {@value #FAKE_DEATH_MIN_PERCENT} %</td>
 *       <td>любое получение урона — {@value #FAKE_DEATH_CHANCE} шанс ложного экрана смерти
 *       (игрок жив, ходит, кнопки просто убирают экран); все мобы не сводят с игрока взгляда</td></tr>
 * </table>
 *
 * <h2>Что сохраняет и восстанавливает каскад</h2>
 * <p>Только состояние САМОГО игрока: координаты, yaw/pitch, здоровье, голод (с насыщением),
 * эффекты, шесть шкал психики и время мира. Мир, мобы, предметы, хранилища не трогаются вообще.</p>
 *
 * <p><b>Время мира</b> сохраняется в слепок, но не «откатывается»: мировые часы — глобальные,
 * подкрутить их только этому игроку можно лишь фальшивым пакетом времени, который сервер тут же
 * перезапишет (получился бы мигающий скайбокс). Поэтому поле живёт в слепке и помечено
 * {@link #RESTORE_FAKE_TIME} — включим, если автор подтвердит, что имел в виду именно это.</p>
 *
 * <p><b>Наслоение каскадов</b> разрешено (автор): у каждого игрока список каскадов, каждый со своим
 * таймером и слепком; применение идёт по очереди, конфликтов нет.</p>
 */
public final class PsycheCrisis {

    // ── Каскад-чекпойнт ──
    public static final int CASCADE_MIN_PERCENT = 38;
    public static final double CASCADE_CHANCE = 0.003;
    public static final int CASCADE_SECONDS_MIN = 2;
    public static final int CASCADE_SECONDS_MAX = 30;
    /** Сколько тиков взгляд зафиксирован после переноса (0.3 с). */
    public static final int CAMERA_LOCK_TICKS = 6;
    /** Откатывать ли клиентское «время мира» (см. примечание в классе). */
    public static final boolean RESTORE_FAKE_TIME = false;

    /** Звуки перед переносом — ровно список автора (id ванильных звуков). */
    private static final String[] CASCADE_SOUNDS = {
            "ambient.cave",
            "entity.item.break",
            "entity.creeper.primed",
            "entity.tnt.primed",
            "entity.cat.ambient",
            "entity.wolf.ambient",
            "entity.villager.no",
            "entity.warden.heartbeat",
            "block.amethyst_block.chime",
            "entity.player.hurt"
    };

    // ── Подмена предмета ──
    public static final int SWAP_MIN_PERCENT = 56;
    public static final double SWAP_CHANCE = 0.018;

    // ── Микрошаг ──
    public static final int MICROSTEP_MIN_PERCENT = 67;
    public static final int MICROSTEP_IDLE_SECONDS = 20;
    public static final double MICROSTEP_CHANCE = 0.04;
    /** «1–2 тика прожатия W»: сколько тиков толкаем и с какой скоростью. */
    public static final double MICROSTEP_IMPULSE = 0.14;

    // ── Экранный эффект и ложная смерть ──
    public static final int OVERLAY_MIN_PERCENT = 74;
    public static final int FAKE_DEATH_MIN_PERCENT = 87;
    public static final double FAKE_DEATH_CHANCE = 0.31;
    /** Радиус, в котором мобы «не сводят взгляда» (кризис > 87 %). */
    public static final double MOB_STARE_RADIUS = 32.0;

    private static final Random RNG = new Random();

    /** Активные каскады по игрокам (наслоение разрешено — список). */
    private static final Map<UUID, List<Cascade>> CASCADES = new HashMap<>();
    /** Состояние «камера не двигалась»: [последний yaw, последний pitch, тик изменения, тик броска]. */
    private static final Map<UUID, float[]> CAMERA_STATE = new HashMap<>();
    /** Остаток микрошага в тиках (толкаем вперёд). */
    private static final Map<UUID, Integer> MICROSTEPS = new HashMap<>();

    private PsycheCrisis() {
    }

    // ═══════════════════════ секундные броски ═══════════════════════

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        tickPerTick(player);

        if (player.tickCount % 20 != 0) {
            return;
        }
        if (player.isCreative() || player.isSpectator() || player.isDeadOrDying()) {
            return;
        }
        int crisis = percent(player);
        if (crisis <= CASCADE_MIN_PERCENT) {
            return;
        }
        long now = player.serverLevel().getGameTime();

        // Каскад-чекпойнт (наслоение разрешено).
        if (RNG.nextDouble() < CASCADE_CHANCE) {
            scheduleCascade(player, now);
        }
        // Микрошаг: камеру давно не двигали.
        if (crisis > MICROSTEP_MIN_PERCENT && cameraIdleSeconds(player, now) > MICROSTEP_IDLE_SECONDS
                && RNG.nextDouble() < MICROSTEP_CHANCE) {
            MICROSTEPS.put(player.getUUID(), 1 + RNG.nextInt(2));
        }
        // Мобы не сводят взгляда.
        if (crisis > FAKE_DEATH_MIN_PERCENT) {
            stareAtPlayer(player);
        }
    }

    /** Тик-в-тик: каскады по таймеру и толчки микрошага. */
    private static void tickPerTick(ServerPlayer player) {
        Integer micro = MICROSTEPS.get(player.getUUID());
        if (micro != null) {
            if (micro <= 0) {
                MICROSTEPS.remove(player.getUUID());
            } else {
                Vec3 look = player.getLookAngle();
                player.setDeltaMovement(player.getDeltaMovement()
                        .add(look.x * MICROSTEP_IMPULSE, 0.0, look.z * MICROSTEP_IMPULSE));
                player.hurtMarked = true;                 // синхронизировать рывок клиенту
                MICROSTEPS.put(player.getUUID(), micro - 1);
            }
        }
    }

    /** Каскады: звук за секунду до переноса, перенос и фиксация камеры — по таймеру. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (CASCADES.isEmpty()) {
            return;
        }
        long now = event.getServer().getTickCount();
        Iterator<Map.Entry<UUID, List<Cascade>>> players = CASCADES.entrySet().iterator();
        while (players.hasNext()) {
            Map.Entry<UUID, List<Cascade>> entry = players.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                players.remove();
                continue;
            }
            Iterator<Cascade> cascades = entry.getValue().iterator();
            while (cascades.hasNext()) {
                Cascade cascade = cascades.next();
                if (!cascade.soundPlayed && now >= cascade.soundTick) {
                    playCascadeSound(player);
                    cascade.soundPlayed = true;
                }
                if (now >= cascade.jumpTick) {
                    apply(player, cascade.snapshot);
                    cascades.remove();
                }
            }
            if (entry.getValue().isEmpty()) {
                players.remove();
            }
        }
    }

    // ═══════════════════════ каскад-чекпойнт ═══════════════════════

    /**
     * «Втихоря» сохранить состояние игрока и поставить таймер переноса. Игрок ничего не видит:
     * ни сообщения, ни звука — только через 2–30 с знакомый звук и рывок в прошлое.
     */
    public static void scheduleCascade(ServerPlayer player, long now) {
        PlayerPsyche psyche = player.getData(ModPsycheAttachments.PSYCHE);
        List<MobEffectInstance> effects = new ArrayList<>();
        for (MobEffectInstance instance : player.getActiveEffects()) {
            effects.add(new MobEffectInstance(instance));
        }
        FoodData food = player.getFoodData();
        Snapshot snapshot = new Snapshot(
                player.serverLevel().dimension(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(),
                player.getHealth(), food.getFoodLevel(), food.getSaturationLevel(),
                player.serverLevel().getGameTime(),
                effects,
                new int[]{psyche.getAddiction(), psyche.getStress(), psyche.getCrisis(),
                        psyche.getRadiation(), psyche.getUv(), psyche.getChemical()});

        int seconds = CASCADE_SECONDS_MIN + RNG.nextInt(CASCADE_SECONDS_MAX - CASCADE_SECONDS_MIN + 1);
        long jumpTick = now + seconds * 20L;
        CASCADES.computeIfAbsent(player.getUUID(), key -> new ArrayList<>())
                .add(new Cascade(snapshot, jumpTick - 20L, jumpTick));
    }

    /** Применить слепок: перенос, взгляд, здоровье, голод, эффекты, шкалы. Мир не трогаем. */
    private static void apply(ServerPlayer player, Snapshot snapshot) {
        ServerLevel level = player.server.getLevel(snapshot.dimension());
        if (level != null) {
            // 1.21.4: у ServerPlayer только teleportTo(x,y,z) и полная форма с Set<Relative>
            // и флагом resetCamera (последний ещё и снимает «камеру-наблюдателя»).
            player.teleportTo(level, snapshot.x(), snapshot.y(), snapshot.z(),
                    Set.<Relative>of(), snapshot.yaw(), snapshot.pitch(), true);
        }
        player.setHealth(Math.max(1.0F, Math.min(snapshot.health(), player.getMaxHealth())));
        player.getFoodData().setFoodLevel(snapshot.food());
        player.getFoodData().setSaturation(snapshot.saturation());

        // Эффекты ровно как были в момент слепка. Перед этим снимаем пометку «сердечного
        // приступа»: иначе снятый откатом эффект засчитался бы как «приступ истёк» и игрок
        // получил бы урон до 1 HP не за провал приступа, а за восстановление состояния.
        PsycheStressEffects.forgetHeartAttack(player);
        player.removeAllEffects();
        for (MobEffectInstance instance : snapshot.effects()) {
            player.addEffect(new MobEffectInstance(instance));
        }

        // Шкалы психики — как были.
        PlayerPsyche psyche = player.getData(ModPsycheAttachments.PSYCHE);
        int[] scales = snapshot.scales();
        psyche.setAddiction(scales[0]);
        psyche.setStress(scales[1]);
        psyche.setCrisis(scales[2]);
        psyche.setRadiation(scales[3]);
        psyche.setUv(scales[4]);
        psyche.setChemical(scales[5]);
        player.setData(ModPsycheAttachments.PSYCHE, psyche);
        PsycheNetwork.sendToPlayer(player);

        // 0.3 с камера зафиксирована в сохранённом положении (клиент держит взгляд сам).
        PsycheCrisisNetwork.sendCameraLock(player, snapshot.yaw(), snapshot.pitch(), CAMERA_LOCK_TICKS);

        // Время мира: только если автор решит, что это оно (см. RESTORE_FAKE_TIME).
        if (RESTORE_FAKE_TIME && level != null) {
            level.setDayTime(snapshot.dayTime());
        }
    }

    /** Случайный звук перед переносом — по id ванильного звука (устойчиво к типам констант). */
    private static void playCascadeSound(ServerPlayer player) {
        String id = CASCADE_SOUNDS[RNG.nextInt(CASCADE_SOUNDS.length)];
        ResourceKey<SoundEvent> key = ResourceKey.create(Registries.SOUND_EVENT,
                ResourceLocation.withDefaultNamespace(id));
        Holder<SoundEvent> sound = player.serverLevel().registryAccess()
                .lookupOrThrow(Registries.SOUND_EVENT).getOrThrow(key);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                sound.value(), SoundSource.AMBIENT, 1.0F, 1.0F);
    }

    // ═══════════════════════ подмена предмета и сон ═══════════════════════

    /**
     * «Использование предмета»: ЛКМ (в том числе по воздуху, сигнал приходит от клиента),
     * ПКМ по предмету/блоку/сущности. Шанс {@value #SWAP_CHANCE} — предмет в руке меняется
     * местами со случайным предметом инвентаря.
     */
    public static void onItemUsed(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator() || percent(player) <= SWAP_MIN_PERCENT) {
            return;
        }
        if (RNG.nextDouble() >= SWAP_CHANCE) {
            return;
        }
        List<ItemStack> items = player.getInventory().items;
        int from = player.getInventory().selected;
        if (from < 0 || from >= items.size()) {
            from = 0;
        }
        int to = RNG.nextInt(items.size());
        if (to == from) {
            return;
        }
        ItemStack used = items.get(from);
        ItemStack other = items.get(to);
        if (used.isEmpty() && other.isEmpty()) {
            return;
        }
        items.set(from, other);
        items.set(to, used);
        player.containerMenu.broadcastChanges();
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                sound(player, "entity.item.pickup"), SoundSource.PLAYERS, 0.4F, 1.6F);
    }

    // ═══════════════════════ подписки на действия ═══════════════════════

    /** Сон: при кризисе > {@value #SWAP_MIN_PERCENT} % кровать требует динамит под собой. */
    @SubscribeEvent
    public static void onUseBed(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockState state = level.getBlockState(event.getPos());
        if (!(state.getBlock() instanceof BedBlock)) {
            return;
        }
        if (percent(player) <= SWAP_MIN_PERCENT) {
            return;
        }
        if (hasTntUnderBed(level, event.getPos(), state)) {
            return;
        }
        event.setCanceled(true);
        player.displayClientMessage(
                Component.translatable("message.gonzotech.crisis.sleep_tnt"), true);
    }

    /** Динамит должен лежать под кроватью (под любой из её половин). */
    private static boolean hasTntUnderBed(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getBlockState(pos.below()).is(Blocks.TNT)) {
            return true;
        }
        BlockPos other = state.getValue(BedBlock.PART) == BedPart.FOOT
                ? pos.relative(state.getValue(BedBlock.FACING))
                : pos.relative(state.getValue(BedBlock.FACING).getOpposite());
        return level.getBlockState(other.below()).is(Blocks.TNT);
    }

    /** ПКМ по предмету/блоку/сущности — тоже «использование предмета». */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            onItemUsed(player);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            onItemUsed(player);
        }
    }

    /** Ложный экран смерти: кризис > 87 %, шанс {@value #FAKE_DEATH_CHANCE} на любой полученный урон. */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getNewDamage() <= 0.0F) {
            return;
        }
        if (player.isCreative() || player.isSpectator() || player.isDeadOrDying()) {
            return;
        }
        if (percent(player) > FAKE_DEATH_MIN_PERCENT && RNG.nextDouble() < FAKE_DEATH_CHANCE) {
            PsycheCrisisNetwork.sendFakeDeath(player);
        }
    }

    /** Все мобы в радиусе смотрят только на игрока, не отрывая взгляда. */
    private static void stareAtPlayer(ServerPlayer player) {
        AABB box = player.getBoundingBox().inflate(MOB_STARE_RADIUS);
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, box)) {
            // ServerPlayer не наследует Mob, поэтому сравнение по UUID (иначе — incomparable types)
            if (mob.isAlive() && !mob.getUUID().equals(player.getUUID())) {
                mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
            }
        }
    }

    /** Выход из игры: снять каскады и состояние игрока — карты не должны течь. */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            forget(player.getUUID());
        }
    }

    /** Смерть: каскады отменяются (переносить мёртвого на чекпойнт нельзя). */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.Clone event) {
        if (event.isWasDeath() && event.getEntity() instanceof ServerPlayer player) {
            forget(player.getUUID());
        }
    }

    /** Забыть игрока. */
    public static void forget(UUID playerId) {
        CASCADES.remove(playerId);
        CAMERA_STATE.remove(playerId);
        MICROSTEPS.remove(playerId);
    }

    // ═══════════════════════ мелочи ═══════════════════════

    /** Проценты кризиса текущего игрока. */
    private static int percent(ServerPlayer player) {
        PlayerPsyche psyche = player.getData(ModPsycheAttachments.PSYCHE);
        return PsycheStress.percent(psyche.getCrisis());
    }

    /**
     * Сколько секунд камера «не двигалась»: сравниваем yaw/pitch с прошлым замером и считаем
     * отклонением всё, что больше 1 % хода камеры (3.6° по yaw, 1.8° по pitch).
     */
    private static long cameraIdleSeconds(ServerPlayer player, long now) {
        float[] state = CAMERA_STATE.get(player.getUUID());
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        if (state == null) {
            CAMERA_STATE.put(player.getUUID(), new float[]{yaw, pitch, now, 0});
            return 0L;
        }
        float dYaw = Math.abs(yaw - state[0]);
        float dPitch = Math.abs(pitch - state[1]);
        if (dYaw > 3.6F || dPitch > 1.8F) {
            state[0] = yaw;
            state[1] = pitch;
            state[2] = now;
            return 0L;
        }
        state[0] = yaw;
        state[1] = pitch;
        return (now - (long) state[2]) / 20L;
    }

    private static SoundEvent sound(ServerPlayer player, String id) {
        Holder<SoundEvent> holder = player.serverLevel().registryAccess()
                .lookupOrThrow(Registries.SOUND_EVENT)
                .getOrThrow(ResourceKey.create(Registries.SOUND_EVENT,
                        ResourceLocation.withDefaultNamespace(id)));
        return holder.value();
    }

    /** Слепок состояния игрока. */
    private record Snapshot(ResourceKey<Level> dimension,
                            double x, double y, double z,
                            float yaw, float pitch,
                            float health, int food, float saturation,
                            long dayTime,
                            List<MobEffectInstance> effects,
                            int[] scales) {
    }

    /** Запланированный каскад: когда играть звук и когда переносить. */
    private static final class Cascade {
        private final Snapshot snapshot;
        private final long soundTick;
        private final long jumpTick;
        private boolean soundPlayed;

        private Cascade(Snapshot snapshot, long soundTick, long jumpTick) {
            this.snapshot = snapshot;
            this.soundTick = soundTick;
            this.jumpTick = jumpTick;
        }
    }
}
