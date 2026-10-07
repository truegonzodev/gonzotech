package com.gonzotech.core.item;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingSwapItemsEvent;
import net.neoforged.neoforge.event.entity.player.SweepAttackEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/** Server-authoritative sweeping, charged impact, particles, and forward impulse. */
@EventBusSubscriber(modid = GonzoTechMod.MOD_ID)
public final class GreatswordCombat {

    private static final double MELEE_REACH = 4.5D;
    private static final double DASH_IMPULSE = 0.20D;
    private static final double BASE_CHARGED_DAMAGE_BONUS = 0.55D;
    private static final double DENSITY_CHARGED_DAMAGE_BONUS_PER_LEVEL = 0.08D;
    private static final double FEATHER_FALLING_ATTACK_SPEED_PER_LEVEL = 0.05D;
    private static final int MAX_DENSITY_LEVEL = 5;
    private static final int MAX_FEATHER_FALLING_LEVEL = 4;
    private static final ResourceLocation FEATHER_FALLING_ATTACK_SPEED_ID =
        ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "greatsword_feather_falling_attack_speed");
    private static final ResourceLocation EXTENDED_ATTACK_COOLDOWN_ID =
        ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "greatsword_extended_attack_cooldown");
    private static final Set<Player> CHARGED_SWINGS = ConcurrentHashMap.newKeySet();
    private static final Map<Player, ChargedAttackContext> CHARGED_ATTACKS = new ConcurrentHashMap<>();
    private static final Map<ServerPlayer, Long> EXTENDED_ATTACK_COOLDOWN_EXPIRY = new WeakHashMap<>();

    private record ChargedAttackContext(LivingEntity target, float damageMultiplier) {
    }

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

    /** Feather Falling is repurposed only on greatswords as a main-hand attack-speed bonus. */
    @SubscribeEvent
    public static void onGreatswordAttributeModifiers(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof GreatswordItem)) return;

        int level = GreatswordItem.enchantmentLevel(stack, Enchantments.FEATHER_FALLING);
        double bonus = featherFallingAttackSpeedBonus(level);
        if (bonus <= 0.0D) return;

        event.addModifier(Attributes.ATTACK_SPEED,
            new AttributeModifier(FEATHER_FALLING_ATTACK_SPEED_ID, bonus,
                AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.MAINHAND);
    }

    /** Scale only the charged attack's primary hit after ordinary damage modifiers are resolved. */
    @SubscribeEvent
    public static void onChargedAttackDamage(LivingDamageEvent.Pre event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        ChargedAttackContext context = CHARGED_ATTACKS.get(player);
        if (context == null || event.getEntity() != context.target()) return;
        event.setNewDamage(event.getNewDamage() * context.damageMultiplier());
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
        tickExtendedAttackCooldown(player);
    }

    /** Full-charge multiplier is 1.55 without Density, then +0.08 per Density level. */
    public static double chargedDamageMultiplier(float charge, int densityLevel) {
        double clampedCharge = Math.max(0.0D, Math.min(1.0D, charge));
        int level = Math.max(0, Math.min(MAX_DENSITY_LEVEL, densityLevel));
        return 1.0D + (BASE_CHARGED_DAMAGE_BONUS
            + DENSITY_CHARGED_DAMAGE_BONUS_PER_LEVEL * level) * clampedCharge;
    }

    /** Feather Falling adds 0.05 attack speed per level, capped at level IV. */
    public static double featherFallingAttackSpeedBonus(int featherFallingLevel) {
        int level = Math.max(0, Math.min(MAX_FEATHER_FALLING_LEVEL, featherFallingLevel));
        return FEATHER_FALLING_ATTACK_SPEED_PER_LEVEL * level;
    }

    private static void tickExtendedAttackCooldown(ServerPlayer player) {
        Long expiresAt = EXTENDED_ATTACK_COOLDOWN_EXPIRY.get(player);
        if (expiresAt == null || player.serverLevel().getGameTime() < expiresAt
            || player.getAttackStrengthScale(0.0F) < 1.0F) {
            return;
        }

        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed != null) attackSpeed.removeModifier(EXTENDED_ATTACK_COOLDOWN_ID);
        EXTENDED_ATTACK_COOLDOWN_EXPIRY.remove(player);
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

    public static boolean release(net.minecraft.world.item.ItemStack stack, Level level,
                                  LivingEntity user, int timeLeft) {
        if (!(user instanceof ServerPlayer player) || !(level instanceof ServerLevel serverLevel)) return false;

        int elapsed = Math.max(0, GreatswordItem.MAX_USE_TICKS - timeLeft);
        float charge = GreatswordItem.chargeProgress(stack, elapsed);
        if (charge <= 0.10F) return false;

        Vec3 look = player.getLookAngle();
        LivingEntity target = findTarget(serverLevel, player, look);
        Vec3 impact = target != null
            ? new Vec3(target.getX(), target.getY(), target.getZ())
            : player.position().add(look.scale(2.75D));
        double baseDamage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);

        if (target != null) {
            int densityLevel = GreatswordItem.enchantmentLevel(stack, Enchantments.DENSITY);
            float damageMultiplier = (float) chargedDamageMultiplier(charge, densityLevel);
            CHARGED_SWINGS.add(player);
            CHARGED_ATTACKS.put(player, new ChargedAttackContext(target, damageMultiplier));
            try {
                // Let vanilla calculate attack strength and all enchantment damage;
                // LivingDamageEvent.Pre then scales the primary hit's final damage.
                // The custom splash below replaces the normal inner sweep for this
                // release; ordinary left-clicks still use vanilla sweep.
                player.attack(target);
            } finally {
                CHARGED_SWINGS.remove(player);
                CHARGED_ATTACKS.remove(player);
            }
        }

        Vec3 splashCenter = target != null ? target.getBoundingBox().getCenter() : impact.add(0.0D, 0.6D, 0.0D);
        applyChargedSplash(serverLevel, player, target, splashCenter, baseDamage, charge);
        spawnGroundBurst(serverLevel, impact, charge);
        spawnChargedSweep(serverLevel, player, look);
        startExtendedAttackCooldown(player);
        serverLevel.playSound(null, impact.x, impact.y, impact.z, ModSounds.SWORD_IMPACT.get(),
            SoundSource.PLAYERS, 1.0F, 1.0F);
        dash(player, look, charge);
        return true;
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
        double radius = (1.35D + 1.4D * charge) * 1.2D;
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

    private static void spawnChargedSweep(ServerLevel level, ServerPlayer player, Vec3 look) {
        Vec3 position = player.position().add(look.scale(0.75D))
            .add(0.0D, player.getBbHeight() * 0.5D, 0.0D);
        // SweepAttackParticle uses negative x-speed to scale its sprite; -2 doubles it.
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, position.x, position.y, position.z,
            0, -2.0D, 0.0D, 0.0D, 0.0D);
    }

    private static void spawnGroundBurst(ServerLevel level, Vec3 impact, float charge) {
        BlockPos ground = BlockPos.containing(impact.x, impact.y, impact.z).below();
        BlockState groundState = level.getBlockState(ground);
        if (groundState.isAir()) return;

        BlockParticleOption dustPillar = new BlockParticleOption(ParticleTypes.DUST_PILLAR, groundState);
        int count = 50 + Math.round(70.0F * charge);
        double radius = (0.45D + 1.1D * charge) * 2.0D;
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0D * i / count;
            double x = impact.x + Math.cos(angle) * radius;
            double z = impact.z + Math.sin(angle) * radius;
            level.sendParticles(dustPillar, x, ground.getY() + 1.01D, z,
                1, 0.08D, 0.04D, 0.08D, 0.01D);
        }
    }

    private static void startExtendedAttackCooldown(ServerPlayer player) {
        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed != null) attackSpeed.removeModifier(EXTENDED_ATTACK_COOLDOWN_ID);

        float baseCooldownTicks = player.getCurrentItemAttackStrengthDelay();
        long extendedCooldownTicks = Math.max(1L, (long) Math.ceil(baseCooldownTicks * 1.5D));
        player.resetAttackStrengthTicker();
        if (attackSpeed == null) return;

        // Multiplying attack speed by 2/3 makes vanilla's next-attack timer 1.5x longer.
        attackSpeed.addTransientModifier(new AttributeModifier(EXTENDED_ATTACK_COOLDOWN_ID,
            -1.0D / 3.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        EXTENDED_ATTACK_COOLDOWN_EXPIRY.put(player,
            player.serverLevel().getGameTime() + extendedCooldownTicks);
    }

    private static void dash(ServerPlayer player, Vec3 look, float charge) {
        Vec3 impulse = look.scale(DASH_IMPULSE * charge);
        // Add the directional impulse to existing velocity, matching the
        // additive, server-synchronized microstep movement pattern.
        player.setDeltaMovement(player.getDeltaMovement().add(impulse));
        player.hurtMarked = true;
    }
}
