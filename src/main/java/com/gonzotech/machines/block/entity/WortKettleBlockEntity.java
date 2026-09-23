package com.gonzotech.machines.block.entity;

import com.gonzotech.core.registry.ModItems;
import com.gonzotech.machines.energy.FluidBlends.MashBlend;
import com.gonzotech.machines.energy.FluidBlends.WortBlend;
import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.Sinks;
import com.gonzotech.machines.energy.Sinks.GthSink;
import com.gonzotech.machines.energy.Sinks.MashSink;
import com.gonzotech.machines.menu.WortKettleMenu;
import com.gonzotech.machines.network.PipeRouting;
import com.gonzotech.machines.network.PipeType;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Блок-сущность Сусловарочного котла (Эпоха III, «Открытие 3»).
 * <ul>
 *   <li>Баки: брага 12 288 mB ({@link MashBlend}), сусло 12 288 mB ({@link WortBlend}). Буфер GTH: 24 301.</li>
 *   <li>Варка: 26 mB/t браги в сусло за 42 GTH/t (очистка гнили: сусло всегда 0% гнили).</li>
 *   <li>Выпаривание: хранимое сусло 2 mB/t за 16 GTH/t (повышает концентрацию до 30% спирта).</li>
 *   <li>Фасовка в слоте (26, 35): бутылёк + 128 mB сусла → Кружка пива; ведро + 1000 mB сусла → Ведро пива.</li>
 *   <li>Автовыдача сусла в трубы и приёмники: до 688 mB/t.</li>
 * </ul>
 */
public class WortKettleBlockEntity extends BaseMachineBlockEntity implements GthSink, MashSink, WorldlyContainer {

    public static final int SLOT_CONTAINER = 0;
    public static final int SLOT_COUNT = 1;
    private static final int[] SLOTS = { SLOT_CONTAINER };

    public static final int MASH_CAPACITY = 12_288;
    public static final int WORT_CAPACITY = 12_288;
    public static final int GTH_CAPACITY = 24_301;

    public static final int BOIL_RATE = 26;
    public static final int BOIL_GTH_COST = 42;
    public static final int EVAPORATE_RATE = 2;
    public static final int EVAPORATE_GTH_COST = 16;
    public static final int MAX_DRAIN_PER_TICK = 492;
    public static final long MAX_GTH_INPUT_PER_TICK = 156L * MachineDefs.MILLI;
    public static final double MAX_WORT_ALCOHOL = 30.0;

    private static final int GTH_PACKET_BASE = 10_000;

    private long currentGthMilli = 0;
    private MashBlend mash = MashBlend.EMPTY;
    private WortBlend wort = WortBlend.EMPTY;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return getData(i);
        }

        @Override
        public void set(int i, int value) {
        }

        @Override
        public int getCount() {
            return WortKettleMenu.DATA_COUNT;
        }
    };

    public WortKettleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.THIRD_WORT_KETTLE.get(), pos, state, SLOT_COUNT);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.gonzotech.third_wort_kettle");
    }

    public ContainerData data() {
        return data;
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new WortKettleMenu(id, inv, this, data);
    }

    public MashBlend getMash() {
        return mash;
    }

    public WortBlend getWort() {
        return wort;
    }

    public int getCurrentGth() {
        return (int) (currentGthMilli / MachineDefs.MILLI);
    }

    public int getMaxGth() {
        return GTH_CAPACITY;
    }

    // ─────────────────────────── Sinks ───────────────────────────

    @Override
    public long receiveGth(long amountMilli, boolean simulate) {
        long maxMilli = (long) GTH_CAPACITY * MachineDefs.MILLI;
        long space = Math.max(0, maxMilli - currentGthMilli);
        long allowed = Math.min(amountMilli, MAX_GTH_INPUT_PER_TICK);
        long accepted = Math.min(allowed, space);
        if (!simulate && accepted > 0) {
            currentGthMilli += accepted;
            setChanged();
        }
        return accepted;
    }

    @Override
    public long receiveMash(long amount, double alcoholPercent, double rotPercent, boolean simulate) {
        long space = Math.max(0, MASH_CAPACITY - mash.amount());
        long allowed = Math.min(amount, 288);
        long accepted = Math.min(allowed, space);
        if (!simulate && accepted > 0) {
            mash = mash.withAdded(accepted, alcoholPercent, rotPercent);
            setChanged();
        }
        return accepted;
    }

    // ─────────────────────────── Тик ───────────────────────────

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide()) return;
        boolean changed = false;

        // 1. Варка: 26 mB/t браги в сусло за 42 GTH/t (очистка гнили).
        long wortSpace = Math.max(0, WORT_CAPACITY - wort.amount());
        if (!mash.isEmpty() && wortSpace > 0) {
            long toBoil = Math.min((long) BOIL_RATE, Math.min(mash.amount(), wortSpace));
            long costGth = (long) Math.ceil((double) (BOIL_GTH_COST * toBoil) / (double) BOIL_RATE);
            long costMilli = costGth * MachineDefs.MILLI;

            if (currentGthMilli >= costMilli) {
                currentGthMilli -= costMilli;
                double mashAlc = mash.alcoholPercent();
                mash = mash.withExtracted(toBoil);
                wort = wort.withAdded(toBoil, mashAlc);
                changed = true;
            }
        }

        // 2. Выпаривание хранимого сусла: 2 mB/t за 16 GTH/t (до 30% спирта).
        if (wort.amount() > EVAPORATE_RATE && wort.alcoholPercent() < MAX_WORT_ALCOHOL) {
            long costMilli = (long) EVAPORATE_GTH_COST * MachineDefs.MILLI;
            if (currentGthMilli >= costMilli) {
                currentGthMilli -= costMilli;
                wort = wort.withVolumeReduction(EVAPORATE_RATE, MAX_WORT_ALCOHOL);
                changed = true;
            }
        }

        // 3. Фасовка в слоте:
        //    Бутылёк + 128 mB сусла → Кружка пива; ведро + 1000 mB сусла → Ведро пива.
        ItemStack container = items.get(SLOT_CONTAINER);
        if (!container.isEmpty()) {
            if (container.is(Items.GLASS_BOTTLE) && wort.amount() >= 128) {
                if (container.getCount() == 1) {
                    items.set(SLOT_CONTAINER, new ItemStack(ModItems.BEER_MUG.get()));
                    wort = wort.withExtracted(128);
                    changed = true;
                }
            } else if (container.is(Items.BUCKET) && wort.amount() >= 1000) {
                if (container.getCount() == 1) {
                    items.set(SLOT_CONTAINER, new ItemStack(ModItems.BEER_BUCKET.get()));
                    wort = wort.withExtracted(1000);
                    changed = true;
                }
            }
        }

        // 4. Автовыдача сусла до 688 mB/t.
        if (!wort.isEmpty()) {
            long budget = Math.min((long) MAX_DRAIN_PER_TICK, wort.amount());
            long moved = PipeRouting.drain(level, pos, PipeType.WORT, budget, level.getGameTime(), (beTarget, p) -> {
                if (beTarget instanceof WortKettleBlockEntity) return null;
                if (beTarget instanceof Sinks.WortSink sink) {
                    return (amount, simulate) -> sink.receiveWort(amount, wort.alcoholPercent(), simulate);
                }
                return null;
            });
            if (moved > 0) {
                wort = wort.withExtracted(moved);
                changed = true;
            }
        }

        if (changed) {
            setChanged();
        }
    }

    // ─────────────────────────── ContainerData ───────────────────────────

    public int getData(int index) {
        int gth = getCurrentGth();
        return switch (index) {
            case 0 -> gth % GTH_PACKET_BASE;
            case 1 -> gth / GTH_PACKET_BASE;
            case 2 -> (int) Math.min(Integer.MAX_VALUE, mash.amount());
            case 3 -> (int) Math.round(mash.alcoholPercent() * 100.0);
            case 4 -> (int) Math.round(mash.rotPercent() * 100.0);
            case 5 -> (int) Math.min(Integer.MAX_VALUE, wort.amount());
            case 6 -> (int) Math.round(wort.alcoholPercent() * 100.0);
            default -> 0;
        };
    }

    // ─────────────────────────── WorldlyContainer ───────────────────────────

    @Override
    public int[] getSlotsForFace(Direction side) {
        return SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction dir) {
        return slot == SLOT_CONTAINER && (stack.is(Items.GLASS_BOTTLE) || stack.is(Items.BUCKET));
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
        return slot == SLOT_CONTAINER && (stack.is(ModItems.BEER_MUG.get()) || stack.is(ModItems.BEER_BUCKET.get()));
    }

    // ─────────────────────────── Сериализация ───────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("GthMilli", currentGthMilli);
        mash.save(tag, "Mash");
        wort.save(tag, "Wort");
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        currentGthMilli = tag.getLong("GthMilli");
        mash = MashBlend.load(tag, "Mash");
        wort = WortBlend.load(tag, "Wort");
    }
}
