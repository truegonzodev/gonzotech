package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.ResourceBuffer;
import com.gonzotech.machines.energy.Sinks;
import com.gonzotech.machines.energy.Sinks.GthSink;
import com.gonzotech.machines.energy.Sinks.SteamSink;
import com.gonzotech.machines.menu.BoilerMenu;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Паровой котёл — принимает GTH (как топливо) от соседней топки и воду,
 * вырабатывает пар и отдаёт его соседнему генератору Стирлинга.
 * <p>
 * Работает ТОЛЬКО если к одной из граней примыкает топка ({@link FireboxBlockEntity}).
 * Нарушив цепочку, котёл перестаёт вырабатывать пар.
 * <p>
 * Слот 0 — ведро воды (наполняет буфер воды, отдаёт пустое ведро в слот 1).
 * Именно этот блок задуман как сложный 3D-объект (Blockbench позже), поэтому
 * тут только логика, без завязки на геометрию куба.
 */
public class BoilerBlockEntity extends BaseMachineBlockEntity implements GthSink, SteamSink {

    public static final int SLOT_WATER_IN = 0;
    public static final int SLOT_BUCKET_OUT = 1;

    private final ResourceBuffer gth = new ResourceBuffer(MachineDefs.GTH_CAPACITY);
    private final ResourceBuffer water = new ResourceBuffer(MachineDefs.WATER_CAPACITY);
    private final ResourceBuffer steam = new ResourceBuffer(MachineDefs.STEAM_CAPACITY);

    /** Есть ли рядом топка — для индикатора «цепочка собрана» в GUI. */
    private boolean chainOk;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> gth.amount();
                case 1 -> water.amount();
                case 2 -> steam.amount();
                case 3 -> chainOk ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {
            switch (i) {
                case 0 -> gth.set(v);
                case 1 -> water.set(v);
                case 2 -> steam.set(v);
                case 3 -> chainOk = v != 0;
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    public BoilerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BOILER.get(), pos, state, 2);
    }

    public ResourceBuffer gthBuffer() {
        return gth;
    }

    public ResourceBuffer waterBuffer() {
        return water;
    }

    public ResourceBuffer steamBuffer() {
        return steam;
    }

    public ContainerData data() {
        return data;
    }

    public boolean chainOk() {
        return chainOk;
    }

    // ─────────────────────────── Sinks ───────────────────────────

    @Override
    public int receiveGth(int amount, boolean simulate) {
        return gth.receive(amount, simulate);
    }

    @Override
    public int receiveSteam(int amount, boolean simulate) {
        return steam.receive(amount, simulate);
    }

    // ─────────────────────────── тик (сервер) ───────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, BoilerBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        boolean changed = false;

        // 0. Проверить цепочку: рядом должна быть топка.
        boolean chain = Sinks.hasNeighbor(server, pos, FireboxBlockEntity.class);
        if (chain != be.chainOk) {
            be.chainOk = chain;
            changed = true;
        }

        // 1. Осушить ведро воды в буфер воды.
        if (be.fillWaterFromBucket()) {
            changed = true;
        }

        // 2. Варить пар: нужен GTH + вода + место под пар + собранная цепочка.
        if (chain
            && be.gth.has(MachineDefs.BOILER_GTH_PER_TICK)
            && be.water.has(MachineDefs.BOILER_WATER_PER_TICK)
            && be.steam.space() >= MachineDefs.BOILER_STEAM_PER_TICK) {
            be.gth.extract(MachineDefs.BOILER_GTH_PER_TICK, false);
            be.water.extract(MachineDefs.BOILER_WATER_PER_TICK, false);
            be.steam.receive(MachineDefs.BOILER_STEAM_PER_TICK, false);
            changed = true;
        }

        // 3. Отдать пар соседнему стирлингу.
        if (be.pushSteam(server, pos)) {
            changed = true;
        }

        if (changed) {
            be.setChanged();
        }
    }

    /** Ведро воды → +1000 mB воды, пустое ведро в выходной слот. */
    private boolean fillWaterFromBucket() {
        ItemStack in = items.get(SLOT_WATER_IN);
        if (in.is(Items.WATER_BUCKET) && water.space() >= MachineDefs.WATER_PER_BUCKET) {
            ItemStack out = items.get(SLOT_BUCKET_OUT);
            if (out.isEmpty()) {
                items.set(SLOT_BUCKET_OUT, new ItemStack(Items.BUCKET));
            } else if (out.is(Items.BUCKET) && out.getCount() < out.getMaxStackSize()) {
                out.grow(1);
            } else {
                return false;
            }
            water.receive(MachineDefs.WATER_PER_BUCKET, false);
            in.shrink(1);
            return true;
        }
        return false;
    }

    /** Толкнуть пар любому соседу-приёмнику (стирлингу). */
    private boolean pushSteam(Level level, BlockPos pos) {
        if (steam.isEmpty()) return false;
        boolean moved = false;
        for (Direction dir : Direction.values()) {
            if (steam.isEmpty()) break;
            BlockEntity be = level.getBlockEntity(pos.relative(dir));
            if (be instanceof SteamSink sink && !(be instanceof BoilerBlockEntity)) {
                int can = Math.min(MachineDefs.STEAM_TRANSFER, steam.amount());
                int accepted = sink.receiveSteam(can, false);
                if (accepted > 0) {
                    steam.extract(accepted, false);
                    moved = true;
                }
            }
        }
        return moved;
    }

    // ─────────────────────────── NBT ───────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        gth.save(tag, "Gth");
        water.save(tag, "Water");
        steam.save(tag, "Steam");
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        gth.load(tag, "Gth");
        water.load(tag, "Water");
        steam.load(tag, "Steam");
    }

    // ─────────────────────────── Menu ───────────────────────────

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.gonzotech.boiler");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new BoilerMenu(id, inv, this, data);
    }
}
