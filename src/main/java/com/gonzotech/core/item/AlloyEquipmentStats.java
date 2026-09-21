package com.gonzotech.core.item;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.component.AlloyComposition;
import com.gonzotech.core.component.AlloyTint;
import com.gonzotech.core.registry.ModDataComponents;
import com.gonzotech.core.registry.ModItems;
import com.gonzotech.machines.processing.AlloyMaterialCatalog;
import com.gonzotech.machines.processing.AlloyProperties;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Server/client-identical conversion of an authored alloy composition into a
 * finished piece of equipment. The values are written to ordinary vanilla data
 * components on the crafted stack, so mining, combat, armor, enchantability,
 * durability, and dropped-item lava resistance work without a ticking handler.
 */
public final class AlloyEquipmentStats {

    private static final int MIN_DURABILITY = 11;
    private static final int MAX_DURABILITY = 4_059;
    private static final int BRITTLENESS_NO_PENALTY = 17;
    private static final int BRITTLENESS_MAX_PENALTY = 90;
    private static final double BRITTLENESS_MAX_PENALTY_FRACTION = 0.75D;
    private static final int INERTNESS_ENCHANTMENT_LOCK = 50;
    private static final int LAVA_RESISTANCE_THRESHOLD = 70;
    private static final double CHESTPLATE_ARMOR_MULTIPLIER = 1.7D;

    /** Blocks reserved for the explicit level-5 netherite-plus alloy pickaxe. */
    private static final TagKey<Block> NEEDS_NETHERITE_PLUS_TOOL = TagKey.create(Registries.BLOCK,
        ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "needs_netherite_plus_tool"));

    private AlloyEquipmentStats() {
    }

    /** The initial alloy equipment forms and their ordinary crafting patterns. */
    public enum Kind {
        PICKAXE(new String[] { "AAA", " S ", " S " }),
        SWORD(new String[] { "A", "A", "S" }),
        CHESTPLATE(new String[] { "A A", "AAA", "AAA" }),
        HELMET(new String[] { "AAA", "A A" }),
        LEGGINGS(new String[] { "AAA", "A A", "A A" }),
        BOOTS(new String[] { "A A", "A A" });

        private final String[] pattern;

        Kind(String[] pattern) {
            this.pattern = pattern;
        }

        public String[] pattern() {
            return pattern;
        }

        /** True for the four armor pieces stamped from one alloy composition. */
        public boolean isArmor() {
            return this == CHESTPLATE || this == HELMET || this == LEGGINGS || this == BOOTS;
        }

        /**
         * Multiplier of every armor stat (armor, toughness, knockback
         * resistance, movement speed) and of durability, relative to the
         * chestplate: chest 1.0, leggings 0.7, helmet 0.5, boots 0.4.
         */
        public double armorStatMultiplier() {
            return switch (this) {
                case CHESTPLATE -> 1.0D;
                case LEGGINGS -> 0.7D;
                case HELMET -> 0.5D;
                case BOOTS -> 0.4D;
                default -> 1.0D;
            };
        }

        /** The equipment slot group the piece's modifiers apply to while worn. */
        public EquipmentSlotGroup armorSlotGroup() {
            return switch (this) {
                case CHESTPLATE -> EquipmentSlotGroup.CHEST;
                case LEGGINGS -> EquipmentSlotGroup.LEGS;
                case HELMET -> EquipmentSlotGroup.HEAD;
                case BOOTS -> EquipmentSlotGroup.FEET;
                default -> throw new IllegalArgumentException(this + " is not armor");
            };
        }

        public Item outputItem() {
            return switch (this) {
                case PICKAXE -> ModItems.ALLOY_PICKAXE.get();
                case SWORD -> ModItems.ALLOY_SWORD.get();
                case CHESTPLATE -> ModItems.ALLOY_CHESTPLATE.get();
                case HELMET -> ModItems.ALLOY_HELMET.get();
                case LEGGINGS -> ModItems.ALLOY_LEGGINGS.get();
                case BOOTS -> ModItems.ALLOY_BOOTS.get();
            };
        }
    }

    /**
     * True only for a complete composition made of materials known to the alloy
     * catalog. It prevents manually malformed component data from becoming gear.
     */
    public static boolean isSupported(AlloyComposition composition) {
        return composition != null
            && composition.parts().keySet().stream().allMatch(id -> AlloyMaterialCatalog.material(id) != null)
            && AlloyProperties.from(composition).isPresent();
    }

    /**
     * Enchantment tags describe which item forms accept each enchantment, while
     * this component makes that tag eligibility composition-dependent.
     */
    public static boolean allowsEnchantments(ItemStack stack) {
        return stack.has(DataComponents.ENCHANTABLE);
    }

    /** Creates a fresh configured tool or armor stack from a valid alloy composition. */
    public static ItemStack create(Kind kind, AlloyComposition composition) {
        if (!isSupported(composition)) return ItemStack.EMPTY;
        AlloyProperties properties = AlloyProperties.from(composition).orElseThrow();
        ItemStack result = new ItemStack(kind.outputItem());

        result.set(ModDataComponents.ALLOY_COMPOSITION.get(), composition);
        result.set(ModDataComponents.ALLOY_TINT.get(), new AlloyTint(properties.argbTint()));
        int baseDurability = durability(properties);
        // Armor pieces scale durability by the same slot multiplier as their
        // protective stats; tools keep the exact strength-based value.
        result.set(DataComponents.MAX_DAMAGE, kind.isArmor()
            ? Math.max(1, (int) Math.round(baseDurability * kind.armorStatMultiplier()))
            : baseDurability);
        result.set(DataComponents.DAMAGE, 0);
        // These items are created from a stack-specific alloy. Static vanilla repair
        // tags would allow unrelated host materials to repair them, so leave future
        // alloy-aware repair mechanics as a separate feature.
        result.remove(DataComponents.REPAIRABLE);

        if (kind.isArmor()) {
            configureArmor(result, kind, properties);
        } else {
            configureTool(result, kind, properties);
        }
        applyEnchantmentAndHeatRules(result, properties);
        return result;
    }

    /** Recalculates the durability displayed by S after its effective B penalty. */
    public static int durability(AlloyProperties properties) {
        int strengthDurability = lerpInt(MIN_DURABILITY, MAX_DURABILITY, properties.strength());
        return Math.max(1, (int) Math.round(strengthDurability * (1.0D - brittlenessPenalty(properties.brittleness()))));
    }

    /** B is already plasticity-adjusted by {@link AlloyProperties}. */
    public static double brittlenessPenalty(int effectiveBrittleness) {
        if (effectiveBrittleness <= BRITTLENESS_NO_PENALTY) return 0.0D;
        if (effectiveBrittleness >= BRITTLENESS_MAX_PENALTY) return BRITTLENESS_MAX_PENALTY_FRACTION;
        return (effectiveBrittleness - BRITTLENESS_NO_PENALTY)
            * BRITTLENESS_MAX_PENALTY_FRACTION
            / (BRITTLENESS_MAX_PENALTY - BRITTLENESS_NO_PENALTY);
    }

    /** B contributes the requested -0.9 to +5.4 bonus to damage and mining speed. */
    public static double brittlenessToolBonus(int effectiveBrittleness) {
        return -0.9D + 6.3D * clampPercent(effectiveBrittleness) / 100.0D;
    }

    /** Weight's piecewise attack-speed modifier: +2.3 at 0, 0 at 50, -0.4 at 100. */
    public static double weightAttackSpeedBonus(int weight) {
        int value = clampPercent(weight);
        return value <= 50
            ? lerp(2.3D, 0.0D, value / 50.0D)
            : lerp(0.0D, -0.4D, (value - 50) / 50.0D);
    }

    /** Weight's specified four-segment mining-speed modifier. */
    public static double weightMiningSpeedBonus(int weight) {
        int value = clampPercent(weight);
        if (value <= 20) return lerp(-1.0D, 0.0D, value / 20.0D);
        if (value <= 40) return lerp(0.0D, 1.0D, (value - 20) / 20.0D);
        if (value <= 60) return lerp(1.0D, 1.5D, (value - 40) / 20.0D);
        return lerp(1.5D, -2.0D, (value - 60) / 40.0D);
    }

    /** S becomes 1.0–5.0 base protection; the current chestplate form is ×1.7. */
    public static double chestplateArmor(AlloyProperties properties) {
        return (1.0D + 4.0D * clampPercent(properties.strength()) / 100.0D) * CHESTPLATE_ARMOR_MULTIPLIER;
    }

    /**
     * The chestplate's vanilla-style toughness follows its material host: iron
     * 0.5, diamond 1.8, and the custom level-5 netherite-plus host 2.5.
     */
    public static double chestplateArmorToughness(AlloyProperties properties) {
        return switch (properties.toolTier()) {
            case STONE -> 0.0D;
            case IRON -> 0.5D;
            case DIAMOND -> 1.8D;
            case NETHERITE_PLUS -> 2.5D;
        };
    }

    /** M becomes 0–80% knockback resistance and 0 to -15% total movement speed. */
    public static double armorKnockbackResistance(AlloyProperties properties) {
        return 0.8D * clampPercent(properties.weight()) / 100.0D;
    }

    public static double armorMovementSpeedModifier(AlloyProperties properties) {
        return -0.15D * clampPercent(properties.weight()) / 100.0D;
    }

    private static void configureTool(ItemStack result, Kind kind, AlloyProperties properties) {
        ItemStack host = new ItemStack(hostItem(kind, properties.toolTier()));
        Tool hostTool = host.get(DataComponents.TOOL);
        if (hostTool != null) {
            double miningBonus = brittlenessToolBonus(properties.brittleness()) + weightMiningSpeedBonus(properties.weight());
            result.set(DataComponents.TOOL, adjustToolMiningSpeed(hostTool, kind, properties.toolTier(), miningBonus));
        }

        ItemAttributeModifiers hostAttributes = host.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (hostAttributes == null) hostAttributes = ItemAttributeModifiers.EMPTY;
        // Fold dynamic values into the host modifiers rather than append separate
        // entries. The normal vanilla green damage/speed rows then remain the only
        // combat-stat rows in the tooltip.
        result.set(DataComponents.ATTRIBUTE_MODIFIERS, adjustToolAttributes(hostAttributes,
            brittlenessToolBonus(properties.brittleness()), weightAttackSpeedBonus(properties.weight())));

        if (properties.inertness() < INERTNESS_ENCHANTMENT_LOCK) {
            Enchantable hostEnchantability = host.get(DataComponents.ENCHANTABLE);
            if (hostEnchantability != null) result.set(DataComponents.ENCHANTABLE, hostEnchantability);
            else result.remove(DataComponents.ENCHANTABLE);
        }
    }

    /**
     * Stamps the slot-specific protective stats of one armor piece: the same
     * alloy formulas as the chestplate, multiplied by the slot factor
     * ({@link Kind#armorStatMultiplier()}) and bound to the piece's equipment
     * slot.
     */
    private static void configureArmor(ItemStack result, Kind kind, AlloyProperties properties) {
        EquipmentSlotGroup slot = kind.armorSlotGroup();
        double multiplier = kind.armorStatMultiplier();
        String prefix = "alloy_" + kind.name().toLowerCase() + "_";
        ItemAttributeModifiers.Builder attributes = ItemAttributeModifiers.builder()
            .add(Attributes.ARMOR,
                modifier(prefix + "armor", chestplateArmor(properties) * multiplier, AttributeModifier.Operation.ADD_VALUE),
                slot)
            .add(Attributes.KNOCKBACK_RESISTANCE,
                modifier(prefix + "knockback_resistance", armorKnockbackResistance(properties) * multiplier,
                    AttributeModifier.Operation.ADD_VALUE),
                slot);
        double armorToughness = chestplateArmorToughness(properties) * multiplier;
        if (armorToughness > 0.0D) {
            attributes.add(Attributes.ARMOR_TOUGHNESS,
                modifier(prefix + "armor_toughness", armorToughness, AttributeModifier.Operation.ADD_VALUE),
                slot);
        }
        double movementModifier = armorMovementSpeedModifier(properties) * multiplier;
        if (movementModifier != 0.0D) {
            attributes.add(Attributes.MOVEMENT_SPEED,
                modifier(prefix + "movement_speed", movementModifier,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
                slot);
        }
        result.set(DataComponents.ATTRIBUTE_MODIFIERS, attributes.build());
        // Надетая броня рендерится по equipment-ассету (AlloyArmorMaterials):
        // листы в textures/entity/equipment/humanoid{,_leggings}/custom_alloy.png.
        // DYED_COLOR хранится как запасной источник цвета — тот же тинт сплава.
        AlloyTint tint = result.get(ModDataComponents.ALLOY_TINT.get());
        result.set(DataComponents.DYED_COLOR, new DyedItemColor(tint.argb() & 0x00FFFFFF, false));
        if (properties.inertness() < INERTNESS_ENCHANTMENT_LOCK) {
            Enchantable leatherEnchantability = new ItemStack(armorHostItem(kind)).get(DataComponents.ENCHANTABLE);
            if (leatherEnchantability != null) result.set(DataComponents.ENCHANTABLE, leatherEnchantability);
        }
    }

    private static void applyEnchantmentAndHeatRules(ItemStack result, AlloyProperties properties) {
        if (properties.inertness() >= INERTNESS_ENCHANTMENT_LOCK) {
            // Absence of ENCHANTABLE makes the stack unavailable to the enchanting
            // table and the normal anvil-book path.
            result.remove(DataComponents.ENCHANTABLE);
        }
        if (properties.heatResistance() >= LAVA_RESISTANCE_THRESHOLD) {
            // In 1.21.4, the former FIRE_RESISTANT flag is DAMAGE_RESISTANT
            // scoped to the vanilla fire/lava damage-type tag.
            result.set(DataComponents.DAMAGE_RESISTANT, new DamageResistant(DamageTypeTags.IS_FIRE));
        } else {
            result.remove(DataComponents.DAMAGE_RESISTANT);
        }
    }

    private static ItemAttributeModifiers adjustToolAttributes(ItemAttributeModifiers hostAttributes,
                                                               double damageBonus, double attackSpeedBonus) {
        ItemAttributeModifiers.Builder result = ItemAttributeModifiers.builder();
        for (ItemAttributeModifiers.Entry entry : hostAttributes.modifiers()) {
            AttributeModifier modifier = entry.modifier();
            if (entry.slot() == EquipmentSlotGroup.MAINHAND
                && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                if (entry.attribute().equals(Attributes.ATTACK_DAMAGE)) {
                    modifier = new AttributeModifier(modifier.id(), modifier.amount() + damageBonus, modifier.operation());
                } else if (entry.attribute().equals(Attributes.ATTACK_SPEED)) {
                    modifier = new AttributeModifier(modifier.id(), modifier.amount() + attackSpeedBonus,
                        modifier.operation());
                }
            }
            result.add(entry.attribute(), modifier, entry.slot());
        }
        return result.build().withTooltip(hostAttributes.showInTooltip());
    }

    private static Tool adjustToolMiningSpeed(Tool hostTool, Kind kind, AlloyMaterialCatalog.ToolTier tier,
                                               double speedBonus) {
        List<Tool.Rule> adjustedRules = new ArrayList<>(hostTool.rules().size() + 1);
        if (kind == Kind.PICKAXE && tier == AlloyMaterialCatalog.ToolTier.NETHERITE_PLUS) {
            // The higher-priority positive rule overrides the netherite host's
            // negative rule for the level-5-only block tag. It is a named tag so
            // future level-5 blocks can be added through data without new code.
            BuiltInRegistries.BLOCK.get(NEEDS_NETHERITE_PLUS_TOOL).ifPresent(blocks ->
                adjustedRules.add(Tool.Rule.minesAndDrops(blocks, topMiningSpeed(hostTool)))
            );
        }
        for (Tool.Rule rule : hostTool.rules()) {
            Optional<Float> adjustedSpeed = rule.speed().map(speed -> Math.max(0.1F, (float) (speed + speedBonus)));
            adjustedRules.add(new Tool.Rule(rule.blocks(), adjustedSpeed, rule.correctForDrops()));
        }
        // The default speed governs every block not covered by a vanilla host-tool
        // rule. Keep it unmodified so B/M only affect the pickaxe/sword's intended
        // material categories (stone ores for a pickaxe, cobwebs for a sword, etc.).
        return new Tool(adjustedRules, hostTool.defaultMiningSpeed(), hostTool.damagePerBlock());
    }

    /**
     * Use the host pickaxe's ordinary fastest rule for level-5 blocks. Dynamic
     * B/M speed remains confined to pre-existing host rules, never this added
     * level rule or {@link Tool#defaultMiningSpeed()}.
     */
    private static float topMiningSpeed(Tool tool) {
        float speed = tool.defaultMiningSpeed();
        for (Tool.Rule rule : tool.rules()) {
            if (rule.speed().isPresent()) speed = Math.max(speed, rule.speed().get());
        }
        return speed;
    }

    private static Item hostItem(Kind kind, AlloyMaterialCatalog.ToolTier tier) {
        return switch (kind) {
            case PICKAXE -> switch (tier) {
                case STONE -> Items.STONE_PICKAXE;
                case IRON -> Items.IRON_PICKAXE;
                case DIAMOND -> Items.DIAMOND_PICKAXE;
                case NETHERITE_PLUS -> Items.NETHERITE_PICKAXE;
            };
            case SWORD -> switch (tier) {
                case STONE -> Items.STONE_SWORD;
                case IRON -> Items.IRON_SWORD;
                case DIAMOND -> Items.DIAMOND_SWORD;
                case NETHERITE_PLUS -> Items.NETHERITE_SWORD;
            };
            case CHESTPLATE, HELMET, LEGGINGS, BOOTS ->
                throw new IllegalArgumentException("Armor pieces have no mining host");
        };
    }

    /** Vanilla leather armor host providing the slot's enchantability rules. */
    private static Item armorHostItem(Kind kind) {
        return switch (kind) {
            case CHESTPLATE -> Items.LEATHER_CHESTPLATE;
            case HELMET -> Items.LEATHER_HELMET;
            case LEGGINGS -> Items.LEATHER_LEGGINGS;
            case BOOTS -> Items.LEATHER_BOOTS;
            default -> throw new IllegalArgumentException(kind + " is not armor");
        };
    }

    private static AttributeModifier modifier(String path, double amount, AttributeModifier.Operation operation) {
        return new AttributeModifier(ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, path), amount, operation);
    }

    private static int lerpInt(int minimum, int maximum, int percent) {
        return (int) Math.round(lerp(minimum, maximum, clampPercent(percent) / 100.0D));
    }

    private static double lerp(double from, double to, double progress) {
        return from + (to - from) * progress;
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
