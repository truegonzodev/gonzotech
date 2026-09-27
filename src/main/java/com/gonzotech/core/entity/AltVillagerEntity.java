package com.gonzotech.core.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
 *
 * <p>Скин (0.3.36): {@link AltVariant} выбирается взвешенной рулеткой (70/15/11/4)
 * прямо в конструкторе — значит, на ЛЮБОМ пути появления (яйцо, /summon,
 * спавнер, будущий натуральный спавн). Держится в SynchedEntityData — клиент
 * получает id варианта автоматически — и в NBT-теге {@code AltVariant} при
 * сохранении мира; старые сохранения без тега получают свежую рулетку.</p>
 */
public class AltVillagerEntity extends PathfinderMob {

    /** Id варианта скина; синхронизируется на клиент средствами SynchedEntityData. */
    private static final EntityDataAccessor<Integer> DATA_VARIANT =
        SynchedEntityData.defineId(AltVillagerEntity.class, EntityDataSerializers.INT);

    public AltVillagerEntity(EntityType<? extends AltVillagerEntity> type, Level level) {
        super(type, level);
        // Рулетка скинов 70/15/11/4 — выполняется на любом пути создания сущности.
        this.setVariant(AltVariant.weightedPick(this.getRandom()));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT, AltVariant.ALT.id());
    }

    public AltVariant getVariant() {
        return AltVariant.byId(this.entityData.get(DATA_VARIANT));
    }

    public void setVariant(AltVariant variant) {
        this.entityData.set(DATA_VARIANT, variant.id());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("AltVariant", this.getVariant().name());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // Сущности до 0.3.36 без тега получают новую рулетку.
        this.setVariant(AltVariant.byName(tag.getString("AltVariant"),
            AltVariant.weightedPick(this.getRandom())));
    }

    /** Как у вилладжера: не деспавнится, оказавшись далеко от игрока. */
    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
