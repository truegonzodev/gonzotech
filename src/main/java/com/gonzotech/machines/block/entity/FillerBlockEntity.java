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
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.component.CustomData;
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
        Sinks.FormaldehydeSink, Sinks.DistillateSink, Sinks.MashSink, Sinks.WortSink,
        Sinks.HotWaterSink, Sinks.PoisonPotionSink {

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
    private double leftMashAlcohol = 0.0;
    private double leftMashRot = 0.0;

    private int rightFluidType = FLUID_EMPTY;
    private int rightFluidAmount = 0;
    private int rightSaltMb = 0;
    private double rightMashAlcohol = 0.0;
    private double rightMashRot = 0.0;

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
        long allowed = Math.min(amount, 156L * MachineDefs.MILLI);
        long accepted = Math.min(allowed, Math.max(0, space));
        if (!simulate && accepted > 0) {
            currentGthMilli += accepted;
            setChanged();
        }
        return accepted;
    }

    @Override
    public long receiveGtu(long amount, boolean simulate) {
        long space = (long) GTU_CAPACITY * MachineDefs.MILLI - currentGtuMilli;
        long allowed = Math.min(amount, 66L * MachineDefs.MILLI);
        long accepted = Math.min(allowed, Math.max(0, space));
        if (!simulate && accepted > 0) {
            currentGtuMilli += accepted;
            setChanged();
        }
        return accepted;
    }

    private long receiveFluidToTank(int fluidId, long amount, boolean simulate) {
        if (amount <= 0) return 0;
        long maxThroughput = (fluidId == FLUID_MASH || fluidId == FLUID_FORMALDEHYDE) ? 288 : 492;
        long allowed = Math.min(amount, maxThroughput);
        // Приоритет — левый бак
        if (leftFluidType == FLUID_EMPTY || (leftFluidType == fluidId && leftFluidAmount < TANK_CAPACITY)) {
            int space = TANK_CAPACITY - leftFluidAmount;
            long accepted = Math.min(allowed, space);
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

    @Override
    public long receiveDistillate(long amount, boolean simulate) {
        return receiveFluidToTank(FLUID_DISTILLATE, amount, simulate);
    }

    @Override
    public long receiveMash(long amount, double alcoholPercent, double rotPercent, boolean simulate) {
        if (amount <= 0) return 0;
        long allowed = Math.min(amount, 288);
        if (leftFluidType == FLUID_EMPTY || (leftFluidType == FLUID_MASH && leftFluidAmount < TANK_CAPACITY)) {
            int space = TANK_CAPACITY - leftFluidAmount;
            long accepted = Math.min(allowed, space);
            if (!simulate && accepted > 0) {
                long total = leftFluidAmount + accepted;
                leftMashAlcohol = (leftMashAlcohol * leftFluidAmount + alcoholPercent * accepted) / (double) total;
                leftMashRot = (leftMashRot * leftFluidAmount + rotPercent * accepted) / (double) total;
                leftFluidType = FLUID_MASH;
                leftFluidAmount += (int) accepted;
                setChanged();
            }
            return accepted;
        }
        return 0;
    }

    @Override
    public long receiveWort(long amount, double alcoholPercent, boolean simulate) {
        return receiveFluidToTank(FLUID_WORT, amount, simulate);
    }

    @Override
    public long receiveHotWater(long amount, boolean simulate) {
        return receiveFluidToTank(FLUID_HOT_WATER, amount, simulate);
    }

    @Override
    public long receivePoisonPotion(long amount, boolean simulate) {
        return receiveFluidToTank(FLUID_POISON_POTION, amount, simulate);
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
        double tmpAlc = leftMashAlcohol;
        double tmpRot = leftMashRot;

        leftFluidType = rightFluidType;
        leftFluidAmount = rightFluidAmount;
        leftSaltMb = rightSaltMb;
        leftMashAlcohol = rightMashAlcohol;
        leftMashRot = rightMashRot;

        rightFluidType = tmpType;
        rightFluidAmount = tmpAmount;
        rightSaltMb = tmpSalt;
        rightMashAlcohol = tmpAlc;
        rightMashRot = tmpRot;

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

        // Накопление гнили в хранимой браге
        if (leftFluidType == FLUID_MASH && leftFluidAmount > 0 && leftMashRot < 98.0) {
            leftMashRot = Math.min(98.0, leftMashRot + 0.40 / 1200.0);
            leftMashAlcohol = Math.max(0.0, leftMashAlcohol - 0.10 / 1200.0);
            changed = true;
        }
        if (rightFluidType == FLUID_MASH && rightFluidAmount > 0 && rightMashRot < 98.0) {
            rightMashRot = Math.min(98.0, rightMashRot + 0.40 / 1200.0);
            rightMashAlcohol = Math.max(0.0, rightMashAlcohol - 0.10 / 1200.0);
            changed = true;
        }

        // 0. Проверка едких вёдер в слотах тары на разъедание (180 тиков)
        long gameTime = level.getGameTime();
        for (int s = 0; s < 4; s++) {
            ItemStack st = items.get(s);
            if (!st.isEmpty() && (st.getItem() instanceof com.gonzotech.core.item.CorrosiveBucketItem
                    || st.getItem() instanceof com.gonzotech.core.item.CorrosiveFluidBucketItem)) {
                if (com.gonzotech.core.item.CorrosiveBucketItem.isExpired(st, gameTime)) {
                    if (st.is(ModItems.ETHYLENE_BUCKET.get())) {
                        com.gonzotech.core.item.CorrosiveBucketItem.triggerEthyleneExplosion(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, null);
                    } else if (st.getItem() instanceof com.gonzotech.core.item.CorrosiveFluidBucketItem) {
                        level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 1.2F);
                        com.gonzotech.core.item.CorrosiveFluidBucketItem.spillAcidNear(level, pos.above());
                    }
                    items.set(s, new ItemStack(ModItems.LEAKY_BUCKET.get(), st.getCount()));
                    changed = true;
                }
            }
        }

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
        } else if (in.is(ModItems.SULFURIC_ACID_BUCKET.get()) && (tankType == FLUID_EMPTY || tankType == FLUID_SULFURIC_ACID) && tankSpace >= 1000) {
            if (canAcceptItem(out, Items.BUCKET, 1)) {
                if (tankType == FLUID_EMPTY) tankType = FLUID_SULFURIC_ACID;
                tankAmount += 1000;
                applyTankChange(isLeftTank, tankType, tankAmount, tankSalt);
                in.shrink(1);
                addOutputItem(outSlot, new ItemStack(Items.BUCKET));
                return true;
            }
        } else if (in.is(ModItems.ETHYLENE_BUCKET.get()) && (tankType == FLUID_EMPTY || tankType == FLUID_ETHYLENE) && tankSpace >= 1000) {
            if (canAcceptItem(out, Items.BUCKET, 1)) {
                if (tankType == FLUID_EMPTY) tankType = FLUID_ETHYLENE;
                tankAmount += 1000;
                applyTankChange(isLeftTank, tankType, tankAmount, tankSalt);
                in.shrink(1);
                addOutputItem(outSlot, new ItemStack(Items.BUCKET));
                return true;
            }
        } else if (in.is(ModFluids.DISTILLATE_BUCKET.get()) && (tankType == FLUID_EMPTY || tankType == FLUID_DISTILLATE) && tankSpace >= 1000) {
            if (canAcceptItem(out, Items.BUCKET, 1)) {
                if (tankType == FLUID_EMPTY) tankType = FLUID_DISTILLATE;
                tankAmount += 1000;
                applyTankChange(isLeftTank, tankType, tankAmount, tankSalt);
                in.shrink(1);
                addOutputItem(outSlot, new ItemStack(Items.BUCKET));
                return true;
            }
        } else if (in.is(ModFluids.MASH_BUCKET.get()) && (tankType == FLUID_EMPTY || tankType == FLUID_MASH) && tankSpace >= 1000) {
            if (canAcceptItem(out, Items.BUCKET, 1)) {
                CustomData d = in.get(DataComponents.CUSTOM_DATA);
                double inAlc = (d != null) ? d.copyTag().getDouble("mash_alc") : 0.0;
                double inRot = (d != null) ? d.copyTag().getDouble("mash_rot") : 0.0;
                if (isLeftTank) {
                    long total = leftFluidAmount + 1000;
                    leftMashAlcohol = (leftMashAlcohol * leftFluidAmount + inAlc * 1000) / (double) total;
                    leftMashRot = (leftMashRot * leftFluidAmount + inRot * 1000) / (double) total;
                } else {
                    long total = rightFluidAmount + 1000;
                    rightMashAlcohol = (rightMashAlcohol * rightFluidAmount + inAlc * 1000) / (double) total;
                    rightMashRot = (rightMashRot * rightFluidAmount + inRot * 1000) / (double) total;
                }
                if (tankType == FLUID_EMPTY) tankType = FLUID_MASH;
                tankAmount += 1000;
                applyTankChange(isLeftTank, tankType, tankAmount, tankSalt);
                in.shrink(1);
                addOutputItem(outSlot, new ItemStack(Items.BUCKET));
                return true;
            }
        } else if ((in.is(ModFluids.WORT_BUCKET.get()) || in.is(ModItems.BEER_BUCKET.get())) && (tankType == FLUID_EMPTY || tankType == FLUID_WORT) && tankSpace >= 1000) {
            if (canAcceptItem(out, Items.BUCKET, 1)) {
                if (tankType == FLUID_EMPTY) tankType = FLUID_WORT;
                tankAmount += 1000;
                applyTankChange(isLeftTank, tankType, tankAmount, tankSalt);
                in.shrink(1);
                addOutputItem(outSlot, new ItemStack(Items.BUCKET));
                return true;
            }
        } else if (in.is(ModItems.AMINOBLAZEETHANOL_BUCKET.get()) && (tankType == FLUID_EMPTY || tankType == FLUID_AMINOBLAZEETHANOL) && tankSpace >= 1000) {
            if (canAcceptItem(out, Items.BUCKET, 1)) {
                if (tankType == FLUID_EMPTY) tankType = FLUID_AMINOBLAZEETHANOL;
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
        } else if (in.is(ModItems.AMPOULE.get())) {
            String ampFluid = com.gonzotech.core.item.AmpouleItem.getStoredFluid(in);
            int ampAmount = com.gonzotech.core.item.AmpouleItem.getStoredAmount(in);
            int ampFluidId = fluidIdByName(ampFluid);

            if (ampAmount > 0 && ampFluidId != FLUID_EMPTY && (tankType == FLUID_EMPTY || tankType == ampFluidId) && tankSpace >= ampAmount) {
                if (tankType == FLUID_EMPTY) tankType = ampFluidId;
                tankAmount += ampAmount;
                applyTankChange(isLeftTank, tankType, tankAmount, tankSalt);
                in.shrink(1);
                // При опустошении в филлере ампула пропадает (одноразовая).
                return true;
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
            } else if (tankType == FLUID_SULFURIC_ACID && canAcceptItem(out, ModItems.SULFURIC_ACID_BUCKET.get(), 1)) {
                tankAmount -= 1000;
                if (tankAmount == 0) tankType = FLUID_EMPTY;
                applyTankChange(isLeftTank, tankType, tankAmount, 0);
                in.shrink(1);
                ItemStack corrosive = new ItemStack(ModItems.SULFURIC_ACID_BUCKET.get());
                if (level != null) {
                    com.gonzotech.core.item.CorrosiveBucketItem.initLeakAt(corrosive, level.getGameTime());
                }
                addOutputItem(outSlot, corrosive);
                return true;
            } else if (tankType == FLUID_ETHYLENE && canAcceptItem(out, ModItems.ETHYLENE_BUCKET.get(), 1)) {
                tankAmount -= 1000;
                if (tankAmount == 0) tankType = FLUID_EMPTY;
                applyTankChange(isLeftTank, tankType, tankAmount, 0);
                in.shrink(1);
                ItemStack corrosive = new ItemStack(ModItems.ETHYLENE_BUCKET.get());
                if (level != null) {
                    com.gonzotech.core.item.CorrosiveBucketItem.initLeakAt(corrosive, level.getGameTime());
                }
                addOutputItem(outSlot, corrosive);
                return true;
            } else if (tankType == FLUID_DISTILLATE && canAcceptItem(out, ModFluids.DISTILLATE_BUCKET.get(), 1)) {
                tankAmount -= 1000;
                if (tankAmount == 0) tankType = FLUID_EMPTY;
                applyTankChange(isLeftTank, tankType, tankAmount, 0);
                in.shrink(1);
                addOutputItem(outSlot, new ItemStack(ModFluids.DISTILLATE_BUCKET.get()));
                return true;
            } else if (tankType == FLUID_MASH && canAcceptItem(out, ModFluids.MASH_BUCKET.get(), 1)) {
                tankAmount -= 1000;
                double curAlc = isLeftTank ? leftMashAlcohol : rightMashAlcohol;
                double curRot = isLeftTank ? leftMashRot : rightMashRot;
                if (tankAmount == 0) {
                    tankType = FLUID_EMPTY;
                    if (isLeftTank) { leftMashAlcohol = 0.0; leftMashRot = 0.0; }
                    else { rightMashAlcohol = 0.0; rightMashRot = 0.0; }
                }
                applyTankChange(isLeftTank, tankType, tankAmount, 0);
                in.shrink(1);
                ItemStack filledBucket = new ItemStack(ModFluids.MASH_BUCKET.get());
                filledBucket.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, d -> d.update(t -> {
                    t.putDouble("mash_alc", curAlc);
                    t.putDouble("mash_rot", curRot);
                }));
                addOutputItem(outSlot, filledBucket);
                return true;
            } else if (tankType == FLUID_WORT && canAcceptItem(out, ModFluids.WORT_BUCKET.get(), 1)) {
                tankAmount -= 1000;
                if (tankAmount == 0) tankType = FLUID_EMPTY;
                applyTankChange(isLeftTank, tankType, tankAmount, 0);
                in.shrink(1);
                addOutputItem(outSlot, new ItemStack(ModFluids.WORT_BUCKET.get()));
                return true;
            } else if (tankType == FLUID_AMINOBLAZEETHANOL && canAcceptItem(out, ModItems.AMINOBLAZEETHANOL_BUCKET.get(), 1)) {
                tankAmount -= 1000;
                if (tankAmount == 0) tankType = FLUID_EMPTY;
                applyTankChange(isLeftTank, tankType, tankAmount, 0);
                in.shrink(1);
                addOutputItem(outSlot, new ItemStack(ModItems.AMINOBLAZEETHANOL_BUCKET.get()));
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
                    int fluidTypeToFill = (tankType != FLUID_EMPTY) ? tankType : canFluidId;
                    tankAmount -= fill;
                    if (tankAmount == 0) tankType = FLUID_EMPTY;
                    applyTankChange(isLeftTank, tankType, tankAmount, tankSalt);

                    ItemStack resultCan = in.copy();
                    resultCan.setCount(1);
                    CanisterItem.setFluidContent(resultCan, fluidNameById(fluidTypeToFill),
                            canAmount + fill, CanisterItem.getStoredSaltMb(in) + takenSalt);

                    in.shrink(1);
                    items.set(outSlot, resultCan);
                    return true;
                }
            }
        } else if ((in.is(ModItems.EMPTY_AMPOULE.get()) || in.is(ModItems.DURABLE_AMPOULE.get())) && tankAmount >= 128 && tankType != FLUID_EMPTY) {
            boolean durable = in.is(ModItems.DURABLE_AMPOULE.get());
            String fluidName = fluidNameById(tankType);
            ItemStack filled = com.gonzotech.core.item.AmpouleItem.createFilled(fluidName, 128, durable);

            if (out.isEmpty() || (ItemStack.isSameItemSameComponents(out, filled) && out.getCount() < out.getMaxStackSize())) {
                tankAmount -= 128;
                if (tankSalt > 0) {
                    tankSalt = (int) Math.round((double) tankSalt * tankAmount / (tankAmount + 128));
                }
                if (tankAmount == 0) tankType = FLUID_EMPTY;
                applyTankChange(isLeftTank, tankType, tankAmount, tankSalt);
                in.shrink(1);
                addOutputItem(outSlot, filled);
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

            if (hasWater && countGridItem(ModItems.SALT.get()) >= 1 && countGridItem(Items.CALCITE) >= 1
                    && countCalcium() >= 1
                    && currentGthMilli >= 24L * MachineDefs.MILLI && currentGtuMilli >= 2L * MachineDefs.MILLI) {
                consumeFromGrid(ModItems.SALT.get(), 1);
                consumeFromGrid(Items.CALCITE, 1);
                consumeCalcium(1);
                activeRecipe = 7;
                targetTankIsRight = targetIsRight;
                smeltProgress = 0;
                smeltTotal = 50;
                return true;
            }

            // 6. Формальдегид: Этанол (>=19) + 1 соль + 1 нарост + 1 уголь -> формальдегид (16 mB/t)
            if (leftFluidType == FLUID_RECTIFICATE && leftFluidAmount >= 19
                    && (rightFluidType == FLUID_EMPTY || (rightFluidType == FLUID_FORMALDEHYDE && rightFluidAmount + 16 <= TANK_CAPACITY))
                    && countGridItem(ModItems.SALT.get()) >= 1 && countGridItem(Items.NETHER_WART) >= 1
                    && (countGridItem(Items.COAL) >= 1 || countGridItem(Items.CHARCOAL) >= 1)
                    && currentGthMilli >= 16L * MachineDefs.MILLI && currentGtuMilli >= 7L * MachineDefs.MILLI) {
                consumeFromGrid(ModItems.SALT.get(), 1);
                consumeFromGrid(Items.NETHER_WART, 1);
                if (countGridItem(Items.COAL) >= 1) {
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
                    && countGridItem(Items.BLAZE_POWDER) >= 2
                    && currentGthMilli >= 2L * MachineDefs.MILLI && currentGtuMilli >= 12L * MachineDefs.MILLI) {
                consumeFromGrid(Items.BLAZE_POWDER, 2);
                activeRecipe = 5;
                smeltProgress = 0;
                smeltTotal = 180;
                return true;
            }

            // 3. Растворение кальцита: Вода в левом баке + 1 кальцит -> +5 mB соли/t (всего 100 mB)
            if (leftFluidType == FLUID_WATER && leftFluidAmount > 0 && countGridItem(Items.CALCITE) >= 1) {
                consumeFromGrid(Items.CALCITE, 1);
                activeRecipe = 3;
                smeltProgress = 0;
                smeltTotal = 20;
                return true;
            }

            // 2. Этилен: Этанол (>=19) + 2 серы + 1 алюм. пыль -> этилен (20 mB/t)
            int sulfurCount = countSulfur();
            if (leftFluidType == FLUID_RECTIFICATE && leftFluidAmount >= 19
                    && (rightFluidType == FLUID_EMPTY || (rightFluidType == FLUID_ETHYLENE && rightFluidAmount + 20 <= TANK_CAPACITY))
                    && sulfurCount >= 2 && countAluminumDust() >= 1
                    && currentGthMilli >= 32L * MachineDefs.MILLI && currentGtuMilli >= 3L * MachineDefs.MILLI) {
                consumeSulfur(2);
                consumeAluminumDust(1);
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

        // Выполнение активного рецепта:
        // Всегда проходит smeltTotal тиков, расходуя GTH и GTU на каждом тике.
        // Если не хватает GTH или GTU — шкала застревает (ждёт).
        // Жидкость расходуется, если есть в баке. Если осталось меньше минимальной порции рецепта —
        // удаляется весь грязный остаток, очищая бак.
        // Продукт добавляется в правый бак, если там есть место и тип подходит.
        switch (activeRecipe) {
            case 1 -> { // Серная кислота: 20 GTH/t, 9 GTU/t, порция 23 mB воды -> 17 mB кислоты
                if (currentGthMilli < 20L * MachineDefs.MILLI || currentGtuMilli < 9L * MachineDefs.MILLI) {
                    return false;
                }
                currentGthMilli -= 20L * MachineDefs.MILLI;
                currentGtuMilli -= 9L * MachineDefs.MILLI;

                if (leftFluidType == FLUID_WATER && leftFluidAmount > 0) {
                    if (leftFluidAmount >= 23) {
                        leftFluidAmount -= 23;
                        if (rightFluidType == FLUID_EMPTY || rightFluidType == FLUID_SULFURIC_ACID) {
                            int produce = Math.min(17, TANK_CAPACITY - rightFluidAmount);
                            if (produce > 0) {
                                rightFluidType = FLUID_SULFURIC_ACID;
                                rightFluidAmount += produce;
                            }
                        }
                    } else {
                        leftFluidAmount = 0;
                        leftFluidType = FLUID_EMPTY;
                        leftSaltMb = 0;
                    }
                    if (leftFluidAmount <= 0) {
                        leftFluidAmount = 0;
                        leftFluidType = FLUID_EMPTY;
                        leftSaltMb = 0;
                    }
                }
                smeltProgress++;
            }
            case 2 -> { // Этилен: 32 GTH/t, 3 GTU/t, порция 19 mB ректификата -> 20 mB этилена
                if (currentGthMilli < 32L * MachineDefs.MILLI || currentGtuMilli < 3L * MachineDefs.MILLI) {
                    return false;
                }
                currentGthMilli -= 32L * MachineDefs.MILLI;
                currentGtuMilli -= 3L * MachineDefs.MILLI;

                if (leftFluidType == FLUID_RECTIFICATE && leftFluidAmount > 0) {
                    if (leftFluidAmount >= 19) {
                        leftFluidAmount -= 19;
                        if (rightFluidType == FLUID_EMPTY || rightFluidType == FLUID_ETHYLENE) {
                            int produce = Math.min(20, TANK_CAPACITY - rightFluidAmount);
                            if (produce > 0) {
                                rightFluidType = FLUID_ETHYLENE;
                                rightFluidAmount += produce;
                            }
                        }
                    } else {
                        leftFluidAmount = 0;
                        leftFluidType = FLUID_EMPTY;
                    }
                    if (leftFluidAmount <= 0) {
                        leftFluidAmount = 0;
                        leftFluidType = FLUID_EMPTY;
                    }
                }
                smeltProgress++;
            }
            case 3 -> { // Растворение кальцита: 0 GTH, 0 GTU
                if (leftFluidType == FLUID_WATER && leftFluidAmount > 0) {
                    leftSaltMb = Math.min(leftFluidAmount, leftSaltMb + 5);
                }
                smeltProgress++;
            }
            case 5 -> { // Аминоблейзатанол: 2 GTH/t, 12 GTU/t, порция 19 mB ректификата -> 19 mB продукта
                if (currentGthMilli < 2L * MachineDefs.MILLI || currentGtuMilli < 12L * MachineDefs.MILLI) {
                    return false;
                }
                currentGthMilli -= 2L * MachineDefs.MILLI;
                currentGtuMilli -= 12L * MachineDefs.MILLI;

                if (leftFluidType == FLUID_RECTIFICATE && leftFluidAmount > 0) {
                    if (leftFluidAmount >= 19) {
                        leftFluidAmount -= 19;
                        if (rightFluidType == FLUID_EMPTY || rightFluidType == FLUID_AMINOBLAZEETHANOL) {
                            int produce = Math.min(19, TANK_CAPACITY - rightFluidAmount);
                            if (produce > 0) {
                                rightFluidType = FLUID_AMINOBLAZEETHANOL;
                                rightFluidAmount += produce;
                            }
                        }
                    } else {
                        leftFluidAmount = 0;
                        leftFluidType = FLUID_EMPTY;
                    }
                    if (leftFluidAmount <= 0) {
                        leftFluidAmount = 0;
                        leftFluidType = FLUID_EMPTY;
                    }
                }
                smeltProgress++;
            }
            case 6 -> { // Формальдегид: 16 GTH/t, 7 GTU/t, порция 19 mB ректификата -> 16 mB формальдегида
                if (currentGthMilli < 16L * MachineDefs.MILLI || currentGtuMilli < 7L * MachineDefs.MILLI) {
                    return false;
                }
                currentGthMilli -= 16L * MachineDefs.MILLI;
                currentGtuMilli -= 7L * MachineDefs.MILLI;

                if (leftFluidType == FLUID_RECTIFICATE && leftFluidAmount > 0) {
                    if (leftFluidAmount >= 19) {
                        leftFluidAmount -= 19;
                        if (rightFluidType == FLUID_EMPTY || rightFluidType == FLUID_FORMALDEHYDE) {
                            int produce = Math.min(16, TANK_CAPACITY - rightFluidAmount);
                            if (produce > 0) {
                                rightFluidType = FLUID_FORMALDEHYDE;
                                rightFluidAmount += produce;
                            }
                        }
                    } else {
                        leftFluidAmount = 0;
                        leftFluidType = FLUID_EMPTY;
                    }
                    if (leftFluidAmount <= 0) {
                        leftFluidAmount = 0;
                        leftFluidType = FLUID_EMPTY;
                    }
                }
                smeltProgress++;
            }
            case 7 -> { // Хлорид кальция: 24 GTH/t, 2 GTU/t, порция 12 mB воды
                if (currentGthMilli < 24L * MachineDefs.MILLI || currentGtuMilli < 2L * MachineDefs.MILLI) {
                    return false;
                }
                currentGthMilli -= 24L * MachineDefs.MILLI;
                currentGtuMilli -= 2L * MachineDefs.MILLI;

                int targetSlot = targetTankIsRight ? 3 : 1;
                if (targetTankIsRight) {
                    if (rightFluidType == FLUID_WATER && rightFluidAmount > 0) {
                        if (rightFluidAmount >= 12) {
                            rightFluidAmount -= 12;
                        } else {
                            rightFluidAmount = 0;
                            rightFluidType = FLUID_EMPTY;
                            rightSaltMb = 0;
                        }
                        if (rightFluidAmount <= 0) {
                            rightFluidAmount = 0;
                            rightFluidType = FLUID_EMPTY;
                            rightSaltMb = 0;
                        }
                    }
                } else {
                    if (leftFluidType == FLUID_WATER && leftFluidAmount > 0) {
                        if (leftFluidAmount >= 12) {
                            leftFluidAmount -= 12;
                        } else {
                            leftFluidAmount = 0;
                            leftFluidType = FLUID_EMPTY;
                            leftSaltMb = 0;
                        }
                        if (leftFluidAmount <= 0) {
                            leftFluidAmount = 0;
                            leftFluidType = FLUID_EMPTY;
                            leftSaltMb = 0;
                        }
                    }
                }
                smeltProgress++;
                if (smeltProgress >= smeltTotal) {
                    addOutputItem(targetSlot, new ItemStack(ModItems.CALCIUM_CHLORIDE.get()));
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

    private boolean isSulfur(ItemStack stack) {
        net.minecraft.world.item.Item raw = ModItems.RAW_ORE_ITEMS.get("sulfur") != null ? ModItems.RAW_ORE_ITEMS.get("sulfur").get() : null;
        net.minecraft.world.item.Item ingot = ModItems.INGOT_ITEMS.get("sulfur_ingot") != null ? ModItems.INGOT_ITEMS.get("sulfur_ingot").get() : null;
        return (raw != null && stack.is(raw)) || (ingot != null && stack.is(ingot));
    }

    private boolean isAluminumDust(ItemStack stack) {
        net.minecraft.world.item.Item dust = ModItems.DUST_ITEMS.get("aluminum_dust") != null ? ModItems.DUST_ITEMS.get("aluminum_dust").get() : null;
        return dust != null && stack.is(dust);
    }

    private boolean isCalcium(ItemStack stack) {
        net.minecraft.world.item.Item ingot = ModItems.INGOT_ITEMS.get("calcium_ingot") != null ? ModItems.INGOT_ITEMS.get("calcium_ingot").get() : null;
        net.minecraft.world.item.Item dust = ModItems.DUST_ITEMS.get("calcium_dust") != null ? ModItems.DUST_ITEMS.get("calcium_dust").get() : null;
        return (ingot != null && stack.is(ingot)) || (dust != null && stack.is(dust));
    }

    private int countGridItem(net.minecraft.world.item.Item item) {
        int total = 0;
        for (int i = 4; i <= 9; i++) {
            ItemStack stack = items.get(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private int countSulfur() {
        int total = 0;
        for (int i = 4; i <= 9; i++) {
            ItemStack stack = items.get(i);
            if (isSulfur(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private int countAluminumDust() {
        int total = 0;
        for (int i = 4; i <= 9; i++) {
            ItemStack stack = items.get(i);
            if (isAluminumDust(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private int countCalcium() {
        int total = 0;
        for (int i = 4; i <= 9; i++) {
            ItemStack stack = items.get(i);
            if (isCalcium(stack)) {
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
            if (isSulfur(stack)) {
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }
    }

    private void consumeAluminumDust(int count) {
        int left = count;
        for (int i = 4; i <= 9 && left > 0; i++) {
            ItemStack stack = items.get(i);
            if (isAluminumDust(stack)) {
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }
    }

    private void consumeCalcium(int count) {
        int left = count;
        for (int i = 4; i <= 9 && left > 0; i++) {
            ItemStack stack = items.get(i);
            if (isCalcium(stack)) {
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

        long maxDrain = (rightFluidType == FLUID_MASH || rightFluidType == FLUID_FORMALDEHYDE) ? 288 : 492;
        long budget = Math.min((long) rightFluidAmount, Math.min(pipeType.maxThroughput(), maxDrain));
        if (budget <= 0) return false;

        long moved = PipeRouting.drain(level, pos, pipeType, budget, level.getGameTime(), (be, p) -> {
            if (be == this) return null;
            return switch (rightFluidType) {
                case FLUID_WATER -> be instanceof Sinks.WaterSink s ? s::receiveWater : null;
                case FLUID_RECTIFICATE -> be instanceof Sinks.RectificateSink s ? s::receiveRectificate : null;
                case FLUID_SULFURIC_ACID -> be instanceof Sinks.SulfuricAcidSink s ? s::receiveSulfuricAcid : null;
                case FLUID_ETHYLENE -> be instanceof Sinks.EthyleneSink s ? s::receiveEthylene : null;
                case FLUID_AMINOBLAZEETHANOL -> be instanceof Sinks.AminoblazeethanolSink s ? s::receiveAminoblazeethanol : null;
                case FLUID_FORMALDEHYDE -> be instanceof Sinks.FormaldehydeSink s ? s::receiveFormaldehyde : null;
                case FLUID_MASH -> be instanceof Sinks.MashSink s ? (amt, sim) -> s.receiveMash(amt, rightMashAlcohol, rightMashRot, sim) : null;
                case FLUID_WORT -> be instanceof Sinks.WortSink s ? (amt, sim) -> s.receiveWort(amt, 0, sim) : null;
                case FLUID_DISTILLATE -> be instanceof Sinks.DistillateSink s ? s::receiveDistillate : null;
                case FLUID_HOT_WATER -> be instanceof Sinks.HotWaterSink s ? s::receiveHotWater : null;
                case FLUID_POISON_POTION -> be instanceof Sinks.PoisonPotionSink s ? s::receivePoisonPotion : null;
                default -> null;
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
        tag.putDouble("leftMashAlcohol", leftMashAlcohol);
        tag.putDouble("leftMashRot", leftMashRot);
        tag.putInt("rightFluidType", rightFluidType);
        tag.putInt("rightFluidAmount", rightFluidAmount);
        tag.putInt("rightSaltMb", rightSaltMb);
        tag.putDouble("rightMashAlcohol", rightMashAlcohol);
        tag.putDouble("rightMashRot", rightMashRot);
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
        leftMashAlcohol = tag.getDouble("leftMashAlcohol");
        leftMashRot = tag.getDouble("leftMashRot");
        rightFluidType = tag.getInt("rightFluidType");
        rightFluidAmount = tag.getInt("rightFluidAmount");
        rightSaltMb = tag.getInt("rightSaltMb");
        rightMashAlcohol = tag.getDouble("rightMashAlcohol");
        rightMashRot = tag.getDouble("rightMashRot");
        activeRecipe = tag.getInt("activeRecipe");
        smeltProgress = tag.getInt("smeltProgress");
        smeltTotal = tag.getInt("smeltTotal");
        targetTankIsRight = tag.getBoolean("targetTankIsRight");
        evapProgress = tag.getInt("evapProgress");
    }
}
