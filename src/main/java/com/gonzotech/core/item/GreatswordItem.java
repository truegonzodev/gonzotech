package com.gonzotech.core.item;

import com.gonzotech.core.component.AlloyComposition;
import com.gonzotech.core.component.AlloyTint;
import com.gonzotech.core.registry.GreatswordContent;
import com.gonzotech.core.registry.ModDataComponents;
import com.gonzotech.core.registry.ModSounds;
import com.gonzotech.machines.processing.AlloyProperties;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Shared identity and use behavior for the large swords. The constructor's
 * damage and speed arguments are the final player-facing attribute values.
 */
public final class GreatswordItem extends SwordItem {

    public static final int FULL_CHARGE_TICKS = 15 * 20;
    static final int MAX_USE_TICKS = 72_000;
    private static final int INERTNESS_ENCHANTMENT_LOCK = 50;
    private static final int LAVA_RESISTANCE_THRESHOLD = 70;
    private static final Map<LivingEntity, Integer> CHARGE_READY_SOUND_STAGE = new WeakHashMap<>();

    public GreatswordItem(ToolMaterial hostMaterial, int durability, float attackDamage,
                          float attackSpeed, boolean compositionRepairOnly, Item.Properties properties) {
        super(swordProperties(hostMaterial, durability, attackDamage, attackSpeed,
            compositionRepairOnly, properties));
    }

    private static Item.Properties swordProperties(ToolMaterial host, int durability,
                                                    float attackDamage, float attackSpeed,
                                                    boolean compositionRepairOnly,
                                                    Item.Properties properties) {
        // ToolMaterial.applySwordProperties supplies the vanilla sword tool rules,
        // enchantability, and repair tag. Use a copy with the authored durability.
        ToolMaterial material = new ToolMaterial(host.incorrectBlocksForDrops(), durability,
            host.speed(), host.attackDamageBonus(), host.enchantmentValue(), host.repairItems());
        Item.Properties configured = material.applySwordProperties(properties,
            attackDamage - 1.0F - host.attackDamageBonus(), attackSpeed - 4.0F);
        return compositionRepairOnly ? configured.setNoCombineRepair() : configured;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        // Vanilla bow-draw pose gives the raised, forward-held charge animation.
        return ItemUseAnimation.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        // Keep using after the bar fills; releasing the button, not the timer,
        // launches the charged attack.
        return MAX_USE_TICKS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level.isClientSide()) CHARGE_READY_SOUND_STAGE.put(player, 0);
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remainingUseDuration) {
        if (!level.isClientSide()) return;

        int elapsed = Math.max(0, MAX_USE_TICKS - remainingUseDuration);
        int stage = elapsed >= FULL_CHARGE_TICKS ? 2 : elapsed >= FULL_CHARGE_TICKS / 10 ? 1 : 0;
        int previousStage = CHARGE_READY_SOUND_STAGE.getOrDefault(user, 0);
        if (stage <= previousStage) return;

        CHARGE_READY_SOUND_STAGE.put(user, stage);
        float pitch = stage == 1 ? 1.5F : 1.0F;
        level.playLocalSound(user.getX(), user.getY(), user.getZ(), ModSounds.SWORD_READY.get(),
            SoundSource.PLAYERS, 1.0F, pitch, false);
    }

    @Override
    public boolean canEquip(ItemStack stack, EquipmentSlot slot, LivingEntity entity) {
        return slot != EquipmentSlot.OFFHAND && super.canEquip(stack, slot, entity);
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int timeLeft) {
        if (level.isClientSide()) {
            CHARGE_READY_SOUND_STAGE.remove(user);
            int elapsed = Math.max(0, MAX_USE_TICKS - timeLeft);
            if (elapsed > FULL_CHARGE_TICKS / 10 && user instanceof Player player) {
                player.resetAttackStrengthTicker();
            }
        }
        return GreatswordCombat.release(stack, level, user, timeLeft);
    }

    /** Progress used by the client charge indicator; clamped after 15 seconds. */
    public static float chargeProgress(Player player) {
        int elapsed = MAX_USE_TICKS - player.getUseItemRemainingTicks();
        return Math.max(0.0F, Math.min(1.0F, elapsed / (float) FULL_CHARGE_TICKS));
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return !isSharpness(enchantment) && super.supportsEnchantment(stack, enchantment);
    }

    @Override
    public boolean isPrimaryItemFor(ItemStack stack, Holder<Enchantment> enchantment) {
        return !isSharpness(enchantment) && super.isPrimaryItemFor(stack, enchantment);
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        ItemEnchantments stored = book.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored != null) {
            for (Holder<Enchantment> enchantment : stored.keySet()) {
                if (isSharpness(enchantment)) return false;
            }
        }
        return super.isBookEnchantable(stack, book);
    }

    private static boolean isSharpness(Holder<Enchantment> enchantment) {
        return enchantment.is(Enchantments.SHARPNESS);
    }

    /** Stamp one exact, validated custom-alloy composition onto the recipe output. */
    public static ItemStack createAlloyStack(AlloyComposition composition) {
        if (!AlloyEquipmentStats.isSupported(composition)) return ItemStack.EMPTY;

        AlloyProperties properties = AlloyProperties.from(composition).orElseThrow();
        ItemStack result = new ItemStack(GreatswordContent.ALLOY_GREATSWORD.get());
        result.set(ModDataComponents.ALLOY_COMPOSITION.get(), composition);
        result.set(ModDataComponents.ALLOY_TINT.get(), new AlloyTint(properties.argbTint()));
        result.set(DataComponents.MAX_DAMAGE,
            AlloyEquipmentStats.durability(properties, 3, 1_259));
        result.set(DataComponents.DAMAGE, 0);
        // The base item is marked no-combine-repair. Keep the repair path
        // composition-specific rather than accepting a generic host ingot.
        result.remove(DataComponents.REPAIRABLE);

        double weightDamage = 9.0D + 12.3D * properties.weight() / 100.0D;
        double brittlenessBonus = -1.0D + 2.0D * properties.brittleness() / 100.0D;
        double damage = weightDamage + brittlenessBonus;
        double attackSpeed = 0.9D - 0.8D * properties.weight() / 100.0D;
        ItemAttributeModifiers hostAttributes = result.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (hostAttributes == null) hostAttributes = ItemAttributeModifiers.EMPTY;
        // Adjust the fixed iron-host modifiers so the tooltip uses the same
        // total-stat and base-plus-modifier layout as alloy tools and swords.
        result.set(DataComponents.ATTRIBUTE_MODIFIERS, AlloyEquipmentStats.adjustToolAttributes(
            hostAttributes,
            damage - GreatswordContent.ALLOY_BASE_DAMAGE,
            attackSpeed - GreatswordContent.ALLOY_BASE_ATTACK_SPEED));

        // U (the dominant material tier) is deliberately irrelevant. Low-I stacks
        // retain the alloy item's fixed iron-host enchantability; high-I stacks lose it.
        if (properties.inertness() >= INERTNESS_ENCHANTMENT_LOCK) {
            result.remove(DataComponents.ENCHANTABLE);
        }
        if (properties.heatResistance() >= LAVA_RESISTANCE_THRESHOLD) {
            result.set(DataComponents.DAMAGE_RESISTANT, new DamageResistant(DamageTypeTags.IS_FIRE));
        } else {
            result.remove(DataComponents.DAMAGE_RESISTANT);
        }
        return result;
    }
}
