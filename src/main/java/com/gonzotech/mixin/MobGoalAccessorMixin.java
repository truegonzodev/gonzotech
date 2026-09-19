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

    @Accessor("goalSelector")
    protected abstract GoalSelector gonzotech$getGoalSelector();
}
