package com.gonzotech.machines.litho;

import com.gonzotech.core.registry.ModItems;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.gonzotech.machines.menu.SiliconFactoryMenu;

/**
 * BE литографической фабрики: состояние многоблока (origin, вариант,
 * оригиналы блоков-участников) и контейнер — 3 входа + 4 выхода.
 *
 * <p>Процесс (ПРЕДВАРИТЕЛЬНЫЙ, до ТЗ начинки от автора): 1 «суп-набор» →
 * 1 чип вида, соответствующего варианту структуры, за {@link #PROGRESS_TOTAL}
 * тиков; энергия не требуется. Слоты 0..2 — входы, 3..6 — выходы.</p>
 */
public final class SiliconFactoryBlockEntity extends BlockEntity implements Container, MenuProvider {

    public static final int SLOT_COUNT = 7;
    public static final int DATA_COUNT = 2;
    public static final int PROGRESS_TOTAL = 100;
    public static final int INPUT_SLOTS = 3;
    public static final int OUTPUT_SLOTS = 4;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private int progress;

    private boolean formed;
    private int variant;
    private BlockPos origin;
    private BlockPos[] memberPos;
    private BlockState[] originalStates;
    /** BE загружен из NBT: тик обязан проверить мир ({@code restoreController}). */
    private boolean restorePending;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> formed ? variant : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // server-owned
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public SiliconFactoryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SILICON_FACTORY.get(), pos, state);
    }

    // ─────────────────── Состояние многоблока (вызывает структура) ───────────────────

    public boolean isFormed() {
        return formed;
    }

    public int variant() {
        return variant;
    }

    public BlockPos origin() {
        return origin;
    }

    void setFormed(BlockPos origin, int variant, BlockPos[] memberPos, BlockState[] originalStates) {
        this.origin = origin;
        this.variant = variant;
        this.memberPos = memberPos;
        this.originalStates = originalStates;
        this.formed = true;
        this.restorePending = false;
        setChanged();
    }

    void clearFormed() {
        this.formed = false;
        this.variant = 0;
        this.origin = null;
        this.memberPos = null;
        this.originalStates = null;
        this.progress = 0;
        this.restorePending = false;
        setChanged();
    }

    /** Индекс восстановлен по миру — ретраи больше не нужны. */
    void onIndexRestored() {
        this.restorePending = false;
    }

    int memberCount() {
        return memberPos == null ? 0 : memberPos.length;
    }

    BlockPos memberPos(int i) {
        return memberPos[i];
    }

    BlockState originalState(int i) {
        return originalStates[i];
    }

    BlockState originalAt(BlockPos pos) {
        if (memberPos == null) return null;
        for (int i = 0; i < memberPos.length; i++) {
            if (memberPos[i].equals(pos)) return originalStates[i];
        }
        return null;
    }

    // ─────────────────── Тик ───────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, SiliconFactoryBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        if (!be.formed) {
            if (be.restorePending) {
                be.restorePending = false;
                SiliconFactoryStructure.restoreController(server, be);
            }
            return;
        }
        if (be.canProcess()) {
            be.progress++;
            if (be.progress >= PROGRESS_TOTAL) {
                be.progress = 0;
                be.processOne();
            }
            be.setChanged();
        } else if (be.progress != 0) {
            be.progress = 0;
            be.setChanged();
        }
    }

    private ItemStack outputItem() {
        return switch (variant) {
            case 1 -> new ItemStack(ModItems.CHIP_1.get());
            case 2 -> new ItemStack(ModItems.CHIP_2.get());
            case 3 -> new ItemStack(ModItems.CHIP_3.get());
            default -> ItemStack.EMPTY;
        };
    }

    private boolean canProcess() {
        ItemStack out = outputItem();
        if (out.isEmpty()) return false;
        boolean hasSoup = false;
        for (int i = 0; i < INPUT_SLOTS; i++) {
            if (items.get(i).is(ModItems.CHIP_SOUP.get())) {
                hasSoup = true;
                break;
            }
        }
        if (!hasSoup) return false;
        for (int i = INPUT_SLOTS; i < SLOT_COUNT; i++) {
            ItemStack slot = items.get(i);
            if (slot.isEmpty()) return true;
            if (ItemStack.isSameItemSameComponents(slot, out) && slot.getCount() < slot.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    private void processOne() {
        for (int i = 0; i < INPUT_SLOTS; i++) {
            ItemStack in = items.get(i);
            if (in.is(ModItems.CHIP_SOUP.get())) {
                in.shrink(1);
                break;
            }
        }
        ItemStack out = outputItem();
        for (int i = INPUT_SLOTS; i < SLOT_COUNT; i++) {
            ItemStack slot = items.get(i);
            if (slot.isEmpty()) {
                items.set(i, out);
                return;
            }
            if (ItemStack.isSameItemSameComponents(slot, out) && slot.getCount() < slot.getMaxStackSize()) {
                slot.grow(1);
                return;
            }
        }
    }

    // ─────────────────── NBT ───────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("Progress", progress);
        if (formed && origin != null && memberPos != null && originalStates != null) {
            tag.putBoolean("Formed", true);
            tag.putInt("Variant", variant);
            tag.putLong("Origin", origin.asLong());
            long[] members = new long[memberPos.length];
            for (int i = 0; i < memberPos.length; i++) {
                members[i] = memberPos[i].asLong();
            }
            tag.put("Members", new LongArrayTag(members));
            // Оригиналы — простые блоки без свойств: достаточно id блока.
            ListTag states = new ListTag();
            for (BlockState st : originalStates) {
                states.add(StringTag.valueOf(BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString()));
            }
            tag.put("Originals", states);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        progress = tag.getInt("Progress");
        formed = false;
        restorePending = false;
        origin = null;
        memberPos = null;
        originalStates = null;
        variant = 0;
        if (tag.getBoolean("Formed")) {
            variant = tag.getInt("Variant");
            origin = BlockPos.of(tag.getLong("Origin"));
            long[] members = tag.getLongArray("Members");
            ListTag states = tag.getList("Originals", Tag.TAG_STRING);
            if (variant >= 1 && variant <= 3 && origin != null
                && members.length == SiliconFactoryStructure.SLOTS - 1
                && states.size() == members.length) {
                memberPos = new BlockPos[members.length];
                originalStates = new BlockState[members.length];
                for (int i = 0; i < members.length; i++) {
                    memberPos[i] = BlockPos.of(members[i]);
                    Block block = BuiltInRegistries.BLOCK.getValue(ResourceLocation.parse(states.getString(i)));
                    originalStates[i] = (block != null ? block : Blocks.STONE).defaultBlockState();
                }
                formed = true;
                restorePending = true;
            }
        }
    }

    // ─────────────────── Container / MenuProvider ───────────────────

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack split = ContainerHelper.removeItem(items, slot, amount);
        if (!split.isEmpty()) {
            setChanged();
        }
        return split;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.gonzotech.third_silicon_factory");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new SiliconFactoryMenu(id, inventory, this, data);
    }
}
