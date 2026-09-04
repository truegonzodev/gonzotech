package com.gonzotech.machines.block.entity;

import com.gonzotech.core.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Побочные эффекты переплавки, общие для топки и электропечи (Фаза 3):
 * <ul>
 *   <li><b>Цезий</b>: если только что переплавили цезиевую руду/поллуцит и в
 *       результате появился цезиевый слиток — печь взрывается (сила 2, ломает
 *       блоки, урон). Проверяем по ПРОИЗВЕДЁННОМУ предмету, чтобы не завязываться
 *       на вход.</li>
 *   <li><b>Свинец</b>: при переплавке железной руды/сырого железа с шансом 5%
 *       за каждый переплавленный предмет образуется свинцовый слиток. Так как
 *       ванильный результат — {@code minecraft:iron_ingot}, шанс проверяем по
 *       произведённому предмету. Свинец кладём в выходной слот (если влезает),
 *       иначе выбрасываем в мир рядом.</li>
 * </ul>
 */
public final class SmeltSideEffects {

    /** Сила взрыва при плавке цезия — «настоящий» взрыв, ломающий блоки. */
    private static final float CESIUM_EXPLOSION = 2.0F;
    /** Шанс образования свинца за каждое переплавленное железо. */
    private static final float LEAD_CHANCE = 0.05F;

    private SmeltSideEffects() {
    }

    /**
     * Обработать побочные эффекты одной переплавки.
     *
     * @param produced  копия произведённого стака (из {@link SmeltHelper.Result})
     * @return true, если печь взорвалась (вызывающему стоит прекратить дальнейшую
     *         работу с этим блок-энтити в текущем тике)
     */
    public static boolean apply(ServerLevel level, BlockPos pos, ItemStack produced) {
        if (produced.isEmpty()) return false;

        // Свинец-побочка при плавке железа (результат — ванильный iron_ingot).
        // Свинец с железом не стакается, поэтому просто выбрасываем предметом рядом.
        if (produced.is(Items.IRON_INGOT) && level.random.nextFloat() < LEAD_CHANCE) {
            ItemStack lead = new ItemStack(ModItems.INGOT_ITEMS.get("lead_ingot").get());
            level.addFreshEntity(dropEntity(level, pos, lead));
        }

        // Цезий: взрыв, ломающий блоки. Делаем ПОСЛЕДНИМ — уничтожит сам блок печи.
        if (produced.is(ModItems.INGOT_ITEMS.get("cesium_ingot").get())) {
            level.explode(null,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                CESIUM_EXPLOSION, Level.ExplosionInteraction.BLOCK);
            return true;
        }
        return false;
    }

    private static ItemEntity dropEntity(Level level, BlockPos pos, ItemStack stack) {
        ItemEntity e = new ItemEntity(level,
            pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, stack);
        e.setDefaultPickUpDelay();
        return e;
    }
}
