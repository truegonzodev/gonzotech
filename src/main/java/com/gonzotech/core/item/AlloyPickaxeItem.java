package com.gonzotech.core.item;

import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * The item identity for a composition-configured alloy pickaxe. Its actual
 * material components are replaced on each crafted stack by AlloyEquipmentStats.
 */
public final class AlloyPickaxeItem extends PickaxeItem {

    public AlloyPickaxeItem(Item.Properties properties) {
        super(ToolMaterial.STONE, 1.0F, -2.8F, properties.setNoCombineRepair());
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
