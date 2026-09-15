package com.gonzotech.core.item;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Identity of a custom-alloy armor piece (helmet / leggings / boots). Like
 * the chestplate, the vanilla dyeable leather equipment layer gives the
 * generated per-stack alloy tint its worn appearance, and the slot-specific
 * stats are stamped by {@link AlloyEquipmentStats} from the exact alloy
 * composition at crafting time.
 */
public final class AlloyArmorItem extends ArmorItem {

    public AlloyArmorItem(ArmorType type, Item.Properties properties) {
        super(ArmorMaterials.LEATHER, type, properties.setNoCombineRepair());
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
