package com.gonzotech.machines.block.entity;

import com.gonzotech.core.ore.OreDefinition;
import com.gonzotech.core.registry.ModBlocks;
import com.gonzotech.machines.energy.GtBuffer;
import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.ResourceBuffer;
import com.gonzotech.machines.energy.Sinks.GtuSink;
import com.gonzotech.machines.energy.Sinks.WaterSink;
import com.gonzotech.machines.menu.CobbleGeneratorMenu;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Генератор булыжника — механизм паровой эры, «печатающий» булыжник из воды при
 * наличии ведра лавы (требование-модификатор, НЕ тратится) и энергии.
 * <p>
 * Слоты:
 * <ul>
 *   <li>{@link #SLOT_LAVA} — ведро лавы: модификатор-ТРЕБОВАНИЕ, без него генератор
 *       стоит; само ведро не расходуется (может внезапно «застыть» в обсидиан —
 *       см. глобальный шанс при создании виртуального булыжника);</li>
 *   <li>{@link #SLOT_PICKAXE} — кирка: модификатор СКОРОСТИ вскопки (тиков на
 *       булыжник зависит от материала кирки; без кирки — самый медленный режим).
 *       Кирка не изнашивается, но с малым шансом ломается (пропадает);</li>
 *   <li>{@link #SLOT_OUTPUT} — только ЗАБОР, максимум 1 стак булыжника. Воду в
 *       генератор через слот подать НЕЛЬЗЯ — только жидкостными трубами.</li>
 * </ul>
 * <p>
 * Цикл: если есть ведро лавы, кран воды (≥ 1000 mB) и ещё не идёт копание —
 * единовременно списывается 1000 mB воды и создаётся «виртуальный булыжник»
 * (в этот момент бросаются глобальные кубики: лава→обсидиан 0.16%, кирка
 * ломается 0.09%). Далее шкала копания растёт, тратя стабильно 0.8 GTU/t, пока не
 * достигнет длительности по кирке; тогда в слот выдачи кладётся 1 булыжник
 * (с шансом подмены результата на руду/обсидиан) и цикл может начаться заново.
 */
public class CobbleGeneratorBlockEntity extends BaseMachineBlockEntity
        implements GtuSink, WaterSink, WorldlyContainer {

    public static final int SLOT_LAVA = 0;
    public static final int SLOT_PICKAXE = 1;
    public static final int SLOT_OUTPUT = 2;

    private static final int[] SLOTS_ALL = { SLOT_LAVA, SLOT_PICKAXE, SLOT_OUTPUT };

    private final GtBuffer gtu = new GtBuffer((long) MachineDefs.COBBLE_GTU_CAPACITY);
    private final ResourceBuffer water = new ResourceBuffer(MachineDefs.COBBLE_WATER_CAPACITY);

    /** Идёт ли сейчас копание (виртуальный булыжник создан, шкала растёт). */
    private boolean digging = false;
    /** Прогресс копания в тиках. */
    private int digProgress = 0;
    /** Требуемая длительность текущего копания (зафиксирована по кирке на старте). */
    private int digTotal = MachineDefs.COBBLE_TICKS_NONE;
    /**
     * Готовый, но ещё не выданный результат (булыжник/руда/обсидиан). Результат
     * бросается ОДИН раз по завершении копания и хранится здесь, пока в выходной
     * слот его нельзя положить (слот занят несовместимым предметом). Так редкая
     * руда не теряется: генератор ждёт, пока игрок освободит слот, — как печь с
     * забитым выходом. Новое копание не стартует, пока результат не выдан.
     */
    private ItemStack pendingResult = ItemStack.EMPTY;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> gtu.amountUnitsInt();       // GTU в целых единицах
                case 1 -> water.amount();             // вода в mB
                case 2 -> digProgress;
                case 3 -> digTotal;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {
            switch (i) {
                case 0 -> gtu.set(MachineDefs.toMilli(v));
                case 1 -> water.set(v);
                case 2 -> digProgress = v;
                case 3 -> digTotal = v;
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    public CobbleGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COBBLE_GENERATOR.get(), pos, state, 3);
    }

    public GtBuffer gtuBuffer() {
        return gtu;
    }

    public ResourceBuffer waterBuffer() {
        return water;
    }

    public ContainerData data() {
        return data;
    }

    // ─────────────────────────── Sinks ───────────────────────────

    @Override
    public long receiveGtu(long amount, boolean simulate) {
        return gtu.receive(Math.min(amount, (long) MachineDefs.COBBLE_GTU_INTAKE), simulate);
    }

    @Override
    public long receiveWater(long amount, boolean simulate) {
        return water.receive(Math.min(amount, (long) MachineDefs.COBBLE_WATER_INTAKE), simulate);
    }

    // ─────────────────────────── тик (сервер) ───────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, CobbleGeneratorBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        boolean changed = false;

        // 0. Сначала пытаемся выдать отложенный результат (если слот освободился).
        if (!be.pendingResult.isEmpty()) {
            if (be.tryOutputPending()) changed = true;
        }

        // 1. Старт копания: есть ведро лавы, вода ≥ 1000 mB, копание не идёт и нет
        //    невыданного результата (иначе он бы потерялся/перезаписался).
        if (!be.digging && be.pendingResult.isEmpty()
            && be.hasLavaBucket() && be.water.amount() >= MachineDefs.COBBLE_WATER_PER_ROCK) {
            if (be.tryStartDig(server)) changed = true;
        }

        // 2. Ход копания: тратим стабильно 0.8 GTU/t, растим прогресс. Требует
        //    ведра лавы (иначе процесс замирает) и наличия энергии.
        if (be.digging && be.hasLavaBucket() && be.gtu.has(MachineDefs.COBBLE_GTU_MILLI_PER_TICK)) {
            be.gtu.extract(MachineDefs.COBBLE_GTU_MILLI_PER_TICK, false);
            be.digProgress++;
            changed = true;
            if (be.digProgress >= be.digTotal) {
                be.finishDig(server);
                changed = true;
            }
        }

        if (changed) be.setChanged();
    }

    /** Есть ли в слоте ведро лавы (требование-модификатор). */
    private boolean hasLavaBucket() {
        return items.get(SLOT_LAVA).is(Items.LAVA_BUCKET);
    }

    /**
     * Старт копания: списать 1000 mB воды, создать виртуальный булыжник, бросить
     * глобальные кубики (лава→обсидиан, кирка ломается). Фиксирует длительность по
     * текущей кирке.
     *
     * @return {@code true}, если копание реально стартовало
     */
    private boolean tryStartDig(ServerLevel server) {
        water.extract(MachineDefs.COBBLE_WATER_PER_ROCK, false);
        digging = true;
        digProgress = 0;
        digTotal = digTicksForPickaxe();

        RandomSource rng = server.getRandom();

        // Глобальный шанс: ведро лавы застывает в ведро обсидиана.
        if (rng.nextDouble() < MachineDefs.COBBLE_CHANCE_LAVA_TO_OBSIDIAN) {
            items.set(SLOT_LAVA, new ItemStack(
                com.gonzotech.core.registry.ModItems.OBSIDIAN_BUCKET.get()));
        }

        // Глобальный шанс: кирка ломается (пропадает).
        ItemStack pick = items.get(SLOT_PICKAXE);
        if (!pick.isEmpty() && rng.nextDouble() < MachineDefs.COBBLE_CHANCE_PICKAXE_BREAK) {
            items.set(SLOT_PICKAXE, ItemStack.EMPTY);
        }

        return true;
    }

    /**
     * Завершить копание: бросить результат (с шансом подмены) и попытаться выдать
     * его сразу; если слот занят — результат остаётся в {@link #pendingResult} и
     * будет выдан позже (не теряется).
     */
    private void finishDig(ServerLevel server) {
        pendingResult = rollResult(server.getRandom());
        digging = false;
        digProgress = 0;
        tryOutputPending();
    }

    /**
     * Длительность копания по кирке в слоте-модификаторе (тиков). Сопоставление по
     * конкретному ванильному предмету — надёжно и не зависит от версии API Tier.
     * Незнакомая (модовая) кирка → как без кирки, чтобы не давать бонус даром.
     */
    private int digTicksForPickaxe() {
        Item pick = items.get(SLOT_PICKAXE).getItem();
        if (pick == Items.WOODEN_PICKAXE) return MachineDefs.COBBLE_TICKS_WOOD;
        if (pick == Items.STONE_PICKAXE) return MachineDefs.COBBLE_TICKS_STONE;
        if (pick == Items.IRON_PICKAXE) return MachineDefs.COBBLE_TICKS_IRON;
        if (pick == Items.GOLDEN_PICKAXE) return MachineDefs.COBBLE_TICKS_GOLD;
        if (pick == Items.DIAMOND_PICKAXE) return MachineDefs.COBBLE_TICKS_DIAMOND;
        if (pick == Items.NETHERITE_PICKAXE) return MachineDefs.COBBLE_TICKS_NETHERITE;
        return MachineDefs.COBBLE_TICKS_NONE;
    }

    /**
     * Бросить результат выдачи: обычно булыжник, но с малыми шансами — угольная
     * руда (5%), железная руда (1%), обсидиан (0.1%) или случайная КАМЕННАЯ
     * (host STONE) руда gonzotech (0.03%). Проверяются по порядку.
     */
    private ItemStack rollResult(RandomSource rng) {
        double r = rng.nextDouble();
        double c = MachineDefs.COBBLE_CHANCE_COAL_ORE;
        if (r < c) return new ItemStack(Blocks.COAL_ORE);
        c += MachineDefs.COBBLE_CHANCE_IRON_ORE;
        if (r < c) return new ItemStack(Blocks.IRON_ORE);
        c += MachineDefs.COBBLE_CHANCE_OBSIDIAN;
        if (r < c) return new ItemStack(Blocks.OBSIDIAN);
        c += MachineDefs.COBBLE_CHANCE_GONZO_STONE_ORE;
        if (r < c) {
            ItemStack ore = randomGonzoStoneOre(rng);
            if (!ore.isEmpty()) return ore;
        }
        return new ItemStack(Blocks.COBBLESTONE);
    }

    /** Случайная каменная (host STONE) руда gonzotech, или пусто, если таких нет. */
    private static ItemStack randomGonzoStoneOre(RandomSource rng) {
        List<Block> stoneOres = new ArrayList<>();
        for (OreDefinition ore : OreDefinition.ALL) {
            if (!ore.hosts().contains(OreDefinition.Host.STONE)) continue;
            var byHost = ModBlocks.ORE_BLOCKS.get(ore.id());
            if (byHost == null) continue;
            var block = byHost.get(OreDefinition.Host.STONE);
            if (block != null) stoneOres.add(block.get());
        }
        if (stoneOres.isEmpty()) return ItemStack.EMPTY;
        return new ItemStack(stoneOres.get(rng.nextInt(stoneOres.size())));
    }

    /**
     * Попытаться выдать {@link #pendingResult} в выходной слот (стакая при
     * совпадении). Если слот пуст — кладём предмет; если там уже такой же и не
     * полон — растим стак; иначе оставляем в pending до освобождения слота.
     *
     * @return {@code true}, если результат (полностью) выдан
     */
    private boolean tryOutputPending() {
        if (pendingResult.isEmpty()) return false;
        ItemStack out = items.get(SLOT_OUTPUT);
        if (out.isEmpty()) {
            items.set(SLOT_OUTPUT, pendingResult);
            pendingResult = ItemStack.EMPTY;
            return true;
        }
        if (ItemStack.isSameItemSameComponents(out, pendingResult)
                && out.getCount() < out.getMaxStackSize()) {
            out.grow(1);
            pendingResult = ItemStack.EMPTY;
            return true;
        }
        return false; // слот занят несовместимым — ждём, ничего не теряем
    }

    // ─────────────────────────── NBT ───────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        gtu.save(tag, "Gtu");
        water.save(tag, "Water");
        tag.putBoolean("Digging", digging);
        tag.putInt("DigProgress", digProgress);
        tag.putInt("DigTotal", digTotal);
        if (!pendingResult.isEmpty()) {
            tag.put("PendingResult", pendingResult.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        gtu.load(tag, "Gtu");
        water.load(tag, "Water");
        digging = tag.getBoolean("Digging");
        digProgress = tag.getInt("DigProgress");
        digTotal = tag.contains("DigTotal") ? tag.getInt("DigTotal") : MachineDefs.COBBLE_TICKS_NONE;
        pendingResult = tag.contains("PendingResult")
            ? ItemStack.parseOptional(registries, tag.getCompound("PendingResult"))
            : ItemStack.EMPTY;
    }

    // ─────────────────── Container / WorldlyContainer ───────────────────

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_LAVA -> stack.is(Items.LAVA_BUCKET);
            case SLOT_PICKAXE -> isPickaxe(stack);
            default -> false; // выход — только забор; воду только трубами
        };
    }

    /** Кирка ли: ванильные + любой предмет с id-путём вида {@code *_pickaxe}. */
    private static boolean isPickaxe(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("_pickaxe");
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return SLOTS_ALL;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        // Автоматизация забирает только готовый результат из выходного слота.
        return slot == SLOT_OUTPUT;
    }

    // ─────────────────────────── Menu ───────────────────────────

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.gonzotech.cobble_generator");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new CobbleGeneratorMenu(id, inv, this, data);
    }
}
