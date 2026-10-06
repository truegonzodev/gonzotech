package com.gonzotech.core.item;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.component.AlloyComposition;
import com.gonzotech.core.component.AlloyTint;
import com.gonzotech.core.registry.GreatswordContent;
import com.gonzotech.core.registry.ModDataComponents;
import com.gonzotech.machines.processing.AlloyMaterialCatalog;
import com.gonzotech.machines.processing.AlloyProperties;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.level.Level;

/**
 * Shared identity and use behavior for the large swords. The constructor's
 * damage and speed arguments are the final player-facing attribute values.
 */
public final class GreatswordItem extends SwordItem {

    public static final int FULL_CHARGE_TICKS = 15 * 20;
    static final int MAX_USE_TICKS = 72_000;
    private static final int INERTNESS_ENCHANTMENT_LOCK = 50;
    private static final int LAVA_RESISTANCE_THRESHOLD = 70;

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
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(player.getItemInHand(hand));
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int timeLeft) {
        GreatswordCombat.release(stack, level, user, timeLeft);
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
        result.set(DataComponents.MAX_DAMAGE, AlloyEquipmentStats.durability(properties));
        result.set(DataComponents.DAMAGE, 0);
        // The base item is marked no-combine-repair. Keep the repair path
        // composition-specific rather than accepting a generic host ingot.
        result.remove(DataComponents.REPAIRABLE);
        result.set(DataComponents.ATTRIBUTE_MODIFIERS, alloyAttributes(properties));

        if (properties.inertness() >= INERTNESS_ENCHANTMENT_LOCK) {
            result.remove(DataComponents.ENCHANTABLE);
        } else {
            Enchantable hostEnchantability = new ItemStack(hostSword(properties.toolTier()))
                .get(DataComponents.ENCHANTABLE);
            if (hostEnchantability != null) result.set(DataComponents.ENCHANTABLE, hostEnchantability);
        }
        if (properties.heatResistance() >= LAVA_RESISTANCE_THRESHOLD) {
            result.set(DataComponents.DAMAGE_RESISTANT, new DamageResistant(DamageTypeTags.IS_FIRE));
        } else {
            result.remove(DataComponents.DAMAGE_RESISTANT);
        }
        return result;
    }

    private static ItemAttributeModifiers alloyAttributes(AlloyProperties properties) {
        double damage = switch (properties.toolTier()) {
            case STONE -> 11.0D;
            case IRON -> 13.0D;
            case DIAMOND -> 15.0D;
            case NETHERITE_PLUS -> 17.0D;
        } + 0.5D * AlloyEquipmentStats.brittlenessToolBonus(properties.brittleness());
        double baseSpeed = switch (properties.toolTier()) {
            case STONE -> 0.35D;
            case IRON -> 0.32D;
            case DIAMOND -> 0.44D;
            case NETHERITE_PLUS -> 0.38D;
        };
        // M is deliberately gentler on a greatsword than on the ordinary alloy
        // sword: it shifts the slow base by at most ±0.275 attack speed.
        double speed = clamp(baseSpeed + 0.55D * (50.0D - properties.weight()) / 100.0D,
            0.10D, 0.80D);

        return ItemAttributeModifiers.builder()
            .add(Attributes.ATTACK_DAMAGE,
                modifier("alloy_greatsword_damage", damage - 1.0D), EquipmentSlotGroup.MAINHAND)
            .add(Attributes.ATTACK_SPEED,
                modifier("alloy_greatsword_speed", speed - 4.0D), EquipmentSlotGroup.MAINHAND)
            .build();
    }

    private static AttributeModifier modifier(String path, double amount) {
        return new AttributeModifier(ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, path),
            amount, AttributeModifier.Operation.ADD_VALUE);
    }

    private static Item hostSword(AlloyMaterialCatalog.ToolTier tier) {
        return switch (tier) {
            case STONE -> Items.STONE_SWORD;
            case IRON -> Items.IRON_SWORD;
            case DIAMOND -> Items.DIAMOND_SWORD;
            case NETHERITE_PLUS -> Items.NETHERITE_SWORD;
        };
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
