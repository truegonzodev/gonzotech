package com.gonzotech.machines.block.entity;

import com.gonzotech.core.fluid.ModFluids;
import com.gonzotech.core.item.CanisterItem;
import com.gonzotech.core.registry.ModItems;
import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.Sinks;
import com.gonzotech.machines.menu.FillerMenu;
import com.gonzotech.machines.network.PipeRouting;
import com.gonzotech.machines.network.PipeType;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Блок-сущность Наполнителя («Открытие 3»).
 * <ul>
 *   <li>2 бака по 9000 mB (левый и правый);</li>
 *   <li>Шкалы GTH (2000) и GTU (2000);</li>
 *   <li>10 слотов инвентаря: 4 слота тары + 6 слотов сетки реагентов 2×3;</li>
 *   <li>7 рецептов химического синтеза и растворения;</li>
 *   <li>Кнопка смены баков местами за 32 GTU.</li>
 * </ul>
 */
public class FillerBlockEntity extends BaseMachineBlockEntity implements
        Sinks.GthSink, Sinks.GtuSink, Sinks.WaterSink, Sinks.RectificateSink,
        Sinks.SulfuricAcidSink, Sinks.EthyleneSink, Sinks.AminoblazeethanolSink,
        Sinks.FormaldehydeSink {

    public static final int TANK_CAPACITY = 9_000;
    public static final int GTH_CAPACITY = 2_000;
    public static final int GTU_CAPACITY = 2_000;

    public static final int FLUID_EMPTY = 0;
    public static final int FLUID_WATER = 1;
    public static final int FLUID_RECTIFICATE = 2;
    public static final int FLUID_SULFURIC_ACID = 3;
    public static final int FLUID_ETHYLENE = 4;
    public static final int FLUID_AMINOBLAZEETHANOL = 5;
    public static final int FLUID_FORMALDEHYDE = 6;
    public static final int FLUID_MASH = 7;
    public static final int FLUID_WORT = 8;
    public static final int FLUID_DISTILLATE = 9;
    public static final int FLUID_HOT_WATER = 10;
    public static final int FLUID_POISON_POTION = 11;

    private long currentGthMilli = 0;
    private long currentGtuMilli = 0;

    private int leftFluidType = FLUID_EMPTY;
    private int leftFluidAmount = 0;
    private int leftSaltMb = 0;

    private int rightFluidType = FLUID_EMPTY;
    private int rightFluidAmount = 0;
    private int rightSaltMb = 0;

    private int activeRecipe = 0;
    private int smeltProgress = 0;
    private int smeltTotal = 0;
    private boolean targetTankIsRight = false;
    private int evapProgress = 0;

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
            return FillerMenu.DATA_COUNT;
        }
    };

    public FillerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.THIRD_FILLER.get(), pos, state, 10);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.gonzotech.third_filler");
    }

    public ContainerData data() {
        return data;
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new FillerMenu(id, inv, this, data);
    }

    public int getCurrentGth() {
        return (int) (currentGthMilli / MachineDefs.MILLI);
    }

    public int getMaxGth() {
        return GTH_CAPACITY;
    }

    public int getCurrentGtu() {
        return (int) (currentGtuMilli / MachineDefs.MILLI);
    }

    public int getMaxGtu() {
        return GTU_CAPACITY;
    }

    public int getLeftFluidType() {
        return leftFluidType;
    }

    public int getLeftFluidAmount() {
        return leftFluidAmount;
    }

    public int getLeftSaltMb() {
        return leftSaltMb;
    }

    public int getRightFluidType() {
        return rightFluidType;
    }

    public int getRightFluidAmount() {
        return rightFluidAmount;
    }

    public int getRightSaltMb() {
        return rightSaltMb;
    }

    public int getSmeltProgress() {
        return smeltProgress;
    }

    public int getSmeltTotal() {
        return smeltTotal;
    }

    public int getActiveRecipe() {
        return activeRecipe;
    }

    private int getData(int i) {
        return switch (i) {
            case 0 -> getCurrentGth();
            case 1 -> getCurrentGtu();
            case 2 -> leftFluidType;
            case 3 -> leftFluidAmount;
            case 4 -> leftSaltMb;
            case 5 -> rightFluidType;
            case 6 -> rightFluidAmount;
            case 7 -> rightSaltMb;
            case 8 -> smeltProgress;
            case 9 -> smeltTotal;
            case 10 -> activeRecipe;
            default -> 0;
        };
    }

    // ─────────────────────── Sinks ───────────────────────

    @Override
    public long receiveGth(long amount, boolean simulate) {
        long space = (long) GTH_CAPACITY * MachineDefs.MILLI - currentGthMilli;
        long accepted = Math.min(amount, Math.max(0, space));
        if (!simulate && accepted > 0) {
            currentGthMilli += accepted;
            setChanged();
        }
        return accepted;
    }

    @Override
    public long receiveGtu(long amount, boolean simulate) {
        long space = (long) GTU_CAPACITY * MachineDefs.MILLI - currentGtuMilli;
        long accepted = Math.min(amount, Math.max(0, space));
        if (!simulate && accepted > 0) {
            currentGtuMilli += accepted;
            setChanged();
        }
        return accepted;
    }

    private long receiveFluidToTank(int fluidId, long amount, boolean simulate) {
        if (amount <= 0) return 0;
        // Приоритет — левый бак
        if (leftFluidType == FLUID_EMPTY || (leftFluidType == fluidId && leftFluidAmount < TANK_CAPACITY)) {
            int space = TANK_CAPACITY - leftFluidAmount;
            long accepted = Math.min(amount, space);
            if (!simulate && accepted > 0) {
                leftFluidType = fluidId;
                leftFluidAmount += (int) accepted;
                setChanged();
            }
            return accepted;
        }
        return 0;
    }

    @Override
    public long receiveWater(long amount, boolean simulate) {
        return receiveFluidToTank(FLUID_WATER, amount, simulate);
    }

    @Override
    public long receiveRectificate(long amount, boolean simulate) {
        return receiveFluidToTank(FLUID_RECTIFICATE, amount, simulate);
    }

    @Override
    public long receiveSulfuricAcid(long amount, boolean simulate) {
        return receiveFluidToTank(FLUID_SULFURIC_ACID, amount, simulate);
    }

    @Override
    public long receiveEthylene(long amount, boolean simulate) {
        return receiveFluidToTank(FLUID_ETHYLENE, amount, simulate);
    }

    @Override
    public long receiveAminoblazeethanol(long amount, boolean simulate) {
        return receiveFluidToTank(FLUID_AMINOBLAZEETHANOL, amount, simulate);
    }

    @Override
    public long receiveFormaldehyde(long amount, boolean simulate) {
        return receiveFluidToTank(FLUID_FORMALDEHYDE, amount, simulate);
    }

    // ─────────────────────── Смена баков местами ───────────────────────

    public boolean swapTanks(Player player) {
        if (currentGtuMilli < 32L * MachineDefs.MILLI) {
            return false;
        }
        currentGtuMilli -= 32L * MachineDefs.MILLI;

        int tmpType = leftFluidType;
        int tmpAmount = leftFluidAmount;
        int tmpSalt = leftSaltMb;

        leftFluidType = rightFluidType;
        leftFluidAmount = rightFluidAmount;
        leftSaltMb = rightSaltMb;

        rightFluidType = tmpType;
        rightFluidAmount = tmpAmount;
        rightSaltMb = tmpSalt;

        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.5f, 1.2f);
        }
        setChanged();
        return true;
    }

    // ─────────────────────── Tick-логика ───────────────────────

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide) return;

        boolean changed = false;

        // 1. Обработка тары в слотах
        changed |= handleContainers(0, 1, true);
        changed |= handleContainers(2, 3, false);

        // 2. Выпаривание соли (Рецепт 4)
        changed |= handleSaltEvaporation();

        // 3. Химические реакции (Рецепты 1, 2, 3, 5, 6, 7)
        changed |= handleReactions();

        // 4. Слив жидкости из правого бака в подключенные трубы
        if (rightFluidAmount > 0 && rightFluidType != FLUID_EMPTY) {
            changed |= drainRightTankToPipes(level, pos);
        }

        if (changed) {
            setChanged();
        }
    }

    // ─────────────────────── Обработка тары ───────────────────────

    private boolean handleContainers(int inSlot, int outSlot, boolean isLeftTank) {
        ItemStack in = items.get(inSlot);
        if (in.isEmpty()) return false;

        int tankType = isLeftTank ? leftFluidType : rightFluidType;
        int tankAmount = isLeftTank ? leftFluidAmount : rightFluidAmount;
        int tankSalt = isLeftTank ? leftSaltMb : rightSaltMb;
        int tankSpace = TANK_CAPACITY - tankAmount;

        ItemStack out = items.get(outSlot);

        // Ветка 1: Залив из полной тары в бак
        if (in.is(Items.WATER_BUCKET) && (tankType == FLUID_EMPTY || tankType == FLUID_WATER) && tankSpace >= 1000) {
            if (canAcceptItem(out, Items.BUCKET, 1)) {
                if (tankType == FLUID_EMPTY) tankType = FLUID_WATER;
                tankAmount += 1000;
                applyTankChange(isLeftTank, tankType, tankAmount, tankSalt);
                in.shrink(1);
                addOutputItem(outSlot, new ItemStack(Items.BUCKET));
                return true;
            }
        } else if (in.is(ModFluids.ETHANOL_BUCKET.get()) && (tankType == FLUID_EMPTY || tankType == FLUID_RECTIFICATE) && tankSpace >= 1000) {
            if (canAcceptItem(out, Items.BUCKET, 1)) {
                if (tankType == FLUID_EMPTY) tankType = FLUID_RECTIFICATE;
                tankAmount += 1000;
                applyTankChange(isLeftTank, tankType, tankAmount, tankSalt);
                in.shrink(1);
                addOutputItem(outSlot, new ItemStack(Items.BUCKET));
                return true;
            }
        } else if (in.is(ModFluids.FORMALDEHYDE_BUCKET.get()) && (tankType == FLUID_EMPTY || tankType == FLUID_FORMALDEHYDE) && tankSpace >= 1000) {
            if (canAcceptItem(out, Items.BUCKET, 1)) {
                if (tankType == FLUID_EMPTY) tankType = FLUID_FORMALDEHYDE;
                tankAmount += 1000;
                applyTankChange(isLeftTank, tankType, tankAmount, tankSalt);
                in.shrink(1);
                addOutputItem(outSlot, new ItemStack(Items.BUCKET));
                return true;
            }
        } else if (in.is(ModItems.CANISTER.get())) {
            String canFluid = CanisterItem.getStoredFluid(in);
            int canAmount = CanisterItem.getStoredAmount(in);
            int canSalt = CanisterItem.getStoredSaltMb(in);
            int canFluidId = fluidIdByName(canFluid);

            if (canAmount > 0 && canFluidId != FLUID_EMPTY && (tankType == FLUID_EMPTY || tankType == canFluidId)) {
                int transfer = Math.min(canAmount, tankSpace);
                if (transfer > 0 && out.isEmpty()) {
                    if (tankType == FLUID_EMPTY) tankType = canFluidId;
                    tankAmount += transfer;
                    if (canFluidId == FLUID_WATER && canAmount > 0) {
                        int transferredSalt = (int) Math.round((double) canSalt * transfer / canAmount);
                        tankSalt += transferredSalt;
                    }
                    applyTankChange(isLeftTank, tankType, tankAmount, tankSalt);

                    ItemStack resultCan = in.copy();
                    resultCan.setCount(1);
                    int remaining = canAmount - transfer;
                    int remainingSalt = canSalt - (int) Math.round((double) canSalt * transfer / canAmount);
                    CanisterItem.setFluidContent(resultCan, remaining > 0 ? canFluid : "empty", remaining, remainingSalt);

                    in.shrink(1);
                    items.set(outSlot, resultCan);
                    return true;
                }
            }
        }

        // Ветка 2: Наполнение пустой тары из бака
        if (in.is(Items.BUCKET) && tankAmount >= 1000) {
            if (tankType == FLUID_WATER && canAcceptItem(out, Items.WATER_BUCKET, 1)) {
                tankAmount -= 1000;
                if (tankSalt > 0) {
                    tankSalt = (int) Math.round((double) tankSalt * tankAmount / (tankAmount + 1000));
                }
                if (tankAmount == 0) tankType = FLUID_EMPTY;
                applyTankChange(isLeftTank, tankType, tankAmount, tankSalt);
                in.shrink(1);
                addOutputItem(outSlot, new ItemStack(Items.WATER_BUCKET));
                return true;
            } else if (tankType == FLUID_RECTIFICATE && canAcceptItem(out, ModFluids.ETHANOL_BUCKET.get(), 1)) {
                tankAmount -= 1000;
                if (tankAmount == 0) tankType = FLUID_EMPTY;
                applyTankChange(isLeftTank, tankType, tankAmount, 0);
                in.shrink(1);
                addOutputItem(outSlot, new ItemStack(ModFluids.ETHANOL_BUCKET.get()));
                return true;
            } else if (tankType == FLUID_FORMALDEHYDE && canAcceptItem(out, ModFluids.FORMALDEHYDE_BUCKET.get(), 1)) {
                tankAmount -= 1000;
                if (tankAmount == 0) tankType = FLUID_EMPTY;
                applyTankChange(isLeftTank, tankType, tankAmount, 0);
                in.shrink(1);
                addOutputItem(outSlot, new ItemStack(ModFluids.FORMALDEHYDE_BUCKET.get()));
                return true;
            }
        } else if (in.is(ModItems.CANISTER.get()) && tankAmount > 0 && out.isEmpty()) {
            String canFluid = CanisterItem.getStoredFluid(in);
            int canAmount = CanisterItem.getStoredAmount(in);
            int canFluidId = fluidIdByName(canFluid);

            if (canAmount == 0 || "empty".equals(canFluid) || canFluidId == tankType) {
                int canSpace = 8000 - canAmount;
                int fill = Math.min(canSpace, tankAmount);
                if (fill > 0) {
                    int takenSalt = 0;
                    if (tankType == FLUID_WATER && tankAmount > 0) {
                        takenSalt = (int) Math.round((double) tankSalt * fill / tankAmount);
                        tankSalt -= takenSalt;
                    }
                    tankAmount -= fill;
                    if (tankAmount == 0) tankType = FLUID_EMPTY;
                    applyTankChange(isLeftTank, tankType, tankAmount, tankSalt);

                    ItemStack resultCan = in.copy();
                    resultCan.setCount(1);
                    CanisterItem.setFluidContent(resultCan, fluidNameById(tankType == FLUID_EMPTY ? canFluidId : tankType),
                            canAmount + fill, CanisterItem.getStoredSaltMb(in) + takenSalt);

                    in.shrink(1);
                    items.set(outSlot, resultCan);
                    return true;
                }
            }
        } else if (in.is(Items.GLASS_BOTTLE) && tankType == FLUID_WATER && tankAmount >= 250) {
            ItemStack waterBottle = PotionContents.createItemStack(Items.POTION, Potions.WATER);
            if (canAcceptItem(out, waterBottle.getItem(), 1)) {
                tankAmount -= 250;
                if (tankAmount == 0) tankType = FLUID_EMPTY;
                applyTankChange(isLeftTank, tankType, tankAmount, tankSalt);
                in.shrink(1);
                addOutputItem(outSlot, waterBottle);
                return true;
            }
        }

        return false;
    }

    private void applyTankChange(boolean isLeft, int type, int amount, int salt) {
        if (amount <= 0) {
            type = FLUID_EMPTY;
            amount = 0;
            salt = 0;
        }
        if (isLeft) {
            leftFluidType = type;
            leftFluidAmount = amount;
            leftSaltMb = salt;
        } else {
            rightFluidType = type;
            rightFluidAmount = amount;
            rightSaltMb = salt;
        }
    }

    private boolean canAcceptItem(ItemStack slotStack, net.minecraft.world.item.Item item, int count) {
        if (slotStack.isEmpty()) return true;
        return slotStack.is(item) && slotStack.getCount() + count <= slotStack.getMaxStackSize();
    }

    private void addOutputItem(int slot, ItemStack stack) {
        ItemStack cur = items.get(slot);
        if (cur.isEmpty()) {
            items.set(slot, stack);
        } else {
            cur.grow(stack.getCount());
        }
    }

    // ─────────────────────── Выпаривание соли (Рецепт 4) ───────────────────────

    private boolean handleSaltEvaporation() {
        boolean leftHasSalt = (leftFluidType == FLUID_WATER && leftSaltMb >= 128);
        boolean rightHasSalt = (rightFluidType == FLUID_WATER && rightSaltMb >= 128);

        if (!leftHasSalt && !rightHasSalt) {
            evapProgress = 0;
            return false;
        }

        int targetSlot = leftHasSalt ? 1 : 3;
        ItemStack out = items.get(targetSlot);
        if (!canAcceptItem(out, ModItems.SALT.get(), 1)) {
            return false;
        }

        if (currentGthMilli < 11L * MachineDefs.MILLI) {
            return false;
        }

        currentGthMilli -= 11L * MachineDefs.MILLI;
        evapProgress++;

        if (evapProgress >= 80) {
            evapProgress = 0;
            if (leftHasSalt) {
                leftSaltMb -= 128;
            } else {
                rightSaltMb -= 128;
            }
            addOutputItem(targetSlot, new ItemStack(ModItems.SALT.get()));
        }
        return true;
    }

    // ─────────────────────── Химические реакции ───────────────────────

    private boolean handleReactions() {
        if (activeRecipe == 0) {
            // Поиск подходящего рецепта
            // 7. Хлорид кальция: Вода (>=12) + соль + кальцит + кальций -> нижний слот
            boolean targetIsRight = false;
            boolean hasWater = false;
            if (leftFluidType == FLUID_WATER && leftFluidAmount >= 12 && canAcceptItem(items.get(1), ModItems.CALCIUM_CHLORIDE.get(), 1)) {
                hasWater = true;
                targetIsRight = false;
            } else if (rightFluidType == FLUID_WATER && rightFluidAmount >= 12 && canAcceptItem(items.get(3), ModItems.CALCIUM_CHLORIDE.get(), 1)) {
                hasWater = true;
                targetIsRight = true;
            }

            if (hasWater && countItem(ModItems.SALT.get()) >= 1 && countItem(Items.CALCITE) >= 1
                    && (countItem(ModItems.CALCIUM_INGOT.get()) >= 1 || countItem(ModItems.CALCIUM_DUST.get()) >= 1)
                    && currentGthMilli >= 24L * MachineDefs.MILLI && currentGtuMilli >= 2L * MachineDefs.MILLI) {
                consumeFromGrid(ModItems.SALT.get(), 1);
                consumeFromGrid(Items.CALCITE, 1);
                if (countItem(ModItems.CALCIUM_INGOT.get()) >= 1) {
                    consumeFromGrid(ModItems.CALCIUM_INGOT.get(), 1);
                } else {
                    consumeFromGrid(ModItems.CALCIUM_DUST.get(), 1);
                }
                activeRecipe = 7;
                targetTankIsRight = targetIsRight;
                smeltProgress = 0;
                smeltTotal = 50;
                return true;
            }

            // 6. Формальдегид: Этанол (>=19) + 1 соль + 1 нарост + 1 уголь -> формальдегид (16 mB/t)
            if (leftFluidType == FLUID_RECTIFICATE && leftFluidAmount >= 19
                    && (rightFluidType == FLUID_EMPTY || (rightFluidType == FLUID_FORMALDEHYDE && rightFluidAmount + 16 <= TANK_CAPACITY))
                    && countItem(ModItems.SALT.get()) >= 1 && countItem(Items.NETHER_WART) >= 1
                    && (countItem(Items.COAL) >= 1 || countItem(Items.CHARCOAL) >= 1)
                    && currentGthMilli >= 16L * MachineDefs.MILLI && currentGtuMilli >= 7L * MachineDefs.MILLI) {
                consumeFromGrid(ModItems.SALT.get(), 1);
                consumeFromGrid(Items.NETHER_WART, 1);
                if (countItem(Items.COAL) >= 1) {
                    consumeFromGrid(Items.COAL, 1);
                } else {
                    consumeFromGrid(Items.CHARCOAL, 1);
                }
                activeRecipe = 6;
                smeltProgress = 0;
                smeltTotal = 110;
                return true;
            }

            // 5. Аминоблейзатанол: Этанол (>=19) + 2 порошка блейза -> аминоблейзатанол (19 mB/t)
            if (leftFluidType == FLUID_RECTIFICATE && leftFluidAmount >= 19
                    && (rightFluidType == FLUID_EMPTY || (rightFluidType == FLUID_AMINOBLAZEETHANOL && rightFluidAmount + 19 <= TANK_CAPACITY))
                    && countItem(Items.BLAZE_POWDER) >= 2
                    && currentGthMilli >= 2L * MachineDefs.MILLI && currentGtuMilli >= 12L * MachineDefs.MILLI) {
                consumeFromGrid(Items.BLAZE_POWDER, 2);
                activeRecipe = 5;
                smeltProgress = 0;
                smeltTotal = 180;
                return true;
            }

            // 3. Растворение кальцита: Вода в левом баке + 1 кальцит -> +5 mB соли/t (всего 100 mB)
            if (leftFluidType == FLUID_WATER && leftFluidAmount > 0 && countItem(Items.CALCITE) >= 1) {
                consumeFromGrid(Items.CALCITE, 1);
                activeRecipe = 3;
                smeltProgress = 0;
                smeltTotal = 20;
                return true;
            }

            // 2. Этилен: Этанол (>=19) + 2 серы + 1 алюм. пыль -> этилен (20 mB/t)
            int sulfurCount = countItem(ModItems.RAW_SULFUR.get()) + countItem(ModItems.SULFUR_INGOT.get());
            if (leftFluidType == FLUID_RECTIFICATE && leftFluidAmount >= 19
                    && (rightFluidType == FLUID_EMPTY || (rightFluidType == FLUID_ETHYLENE && rightFluidAmount + 20 <= TANK_CAPACITY))
                    && sulfurCount >= 2 && countItem(ModItems.ALUMINUM_DUST.get()) >= 1
                    && currentGthMilli >= 32L * MachineDefs.MILLI && currentGtuMilli >= 3L * MachineDefs.MILLI) {
                consumeSulfur(2);
                consumeFromGrid(ModItems.ALUMINUM_DUST.get(), 1);
                activeRecipe = 2;
                smeltProgress = 0;
                smeltTotal = 100;
                return true;
            }

            // 1. Серная кислота: Вода (>=23) + 1 сера -> серная кислота (17 mB/t)
            if (leftFluidType == FLUID_WATER && leftFluidAmount >= 23
                    && (rightFluidType == FLUID_EMPTY || (rightFluidType == FLUID_SULFURIC_ACID && rightFluidAmount + 17 <= TANK_CAPACITY))
                    && sulfurCount >= 1
                    && currentGthMilli >= 20L * MachineDefs.MILLI && currentGtuMilli >= 9L * MachineDefs.MILLI) {
                consumeSulfur(1);
                activeRecipe = 1;
                smeltProgress = 0;
                smeltTotal = 60;
                return true;
            }

            return false;
        }

        // Выполнение активного рецепта
        switch (activeRecipe) {
            case 1 -> { // Серная кислота
                if (currentGthMilli >= 20L * MachineDefs.MILLI && currentGtuMilli >= 9L * MachineDefs.MILLI
                        && leftFluidType == FLUID_WATER && leftFluidAmount >= 23
                        && (rightFluidType == FLUID_EMPTY || (rightFluidType == FLUID_SULFURIC_ACID && rightFluidAmount + 17 <= TANK_CAPACITY))) {
                    currentGthMilli -= 20L * MachineDefs.MILLI;
                    currentGtuMilli -= 9L * MachineDefs.MILLI;
                    leftFluidAmount -= 23;
                    if (leftFluidAmount <= 0) { leftFluidType = FLUID_EMPTY; leftFluidAmount = 0; leftSaltMb = 0; }
                    rightFluidType = FLUID_SULFURIC_ACID;
                    rightFluidAmount += 17;
                    smeltProgress++;
                }
            }
            case 2 -> { // Этилен
                if (currentGthMilli >= 32L * MachineDefs.MILLI && currentGtuMilli >= 3L * MachineDefs.MILLI
                        && leftFluidType == FLUID_RECTIFICATE && leftFluidAmount >= 19
                        && (rightFluidType == FLUID_EMPTY || (rightFluidType == FLUID_ETHYLENE && rightFluidAmount + 20 <= TANK_CAPACITY))) {
                    currentGthMilli -= 32L * MachineDefs.MILLI;
                    currentGtuMilli -= 3L * MachineDefs.MILLI;
                    leftFluidAmount -= 19;
                    if (leftFluidAmount <= 0) { leftFluidType = FLUID_EMPTY; leftFluidAmount = 0; }
                    rightFluidType = FLUID_ETHYLENE;
                    rightFluidAmount += 20;
                    smeltProgress++;
                }
            }
            case 3 -> { // Растворение кальцита
                if (leftFluidType == FLUID_WATER && leftFluidAmount > 0) {
                    leftSaltMb = Math.min(leftFluidAmount, leftSaltMb + 5);
                    smeltProgress++;
                }
            }
            case 5 -> { // Аминоблейзатанол
                if (currentGthMilli >= 2L * MachineDefs.MILLI && currentGtuMilli >= 12L * MachineDefs.MILLI
                        && leftFluidType == FLUID_RECTIFICATE && leftFluidAmount >= 19
                        && (rightFluidType == FLUID_EMPTY || (rightFluidType == FLUID_AMINOBLAZEETHANOL && rightFluidAmount + 19 <= TANK_CAPACITY))) {
                    currentGthMilli -= 2L * MachineDefs.MILLI;
                    currentGtuMilli -= 12L * MachineDefs.MILLI;
                    leftFluidAmount -= 19;
                    if (leftFluidAmount <= 0) { leftFluidType = FLUID_EMPTY; leftFluidAmount = 0; }
                    rightFluidType = FLUID_AMINOBLAZEETHANOL;
                    rightFluidAmount += 19;
                    smeltProgress++;
                }
            }
            case 6 -> { // Формальдегид
                if (currentGthMilli >= 16L * MachineDefs.MILLI && currentGtuMilli >= 7L * MachineDefs.MILLI
                        && leftFluidType == FLUID_RECTIFICATE && leftFluidAmount >= 19
                        && (rightFluidType == FLUID_EMPTY || (rightFluidType == FLUID_FORMALDEHYDE && rightFluidAmount + 16 <= TANK_CAPACITY))) {
                    currentGthMilli -= 16L * MachineDefs.MILLI;
                    currentGtuMilli -= 7L * MachineDefs.MILLI;
                    leftFluidAmount -= 19;
                    if (leftFluidAmount <= 0) { leftFluidType = FLUID_EMPTY; leftFluidAmount = 0; }
                    rightFluidType = FLUID_FORMALDEHYDE;
                    rightFluidAmount += 16;
                    smeltProgress++;
                }
            }
            case 7 -> { // Хлорид кальция
                int targetSlot = targetTankIsRight ? 3 : 1;
                int tankAmt = targetTankIsRight ? rightFluidAmount : leftFluidAmount;
                int tankTyp = targetTankIsRight ? rightFluidType : leftFluidType;

                if (currentGthMilli >= 24L * MachineDefs.MILLI && currentGtuMilli >= 2L * MachineDefs.MILLI
                        && tankTyp == FLUID_WATER && tankAmt >= 12
                        && canAcceptItem(items.get(targetSlot), ModItems.CALCIUM_CHLORIDE.get(), 1)) {
                    currentGthMilli -= 24L * MachineDefs.MILLI;
                    currentGtuMilli -= 2L * MachineDefs.MILLI;
                    if (targetTankIsRight) {
                        rightFluidAmount -= 12;
                        if (rightFluidAmount <= 0) { rightFluidType = FLUID_EMPTY; rightFluidAmount = 0; rightSaltMb = 0; }
                    } else {
                        leftFluidAmount -= 12;
                        if (leftFluidAmount <= 0) { leftFluidType = FLUID_EMPTY; leftFluidAmount = 0; leftSaltMb = 0; }
                    }
                    smeltProgress++;
                    if (smeltProgress >= smeltTotal) {
                        addOutputItem(targetSlot, new ItemStack(ModItems.CALCIUM_CHLORIDE.get()));
                    }
                }
            }
        }

        if (smeltProgress >= smeltTotal) {
            activeRecipe = 0;
            smeltProgress = 0;
            smeltTotal = 0;
        }

        return true;
    }

    private int countItem(net.minecraft.world.item.Item item) {
        int total = 0;
        for (int i = 4; i <= 9; i++) {
            ItemStack stack = items.get(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private void consumeFromGrid(net.minecraft.world.item.Item item, int count) {
        int left = count;
        for (int i = 4; i <= 9 && left > 0; i++) {
            ItemStack stack = items.get(i);
            if (stack.is(item)) {
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }
    }

    private void consumeSulfur(int count) {
        int left = count;
        for (int i = 4; i <= 9 && left > 0; i++) {
            ItemStack stack = items.get(i);
            if (stack.is(ModItems.RAW_SULFUR.get()) || stack.is(ModItems.SULFUR_INGOT.get())) {
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }
    }

    // ─────────────────────── Слив в трубы ───────────────────────

    private boolean drainRightTankToPipes(Level level, BlockPos pos) {
        PipeType pipeType = switch (rightFluidType) {
            case FLUID_WATER -> PipeType.WATER;
            case FLUID_RECTIFICATE -> PipeType.RECTIFICATE;
            case FLUID_SULFURIC_ACID -> PipeType.SULFURIC_ACID;
            case FLUID_ETHYLENE -> PipeType.ETHYLENE;
            case FLUID_AMINOBLAZEETHANOL -> PipeType.AMINOBLAZEETHANOL;
            case FLUID_FORMALDEHYDE -> PipeType.FORMALDEHYDE;
            case FLUID_MASH -> PipeType.MASH;
            case FLUID_WORT -> PipeType.WORT;
            case FLUID_DISTILLATE -> PipeType.DISTILLATE;
            case FLUID_HOT_WATER -> PipeType.BOILING_WATER;
            case FLUID_POISON_POTION -> PipeType.POISON_POTION;
            default -> null;
        };
        if (pipeType == null) return false;

        long budget = Math.min(rightFluidAmount, pipeType.maxThroughput());
        if (budget <= 0) return false;

        long moved = PipeRouting.drain(level, pos, pipeType, budget, level.getGameTime(), (be, p) -> {
            return switch (rightFluidType) {
                case FLUID_WATER -> be instanceof Sinks.WaterSink s ? s.receiveWater(p, false) : 0;
                case FLUID_RECTIFICATE -> be instanceof Sinks.RectificateSink s ? s.receiveRectificate(p, false) : 0;
                case FLUID_SULFURIC_ACID -> be instanceof Sinks.SulfuricAcidSink s ? s.receiveSulfuricAcid(p, false) : 0;
                case FLUID_ETHYLENE -> be instanceof Sinks.EthyleneSink s ? s.receiveEthylene(p, false) : 0;
                case FLUID_AMINOBLAZEETHANOL -> be instanceof Sinks.AminoblazeethanolSink s ? s.receiveAminoblazeethanol(p, false) : 0;
                case FLUID_FORMALDEHYDE -> be instanceof Sinks.FormaldehydeSink s ? s.receiveFormaldehyde(p, false) : 0;
                case FLUID_MASH -> be instanceof Sinks.MashSink s ? s.receiveMash(p, 0, 0, false) : 0;
                case FLUID_WORT -> be instanceof Sinks.WortSink s ? s.receiveWort(p, 0, false) : 0;
                case FLUID_DISTILLATE -> be instanceof Sinks.DistillateSink s ? s.receiveDistillate(p, false) : 0;
                case FLUID_HOT_WATER -> be instanceof Sinks.HotWaterSink s ? s.receiveHotWater(p, false) : 0;
                case FLUID_POISON_POTION -> be instanceof Sinks.PoisonPotionSink s ? s.receivePoisonPotion(p, false) : 0;
                default -> 0;
            };
        });

        if (moved > 0) {
            rightFluidAmount -= (int) moved;
            if (rightFluidAmount <= 0) {
                rightFluidType = FLUID_EMPTY;
                rightFluidAmount = 0;
                rightSaltMb = 0;
            }
            return true;
        }
        return false;
    }

    public static int fluidIdByName(String name) {
        if (name == null) return FLUID_EMPTY;
        return switch (name) {
            case "water" -> FLUID_WATER;
            case "rectificate", "ethanol" -> FLUID_RECTIFICATE;
            case "sulfuric_acid" -> FLUID_SULFURIC_ACID;
            case "ethylene" -> FLUID_ETHYLENE;
            case "aminoblazeethanol" -> FLUID_AMINOBLAZEETHANOL;
            case "formaldehyde" -> FLUID_FORMALDEHYDE;
            case "mash" -> FLUID_MASH;
            case "wort" -> FLUID_WORT;
            case "distillate" -> FLUID_DISTILLATE;
            case "hot_water" -> FLUID_HOT_WATER;
            case "poison_potion" -> FLUID_POISON_POTION;
            default -> FLUID_EMPTY;
        };
    }

    public static String fluidNameById(int id) {
        return switch (id) {
            case FLUID_WATER -> "water";
            case FLUID_RECTIFICATE -> "rectificate";
            case FLUID_SULFURIC_ACID -> "sulfuric_acid";
            case FLUID_ETHYLENE -> "ethylene";
            case FLUID_AMINOBLAZEETHANOL -> "aminoblazeethanol";
            case FLUID_FORMALDEHYDE -> "formaldehyde";
            case FLUID_MASH -> "mash";
            case FLUID_WORT -> "wort";
            case FLUID_DISTILLATE -> "distillate";
            case FLUID_HOT_WATER -> "hot_water";
            case FLUID_POISON_POTION -> "poison_potion";
            default -> "empty";
        };
    }

    // ─────────────────────── NBT ───────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("currentGthMilli", currentGthMilli);
        tag.putLong("currentGtuMilli", currentGtuMilli);
        tag.putInt("leftFluidType", leftFluidType);
        tag.putInt("leftFluidAmount", leftFluidAmount);
        tag.putInt("leftSaltMb", leftSaltMb);
        tag.putInt("rightFluidType", rightFluidType);
        tag.putInt("rightFluidAmount", rightFluidAmount);
        tag.putInt("rightSaltMb", rightSaltMb);
        tag.putInt("activeRecipe", activeRecipe);
        tag.putInt("smeltProgress", smeltProgress);
        tag.putInt("smeltTotal", smeltTotal);
        tag.putBoolean("targetTankIsRight", targetTankIsRight);
        tag.putInt("evapProgress", evapProgress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        currentGthMilli = tag.getLong("currentGthMilli");
        currentGtuMilli = tag.getLong("currentGtuMilli");
        leftFluidType = tag.getInt("leftFluidType");
        leftFluidAmount = tag.getInt("leftFluidAmount");
        leftSaltMb = tag.getInt("leftSaltMb");
        rightFluidType = tag.getInt("rightFluidType");
        rightFluidAmount = tag.getInt("rightFluidAmount");
        rightSaltMb = tag.getInt("rightSaltMb");
        activeRecipe = tag.getInt("activeRecipe");
        smeltProgress = tag.getInt("smeltProgress");
        smeltTotal = tag.getInt("smeltTotal");
        targetTankIsRight = tag.getBoolean("targetTankIsRight");
        evapProgress = tag.getInt("evapProgress");
    }
}
