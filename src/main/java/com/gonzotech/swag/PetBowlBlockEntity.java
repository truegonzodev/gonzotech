package com.gonzotech.swag;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Блок-сущность миски: один слот корма (до 64 предметов одного вида).
 *
 * <p>Коты/волки находят миски через статический реестр загруженных BE
 * (не сканируют чанки): добавление — {@link #clearRemoved()}, удаление —
 * {@link #setRemoved()} (каноническая пара жизненного цикла BE).
 * Видимость корма идёт через блок-стейт {@link PetBowlBlock#HAS_FOOD},
 * поэтому клиентская синхронизация NBT не нужна.
 */
public class PetBowlBlockEntity extends BlockEntity {

    /** Допустимый корм миски (датапак: #minecraft:cat_food + #minecraft:wolf_food). */
    public static final TagKey<Item> PET_BOWL_FOOD =
        TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "pet_bowl_food"));

    private static final int MAX_FOOD = 64;

    /** Все загруженные миски (и клиентские тоже — цель фильтруется по уровню). */
    private static final Set<PetBowlBlockEntity> LOADED_BOWLS = new HashSet<>();

    private ItemStack food = ItemStack.EMPTY;

    public PetBowlBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PET_BOWL.get(), pos, state);
    }

    public static boolean isPetFood(ItemStack stack) {
        return stack.is(PET_BOWL_FOOD);
    }

    public boolean hasFood() {
        return !this.food.isEmpty();
    }

    /** Содержимое (НЕ копия — работать аккуратно, см. tryEatOne/getDrops). */
    public ItemStack peekFood() {
        return this.food;
    }

    /**
     * Положить корм из руки/стака (сервер). Стопка источника уменьшается.
     * Миска принимает еду одного вида до 64 штук.
     */
    public boolean insertFood(ItemStack held) {
        if (!isPetFood(held)) {
            return false;
        }
        if (this.food.isEmpty()) {
            int move = Math.min(MAX_FOOD, held.getCount());
            if (move <= 0) {
                return false;
            }
            this.food = held.copyWithCount(move);
            held.shrink(move);
        } else if (ItemStack.isSameItem(this.food, held)) {
            int space = MAX_FOOD - this.food.getCount();
            int move = Math.min(space, held.getCount());
            if (move <= 0) {
                return false;
            }
            this.food.grow(move);
            held.shrink(move);
        } else {
            return false;
        }
        this.setChanged();
        this.syncFoodState();
        return true;
    }

    /** Снять весь корм себе в инвентарь (сервер); остаток падает под ноги. */
    public void extractAll(Player player) {
        if (this.food.isEmpty()) {
            return;
        }
        ItemStack taken = this.food;
        this.food = ItemStack.EMPTY;
        this.setChanged();
        this.syncFoodState();
        if (!player.getInventory().add(taken)) {
            player.drop(taken, false);
        }
    }

    /** Одно кусание питомцем (сервер): уменьшает на 1, пустая → стейт HAS_FOOD=false. */
    public boolean tryEatOne() {
        if (this.food.isEmpty()) {
            return false;
        }
        this.food.shrink(1);
        if (this.food.isEmpty()) {
            this.food = ItemStack.EMPTY;
        }
        this.setChanged();
        this.syncFoodState();
        return true;
    }

    private void syncFoodState() {
        if (this.level != null) {
            boolean has = this.hasFood();
            BlockState state = this.getBlockState();
            if (state.getValue(PetBowlBlock.HAS_FOOD) != has) {
                this.level.setBlock(this.worldPosition, state.setValue(PetBowlBlock.HAS_FOOD, has), 3);
            }
        }
    }

    // ─────────────── реестр загруженных мисок ───────────────

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        LOADED_BOWLS.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        LOADED_BOWLS.remove(this);
    }

    /**
     * Ближайшая загруженная миска к мобу (тот же уровень), в радиусе ~radius блоков.
     * Возвращает позицию блока или null. Дешёвая: перебор зарегистрированных BE.
     */
    public static BlockPos findNearestBowl(Mob pet, int radius) {
        BlockPos petPos = pet.blockPosition();
        double bestDistSqr = (double) radius * radius + 4.0; // +запас на угловые чанки
        BlockPos best = null;
        for (PetBowlBlockEntity bowl : LOADED_BOWLS) {
            if (bowl.getLevel() != pet.level()) {
                continue;
            }
            double distSqr = bowl.getBlockPos().distSqr(petPos);
            if (distSqr < bestDistSqr) {
                bestDistSqr = distSqr;
                best = bowl.getBlockPos();
            }
        }
        return best;
    }

    /** Быстрый доступ к BE по позиции (goal уже знает, что это миска). */
    public static PetBowlBlockEntity getAt(net.minecraft.world.level.Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof PetBowlBlockEntity bowl ? bowl : null;
    }

    // ─────────────── персист ───────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!this.food.isEmpty()) {
            tag.put("Food", this.food.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        Optional<ItemStack> parsed = ItemStack.parse(registries, tag.getCompound("Food"));
        this.food = parsed.orElse(ItemStack.EMPTY);
        // Блок-стейт сохраняется в чанке вместе с NBT — пересинхронизация HAS_FOOD
        // после загрузки не требуется.
    }
}
