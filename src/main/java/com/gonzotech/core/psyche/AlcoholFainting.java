package com.gonzotech.core.psyche;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Temporary native sleeping state. No fake bed blocks, respawn changes or successful-sleep rewards. */
@EventBusSubscriber(modid = "gonzotech")
public final class AlcoholFainting {
    private record Session(ResourceKey<Level> dimension, long until, Vec3 origin) {}
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();
    private AlcoholFainting() {}

    public static boolean isFainting(Player player) {
        return player instanceof FaintingPlayer faint && faint.gonzotech$isFainting();
    }

    public static boolean start(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || player.isSleeping() || isFainting(player)) return false;
        var anchor = player.blockPosition();
        var state = player.level().getBlockState(anchor);
        // Never take another sleeper's occupied real bed.
        if (state.isBed(player.level(), anchor, player) && state.hasProperty(BlockStateProperties.OCCUPIED)
                && state.getValue(BlockStateProperties.OCCUPIED)) return false;
        var faint = (FaintingPlayer) player;
        ACTIVE.put(player.getUUID(), new Session(player.level().dimension(), player.serverLevel().getGameTime() + AlcoholDose.FAINT_TICKS, player.position()));
        faint.gonzotech$beginFaint((float)(player.getY() - anchor.getY()));
        player.closeContainer();
        // LivingEntity's lower-level entry starts the real pose + sleeping-position sync.
        // Do NOT call ServerPlayer.startSleepInBed: it sets spawn and grants sleep stats/advancements.
        player.startSleeping(anchor);
        player.serverLevel().updateSleepingPlayerList();
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, AlcoholDose.BLINDNESS_TICKS, 0));
        return true;
    }

    @SubscribeEvent
    public static void keepSleeping(CanContinueSleepingEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isFainting(player)) return;
        Session session = ACTIVE.get(player.getUUID());
        if (session == null || !session.dimension.equals(player.level().dimension())
                || player.serverLevel().getGameTime() >= session.until || !player.isAlive()) return;
        if (event.getProblem() == Player.BedSleepingProblem.NOT_POSSIBLE_HERE
                || event.getProblem() == Player.BedSleepingProblem.NOT_POSSIBLE_NOW) {
            event.setContinueSleeping(true); // No bed and daytime are expected for a faint.
        }
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isFainting(player)) return;
        Session session = ACTIVE.get(player.getUUID());
        if (!player.isSleeping() || !player.isAlive() || session == null
                || !session.dimension.equals(player.level().dimension())
                || player.position().distanceToSqr(session.origin) > 4
                || player.serverLevel().getGameTime() >= session.until) finish(player);
    }

    public static void finish(ServerPlayer player) {
        if (!isFainting(player)) { ACTIVE.remove(player.getUUID()); return; }
        // Keep the flag through PlayerWakeUpEvent, so it cannot grant real-sleep benefits.
        if (player.isSleeping()) {
            Session session = ACTIVE.get(player.getUUID());
            if (session != null && (!session.dimension.equals(player.level().dimension())
                    || player.position().distanceToSqr(session.origin) > 4)) player.clearSleepingPos();
            player.stopSleepInBed(true, true);
        }
        afterWake(player);
    }

    /** Player.stopSleepInBed RETURN also handles manual Leave Bed and other external wakeups. */
    public static void afterWake(ServerPlayer player) {
        Session session = ACTIVE.remove(player.getUUID());
        if (!isFainting(player)) return;
        ((FaintingPlayer) player).gonzotech$endFaint();
        // Undo the bed's horizontal centering if the original standing position is
        // still safe. Never return a teleported/dead player to an old location.
        if (session != null && player.isAlive() && session.dimension.equals(player.level().dimension())
                && player.position().distanceToSqr(session.origin) <= 4
                && player.level().hasChunkAt(BlockPos.containing(session.origin))
                && player.level().noCollision(player, player.getBoundingBox().move(session.origin.subtract(player.position())))) {
            player.setPos(session.origin.x, session.origin.y, session.origin.z);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) finish(player);
    }
    @SubscribeEvent
    public static void onTravel(EntityTravelToDimensionEvent event) {
        // Wake in the OLD dimension, before a sleeping anchor could refer to an unrelated new-world bed.
        if (event.getEntity() instanceof ServerPlayer player) finish(player);
    }
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) finish(player);
    }
    @SubscribeEvent
    public static void onStop(ServerStoppingEvent event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) finish(player);
    }
    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) { ACTIVE.clear(); }
}
