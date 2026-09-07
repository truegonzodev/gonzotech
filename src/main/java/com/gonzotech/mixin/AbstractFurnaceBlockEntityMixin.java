package com.gonzotech.mixin;

import com.gonzotech.core.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Фаза 3 — цезий в ВАНИЛЬНЫХ печах (печь/плавильня/коптильня).
 * <p>
 * Логика должна совпадать с нашими машинами (топка/электропечь): взрыв возникает
 * в БЛОКЕ печи в тот момент, когда цезиевый слиток появляется в слоте результата
 * переплавки, — а не при заборе игроком и не в самом игроке. Событие
 * {@code ItemSmeltedEvent} для этого не годится (оно срабатывает только при заборе
 * и не знает позицию печи), поэтому подключаемся прямо к тику печи.
 * <p>
 * Как и в машинах: перед взрывом сами высыпаем содержимое печи ровно один раз и
 * очищаем инвентарь, чтобы разрушение блока взрывом не продублировало дроп.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin {

    /** Слот результата у ванильной печи (0 — вход, 1 — топливо, 2 — результат). */
    private static final int RESULT_SLOT = 2;
    /** Сила взрыва — как у наших печей. */
    private static final float CESIUM_EXPLOSION = 2.0F;

    @Inject(method = "serverTick", at = @At("TAIL"))
    private static void gonzotech$cesiumBlast(ServerLevel level, BlockPos pos, BlockState state,
                                              AbstractFurnaceBlockEntity be, CallbackInfo ci) {
        ItemStack result = be.getItem(RESULT_SLOT);
        if (result.isEmpty()) return;
        if (!result.is(ModItems.INGOT_ITEMS.get("cesium_ingot").get())) return;

        Containers.dropContents(level, pos, be);
        be.clearContent();
        level.explode(null,
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
            CESIUM_EXPLOSION, Level.ExplosionInteraction.BLOCK);
    }
}
