package com.gonzotech.core.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

/**
 * «Альт-житель» ({@code gonzotech:alt}) — точная копия вилладжера по мешу и
 * анимациям: модель берётся из ванильного {@code ModelLayers.VILLAGER}, вся
 * анимация скопирована в {@code AltVillagerModel.setupAnim}.
 *
 * <p>Сознательно БЕЗ искусственного интеллекта (автор 27.09.2026): ни goal
 * selector'ов, ни Brain, ни торговли — стоит на месте. Натурального спавна нет
 * (категория MISC, как у вилладжера, в спавнера мира не попадает); выдаётся
 * только яйцом призыва {@code gonzotech:alt_spawn_egg}.</p>
 */
public class AltVillagerEntity extends PathfinderMob {

    public AltVillagerEntity(EntityType<? extends AltVillagerEntity> type, Level level) {
        super(type, level);
    }

    /** Как у вилладжера: не деспавнится, оказавшись далеко от игрока. */
    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
