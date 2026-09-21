package com.gonzotech.mixin.client;

import com.gonzotech.radiation.client.RadReequipGate;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Подавление re-equip bob'а для «тиков радиации» (баг автора 20.09):
 * каждую секунду обновляется NBT-штамп {@code gonzo_rad} → ваниль видит
 * «другой» стак и проигрывает анимацию смены предмета (прыжок снизу вверх).
 * {@code shouldInstantlyReplaceVisibleItem} зовёт {@code ItemStack.matches} —
 * если стаки отличаются ТОЛЬКО нашим тегом, считаем их «совпадающими»:
 * предмет заменяется мгновенно, без прыжка. Всё остальное в поведении —
 * ванильное (оригинальная проверка сохранена).
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @WrapOperation(method = "shouldInstantlyReplaceVisibleItem",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;matches(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean gonzo$radTickNoReequip(ItemStack from, ItemStack to, Operation<Boolean> original) {
        return original.call(from, to) || RadReequipGate.onlyRadChanged(from, to);
    }
}
