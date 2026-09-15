package com.gonzotech.core.item;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Composition-configured alloy chestplate identity. Leather's dyeable vanilla
 * equipment layer gives the generated per-stack alloy tint a worn appearance.
 */
public final class AlloyChestplateItem extends ArmorItem {

    public AlloyChestplateItem(Item.Properties properties) {
        super(ArmorMaterials.LEATHER, ArmorType.CHESTPLATE, properties.setNoCombineRepair());
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return AlloyEquipmentStats.allowsEnchantments(stack) && super.isBookEnchantable(stack, book);
    }

    @Override
    public boolean isPrimaryItemFor(ItemStack stack, Holder<Enchantment> enchantment) {
        return AlloyEquipmentStats.allowsEnchantments(stack) && super.isPrimaryItemFor(stack, enchantment);
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return AlloyEquipmentStats.allowsEnchantments(stack) && super.supportsEnchantment(stack, enchantment);
    }
}
