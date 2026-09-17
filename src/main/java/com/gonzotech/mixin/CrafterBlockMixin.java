package com.gonzotech.mixin;

import com.gonzotech.core.event.Phase3Events;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Ванильный сборщик (minecraft:crafter, есть с 1.21.0 — в т.ч. у нас в 1.21.4).
 * <p>
 * Это НЕИГРОВОЙ крафтер: крафт идёт в {@code CrafterBlock.dispenseFrom} по
 * редстоун-импульсу без участия игрока — ни {@code ItemCraftedEvent}, ни
 * гейт CraftingMenu не срабатывают, и загейтанная по «Открытию» машина
 * (аттачмент живёт в прогрессе ИГРОКА) крафтится бесплатно.
 * <p>
 * Правило (решение автора 2026-09-17): ВСЕ рецепты с загейченным выводом
 * (тир-1 и всё, что позже — {@link Phase3Events#isAttachmentGated}) из
 * сборщика убираются: для него такой рецепт «не существует» — ванильный
 * звук «крафт не удался» (levelEvent 1050), выход не выдаётся, сетка НЕ
 * тратится. Если крафтить что-то нужно — позже появится наш кастомный
 * паровой сборщик (тир-1 машина после Открытия 1), его правила крафта —
 * отдельное обсуждение. См. TEMP_NOTES §3.5.
 */
@Mixin(CrafterBlock.class)
public abstract class CrafterBlockMixin {

    /** Ванильный уровень-событие «крафт не удался» — тот же звук, что при пустой сетке. */
    private static final int CRAFT_FAILED_EVENT = 1050;

    @Inject(method = "dispenseFrom", at = @At("HEAD"), cancellable = true)
    private void gonzotech$gateCrafter(BlockState state, ServerLevel level, BlockPos pos, CallbackInfo ci) {
        if (!(level.getBlockEntity(pos) instanceof CrafterBlockEntity crafter)) return;

        // Повторяем ванильный подбор (кэшируется в RECIPE_CACHE — дёшево):
        // нам нужен только РЕЗУЛЬТАТ, чтобы проверить гейт.
        CraftingInput input = crafter.asCraftInput();
        Optional<RecipeHolder<CraftingRecipe>> recipe = CrafterBlock.getPotentialResults(level, input);
        if (recipe.isEmpty()) return;
        ItemStack result = recipe.get().value().assemble(input, level.registryAccess());
        if (result.isEmpty()) return;
        if (!Phase3Events.isAttachmentGated(result.getItem())) return;

        // Загейванный вывод: для неигрового крафтера рецепт «не существует».
        level.levelEvent(CRAFT_FAILED_EVENT, pos, 0);
        ci.cancel();
    }
}
