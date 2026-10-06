package com.gonzotech.core.item;

import com.gonzotech.GonzoTechMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingSwapItemsEvent;
import net.neoforged.neoforge.event.entity.player.SweepAttackEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Server-authoritative sweeping, charged impact, particles, and forward impulse. */
@EventBusSubscriber(modid = GonzoTechMod.MOD_ID)
public final class GreatswordCombat {

    private static final double MELEE_REACH = 4.5D;
    private static final double DASH_IMPULSE = 0.20D;
    private static final ResourceLocation CHARGED_DAMAGE_ID =
        ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "greatsword_charge_damage");
    private static final Set<Player> CHARGED_SWINGS = ConcurrentHashMap.newKeySet();

    private GreatswordCombat() {
    }

    /** Make every ordinary left-click with a greatsword use the vanilla sweep path. */
    @SubscribeEvent
    public static void onSweepAttack(SweepAttackEvent event) {
        Player player = event.getEntity();
        if (player.getMainHandItem().getItem() instanceof GreatswordItem) {
            event.setSweeping(!CHARGED_SWINGS.contains(player));
        }
    }

    /** Prevent the F-key swap from placing a greatsword into the offhand. */
    @SubscribeEvent
    public static void onSwapHands(LivingSwapItemsEvent.Hands event) {
        if (event.getItemSwappedToOffHand().getItem() instanceof GreatswordItem) {
            event.setCanceled(true);
        }
    }

    /** Enforce the main-hand-only rule and clear any pre-existing offhand item. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        boolean hasGreatswordInEitherHand = player.getMainHandItem().getItem() instanceof GreatswordItem
            || player.getOffhandItem().getItem() instanceof GreatswordItem;
        if (hasGreatswordInEitherHand) moveOffhandToInventory(player);
    }

    private static void moveOffhandToInventory(ServerPlayer player) {
        ItemStack offhand = player.getOffhandItem();
        if (offhand.isEmpty()) return;

        ItemStack displaced = offhand.copy();
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        var inventory = player.getInventory();
        int freeSlot = inventory.getFreeSlot();
        if (freeSlot >= 0) {
            inventory.setItem(freeSlot, displaced);
        } else {
            // dropAround=false throws the item forward in the player's look direction.
            player.drop(displaced, false, false);
        }
        player.containerMenu.broadcastChanges();
    }

    public static void release(net.minecraft.world.item.ItemStack stack, Level level,
                               LivingEntity user, int timeLeft) {
        if (!(user instanceof ServerPlayer player) || !(level instanceof ServerLevel serverLevel)) return;

        int elapsed = Math.max(0, GreatswordItem.MAX_USE_TICKS - timeLeft);
        float charge = Math.max(0.0F, Math.min(1.0F,
            elapsed / (float) GreatswordItem.FULL_CHARGE_TICKS));
        if (charge <= 0.10F) return;

        Vec3 look = player.getLookAngle();
        LivingEntity target = findTarget(serverLevel, player, look);
        Vec3 impact = target != null
            ? new Vec3(target.getX(), target.getY(), target.getZ())
            : player.position().add(look.scale(2.75D));
        double baseDamage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);

        if (target != null) {
            AttributeInstance attackDamage = player.getAttribute(Attributes.ATTACK_DAMAGE);
            float attackStrength = player.getAttackStrengthScale(0.5F);
            double cooldownScale = 0.2D + attackStrength * attackStrength * 0.8D;
            double desiredDamage = baseDamage * (1.0D + 0.5D * charge);
            double temporaryBonus = desiredDamage / cooldownScale - baseDamage;
            CHARGED_SWINGS.add(player);
            if (attackDamage != null) {
                attackDamage.removeModifier(CHARGED_DAMAGE_ID);
                attackDamage.addTransientModifier(new AttributeModifier(CHARGED_DAMAGE_ID,
                    temporaryBonus, AttributeModifier.Operation.ADD_VALUE));
            }
            try {
                // Compensate vanilla's attack-strength multiplier so the charged
                // pre-mitigation base hit follows D × (1 + 0.5 × charge) directly.
                // The larger custom splash below replaces the normal inner sweep
                // for this release; ordinary left-clicks still use vanilla sweep.
                player.attack(target);
            } finally {
                CHARGED_SWINGS.remove(player);
                if (attackDamage != null) attackDamage.removeModifier(CHARGED_DAMAGE_ID);
            }
        }

        Vec3 splashCenter = target != null ? target.getBoundingBox().getCenter() : impact.add(0.0D, 0.6D, 0.0D);
        applyChargedSplash(serverLevel, player, target, splashCenter, baseDamage, charge);
        spawnGroundBurst(serverLevel, impact, charge);
        dash(player, look, charge);
    }

    private static LivingEntity findTarget(ServerLevel level, Player player, Vec3 look) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(look.scale(MELEE_REACH));
        BlockHitResult blockHit = level.clip(new ClipContext(start, end,
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double maxDistance = blockHit.getType() == net.minecraft.world.phys.HitResult.Type.MISS
            ? MELEE_REACH * MELEE_REACH
            : start.distanceToSqr(blockHit.getLocation());
        AABB search = player.getBoundingBox().expandTowards(look.scale(MELEE_REACH)).inflate(1.0D);

        LivingEntity closest = null;
        double closestDistance = maxDistance;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, search,
            entity -> entity != player && entity.isAlive() && entity.isPickable() && player.canAttack(entity))) {
            var hit = candidate.getBoundingBox().inflate(0.3D).clip(start, end);
            if (hit.isEmpty()) continue;
            double distance = start.distanceToSqr(hit.get());
            if (distance <= closestDistance) {
                closest = candidate;
                closestDistance = distance;
            }
        }
        return closest;
    }

    private static void applyChargedSplash(ServerLevel level, ServerPlayer player, LivingEntity primary,
                                           Vec3 center, double baseDamage, float charge) {
        double radius = 1.35D + 1.4D * charge;
        double damage = baseDamage * 0.25D * charge;
        if (damage <= 0.0D) return;
        AABB bounds = new AABB(center.x - radius, center.y - radius, center.z - radius,
            center.x + radius, center.y + radius, center.z + radius);
        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class, bounds,
            entity -> entity != player && entity != primary && entity.isAlive() && player.canAttack(entity))) {
            if (nearby.position().distanceToSqr(center) > radius * radius) continue;
            nearby.hurt(player.damageSources().playerAttack(player), (float) damage);
        }
    }

    private static void spawnGroundBurst(ServerLevel level, Vec3 impact, float charge) {
        BlockPos ground = BlockPos.containing(impact.x, impact.y, impact.z).below();
        BlockState groundState = level.getBlockState(ground);
        if (groundState.isAir()) return;

        BlockParticleOption dustPillar = new BlockParticleOption(ParticleTypes.DUST_PILLAR, groundState);
        int count = 10 + Math.round(14.0F * charge);
        double radius = 0.45D + 1.1D * charge;
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0D * i / count;
            double x = impact.x + Math.cos(angle) * radius;
            double z = impact.z + Math.sin(angle) * radius;
            level.sendParticles(dustPillar, x, ground.getY() + 1.01D, z,
                1, 0.08D, 0.04D, 0.08D, 0.01D);
        }
    }

    private static void dash(ServerPlayer player, Vec3 look, float charge) {
        Vec3 impulse = look.scale(DASH_IMPULSE * charge);
        // Add the directional impulse to existing velocity, matching the
        // additive, server-synchronized microstep movement pattern.
        player.setDeltaMovement(player.getDeltaMovement().add(impulse));
        player.hurtMarked = true;
    }
}
