package com.gonzotech.core.block.entity;

import com.gonzotech.machines.registry.ModBlockEntities;
import com.gonzotech.machines.energy.Sinks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Блок-сущность канистры: хранит до 8000 mB одной жидкости.
 * Поддерживает приём любых жидкостей из труб (все Sink-интерфейсы).
 */
public class CanisterBlockEntity extends BlockEntity implements
        Sinks.WaterSink, Sinks.MashSink, Sinks.WortSink,
        Sinks.DistillateSink, Sinks.RectificateSink, Sinks.HotWaterSink,
        Sinks.PoisonPotionSink, Sinks.SulfuricAcidSink, Sinks.EthyleneSink,
        Sinks.AminoblazeethanolSink, Sinks.FormaldehydeSink {

    public static final int CAPACITY = 8_000;

    private String fluid = "empty";
    private int amount = 0;
    private int saltMb = 0;
    private double mashAlcohol = 0.0;
    private double mashRot = 0.0;

    public CanisterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CANISTER.get(), pos, state);
    }

    public String getFluid() {
        return fluid;
    }

    public int getAmount() {
        return amount;
    }

    public int getSaltMb() {
        return saltMb;
    }

    public int space() {
        return CAPACITY - amount;
    }

    public void setContent(String fluid, int amount, int saltMb) {
        this.fluid = (amount <= 0 || fluid == null) ? "empty" : fluid;
        this.amount = Math.max(0, Math.min(CAPACITY, amount));
        this.saltMb = "water".equals(this.fluid) ? Math.max(0, Math.min(this.amount, saltMb)) : 0;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void drain(int mb) {
        if (mb <= 0 || amount <= 0) return;
        int drained = Math.min(amount, mb);
        if ("water".equals(fluid) && amount > 0) {
            saltMb = (int) Math.round((double) saltMb * (amount - drained) / amount);
        }
        amount -= drained;
        if (amount <= 0) {
            fluid = "empty";
            saltMb = 0;
        }
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void serverTick() {
        if ("mash".equals(fluid) && amount > 0 && mashRot < 98.0) {
            mashRot = Math.min(98.0, mashRot + 0.40 / 1200.0);
            mashAlcohol = Math.max(0.0, mashAlcohol - 0.10 / 1200.0);
            setChanged();
        }
    }

    private long receiveGeneric(String fluidType, long incoming, boolean simulate) {
        if (incoming <= 0) return 0;
        if (amount > 0 && !fluid.equals(fluidType) && !"empty".equals(fluid)) {
            return 0;
        }
        long maxThroughput = (fluidType.equals("mash") || fluidType.equals("formaldehyde")) ? 288 : 492;
        long allowed = Math.min(incoming, maxThroughput);
        long accepted = Math.min(allowed, space());
        if (accepted > 0 && !simulate) {
            fluid = fluidType;
            amount += (int) accepted;
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
        return accepted;
    }

    @Override
    public long receiveWater(long incoming, boolean simulate) {
        return receiveGeneric("water", incoming, simulate);
    }

    @Override
    public long receiveMash(long incoming, double alc, double rot, boolean simulate) {
        if (incoming <= 0) return 0;
        if (amount > 0 && !"mash".equals(fluid) && !"empty".equals(fluid)) {
            return 0;
        }
        long allowed = Math.min(incoming, 288);
        long accepted = Math.min(allowed, space());
        if (accepted > 0 && !simulate) {
            long total = amount + accepted;
            mashAlcohol = (mashAlcohol * amount + alc * accepted) / (double) total;
            mashRot = (mashRot * amount + rot * accepted) / (double) total;
            fluid = "mash";
            amount += (int) accepted;
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
        return accepted;
    }

    @Override
    public long receiveWort(long incoming, double alc, boolean simulate) {
        return receiveGeneric("wort", incoming, simulate);
    }

    @Override
    public long receiveDistillate(long incoming, boolean simulate) {
        return receiveGeneric("distillate", incoming, simulate);
    }

    @Override
    public long receiveRectificate(long incoming, boolean simulate) {
        return receiveGeneric("rectificate", incoming, simulate);
    }

    @Override
    public long receiveHotWater(long incoming, boolean simulate) {
        return receiveGeneric("hot_water", incoming, simulate);
    }

    @Override
    public long receivePoisonPotion(long incoming, boolean simulate) {
        return receiveGeneric("poison_potion", incoming, simulate);
    }

    @Override
    public long receiveSulfuricAcid(long incoming, boolean simulate) {
        return receiveGeneric("sulfuric_acid", incoming, simulate);
    }

    @Override
    public long receiveEthylene(long incoming, boolean simulate) {
        return receiveGeneric("ethylene", incoming, simulate);
    }

    @Override
    public long receiveAminoblazeethanol(long incoming, boolean simulate) {
        return receiveGeneric("aminoblazeethanol", incoming, simulate);
    }

    @Override
    public long receiveFormaldehyde(long incoming, boolean simulate) {
        return receiveGeneric("formaldehyde", incoming, simulate);
    }

    public void saveToItem(ItemStack stack) {
        if (amount > 0 && !"empty".equals(fluid)) {
            stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, data -> data.update(tag -> {
                tag.putString("fluid", fluid);
                tag.putInt("amount", amount);
                tag.putInt("salt_mb", saltMb);
                tag.putDouble("mash_alc", mashAlcohol);
                tag.putDouble("mash_rot", mashRot);
            }));
        }
    }

    public void loadFromItem(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            CompoundTag tag = data.copyTag();
            String f = tag.getString("fluid");
            int a = tag.getInt("amount");
            int s = tag.getInt("salt_mb");
            mashAlcohol = tag.getDouble("mash_alc");
            mashRot = tag.getDouble("mash_rot");
            setContent(f, a, s);
        } else {
            mashAlcohol = 0.0;
            mashRot = 0.0;
            setContent("empty", 0, 0);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("fluid", fluid);
        tag.putInt("amount", amount);
        tag.putInt("salt_mb", saltMb);
        tag.putDouble("mash_alc", mashAlcohol);
        tag.putDouble("mash_rot", mashRot);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        fluid = tag.getString("fluid");
        amount = tag.getInt("amount");
        saltMb = tag.getInt("salt_mb");
        mashAlcohol = tag.getDouble("mash_alc");
        mashRot = tag.getDouble("mash_rot");
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putString("fluid", fluid);
        tag.putInt("amount", amount);
        tag.putInt("salt_mb", saltMb);
        tag.putDouble("mash_alc", mashAlcohol);
        tag.putDouble("mash_rot", mashRot);
        return tag;
    }
}
