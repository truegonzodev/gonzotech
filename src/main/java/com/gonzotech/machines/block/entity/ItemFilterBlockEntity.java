package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.menu.ItemFilterMenu;
import com.gonzotech.machines.network.ItemFilterRouting;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Блок-энтити Фильтра предметов.
 * <p>
 * Хранит 3 <b>ghost-слота</b> (шаблоны фильтра) и небольшой <b>транзитный буфер</b>.
 * Ghost-слоты — только «образцы» (что пропускать), они НЕ инвентарь.
 * <p>
 * Буфер нужен, чтобы Фильтр работал не только «как воронка» (тянет из
 * прилегающего сундука), но и <b>ВРЕЗАННЫМ в трубопровод</b>: предметные трубы
 * могут ВСТАВЛЯТЬ в Фильтр с любой грани (буфер = {@link WorldlyContainer},
 * приём разрешён, выемка внешней автоматизацией запрещена). Каждый тик
 * {@link ItemFilterRouting} обрабатывает и прилегающие контейнеры-источники, и
 * содержимое буфера: совпавшее гонит по выходной сети Фильтра, несовпавшее — в
 * сеть Отсеивателя (иначе оставляет в источнике/буфере — обратное давление).
 * <p>
 * Пустой набор ghost-слотов = Фильтр «выключен», пропускает всё (см.
 * {@link ItemFilterRouting#matches}).
 */
public class ItemFilterBlockEntity extends BlockEntity implements MenuProvider, WorldlyContainer {

    /** Число слотов-образцов фильтра. */
    public static final int FILTER_SLOTS = 3;
    /** Размер транзитного буфера (входящий поток из труб ждёт здесь раздачи). */
    public static final int BUFFER_SLOTS = 5;

    /** Образцы (ghost). НЕ инвентарь — не отдаются автоматизации, только шаблоны. */
    private final NonNullList<ItemStack> filter = NonNullList.withSize(FILTER_SLOTS, ItemStack.EMPTY);
    /** Транзитный буфер (реальные предметы, пришедшие по трубам во Фильтр). */
    private final NonNullList<ItemStack> buffer = NonNullList.withSize(BUFFER_SLOTS, ItemStack.EMPTY);
    private static final int[] BUFFER_SLOT_IDS = buildSlotIds(BUFFER_SLOTS);

    private static int[] buildSlotIds(int n) {
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = i;
        return a;
    }

    public ItemFilterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ITEM_FILTER.get(), pos, state);
    }

    /** Серверный тик: один проход мгновенной фильтрующей маршрутизации. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, ItemFilterBlockEntity be) {
        ItemFilterRouting.tick(level, pos, state, be);
    }

    // ─────────────────────────── доступ к образцам ───────────────────────────

    public int filterSize() {
        return FILTER_SLOTS;
    }

    public ItemStack getFilter(int index) {
        return filter.get(index);
    }

    /** Ставит образец (кладётся КОПИЯ размером 1 — предмет игрока не расходуется). */
    public void setFilter(int index, ItemStack stack) {
        ItemStack ghost = stack.copy();
        if (!ghost.isEmpty()) ghost.setCount(1);
        filter.set(index, ghost);
        setChanged();
    }

    /** true, если ни один образец не задан → Фильтр пропускает всё. */
    public boolean isFilterEmpty() {
        for (ItemStack s : filter) {
            if (!s.isEmpty()) return false;
        }
        return true;
    }

    public NonNullList<ItemStack> filterTemplates() {
        return filter;
    }

    // ─────────────────────── транзитный буфер (Container) ───────────────────────
    // Container обслуживает ТОЛЬКО буфер (5 слотов). Образцы (ghost) сюда не входят
    // и автоматизации не видны.

    @Override
    public int getContainerSize() {
        return BUFFER_SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : buffer) {
            if (!s.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return buffer.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack result = ContainerHelper.removeItem(buffer, slot, count);
        if (!result.isEmpty()) setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(buffer, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        buffer.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return net.minecraft.world.Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        buffer.clear();
    }

    // WorldlyContainer: трубы могут ВСТАВЛЯТЬ в буфер с любой грани, но НЕ
    // ВЫНИМАТЬ (раздачей занимается сам Фильтр, внешний забор запрещён).

    @Override
    public int[] getSlotsForFace(Direction side) {
        return BUFFER_SLOT_IDS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction dir) {
        return true;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
        return false;
    }

    // ─────────────────────────── NBT ───────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, filter, registries);              // ghost-образцы (корень)
        CompoundTag buf = new CompoundTag();
        ContainerHelper.saveAllItems(buf, buffer, registries);
        tag.put("Buffer", buf);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        filter.clear();
        ContainerHelper.loadAllItems(tag, filter, registries);
        buffer.clear();
        if (tag.contains("Buffer")) {
            ContainerHelper.loadAllItems(tag.getCompound("Buffer"), buffer, registries);
        }
    }

    // ─────────────────────────── меню ───────────────────────────

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.gonzotech.item_filter");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new ItemFilterMenu(id, inv, this);
    }
}
