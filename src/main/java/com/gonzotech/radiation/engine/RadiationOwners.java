package com.gonzotech.radiation.engine;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Sidecar lives in the owner's save, never in an ItemStack codec or component. */
public final class RadiationOwners {
    public static final String KEY = "GonzoItemRadiationV2";
    private RadiationOwners() {}
    public static List<ItemStack> slots(Object owner) {
        List<ItemStack> out = new ArrayList<>();
        if (owner instanceof Player player) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) out.add(player.getInventory().getItem(i));
            for (int i = 0; i < player.getEnderChestInventory().getContainerSize(); i++) out.add(player.getEnderChestInventory().getItem(i));
        } else if (owner instanceof com.gonzotech.swag.PetBowlBlockEntity bowl) {
            out.add(bowl.peekFood());
        } else if (owner instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) out.add(container.getItem(i));
        } else if (owner instanceof ItemEntity item) out.add(item.getItem());
        else if (owner instanceof ItemFrame frame) out.add(frame.getItem());
        else if (owner instanceof LivingEntity living) {
            for (EquipmentSlot slot : EquipmentSlot.values()) out.add(living.getItemBySlot(slot));
        }
        return out;
    }
    public static void save(Object owner, CompoundTag tag) {
        if (owner instanceof Entity entity && entity.level().isClientSide()) return;
        if (owner instanceof net.minecraft.world.level.block.entity.BlockEntity be && be.getLevel() != null && be.getLevel().isClientSide()) return;
        ListTag data = saveList(slots(owner), 0);
        if (!data.isEmpty()) tag.put(KEY, data); else tag.remove(KEY);
    }
    public static void load(Object owner, CompoundTag tag) {
        if (owner instanceof Entity entity && entity.level().isClientSide()) return;
        if (owner instanceof net.minecraft.world.level.block.entity.BlockEntity be && be.getLevel() != null && be.getLevel().isClientSide()) return;
        loadList(slots(owner), tag.getList(KEY, Tag.TAG_COMPOUND), 0);
    }
    private static List<ItemStack> children(ItemStack stack) {
        List<ItemStack> out = new ArrayList<>();
        var container = stack.get(DataComponents.CONTAINER);
        // Include empty slots so persistence uses stable slot addresses. Accessor
        // only exposes references for external bookkeeping; items remain immutable.
        if ((Object) container instanceof ContainerContentsAccess access) out.addAll(access.gonzo$radiationItems());
        var bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) bundle.items().forEach(out::add);
        return out;
    }
    private static ListTag saveList(List<ItemStack> stacks, int depth) {
        ListTag out = new ListTag();
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (stack.isEmpty()) continue;
            ExternalRadiation.Dose dose = ExternalRadiation.read(stack);
            var cargo = ExternalRadiation.readContents(stack);
            ListTag nested = depth < 4 ? saveList(children(stack), depth + 1) : new ListTag();
            if (dose.rate() == 0 && cargo.rate() == 0 && nested.isEmpty()) continue;
            CompoundTag entry = new CompoundTag();
            entry.putInt("Slot", i);
            entry.putString("Item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            entry.putInt("Count", stack.getCount());
            entry.putDouble("Rate", dose.rate());
            entry.putLong("Tick", dose.tick());
            if (cargo.rate() > 0) { entry.putDouble("CargoRate", cargo.rate()); entry.putLong("CargoTick", cargo.tick()); }
            if (!nested.isEmpty()) entry.put("Contents", nested);
            out.add(entry);
        }
        return out;
    }
    private static void loadList(List<ItemStack> stacks, ListTag data, int depth) {
        for (int i = 0; i < data.size(); i++) {
            CompoundTag entry = data.getCompound(i);
            int slot = entry.getInt("Slot");
            if (slot < 0 || slot >= stacks.size()) continue;
            ItemStack stack = stacks.get(slot);
            if (stack.isEmpty() || stack.getCount() != entry.getInt("Count")
                    || !BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(entry.getString("Item"))) continue;
            ExternalRadiation.write(stack, entry.getDouble("Rate"), entry.getLong("Tick"));
            ExternalRadiation.writeContents(stack, new ExternalRadiation.Dose(entry.getDouble("CargoRate"), entry.getLong("CargoTick")));
            if (depth < 4) loadList(children(stack), entry.getList("Contents", Tag.TAG_COMPOUND), depth + 1);
        }
    }
}
