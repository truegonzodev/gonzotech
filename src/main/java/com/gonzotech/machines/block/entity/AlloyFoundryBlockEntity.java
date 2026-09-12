package com.gonzotech.machines.block.entity;

import com.gonzotech.core.registry.ModItems;
import com.gonzotech.machines.menu.AlloyFoundryMenu;
import com.gonzotech.machines.processing.AlloyFoundryRecipes;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

/**
 * Завод сплавов: 5×5 входная сетка и один выход.
 *
 * <p>Это первая, намеренно бесплатная и мгновенная стадия машины: раз в серверный
 * тик она выполняет максимум одну полностью проверенную именную плавку. Ни GTU,
 * ни скрытых таймеров здесь нет. Лимит «одна плавка в тик» делает потребление,
 * выдачу и поведение при заполненном output атомарными и предсказуемыми.</p>
 */
public final class AlloyFoundryBlockEntity extends BaseMachineBlockEntity implements WorldlyContainer {

    public static final int GRID_WIDTH = 5;
    public static final int GRID_HEIGHT = 5;
    public static final int INPUT_SLOTS = GRID_WIDTH * GRID_HEIGHT;
    public static final int SLOT_OUTPUT = INPUT_SLOTS;
    private static final int[] ALL_SLOTS = allSlots();

    public AlloyFoundryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ALLOY_FOUNDRY.get(), pos, state, INPUT_SLOTS + 1);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AlloyFoundryBlockEntity be) {
        if (!(level instanceof ServerLevel)) return;
        AlloyFoundryRecipes.Batch batch = AlloyFoundryRecipes.find(be.inputGrid());
        if (batch == null || !be.canStore(batch)) return;

        // Capacity was checked first. From this point the transaction cannot lose
        // an ingredient because every requested count is available in the grid.
        for (Map.Entry<net.minecraft.world.item.Item, Integer> need : batch.ingredients().entrySet()) {
            be.consume(need.getKey(), need.getValue());
        }
        be.store(batch);
        be.setChanged();
    }

    /** Current 5×5 input view; the output slot is deliberately excluded. */
    private Iterable<ItemStack> inputGrid() {
        return items.subList(0, INPUT_SLOTS);
    }

    private boolean canStore(AlloyFoundryRecipes.Batch batch) {
        ItemStack output = items.get(SLOT_OUTPUT);
        if (output.isEmpty()) {
            // Constructing the prospective stack also keeps this correct if a
            // future named result uses a component-dependent stack limit.
            return batch.outputCount() <= Math.min(new ItemStack(batch.output()).getMaxStackSize(), getMaxStackSize());
        }
        return ItemStack.isSameItemSameComponents(output, new ItemStack(batch.output()))
            && output.getCount() + batch.outputCount() <= Math.min(output.getMaxStackSize(), getMaxStackSize());
    }

    private void consume(net.minecraft.world.item.Item item, int count) {
        int remaining = count;
        for (int slot = 0; slot < INPUT_SLOTS && remaining > 0; slot++) {
            ItemStack stack = items.get(slot);
            if (!stack.is(item)) continue;
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
        if (remaining != 0) {
            throw new IllegalStateException("Validated alloy input vanished during transaction");
        }
    }

    private void store(AlloyFoundryRecipes.Batch batch) {
        ItemStack output = items.get(SLOT_OUTPUT);
        if (output.isEmpty()) {
            items.set(SLOT_OUTPUT, new ItemStack(batch.output(), batch.outputCount()));
        } else {
            output.grow(batch.outputCount());
        }
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot >= 0 && slot < INPUT_SLOTS && isCurrentIngredient(stack);
    }

    /** Only the inputs supported by this first factory pass are accepted. */
    private static boolean isCurrentIngredient(ItemStack stack) {
        return stack.is(Items.IRON_INGOT) || stack.is(Items.COAL) || stack.is(ModItems.IRON_DUST.get());
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return ALL_SLOTS;
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
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new AlloyFoundryMenu(id, inv, this);
    }

    private static int[] allSlots() {
        int[] slots = new int[INPUT_SLOTS + 1];
        for (int i = 0; i < slots.length; i++) slots[i] = i;
        return slots;
    }
}
