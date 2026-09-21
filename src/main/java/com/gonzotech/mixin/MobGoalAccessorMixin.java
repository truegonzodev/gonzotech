package com.gonzotech.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor к {@code Mob#goalSelector} (protected final): @Shadow в миксинах
 * подклассов (Cat/Wolf) не находит поля родителя («@Shadow field goalSelector
 * was not located in the target class Cat» — игра 2026-09-19). Канонический
 * обход — accessor на том классе, где поле объявлено.
 */
@Mixin(Mob.class)
public abstract class MobGoalAccessorMixin {

    // public обязателен: с protected вызов из Cat по ссылке типа Mob режется
    // верификатором («Bad access to protected data in invokevirtual» — protected
    // член суперкласса доступен подклассу только через ссылку типа подкласса).
    @Accessor("goalSelector")
    public abstract GoalSelector gonzotech$getGoalSelector();
}
