package com.gonzotech.machines.litho;

import com.gonzotech.cleanroom.CleanRoomDetector;
import com.gonzotech.cleanroom.CleanRoomSystem;
import com.gonzotech.cleanroom.RoomLedger;
import com.gonzotech.cleanroom.RoomTopology;
import com.gonzotech.core.registry.ModItems;
import com.gonzotech.machines.energy.GtBuffer;
import com.gonzotech.machines.energy.Sinks.GtuSink;
import com.gonzotech.machines.menu.SiliconFactoryMenu;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

/**
 * BE литографической фабрики (автор 28.09.2026): состояние многоблока
 * (origin, вариант, оригиналы блоков-участников) и конвейер чипа.
 *
 * <p>Слоты 0..3 — нижний ряд-конвейер: 0 — вход (только «суп-набор», класть и
 * брать), 1 и 2 — транзит заготовки (нельзя ни класть, ни брать), 3 — выход
 * (только брать). Слоты 4..6 — шлак (flint при браке, только брать).</p>
 *
 * <p>Процесс: суп-набор в слот 0 → «Травление» (180 тиков) → заготовка в слот 1
 * → «Фотолитография» (320 тиков) → слот 2 → «Вулканизация» (90 тиков, каждые
 * 9 тиков скачок 28 GTU) → чип варианта структуры в слот 3. На каждом из трёх
 * шагов заготовка может забраковаться: шанс зависит от чистоты воздуха контура
 * ({@link #rejectPercent}); при браке заготовка пропадает, в шлак-слот шага
 * падает 1× minecraft:flint.</p>
 *
 * <p>Энергия: хранение 29086 GTU, приём 322 GTU/сек (16.1 GTU/t), течение бара
 * 3.8 GTU/t, паразитная потеря 0.003 GTU/t.</p>
 */
public final class SiliconFactoryBlockEntity extends BlockEntity
    implements WorldlyContainer, MenuProvider, GtuSink {

    public static final int SLOT_COUNT = 7;
    public static final int DATA_COUNT = 6;
    /** Слоты-конвейер: 0 вход, 1..2 транзит, 3 выход. */
    public static final int INPUT_SLOT = 0;
    public static final int TRANSIT_FIRST = 1;
    public static final int TRANSIT_LAST = 2;
    public static final int OUTPUT_SLOT = 3;
    /** Шлак-слоты шагов 0..2 → контейнерные индексы 4..6. */
    public static final int SLAG_BASE = 4;

    public static final int STEP_ETCHING = 0;
    public static final int STEP_PHOTOLITHOGRAPHY = 1;
    public static final int STEP_VULCANIZATION = 2;
    /** Длительности шагов: Травление 180, Фотолитография 320, Вулканизация 90. */
    public static final int[] STEP_TICKS = {180, 320, 90};

    /** Максимальное хранение: 29086 GTU (в милли). */
    public static final long CAPACITY_MILLI = 29_086_000L;
    public static final int CAPACITY_GTU = 29_086;
    /** Максимальный приём: 322 GTU/сек = 16.1 GTU/t = 16100 милли. */
    public static final long INTAKE_MILLI_PER_TICK = 16_100L;
    /** Течение бара: 3.8 GTU/t. */
    public static final long RUN_MILLI_PER_TICK = 3_800L;
    /** Скачок напряжения в третьем баре: 28 GTU каждые 9 тиков. */
    public static final long SPIKE_MILLI = 28_000L;
    public static final int SPIKE_INTERVAL_TICKS = 9;
    /** Паразитная потеря: 0.003 GTU/t. */
    public static final long IDLE_MILLI_PER_TICK = 3L;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final GtBuffer gtu = new GtBuffer(CAPACITY_MILLI);
    /** -1 — простой; 0..2 — активный шаг (бар). */
    private int step = -1;
    private int progress;
    /** Чистота воздуха в сотых доля процента; -1 — вне контура («обычный»). */
    private int qualityHundredths = -1;
    private RoomLedger.Room room;
    /** Разовый бюджет приёма GTU за игровой тик (все отправители вместе). */
    private long intakeTick = Long.MIN_VALUE;
    private long intakeReceived;

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
                case 0 -> (int) gtu.amountAsLong() & 0xffff;
                case 1 -> (int) gtu.amountAsLong() >>> 16;
                case 2 -> step;
                case 3 -> progress;
                case 4 -> qualityHundredths;
                case 5 -> formed ? variant : 0;
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
        this.restorePending = false;
        setChanged();
    }

    /**
     * Полный сброс при распаде структуры (0.3.53) — по образцу турбины/парогена
     * ({@code TurbineRotorBlockEntity.clearStructure}): начинка и энергия не
     * выживают распад «призрачно» в выжившем контроллере, а исчезают вместе со
     * структурой. Сознательный трейдофф: предметы начинки и запас GTU при сломе
     * НЕ выпадают (как и лут блоков, 0.3.49) — машина обнуляется; повторная
     * сборка на том же месте начинает с чистого BE. Раньше содержимое
     * переживало распад внутри контроллера и всплывало при ре-формировании.
     */
    public void clearStructure() {
        items.clear();
        gtu.set(0L);
        step = -1;
        progress = 0;
        room = null;
        qualityHundredths = -1;
        clearFormed();
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

    /**
     * Класс оригинального блока-участника для чистой комнаты (0.3.47): подмена
     * участника оболочкой не должна менять класс ячейки (SEAL/INTERIOR), иначе
     * комната перезаполняется и качество воздуха сбрасывается в ноль.
     */
    public RoomTopology.Kind preservedKindAt(BlockPos pos) {
        if (!formed || memberPos == null) return null;
        for (int i = 0; i < memberPos.length; i++) {
            if (memberPos[i].equals(pos)) return CleanRoomDetector.kind(originalStates[i]);
        }
        return null;
    }

    // ─────────────────── Брак по чистоте воздуха (автор 28.09.2026) ───────────────────

    /**
     * Шанс брака на каждом шаге, %: 100% чистоты → 1, 50% → 9, 10% → 24, 0% → 36
     * (между точками — линейно). Вне контура («обычный» воздух) — 26.
     */
    public static double rejectPercent(double qualityPercent) {
        if (qualityPercent < 0) return 26.0;
        if (qualityPercent >= 100) return 1.0;
        if (qualityPercent >= 50) return 9.0 + (qualityPercent - 50) * (1.0 - 9.0) / 50.0;
        if (qualityPercent >= 10) return 24.0 + (qualityPercent - 10) * (9.0 - 24.0) / 40.0;
        return 36.0 + qualityPercent * (24.0 - 36.0) / 10.0;
    }

    // ─────────────────── Тик ───────────────────

    /**
     * Комната, в которой стоит машина (0.3.51). Раньше — filterRoom от позиции
     * контроллера: его соседи — стекло оболочки (SEAL) и резина снизу, и если
     * сверху машина накрыта потолком/полкой, заливка видела только замкнутый
     * «шкаф» из 2 клеток {резина, контроллер} внутри собственной оболочки —
     * отдельную комнату с качеством 0, недостижимую для фильтров (они улучшают
     * большую комнату, которую видят игрок и телифон). Теперь сформированная
     * машина резолвит комнату по внешнему воздуху вокруг ВСЕЙ коробки: каждая
     * не-членская соседняя клетка класса INTERIOR заливается отдельно, все
     * заливки обязаны сойтись в одну комнату. Машина, наглухо замурованная в
     * герметичные стены (нет ни одной открытой INTERIOR-грани), честно показывает
     * «обычный» (26%). Одиночный блок резолвится как настенный фильтр.
     */
    private static RoomLedger.Room roomAround(ServerLevel server, SiliconFactoryBlockEntity be) {
        if (!be.isFormed()) {
            return CleanRoomSystem.filterRoom(server, be.getBlockPos());
        }
        Set<BlockPos> members = new HashSet<>();
        for (int i = 0; i < be.memberCount(); i++) {
            members.add(be.memberPos(i));
        }
        RoomLedger.Room found = null;
        for (int i = 0; i < be.memberCount(); i++) {
            BlockPos cell = be.memberPos(i);
            for (Direction direction : Direction.values()) {
                BlockPos next = cell.relative(direction);
                if (members.contains(next)) continue;
                if (CleanRoomDetector.read(server, CleanRoomDetector.pos(next)) != RoomTopology.Kind.INTERIOR) {
                    continue;
                }
                RoomLedger.Room candidate = CleanRoomSystem.room(server, next);
                if (candidate == null) continue;
                if (found != null && found != candidate) return null;
                found = candidate;
            }
        }
        return found;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SiliconFactoryBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        // Чистота воздуха: известная комната проверяется каждый тик (пролом/выгрузка
        // останавливают работу), поиск вне комнаты — раз в секунду, как у фильтра.
        if (be.room != null || server.getGameTime() % 20 == 0) {
            be.room = roomAround(server, be);
        }
        be.qualityHundredths = be.room == null ? -1 : (int) Math.round(be.room.quality() * 100);
        // Паразитная потеря 0.003 GTU/t — всегда, пока есть запас.
        if (be.gtu.has(IDLE_MILLI_PER_TICK)) {
            be.gtu.extract(IDLE_MILLI_PER_TICK, false);
        }
        if (be.formed) {
            if (be.restorePending) {
                // Не гасим флаг при неудаче: чанк части может быть ещё не загружен —
                // ретраим каждый тик до успеха либо до распада структуры.
                SiliconFactoryStructure.restoreController(server, be);
            }
            // Один чип в полёте за раз (конвейер автора): новый цикл — только когда
            // в транзите никого нет.
            if (be.step < 0
                && be.items.get(INPUT_SLOT).is(ModItems.CHIP_SOUP.get())
                && be.items.get(TRANSIT_FIRST).isEmpty()
                && be.items.get(TRANSIT_LAST).isEmpty()) {
                be.step = STEP_ETCHING;
                be.progress = 0;
                be.setChanged();
            }
            if (be.step >= 0) {
                tickStep(server, be);
            }
        }
    }

    private static void tickStep(ServerLevel server, SiliconFactoryBlockEntity be) {
        int duration = STEP_TICKS[be.step];
        if (be.progress >= duration) {
            // Шаг завершён, но перенос держится (например, нет места под чип).
            completeStep(server, be);
            return;
        }
        long need = RUN_MILLI_PER_TICK;
        if (be.step == STEP_VULCANIZATION && (be.progress + 1) % SPIKE_INTERVAL_TICKS == 0) {
            need += SPIKE_MILLI;
        }
        if (!be.gtu.has(need)) return; // нет энергии — пауза
        be.gtu.extract(need, false);
        be.progress++;
        if (be.progress >= duration) {
            completeStep(server, be);
        } else {
            be.setChanged();
        }
    }

    private static void completeStep(ServerLevel server, SiliconFactoryBlockEntity be) {
        switch (be.step) {
            case STEP_ETCHING -> {
                // Из стака супов расходуется ровно ОДИН (0.3.46: было set(EMPTY) — съедало стак).
                be.items.get(INPUT_SLOT).shrink(1);
                if (reject(server, be)) {
                    dropFlint(be, STEP_ETCHING);
                    be.step = -1; // заготовка пропала — конвейер простаивает
                } else {
                    be.items.set(TRANSIT_FIRST, new ItemStack(ModItems.CHIP_BLANKY.get()));
                    be.step = STEP_PHOTOLITHOGRAPHY;
                }
                be.progress = 0;
            }
            case STEP_PHOTOLITHOGRAPHY -> {
                if (reject(server, be)) {
                    be.items.set(TRANSIT_FIRST, ItemStack.EMPTY);
                    dropFlint(be, STEP_PHOTOLITHOGRAPHY);
                    be.step = -1; // заготовка пропала — конвейер простаивает
                } else {
                    be.items.set(TRANSIT_FIRST, ItemStack.EMPTY);
                    be.items.set(TRANSIT_LAST, new ItemStack(ModItems.CHIP_BLANKY.get()));
                    be.step = STEP_VULCANIZATION;
                }
                be.progress = 0;
            }
            case STEP_VULCANIZATION -> {
                ItemStack out = be.outputItem();
                if (!fits(be, OUTPUT_SLOT, out)) return; // держим бар полным до появления места
                if (reject(server, be)) {
                    be.items.set(TRANSIT_LAST, ItemStack.EMPTY);
                    dropFlint(be, STEP_VULCANIZATION);
                } else {
                    be.items.set(TRANSIT_LAST, ItemStack.EMPTY);
                    insert(be, OUTPUT_SLOT, out);
                }
                be.step = -1;
                be.progress = 0;
            }
            default -> be.step = -1;
        }
        be.setChanged();
    }

    private static boolean reject(ServerLevel server, SiliconFactoryBlockEntity be) {
        // qualityHundredths — СОТЫЕ доли процента (0..10000); rejectPercent ждёт проценты.
        return server.random.nextDouble() * 100.0 < rejectPercent(be.qualityHundredths / 100.0);
    }

    /** Брак: в шлак-слот шага падает 1× minecraft:flint. */
    private static void dropFlint(SiliconFactoryBlockEntity be, int step) {
        int slot = SLAG_BASE + step;
        ItemStack slag = be.items.get(slot);
        if (slag.isEmpty()) {
            be.items.set(slot, new ItemStack(Items.FLINT));
        } else if (slag.is(Items.FLINT) && slag.getCount() < slag.getMaxStackSize()) {
            slag.grow(1);
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

    private static boolean fits(SiliconFactoryBlockEntity be, int slot, ItemStack stack) {
        ItemStack current = be.items.get(slot);
        return current.isEmpty() || (ItemStack.isSameItemSameComponents(current, stack)
            && current.getCount() < current.getMaxStackSize());
    }

    private static void insert(SiliconFactoryBlockEntity be, int slot, ItemStack stack) {
        ItemStack current = be.items.get(slot);
        if (current.isEmpty()) {
            be.items.set(slot, stack);
        } else {
            current.grow(stack.getCount());
        }
    }

    // ─────────────────── Приём GTU: 322 GTU/сек на все лица разом ───────────────────

    @Override
    public long receiveGtu(long amount, boolean simulate) {
        long now = level == null ? 0 : level.getGameTime();
        long used = now == intakeTick ? intakeReceived : 0;
        long accepted = Math.max(0, Math.min(Math.min(amount,
            CAPACITY_MILLI - gtu.amountAsLong()), INTAKE_MILLI_PER_TICK - used));
        if (!simulate && accepted > 0) {
            intakeTick = now;
            intakeReceived = used + accepted;
            gtu.receive(accepted, false);
            setChanged();
        }
        return accepted;
    }

    // ─────────────────── NBT ───────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putLong("Gtu", gtu.amountAsLong());
        tag.putInt("Step", step);
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
        gtu.receive(Math.min(tag.getLong("Gtu"), CAPACITY_MILLI), false);
        step = tag.contains("Step") ? tag.getInt("Step") : -1;
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

    // ─────────── Автоматизация (автор 28.09.2026): трубы и воронки ───────────
    // Вставка — только «суп-набор» в слот 0; извлечение — только готовые чипы
    // (слот 3) и кремень брака (4..6). Транзит заготовки (1..2) закрыт в обе
    // стороны; все стороны машины равноправны.

    /** Все слоты открыты для обхода автоматикой, правила решают canPlace/canTake. */
    private static final int[] ALL_SLOTS = {0, 1, 2, 3, 4, 5, 6};

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == INPUT_SLOT && stack.is(ModItems.CHIP_SOUP.get());
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
        return slot == OUTPUT_SLOT || slot >= SLAG_BASE;
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
