package com.gonzotech.core.item;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.registry.ModItems;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Server-authoritative magnet pickup extension and dropped-item attraction. */
@EventBusSubscriber(modid = GonzoTechMod.MOD_ID)
public final class MagnetEvents {

    /** Vanilla pickup box extends one block horizontally and half a block vertically. */
    private static final double PICKUP_REACH_MULTIPLIER = 3.0D;
    /** Attraction reaches well beyond the enlarged pickup box. */
    private static final double ATTRACTION_RANGE = 12.0D;
    private static final double ATTRACTION_RANGE_SQR = ATTRACTION_RANGE * ATTRACTION_RANGE;
    private static final double MAX_ATTRACTION_SPEED = 0.55D;
    private static final double VELOCITY_RESPONSE = 0.25D;

    private MagnetEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !isHoldingMagnet(player)) {
            return;
        }

        // Match the vanilla pickup box, but triple its extension on each axis.
        AABB pickupBox = player.getBoundingBox().inflate(
                PICKUP_REACH_MULTIPLIER,
                PICKUP_REACH_MULTIPLIER * 0.5D,
                PICKUP_REACH_MULTIPLIER);
        AABB attractionBox = player.getBoundingBox().inflate(ATTRACTION_RANGE);
        Vec3 pullTarget = player.position().add(0.0D, 0.5D, 0.0D);

        for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, attractionBox)) {
            if (!item.isAlive() || item.hasPickUpDelay()
                    || pullTarget.distanceToSqr(item.position()) > ATTRACTION_RANGE_SQR) {
                continue;
            }

            // Use the normal pickup path (inventory, ownership and pickup sounds stay vanilla).
            if (pickupBox.intersects(item.getBoundingBox())) {
                item.playerTouch(player);
                if (!item.isAlive()) {
                    continue;
                }
            }

            pullItem(item, pullTarget);
        }
    }

    private static boolean isHoldingMagnet(Player player) {
        return player.getMainHandItem().is(ModItems.MAGNET.get())
                || player.getOffhandItem().is(ModItems.MAGNET.get());
    }

    private static void pullItem(ItemEntity item, Vec3 target) {
        Vec3 direction = target.subtract(item.position());
        double distanceSqr = direction.lengthSqr();
        if (distanceSqr < 1.0E-6D) {
            return;
        }

        double distance = Math.sqrt(distanceSqr);
        double speed = 0.08D + 0.34D * Math.min(distance / ATTRACTION_RANGE, 1.0D);
        Vec3 desiredVelocity = direction.scale(speed / distance);
        Vec3 velocity = item.getDeltaMovement().scale(1.0D - VELOCITY_RESPONSE)
                .add(desiredVelocity.scale(VELOCITY_RESPONSE));
        double velocitySqr = velocity.lengthSqr();
        if (velocitySqr > MAX_ATTRACTION_SPEED * MAX_ATTRACTION_SPEED) {
            velocity = velocity.scale(MAX_ATTRACTION_SPEED / Math.sqrt(velocitySqr));
        }
        item.setDeltaMovement(velocity);
    }
}
