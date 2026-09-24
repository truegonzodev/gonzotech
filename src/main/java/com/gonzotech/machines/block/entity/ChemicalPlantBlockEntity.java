package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.Sinks.GtuSink;
import com.gonzotech.machines.menu.ChemicalPlantMenu;
import com.gonzotech.machines.processing.ChemicalPlantRecipes;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Блок-энтити Химического завода (тир 3):
 * <ul>
 *   <li>Приём и хранение GTU: макс. 2600 GTU, макс. приём 66 GTU/t;</li>
 *   <li>Слоты катализаторов: 0, 1, 2 (платиновые и палладиевые самородки);
 *       обычным рецептам нужны заняты все 3 слота, опилочным — 1 самородок
 *       (правила автора 24.09.2026, см. {@link ChemicalPlantRecipes});</li>
 *   <li>Сетка ингредиентов 3×3: слоты 3..11;</li>
 *   <li>Слот готовой продукции: слот 12;</li>
 *   <li>Длительность любой реакции: ровно 160 тиков, расход 1.9 GTU/t (1900 mGTU/t);</li>
 *   <li>По завершении: поглощает сырьё из сетки и катализаторы по правилу рецепта
 *       (обычные — случайно 0–3 самородка; опилочные — 90 % ничего, 10 % 1).</li>
 * </ul>
 */
public class ChemicalPlantBlockEntity extends BaseMachineBlockEntity
        implements GtuSink, WorldlyContainer {

    public static final int TOTAL_SLOTS = 13;
    public static final int CATALYST_START = 0;
    public static final int CATALYST_COUNT = 3;
    public static final int GRID_START = 3;
    public static final int GRID_COUNT = 9;
    public static final int SLOT_OUTPUT = 12;

    public static final long GTU_CAPACITY = 2600L;
    public static final long GTU_CAPACITY_MILLI = GTU_CAPACITY * MachineDefs.MILLI;
    public static final long MAX_GTU_INTAKE_MILLI = 66L * MachineDefs.MILLI;
    public static final long GTU_PER_TICK_MILLI = 1900L; // 1.9 GTU/t
    public static final int REACTION_TICKS = 160;

    private static final int[] SLOTS_TOP = { 3, 4, 5, 6, 7, 8, 9, 10, 11 };
    private static final int[] SLOTS_BOTTOM = { SLOT_OUTPUT };
    private static final int[] SLOTS_SIDES = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 };

    private long currentGtuMilli = 0;
    private int progress = 0;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> (int) (currentGtuMilli / MachineDefs.MILLI);
                case 1 -> progress;
                case 2 -> REACTION_TICKS;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {
            switch (i) {
                case 0 -> currentGtuMilli = (long) v * MachineDefs.MILLI;
                case 1 -> progress = v;
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    public ChemicalPlantBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHEMICAL_PLANT.get(), pos, state, TOTAL_SLOTS);
    }

    public ContainerData data() {
        return data;
    }

    public long currentGtuMilli() {
        return currentGtuMilli;
    }

    public int progress() {
        return progress;
    }

    public int totalTicks() {
        return REACTION_TICKS;
    }

    // ─────────────────────────── GtuSink ───────────────────────────

    @Override
    public long receiveGtu(long amount, boolean simulate) {
        if (amount <= 0) return 0;
        long spaceMilli = GTU_CAPACITY_MILLI - currentGtuMilli;
        long allowedMilli = Math.min(amount, MAX_GTU_INTAKE_MILLI);
        long acceptedMilli = Math.min(allowedMilli, Math.max(0, spaceMilli));
        if (!simulate && acceptedMilli > 0) {
            currentGtuMilli += acceptedMilli;
            setChanged();
        }
        return acceptedMilli;
    }

    // ─────────────────────────── Серверный тик ───────────────────────────

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide) return;

        // 1. Поиск активного рецепта (требование катализаторов живёт в рецепте)
        ChemicalPlantRecipes.ChemicalRecipe recipe = ChemicalPlantRecipes.findRecipe(this, GRID_START, GRID_COUNT);

        if (recipe == null) {
            if (progress > 0) {
                progress = 0;
                setChanged();
            }
            return;
        }

        // 2. Проверка катализаторов: обычным рецептам нужны все 3 слота занятыми,
        //    опилочным — минимум 1 самородок (автор 24.09.2026)
        int catalystsPresent = 0;
        for (int c = CATALYST_START; c < CATALYST_START + CATALYST_COUNT; c++) {
            ItemStack catStack = items.get(c);
            if (!catStack.isEmpty() && ChemicalPlantRecipes.isCatalyst(catStack)) {
                catalystsPresent++;
            }
        }
        if (catalystsPresent < recipe.catalystRequired()) {
            if (progress > 0) {
                progress = 0;
                setChanged();
            }
            return;
        }

        // 3. Проверка возможности поместить результат в выходной слот
        ItemStack outSlotStack = items.get(SLOT_OUTPUT);
        ItemStack expectedOut = recipe.output();
        if (!outSlotStack.isEmpty()) {
            if (!ItemStack.isSameItemSameComponents(outSlotStack, expectedOut)
                    || outSlotStack.getCount() + expectedOut.getCount() > outSlotStack.getMaxStackSize()) {
                // Выходной слот забит — ждём
                return;
            }
        }

        // 4. Проверка энергии: нужно 1.9 GTU/t
        if (currentGtuMilli < GTU_PER_TICK_MILLI) {
            // Энергии нет — шкала замирает и ждёт
            return;
        }

        // Потребляем энергию и двигаем прогресс реакции
        currentGtuMilli -= GTU_PER_TICK_MILLI;
        progress++;

        if (progress >= REACTION_TICKS) {
            // Реакция завершена:
            // а) Поглощаем сырьё из сетки
            ChemicalPlantRecipes.consumeInputs(this, GRID_START, GRID_COUNT, recipe);
            // б) Поглощаем катализаторы по правилу рецепта (0–3, либо 90/10 опилочным)
            ChemicalPlantRecipes.consumeCatalystsRandomly(this, CATALYST_START, CATALYST_COUNT, level.random, recipe.softCatalysts());
            // в) Выдаём результат
            if (outSlotStack.isEmpty()) {
                items.set(SLOT_OUTPUT, expectedOut.copy());
            } else {
                outSlotStack.grow(expectedOut.getCount());
            }
            progress = 0;
        }

        setChanged();
    }

    // ─────────────────────────── NBT ───────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("GtuMilli", currentGtuMilli);
        tag.putInt("Progress", progress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        currentGtuMilli = tag.getLong("GtuMilli");
        progress = tag.getInt("Progress");
    }

    // ─────────────────────────── WorldlyContainer ───────────────────────────

    @Override
    public int[] getSlotsForFace(Direction side) {
        if (side == Direction.DOWN) return SLOTS_BOTTOM;
        if (side == Direction.UP) return SLOTS_TOP;
        return SLOTS_SIDES;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == SLOT_OUTPUT;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == SLOT_OUTPUT) return false;
        if (slot >= CATALYST_START && slot < CATALYST_START + CATALYST_COUNT) {
            return ChemicalPlantRecipes.isCatalyst(stack);
        }
        return true;
    }

    // ─────────────────────────── Menu ───────────────────────────

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.gonzotech.third_chemical_plant");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new ChemicalPlantMenu(id, inv, this, data);
    }
}
