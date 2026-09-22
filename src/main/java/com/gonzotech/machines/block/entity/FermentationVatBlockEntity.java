package com.gonzotech.machines.block.entity;

import com.gonzotech.core.registry.ModBlocks;
import com.gonzotech.machines.energy.FluidBlends.MashBlend;
import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.Sinks;
import com.gonzotech.machines.energy.Sinks.GthSink;
import com.gonzotech.machines.menu.FermentationVatMenu;
import com.gonzotech.machines.network.PipeRouting;
import com.gonzotech.machines.network.PipeType;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Блок-сущность Бродильного чана (Эпоха III, «Открытие 3»).
 * <ul>
 *   <li>1 слот растительной органики (не мясо) → мгновенное поглощение в 128 mB браги (0% спирта, 0% гнили).</li>
 *   <li>Бак браги: 16 384 mB ({@link MashBlend}).</li>
 *   <li>Базовый буфер тепла: 5 040 GTH (+30 000 GTH за каждый смежный блок вольфрама).</li>
 *   <li>Постоянный расход тепла: 68 GTH/t.</li>
 *   <li>Брожение при GTH &ge; 90%: +0.56% спирта в 4000 mB/мин (потолок 13%).</li>
 *   <li>Штраф при GTH &lt; 90%: -0.01%/мин спирта и +0.03%/мин гнили за каждый 1% нехватки (гниль до 98%).</li>
 *   <li>Автовыдача браги в трубы и приёмники: до 312 mB/t.</li>
 * </ul>
 */
public class FermentationVatBlockEntity extends BaseMachineBlockEntity implements GthSink, WorldlyContainer {

    public static final int SLOT_ORGANIC = 0;
    public static final int SLOT_COUNT = 1;
    private static final int[] SLOTS = { SLOT_ORGANIC };

    public static final int MASH_CAPACITY = 16_384;
    public static final int BASE_GTH_CAPACITY = 5_040;
    public static final int TUNGSTEN_GTH_BONUS = 30_000;
    public static final int GTH_CONSUMPTION_PER_TICK = 68;
    public static final int MAX_DRAIN_PER_TICK = 312;

    public static final double MAX_ALCOHOL = 13.0;
    public static final double MAX_ROT = 98.0;

    private static final int GTH_PACKET_BASE = 10_000;

    private long currentGthMilli = 0;
    private MashBlend mash = MashBlend.EMPTY;

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
            return FermentationVatMenu.DATA_COUNT;
        }
    };

    public FermentationVatBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.THIRD_FERMENTATION_VAT.get(), pos, state, SLOT_COUNT);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.gonzotech.third_fermentation_vat");
    }

    public ContainerData data() {
        return data;
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new FermentationVatMenu(id, inv, this, data);
    }

    public MashBlend getMash() {
        return mash;
    }

    public int countAdjacentTungsten() {
        if (level == null) return 0;
        int count = 0;
        Block tungsten = ModBlocks.METAL_BLOCKS.get("tungsten_block").get();
        for (Direction dir : Direction.values()) {
            if (level.getBlockState(worldPosition.relative(dir)).is(tungsten)) {
                count++;
            }
        }
        return count;
    }

    public int getMaxGth() {
        return BASE_GTH_CAPACITY + countAdjacentTungsten() * TUNGSTEN_GTH_BONUS;
    }

    public int getCurrentGth() {
        return (int) (currentGthMilli / MachineDefs.MILLI);
    }

    public static boolean isPlantOrganic(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(ItemTags.MEAT)) return false;
        if (stack.is(Items.ROTTEN_FLESH) || stack.is(Items.SPIDER_EYE) || stack.is(Items.FERMENTED_SPIDER_EYE)
            || stack.is(Items.COD) || stack.is(Items.COOKED_COD)
            || stack.is(Items.SALMON) || stack.is(Items.COOKED_SALMON)
            || stack.is(Items.TROPICAL_FISH) || stack.is(Items.PUFFERFISH)) {
            return false;
        }
        if (ComposterBlock.COMPOSTABLES.containsKey(stack.getItem())) return true;
        if (stack.has(DataComponents.FOOD)) return true;
        return stack.is(ItemTags.SAPLINGS) || stack.is(ItemTags.LEAVES)
            || stack.is(ItemTags.FLOWERS) || stack.is(ItemTags.CROPS);
    }

    // ─────────────────────────── GthSink ───────────────────────────

    @Override
    public long receiveGth(long amountMilli, boolean simulate) {
        long maxMilli = (long) getMaxGth() * MachineDefs.MILLI;
        long space = Math.max(0, maxMilli - currentGthMilli);
        long accepted = Math.min(amountMilli, space);
        if (!simulate && accepted > 0) {
            currentGthMilli += accepted;
            setChanged();
        }
        return accepted;
    }

    // ─────────────────────────── Тик ───────────────────────────

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide()) return;
        boolean changed = false;

        // 1. Поглощение органики из слота (128 mB браги с 0% спирта и 0% гнили).
        ItemStack organic = items.get(SLOT_ORGANIC);
        long space = MASH_CAPACITY - mash.amount();
        if (!organic.isEmpty() && isPlantOrganic(organic) && space >= 128) {
            organic.shrink(1);
            mash = mash.withAdded(128, 0.0, 0.0);
            changed = true;
        }

        // 2. Постоянный расход тепла 68 GTH/t.
        long burn = Math.min(currentGthMilli, (long) GTH_CONSUMPTION_PER_TICK * MachineDefs.MILLI);
        if (burn > 0) {
            currentGthMilli -= burn;
            changed = true;
        }

        // 3. Брожение / штрафы при наличии браги.
        if (!mash.isEmpty()) {
            int maxGth = getMaxGth();
            double heatRatio = maxGth > 0 ? (double) currentGthMilli / ((double) maxGth * MachineDefs.MILLI) : 0.0;
            double heatPercent = heatRatio * 100.0;

            if (heatPercent >= 90.0) {
                // Скорость 0.56% спирта в 4000 mB/мин (1200 тиков).
                double rate = 0.56 * (4000.0 / Math.max(4000.0, (double) mash.amount()));
                double alcGain = rate / 1200.0;
                double newAlc = Math.min(MAX_ALCOHOL, mash.alcoholPercent() + alcGain);
                mash = mash.withAlcoholAndRot(newAlc, mash.rotPercent());
            } else {
                // Штраф: -0.01%/мин спирта и +0.03%/мин гнили за 1% нехватки ниже 90%.
                double shortage = 90.0 - heatPercent;
                double alcLoss = (0.01 * shortage) / 1200.0;
                double rotGain = (0.03 * shortage) / 1200.0;
                double newAlc = Math.max(0.0, mash.alcoholPercent() - alcLoss);
                double newRot = Math.min(MAX_ROT, mash.rotPercent() + rotGain);
                mash = mash.withAlcoholAndRot(newAlc, newRot);
            }
            changed = true;
        }

        // 4. Автослив браги до 312 mB/t.
        if (!mash.isEmpty()) {
            long budget = Math.min((long) MAX_DRAIN_PER_TICK, mash.amount());
            long moved = PipeRouting.drain(level, pos, PipeType.MASH, budget, level.getGameTime(), (beTarget, p) -> {
                if (beTarget instanceof FermentationVatBlockEntity) return null;
                if (beTarget instanceof Sinks.MashSink sink) {
                    return (amount, simulate) -> sink.receiveMash(amount, mash.alcoholPercent(), mash.rotPercent(), simulate);
                }
                return null;
            });
            if (moved > 0) {
                mash = mash.withExtracted(moved);
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
        int maxGth = getMaxGth();
        return switch (index) {
            case 0 -> gth % GTH_PACKET_BASE;
            case 1 -> gth / GTH_PACKET_BASE;
            case 2 -> maxGth % GTH_PACKET_BASE;
            case 3 -> maxGth / GTH_PACKET_BASE;
            case 4 -> (int) Math.min(Integer.MAX_VALUE, mash.amount());
            case 5 -> (int) Math.round(mash.alcoholPercent() * 100.0);
            case 6 -> (int) Math.round(mash.rotPercent() * 100.0);
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
        return slot == SLOT_ORGANIC && isPlantOrganic(stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
        return false;
    }

    // ─────────────────────────── Сериализация ───────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("GthMilli", currentGthMilli);
        mash.save(tag, "Mash");
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        currentGthMilli = tag.getLong("GthMilli");
        mash = MashBlend.load(tag, "Mash");
    }
}
