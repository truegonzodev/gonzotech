package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Конденсатор — пассивный блок без меню и без тика.
 * <p>
 * Его единственная роль: примыкая к генератору Стирлинга, повышать его
 * водоотдачу на {@link com.gonzotech.machines.energy.MachineDefs#STIRLING_WATER_PER_CONDENSER}
 * mB/t за штуку. Сам стирлинг считает примыкающие конденсаторы по граням
 * (см. {@code StirlingBlockEntity.countCondensers}). BlockEntity нужен лишь как
 * маркер для {@code instanceof}-проверки — состояния он не хранит.
 */
public class CondenserBlockEntity extends BlockEntity {

    public CondenserBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONDENSER.get(), pos, state);
    }
}
