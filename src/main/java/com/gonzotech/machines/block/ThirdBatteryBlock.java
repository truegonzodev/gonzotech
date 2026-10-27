package com.gonzotech.machines.block;

import com.gonzotech.machines.block.entity.ThirdBatteryBlockEntity;
import com.gonzotech.machines.block.entity.ThirdEnergyStorageBlockEntity;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import java.util.List;

/** Canister-like full cube/orientation, but energy. Normal loot is the sole drop path. */
public final class ThirdBatteryBlock extends AccumulatorBlock implements LiquidBlockContainer {
    public static final MapCodec<ThirdBatteryBlock> CODEC=simpleCodec(ThirdBatteryBlock::new);
    public ThirdBatteryBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<ThirdBatteryBlock> codec() { return CODEC; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ThirdBatteryBlockEntity(pos,state); }
    @Override protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ThirdBatteryBlockEntity battery ? battery.comparatorOutput() : 0;
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : FireboxBlock.createTickerHelper(type,ModBlockEntities.THIRD_BATTERY.get(),ThirdEnergyStorageBlockEntity::serverTick);
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level,pos,state,placer,stack);
        if(!level.isClientSide() && level.getBlockEntity(pos) instanceof ThirdBatteryBlockEntity battery) battery.loadFromItem(stack);
    }
    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops=super.getDrops(state,params);
        if(params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof ThirdBatteryBlockEntity battery)
            for(ItemStack stack:drops) if(stack.is(asItem())) battery.saveToItem(stack);
        return drops;
    }
    @Override public boolean canPlaceLiquid(Player player, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) { return false; }
    @Override public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) { return false; }
}
