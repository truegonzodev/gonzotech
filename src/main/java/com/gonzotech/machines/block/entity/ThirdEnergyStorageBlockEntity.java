package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.energy.ThirdStorageDefs;
import com.gonzotech.machines.energy.TickEnergyStorage;
import com.gonzotech.machines.energy.Sinks.GtuSink;
import com.gonzotech.machines.network.PipeRouting;
import com.gonzotech.machines.network.PipeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Shared finite store for III accumulator and portable battery, not a new FE system. */
public abstract class ThirdEnergyStorageBlockEntity extends BaseMachineBlockEntity implements GtuSink {
    protected final TickEnergyStorage energy;
    private int lastComparator;
    private final ContainerData data = new ContainerData() {
        public int get(int index) { return energy.syncPart(index); }
        // Server-authoritative read-only view. Client menus use independent
        // SimpleContainerData limbs; never clamp a partially received packet.
        public void set(int index, int value) {}
        public int getCount() { return ThirdStorageDefs.SYNC_PARTS; }
    };
    protected ThirdEnergyStorageBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
                                            int slots, ThirdStorageDefs.Spec spec) {
        super(type,pos,state,slots); energy=new TickEnergyStorage(spec);
    }
    public final long storedGtuMilli() { return energy.stored(); }
    public final long capacityGtuMilli() { return energy.spec().capacity(); }
    public final ContainerData data() { return data; }
    public final int comparatorOutput() {
        return energy.stored()==0 ? 0 : 1+(int)(14L*energy.stored()/energy.spec().capacity());
    }
    protected final void energyChanged() {
        setChanged();
        int next=comparatorOutput();
        if(next!=lastComparator && level!=null && !level.isClientSide()) {
            lastComparator=next; level.updateNeighbourForOutputSignal(worldPosition,getBlockState().getBlock());
        }
    }
    @Override public void onLoad() { super.onLoad(); energyChanged(); }
    @Override public final long receiveGtu(long amount, boolean simulate) {
        if (level==null || level.isClientSide()) return 0;
        long accepted=energy.receive(amount,simulate,level.getGameTime());
        if (!simulate && accepted>0) energyChanged();
        return accepted;
    }
    public static void serverTick(Level level, BlockPos pos, BlockState state, ThirdEnergyStorageBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        long before=be.energy.stored(), now=level.getGameTime();
        be.energy.idle(now);
        long budget=be.energy.availableOutput(now), stored=be.energy.stored();
        if(budget>0) {
            long paid=PipeRouting.drain(server,pos,PipeType.WIRE,budget,now,(target,ignored)-> {
                // Same absolute-amount equalization as I/II, including cross-tier pairs.
                long other;
                if(target instanceof ThirdEnergyStorageBlockEntity store) other=store.storedGtuMilli();
                else if(target instanceof AccumulatorBlockEntity store) other=store.gtuBuffer().amountAsLong();
                else if(target instanceof SecondAccumulatorBlockEntity store) other=store.gtuBuffer().amountAsLong();
                else return target instanceof GtuSink sink ? sink::receiveGtu : null;
                long cap=Math.max(0,stored-other)/2;
                if(cap<=0 || !(target instanceof GtuSink sink)) return null;
                return (amount,simulate)->sink.receiveGtu(Math.min(amount,cap),simulate);
            });
            be.energy.extract(paid,false,now); // PipeRouting includes the sender-paid path loss.
        }
        if(before!=be.energy.stored()) be.energyChanged();
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag,registries); tag.putLong("EnergyMilli",energy.stored());
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag,registries); energy.restore(tag.getLong("EnergyMilli"));
    }
}
