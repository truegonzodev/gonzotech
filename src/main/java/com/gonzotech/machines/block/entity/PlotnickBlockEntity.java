package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.energy.GtBuffer;
import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.PlotnickDefs;
import com.gonzotech.machines.energy.Sinks.GtuSink;
import com.gonzotech.machines.menu.PlotnickMenu;
import com.gonzotech.machines.miner.MarkerService;
import com.gonzotech.machines.miner.MinerMarker;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Плотник автошахтера (0.3.256, автор).
 *
 * <p>Хранилище: 9 активных слотов по 512 (любой предмет) + 54 виртуальных стака
 * по 512. Добыча «капает» сначала в активные слоты, переполнение — в виртуальные
 * (кнопка выдачи в воронки/трубы — пакет добычи). Два слота — карты памяти.</p>
 *
 * <p>Кнопка «Отметить» (id 4) на этом этапе = «выставить разметку»: 9 меток-
 * вымпелов в сетке 3×3 чанка (свой чанк + 8 соседних), как на схеме автора.</p>
 */
public final class PlotnickBlockEntity extends BlockEntity
    implements GtuSink, WorldlyContainer, MenuProvider {

    /** 9 активных слотов ×512 (автор). */
    public static final int SLOTS_ACTIVE = 9;
    public static final int SLOT_CARD_A = 9;
    public static final int SLOT_CARD_B = 10;
    public static final int CONTAINER_SIZE = 11;
    /** 54 виртуальных стака ×512 (автор) — невидимый буфер переполнения. */
    public static final int VIRTUAL_SLOTS = 54;
    /** Стак слотов Плотника — до 512 предметов одного типа (автор). */
    public static final int MAX_STACK = 512;

    public static final long GTU_CAPACITY_MILLI = PlotnickDefs.GTU_CAPACITY_MILLI;
    public static final long GTU_INTAKE_MILLI = PlotnickDefs.GTU_INTAKE_MILLI;

    /** Кнопки GUI (макет автора, слева направо, сверху вниз). */
    public static final int BTN_DRILL = 0;
    public static final int BTN_NETWORK = 1;
    public static final int BTN_PACK_UP = 2;
    public static final int BTN_SEISMO = 3;
    public static final int BTN_MARK = 4;
    public static final int BTN_DYNAMITE = 5;

    private final GtBuffer gtu = new GtBuffer(GTU_CAPACITY_MILLI);
    private final NonNullList<ItemStack> items =
        NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
    private final NonNullList<ItemStack> virtual =
        NonNullList.withSize(VIRTUAL_SLOTS, ItemStack.EMPTY);

    private long intakeBudgetTick = Long.MIN_VALUE;
    private int acceptedGtuThisTick;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> gtu.amountUnitsInt();
                case 1 -> (int) virtual.stream().filter(stack -> !stack.isEmpty()).count();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) gtu.set(MachineDefs.toMilli(Math.max(0, value)));
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    public PlotnickBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PLOTNICK.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PlotnickBlockEntity be) {
        // Пока Плотник только держит заряд GTU; сеть/буры/сейсмика — пакеты 2-4.
    }

    public GtBuffer gtuBuffer() {
        return gtu;
    }

    @Override
    public long receiveGtu(long amount, boolean simulate) {
        resetIntakeLedgerIfNeeded();
        long remaining = Math.max(0L, GTU_INTAKE_MILLI - acceptedGtuThisTick);
        long accepted = gtu.receive(Math.min(amount, remaining), simulate);
        if (!simulate) acceptedGtuThisTick += (int) accepted;
        return accepted;
    }

    private void resetIntakeLedgerIfNeeded() {
        long tick = level == null ? Long.MIN_VALUE : level.getGameTime();
        if (tick == intakeBudgetTick) return;
        intakeBudgetTick = tick;
        acceptedGtuThisTick = 0;
    }

    // ─────────────────────────── кнопки ───────────────────────────

    /** @return кнопка обработана */
    public boolean handleButton(int id) {
        if (!(level instanceof ServerLevel server)) return false;
        return switch (id) {
            case BTN_MARK -> placeSectorMarks(server);
            default -> false; // бурить/сеть/свернуться/сейсмо/динамит — пакеты 2-4
        };
    }

    /**
     * «Выставить разметку»: 9 меток-вымпелов — по одной в каждом чанке сектора
     * 3×3 (свой чанк и 8 соседних). В соседних чанках — ближайший к Плотнику
     * столбец поверхности; в своём — столбец вплотную рядом (схема автора).
     */
    private boolean placeSectorMarks(ServerLevel server) {
        MarkerService service = MarkerService.get(server);
        BlockPos base = worldPosition;
        int chunkMinX = base.getX() & ~15;
        int chunkMinZ = base.getZ() & ~15;
        int chunkMaxX = chunkMinX + 15;
        int chunkMaxZ = chunkMinZ + 15;
        List<Item> noOres = List.of();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x;
                int z;
                if (dx == 0 && dz == 0) {
                    x = base.getX() + 1;
                    z = base.getZ();
                } else {
                    x = dx == 0 ? base.getX() : (dx < 0 ? chunkMinX - 1 : chunkMaxX + 1);
                    z = dz == 0 ? base.getZ() : (dz < 0 ? chunkMinZ - 1 : chunkMaxZ + 1);
                }
                BlockPos surface = server.getHeightmapPos(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, base.getY() + 16, z)).below();
                service.add(server, surface, MinerMarker.Kind.STATION, 0, noOres);
            }
        }
        setChanged();
        return true;
    }

    // ─────────────────────────── хранилище ───────────────────────────

    /**
     * Правило автора: сначала активные слоты, переполнение — в виртуальные
     * (54 ×512). Используется будущей добычей и выкладкой сетки.
     */
    public boolean insert(ItemStack incoming) {
        if (incoming.isEmpty()) return false;
        ItemStack rest = incoming.copy();
        for (int slot = 0; slot < SLOTS_ACTIVE; slot++) {
            rest = insertInto(items, slot, rest);
            if (rest.isEmpty()) {
                setChanged();
                return true;
            }
        }
        for (int slot = 0; slot < VIRTUAL_SLOTS; slot++) {
            rest = insertInto(virtual, slot, rest);
            if (rest.isEmpty()) {
                setChanged();
                return true;
            }
        }
        if (rest.getCount() != incoming.getCount()) {
            incoming.setCount(rest.getCount());
            setChanged();
            return true;
        }
        return false;
    }

    private static ItemStack insertInto(NonNullList<ItemStack> list, int slot, ItemStack incoming) {
        ItemStack current = list.get(slot);
        if (current.isEmpty()) {
            list.set(slot, incoming);
            return ItemStack.EMPTY;
        }
        if (!ItemStack.isSameItemSameComponents(current, incoming)) return incoming;
        int space = MAX_STACK - current.getCount();
        if (space <= 0) return incoming;
        int moved = Math.min(space, incoming.getCount());
        current.grow(moved);
        incoming.shrink(moved);
        return incoming.isEmpty() ? ItemStack.EMPTY : incoming;
    }

    public NonNullList<ItemStack> virtualStacks() {
        return virtual;
    }

    // ─────────────────────────── Container ───────────────────────────

    @Override
    public int getContainerSize() {
        return CONTAINER_SIZE;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.getCount() <= MAX_STACK;
    }

    @Override
    public void clearContent() {
        items.clear();
        for (int i = 0; i < CONTAINER_SIZE; i++) items.add(ItemStack.EMPTY);
        virtual.clear();
        for (int i = 0; i < VIRTUAL_SLOTS; i++) virtual.add(ItemStack.EMPTY);
    }

    @Override
    public int getMaxStackSize() {
        return MAX_STACK;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        int[] slots = new int[SLOTS_ACTIVE + 2];
        for (int i = 0; i < slots.length; i++) slots[i] = i;
        return slots;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return true;
    }

    // ─────────────────────────── persistence / menu ───────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        gtu.save(tag, "Gtu");
        ListTag active = new ListTag();
        for (ItemStack stack : items) {
            active.add(stack.save(registries));
        }
        tag.put("Items", active);
        ListTag virtualTag = new ListTag();
        for (ItemStack stack : virtual) {
            if (!stack.isEmpty()) virtualTag.add(stack.save(registries));
        }
        tag.put("Virtual", virtualTag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        gtu.load(tag, "Gtu");
        ListTag active = tag.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(active.size(), CONTAINER_SIZE); i++) {
            items.set(i, ItemStack.parseOptional(registries, active.getCompound(i)));
        }
        ListTag virtualTag = tag.getList("Virtual", Tag.TAG_COMPOUND);
        for (int i = 0; i < VIRTUAL_SLOTS; i++) {
            virtual.set(i, i < virtualTag.size()
                ? ItemStack.parseOptional(registries, virtualTag.getCompound(i))
                : ItemStack.EMPTY);
        }
    }

    public void dropContentsForBreak() {
        if (level == null || level.isClientSide()) return;
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                net.minecraft.world.Containers.dropItemStack(level,
                    worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack.copy());
            }
        }
        for (ItemStack stack : virtual) {
            if (!stack.isEmpty()) {
                net.minecraft.world.Containers.dropItemStack(level,
                    worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack.copy());
            }
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new PlotnickMenu(id, inventory, this, data);
    }
}
