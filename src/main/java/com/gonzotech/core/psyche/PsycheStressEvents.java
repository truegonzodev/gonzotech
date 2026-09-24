package com.gonzotech.core.psyche;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.EnderManAngerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Событийные источники стресса и кризиса (спека автора 22.09.2026).
 * Тиковые (раз в секунду) источники — в {@link PsycheStress}; здесь всё, что случается разово.
 *
 * <p><b>Дополнительно к списку автора</b> (нужно было для однозначности, помечено в доках):
 * смерть зверя дотягивается до игрока только если он убийца ЛИБО находится в
 * {@value PsycheStress#DEATH_AFFECT_RADIUS} блоках; кормление питомца считается по
 * взаимодействию с едой в руке (кулдаун 1 с, чтобы клик не был фармом); «ваншот» — удар,
 * который снёс мобу полное здоровье; «взрыв рядом» — радиус
 * {@value PsycheStress#EXPLOSION_NEAR_RADIUS} блоков от середины задетых блоков.</p>
 */
public final class PsycheStressEvents {

    /** Счётчик антистресс-фиджета: [нажатий в окне, начало окна (gameTime)]. */
    private static final Map<UUID, long[]> LEVER_USES = new HashMap<>();
    /** Кулдаун «кормления питомца» (клик-спам не фармит). */
    private static final Map<UUID, Long> LAST_FEED = new HashMap<>();

    private PsycheStressEvents() {
    }

    // ─────────────── урон и взрывы ───────────────

    /** Урон от взрыва: +200 сверх обычных 200 «за урон» (автор 22.09). */
    @SubscribeEvent
    public static void onExplosionDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getNewDamage() <= 0.0F) {
            return;
        }
        if (event.getSource().is(DamageTypeTags.IS_EXPLOSION)) {
            PsycheStress.gain(player, PsycheStress.EXPLOSION_DAMAGE_BURST);
        }
    }

    /**
     * Взрыв рядом: +500 всем игрокам в радиусе {@value PsycheStress#EXPLOSION_NEAR_RADIUS}
     * блоков. Центр берём как середину задетых блоков — это устойчиво к версиям API взрыва.
     */
    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Vec3 center = affectedCenter(event.getAffectedBlocks());
        if (center == null) {
            return;
        }
        double radius = PsycheStress.EXPLOSION_NEAR_RADIUS;
        AABB box = new AABB(center, center).inflate(radius);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box)) {
            PsycheStress.gain(player, PsycheStress.EXPLOSION_NEAR_BURST);
        }
    }

    private static Vec3 affectedCenter(List<BlockPos> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return null;
        }
        double x = 0, y = 0, z = 0;
        for (BlockPos pos : blocks) {
            x += pos.getX() + 0.5;
            y += pos.getY() + 0.5;
            z += pos.getZ() + 0.5;
        }
        int n = blocks.size();
        return new Vec3(x / n, y / n, z / n);
    }

    /**
     * Ваншот моба: удар, который снёс мобу полное здоровье (автор 22.09) —
     * −{@value PsycheStress#ONESHOT_RELIEF} стресса, +{@value PsycheStress#ONESHOT_CRISIS} кризиса.
     * Убийце, только если убил именно игрок.
     */
    @SubscribeEvent
    public static void onOneShotKill(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0.0F || !(event.getEntity() instanceof Mob mob)) {
            return;
        }
        if (mob.getHealth() > 0.0F || event.getNewDamage() < mob.getMaxHealth()) {
            return; // этот удар моба не убил или это не ваншот
        }
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            PsycheStress.relieve(player, PsycheStress.ONESHOT_RELIEF);
            PsycheStress.crisis(player, PsycheStress.ONESHOT_CRISIS);
        }
    }

    // ─────────────── смерти существ ───────────────

    /**
     * Смерти зверей (автор 22.09): кот/оцелот/пёс/волк — +4000 стресса и +100 кризиса,
     * их детёныш — +40 000 и +1000, скотина — +100 стресса, но только если кризис ниже 20 %.
     * Прирученность не важна («даже не прирученных»). Дракон и житель — только за убийство.
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        Entity dead = event.getEntity();

        if (dead instanceof EnderDragon) {
            for (ServerPlayer player : killers(dead)) {
                PsycheStress.relieve(player, PsycheStress.DRAGON_KILL_RELIEF);
                PsycheStress.crisis(player, PsycheStress.DRAGON_KILL_CRISIS);
            }
            return;
        }
        if (dead instanceof Villager) {
            for (ServerPlayer player : killers(dead)) {
                PsycheStress.gain(player, PsycheStress.VILLAGER_KILL_STRESS);
                PsycheStress.crisis(player, PsycheStress.VILLAGER_KILL_CRISIS);
            }
            return;
        }
        if (isPet(dead)) {
            boolean baby = dead instanceof AgeableMob ageable && ageable.isBaby();
            for (ServerPlayer player : affectedPlayers(level, dead)) {
                PsycheStress.gain(player,
                        baby ? PsycheStress.PET_BABY_DEATH_BURST : PsycheStress.PET_DEATH_BURST);
                PsycheStress.crisis(player,
                        baby ? PsycheStress.PET_BABY_DEATH_CRISIS : PsycheStress.PET_DEATH_CRISIS);
            }
            return;
        }
        if (dead instanceof Animal) {
            for (ServerPlayer player : affectedPlayers(level, dead)) {
                PlayerPsyche psyche = player.getData(ModPsycheAttachments.PSYCHE);
                if (PsycheStress.percent(psyche.getCrisis()) < PsycheStress.LIVESTOCK_CRISIS_BELOW_PERCENT) {
                    PsycheStress.gain(player, PsycheStress.LIVESTOCK_DEATH_BURST);
                }
            }
        }
    }

    private static boolean isPet(Entity entity) {
        return entity instanceof Cat || entity instanceof Ocelot || entity instanceof Wolf;
    }

    /** Игроки, которых касается смерть зверя: убийца + все рядом (без дублей). */
    private static List<ServerPlayer> affectedPlayers(ServerLevel level, Entity dead) {
        List<ServerPlayer> out = new ArrayList<>(killers(dead));
        AABB box = dead.getBoundingBox().inflate(PsycheStress.DEATH_AFFECT_RADIUS);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box)) {
            if (!out.contains(player)) {
                out.add(player);
            }
        }
        return out;
    }

    /** Кто убил: игрок, нанёсший последний удар. */
    private static List<ServerPlayer> killers(Entity dead) {
        if (dead instanceof LivingEntity living && living.getKillCredit() instanceof ServerPlayer player) {
            return List.of(player);
        }
        return List.of();
    }

    // ─────────────── тотем, сон, еда ───────────────

    /**
     * Прок тотема бессмертия: +{@value PsycheStress#TOTEM_ADDICTION_BURST} очков зависимости
     * (0.2 % — шкала зависимости тоже «в очках», автор подтвердил 22.09) и
     * +{@value PsycheStress#TOTEM_STRESS_BURST} стресса.
     */
    @SubscribeEvent
    public static void onTotemUsed(LivingUseTotemEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PsycheStress.addict(player, PsycheStress.TOTEM_ADDICTION_BURST);
        PsycheStress.gain(player, PsycheStress.TOTEM_STRESS_BURST);
    }

    /** Сон в кровати: −500 стресса (автор 22.09). */
    @SubscribeEvent
    public static void onWakeUp(PlayerWakeUpEvent event) {
        // NeoForge: метод называется wakeImmediately(); false = сон «доспал до утра» (не разбудили)
        if (event.getEntity() instanceof ServerPlayer player && !event.wakeImmediately()) {
            PsycheStress.relieve(player, PsycheStress.SLEEP_RELIEF);
        }
    }

    /** Сладкое (золотое яблоко, печенье): −50 стресса за порцию. */
    @SubscribeEvent
    public static void onSweetEaten(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack eaten = event.getItem();
        if (eaten.is(Items.GOLDEN_APPLE) || eaten.is(Items.ENCHANTED_GOLDEN_APPLE)
                || eaten.is(Items.COOKIE)) {
            PsycheStress.relieve(player, PsycheStress.SWEET_RELIEF);
        }
    }

    /** Торт: один съеденный кусок = порция сладкого. */
    @SubscribeEvent
    public static void onCakeSlice(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockState state = level.getBlockState(event.getPos());
        if (state.getBlock() instanceof CakeBlock && player.getFoodData().getFoodLevel() < 20) {
            PsycheStress.relieve(player, PsycheStress.SWEET_RELIEF);
        }
    }

    /** Кормление питомца с рук: −100 стресса (кулдаун 1 с, чтобы клик не был фармом). */
    @SubscribeEvent
    public static void onFeedPet(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof Animal animal)
                || animal.isBaby()
                || !animal.isFood(event.getItemStack())) {
            return;
        }
        if (!(animal instanceof TamableAnimal tameable) || !tameable.isTame()
                || !tameable.isOwnedBy(player)) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        Long last = LAST_FEED.get(player.getUUID());
        if (last != null && now - last < PsycheStress.PET_FEED_COOLDOWN) {
            return;
        }
        LAST_FEED.put(player.getUUID(), now);
        PsycheStress.relieve(player, PsycheStress.PET_FEED_RELIEF);
    }

    // ─────────────── эндермен, рычаг ───────────────

    /** Скример эндермена: он агрится именно от взгляда в глаза — +400 стресса сразу. */
    @SubscribeEvent
    public static void onEnderManAnger(EnderManAngerEvent event) {
        Player player = event.getPlayer();
        if (player instanceof ServerPlayer serverPlayer) {
            PsycheStress.gain(serverPlayer, PsycheStress.ENDERMAN_STARE_BURST);
        }
    }

    /**
     * Антистресс-фиджет: ПКМ по рычагу/кнопке −1 стресса, но не больше
     * {@value PsycheStress#LEVER_DAILY_LIMIT} раз за игровые сутки; дальше — плато:
     * 0 стресса и +1 кризис за нажатие. Окно — {@value PsycheStress#LEVER_COOLDOWN_TICKS}
     * тиков от первого нажатия (то есть «не больше 200 раз в день»).
     */
    @SubscribeEvent
    public static void onLeverOrButton(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockState state = level.getBlockState(event.getPos());
        if (!(state.getBlock() instanceof LeverBlock) && !(state.getBlock() instanceof ButtonBlock)) {
            return;
        }
        long now = level.getGameTime();
        long[] window = LEVER_USES.computeIfAbsent(player.getUUID(), key -> new long[]{0L, now});
        if (now - window[1] > PsycheStress.LEVER_COOLDOWN_TICKS) {
            window[0] = 0L;                      // новое суточное окно
            window[1] = now;
        }
        if (window[0] < PsycheStress.LEVER_DAILY_LIMIT) {
            window[0]++;
            PsycheStress.relieve(player, PsycheStress.LEVER_RELIEF);
        } else {
            PsycheStress.crisis(player, PsycheStress.LEVER_PLATEAU_CRISIS);
        }
    }

    /** Забыть игрока (выход) — карты не должны течь. */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LEVER_USES.remove(player.getUUID());
            LAST_FEED.remove(player.getUUID());
        }
    }
}
