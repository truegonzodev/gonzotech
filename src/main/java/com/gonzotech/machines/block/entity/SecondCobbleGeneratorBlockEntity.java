package com.gonzotech.machines.block.entity;

import com.gonzotech.core.ore.OreDefinition;
import com.gonzotech.core.registry.ModBlocks;
import com.gonzotech.machines.energy.GtBuffer;
import com.gonzotech.machines.energy.ResourceBuffer;
import com.gonzotech.machines.energy.SecondTierDefs;
import com.gonzotech.machines.energy.Sinks.GtuSink;
import com.gonzotech.machines.energy.Sinks.WaterSink;
import com.gonzotech.machines.menu.SecondCobbleGeneratorMenu;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Генератор булыжника II: один слот-модификатор ведра лавы и выходной слот.
 *
 * <p>Кирка намеренно убрана: цикл всегда занимает 60 тиков. Ведро лавы остаётся
 * требованием и не тратится, но больше не имеет случайного превращения в
 * обсидиан. Редкие результаты готового цикла сохраняют правила первого уровня.</p>
 */
public final class SecondCobbleGeneratorBlockEntity extends BaseMachineBlockEntity
    implements GtuSink, WaterSink, WorldlyContainer {

    public static final int SLOT_LAVA = 0;
    public static final int SLOT_OUTPUT = 1;
    private static final int[] SLOTS_ALL = { SLOT_LAVA, SLOT_OUTPUT };

    private final GtBuffer gtu = new GtBuffer((long) SecondTierDefs.COBBLE_GTU_CAPACITY);
    private final ResourceBuffer water = new ResourceBuffer(SecondTierDefs.COBBLE_WATER_CAPACITY);
    private boolean digging;
    private int digProgress;
    private int digTotal = SecondTierDefs.COBBLE_TICKS;
    private ItemStack pendingResult = ItemStack.EMPTY;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> gtu.amountUnitsInt();
                case 1 -> water.amount();
                case 2 -> digProgress;
                case 3 -> digTotal;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int value) {
            switch (i) {
                case 0 -> gtu.set((long) value * 1_000L);
                case 1 -> water.set(value);
                case 2 -> digProgress = value;
                case 3 -> digTotal = value;
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    public SecondCobbleGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SECOND_COBBLE_GENERATOR.get(), pos, state, 2);
    }

    public ContainerData data() {
        return data;
    }

    @Override
    public long receiveGtu(long amount, boolean simulate) {
        return gtu.receive(Math.min(amount, (long) SecondTierDefs.COBBLE_GTU_INTAKE), simulate);
    }

    @Override
    public long receiveWater(long amount, boolean simulate) {
        return water.receive(Math.min(amount, (long) SecondTierDefs.COBBLE_WATER_INTAKE), simulate);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SecondCobbleGeneratorBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        boolean changed = false;

        if (!be.pendingResult.isEmpty() && be.tryOutputPending()) changed = true;
        if (!be.digging && be.pendingResult.isEmpty() && be.hasLavaBucket()
            && be.water.amount() >= SecondTierDefs.COBBLE_WATER_PER_ROCK) {
            be.water.extract(SecondTierDefs.COBBLE_WATER_PER_ROCK, false);
            be.digging = true;
            be.digProgress = 0;
            be.digTotal = SecondTierDefs.COBBLE_TICKS;
            changed = true;
        }
        if (be.digging && be.hasLavaBucket() && be.gtu.has(SecondTierDefs.COBBLE_GTU_MILLI_PER_TICK)) {
            be.gtu.extract(SecondTierDefs.COBBLE_GTU_MILLI_PER_TICK, false);
            be.digProgress++;
            changed = true;
            if (be.digProgress >= be.digTotal) {
                be.digging = false;
                be.digProgress = 0;
                be.digTotal = SecondTierDefs.COBBLE_TICKS;
                be.pendingResult = be.rollResult(server.random);
                if (be.tryOutputPending()) changed = true;
            }
        }
        if (changed) be.setChanged();
    }

    private boolean hasLavaBucket() {
        return items.get(SLOT_LAVA).is(Items.LAVA_BUCKET);
    }

    /** First-tier output rarity table, unchanged for level II. */
    private ItemStack rollResult(RandomSource rng) {
        double roll = rng.nextDouble();
        double threshold = com.gonzotech.machines.energy.MachineDefs.COBBLE_CHANCE_COAL_ORE;
        if (roll < threshold) return new ItemStack(Blocks.COAL_ORE);
        threshold += com.gonzotech.machines.energy.MachineDefs.COBBLE_CHANCE_IRON_ORE;
        if (roll < threshold) return new ItemStack(Blocks.IRON_ORE);
        threshold += com.gonzotech.machines.energy.MachineDefs.COBBLE_CHANCE_OBSIDIAN;
        if (roll < threshold) return new ItemStack(Blocks.OBSIDIAN);
        threshold += com.gonzotech.machines.energy.MachineDefs.COBBLE_CHANCE_GONZO_STONE_ORE;
        if (roll < threshold) {
            ItemStack ore = randomGonzoStoneOre(rng);
            if (!ore.isEmpty()) return ore;
        }
        return new ItemStack(Blocks.COBBLESTONE);
    }

    private static ItemStack randomGonzoStoneOre(RandomSource rng) {
        List<Block> ores = new ArrayList<>();
        for (OreDefinition ore : OreDefinition.ALL) {
            if (!ore.hosts().contains(OreDefinition.Host.STONE)) continue;
            var byHost = ModBlocks.ORE_BLOCKS.get(ore.id());
            if (byHost == null) continue;
            var block = byHost.get(OreDefinition.Host.STONE);
            if (block != null) ores.add(block.get());
        }
        return ores.isEmpty() ? ItemStack.EMPTY : new ItemStack(ores.get(rng.nextInt(ores.size())));
    }

    private boolean tryOutputPending() {
        if (pendingResult.isEmpty()) return false;
        ItemStack output = items.get(SLOT_OUTPUT);
        if (output.isEmpty()) {
            items.set(SLOT_OUTPUT, pendingResult);
            pendingResult = ItemStack.EMPTY;
            return true;
        }
        if (ItemStack.isSameItemSameComponents(output, pendingResult)
            && output.getCount() < output.getMaxStackSize()) {
            output.grow(1);
            pendingResult = ItemStack.EMPTY;
            return true;
        }
        return false;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        gtu.save(tag, "Gtu");
        water.save(tag, "Water");
        tag.putBoolean("Digging", digging);
        tag.putInt("DigProgress", digProgress);
        tag.putInt("DigTotal", digTotal);
        if (!pendingResult.isEmpty()) tag.put("PendingResult", pendingResult.save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        gtu.load(tag, "Gtu");
        water.load(tag, "Water");
        digging = tag.getBoolean("Digging");
        digProgress = tag.getInt("DigProgress");
        digTotal = tag.contains("DigTotal") ? tag.getInt("DigTotal") : SecondTierDefs.COBBLE_TICKS;
        pendingResult = tag.contains("PendingResult")
            ? ItemStack.parseOptional(registries, tag.getCompound("PendingResult"))
            : ItemStack.EMPTY;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SLOT_LAVA && stack.is(Items.LAVA_BUCKET);
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
        return slot == SLOT_OUTPUT;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new SecondCobbleGeneratorMenu(id, inv, this, data);
    }
}
