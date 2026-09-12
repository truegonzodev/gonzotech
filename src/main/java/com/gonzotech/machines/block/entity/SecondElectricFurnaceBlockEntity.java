package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.energy.GtBuffer;
import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.SecondTierDefs;
import com.gonzotech.machines.energy.Sinks.GtuSink;
import com.gonzotech.machines.menu.SecondElectricFurnaceMenu;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Электропечь II: две независимые параллельные линии плавки.
 *
 * <p>Каждая линия имеет собственные input/output и шкалу 90 тиков. Цена одного
 * предмета остаётся ровно 220 GTU: 40 milli-GTU остатка от деления на 90 тиков
 * распределяются по рабочим шагам, а не теряются на округлении.</p>
 */
public final class SecondElectricFurnaceBlockEntity extends BaseMachineBlockEntity
    implements GtuSink, WorldlyContainer, ExperienceOutput {

    public static final int SLOT_INPUT_A = 0;
    public static final int SLOT_INPUT_B = 1;
    public static final int SLOT_OUTPUT_A = 2;
    public static final int SLOT_OUTPUT_B = 3;
    public static final int LANES = 2;

    private static final int[] INPUTS = { SLOT_INPUT_A, SLOT_INPUT_B };
    private static final int[] OUTPUTS = { SLOT_OUTPUT_A, SLOT_OUTPUT_B };
    private static final int[] SLOTS_TOP = INPUTS;
    private static final int[] SLOTS_BOTTOM = OUTPUTS;
    private static final int[] SLOTS_SIDE = INPUTS;
    private static final int SOUND_INTERVAL = 60;

    private final GtBuffer gtu = new GtBuffer((long) SecondTierDefs.ELECTRIC_GTU_CAPACITY);
    private final int[] cookProgress = new int[LANES];
    private final int[] cookTotal = new int[LANES];
    private float storedXp;
    private int soundCooldown;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> gtu.amountUnitsInt();
                case 1 -> cookProgress[0];
                case 2 -> cookTotal[0];
                case 3 -> cookProgress[1];
                case 4 -> cookTotal[1];
                default -> 0;
            };
        }

        @Override
        public void set(int i, int value) {
            switch (i) {
                case 0 -> gtu.set(MachineDefs.toMilli(value));
                case 1 -> cookProgress[0] = value;
                case 2 -> cookTotal[0] = value;
                case 3 -> cookProgress[1] = value;
                case 4 -> cookTotal[1] = value;
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return 5;
        }
    };

    public SecondElectricFurnaceBlockEntity(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        super(ModBlockEntities.SECOND_ELECTRIC_FURNACE.get(), pos, state, 4);
    }

    public GtBuffer gtuBuffer() {
        return gtu;
    }

    public ContainerData data() {
        return data;
    }

    public int cookProgress(int lane) {
        return cookProgress[lane];
    }

    public int cookTotal(int lane) {
        return cookTotal[lane];
    }

    @Override
    public long receiveGtu(long amount, boolean simulate) {
        // Неуказанный входной лимит остаётся как у печи I.
        return gtu.receive(Math.min(amount, (long) MachineDefs.ELECTRIC_GTU_INTAKE), simulate);
    }

    public static void serverTick(Level level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state,
                                  SecondElectricFurnaceBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        boolean changed = false;
        boolean workedAny = false;

        for (int lane = 0; lane < LANES; lane++) {
            int input = INPUTS[lane];
            int output = OUTPUTS[lane];
            boolean worked = false;
            boolean canSmelt = SmeltHelper.canOutput(server, be.items.get(input), be.items.get(output));
            if (canSmelt) {
                if (be.cookTotal[lane] == 0) be.cookTotal[lane] = SecondTierDefs.ELECTRIC_COOK_TIME;
                int need = energyForNextStep(be.cookProgress[lane]);
                if (be.gtu.has(need)) {
                    be.gtu.extract(need, false);
                    be.cookProgress[lane]++;
                    if (be.cookProgress[lane] >= be.cookTotal[lane]) {
                        SmeltHelper.Result result = SmeltHelper.finish(server, be.items, input, output);
                        be.storedXp += result.experience();
                        be.cookProgress[lane] = 0;
                        be.cookTotal[lane] = 0;
                        if (SmeltSideEffects.apply(server, pos, result.produced(), be)) return;
                    }
                    worked = true;
                    workedAny = true;
                    changed = true;
                }
            }

            if (!worked && (be.cookProgress[lane] != 0 || be.cookTotal[lane] != 0)) {
                be.cookProgress[lane] = Math.max(0, be.cookProgress[lane] - 2);
                if (be.cookProgress[lane] == 0) be.cookTotal[lane] = 0;
                changed = true;
            }
        }

        if (workedAny) {
            if (be.soundCooldown <= 0) {
                server.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1.0F, 1.0F);
                be.soundCooldown = SOUND_INTERVAL;
            } else {
                be.soundCooldown--;
            }
        } else {
            be.soundCooldown = 0;
        }

        if (changed) be.setChanged();
    }

    /** Exact milli-GTU charge for the next of 90 progress increments. */
    private static int energyForNextStep(int completedTicks) {
        int before = completedTicks * SecondTierDefs.ELECTRIC_GTU_MILLI_PER_TICK_REMAINDER
            / SecondTierDefs.ELECTRIC_COOK_TIME;
        int after = (completedTicks + 1) * SecondTierDefs.ELECTRIC_GTU_MILLI_PER_TICK_REMAINDER
            / SecondTierDefs.ELECTRIC_COOK_TIME;
        return SecondTierDefs.ELECTRIC_GTU_MILLI_PER_TICK_BASE + after - before;
    }

    @Override
    public void awardExperienceTo(Player player) {
        if (level instanceof ServerLevel server) {
            storedXp = SmeltHelper.awardExperience(server, player, storedXp);
            setChanged();
        }
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SLOT_INPUT_A || slot == SLOT_INPUT_B;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? SLOTS_BOTTOM : (side == Direction.UP ? SLOTS_TOP : SLOTS_SIDE);
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == SLOT_INPUT_A || slot == SLOT_INPUT_B;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == SLOT_OUTPUT_A || slot == SLOT_OUTPUT_B;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        gtu.save(tag, "Gtu");
        tag.putInt("CookProgressA", cookProgress[0]);
        tag.putInt("CookTotalA", cookTotal[0]);
        tag.putInt("CookProgressB", cookProgress[1]);
        tag.putInt("CookTotalB", cookTotal[1]);
        tag.putFloat("StoredXp", storedXp);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        gtu.load(tag, "Gtu");
        cookProgress[0] = tag.getInt("CookProgressA");
        cookTotal[0] = tag.getInt("CookTotalA");
        cookProgress[1] = tag.getInt("CookProgressB");
        cookTotal[1] = tag.getInt("CookTotalB");
        storedXp = tag.getFloat("StoredXp");
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new SecondElectricFurnaceMenu(id, inv, this, data);
    }
}
