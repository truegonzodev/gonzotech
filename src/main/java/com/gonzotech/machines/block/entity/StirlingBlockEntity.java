package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.ResourceBuffer;
import com.gonzotech.machines.energy.Sinks;
import com.gonzotech.machines.energy.Sinks.GtuSink;
import com.gonzotech.machines.energy.Sinks.SteamSink;
import com.gonzotech.machines.menu.StirlingMenu;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Генератор Стирлинга — принимает пар от соседнего котла, тратит его и
 * вырабатывает GTU («электричество»), которое отдаёт соседней электропечи.
 * <p>
 * Работает ТОЛЬКО если к одной из граней примыкает паровой котёл
 * ({@link BoilerBlockEntity}). Слотов для предметов нет — чистый преобразователь.
 */
public class StirlingBlockEntity extends BaseMachineBlockEntity implements SteamSink, GtuSink {

    private final ResourceBuffer steam = new ResourceBuffer(MachineDefs.STEAM_CAPACITY);
    private final ResourceBuffer gtu = new ResourceBuffer(MachineDefs.GTU_CAPACITY);

    /** Есть ли рядом котёл — индикатор собранной цепочки. */
    private boolean chainOk;
    /** Идёт ли сейчас генерация — для анимации/индикатора. */
    private boolean running;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> steam.amount();
                // GTU может превышать диапазон short, поэтому шлём его двумя половинами.
                case 1 -> gtu.amount() & 0xFFFF;
                case 2 -> (gtu.amount() >> 16) & 0xFFFF;
                case 3 -> chainOk ? 1 : 0;
                case 4 -> running ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {
            switch (i) {
                case 0 -> steam.set(v);
                case 1 -> gtu.set((gtu.amount() & ~0xFFFF) | (v & 0xFFFF));
                case 2 -> gtu.set((gtu.amount() & 0xFFFF) | ((v & 0xFFFF) << 16));
                case 3 -> chainOk = v != 0;
                case 4 -> running = v != 0;
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return 5;
        }
    };

    public StirlingBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STIRLING.get(), pos, state, 0);
    }

    public ResourceBuffer steamBuffer() {
        return steam;
    }

    public ResourceBuffer gtuBuffer() {
        return gtu;
    }

    public ContainerData data() {
        return data;
    }

    public boolean chainOk() {
        return chainOk;
    }

    public boolean running() {
        return running;
    }

    // ─────────────────────────── Sinks ───────────────────────────

    @Override
    public int receiveSteam(int amount, boolean simulate) {
        return steam.receive(amount, simulate);
    }

    @Override
    public int receiveGtu(int amount, boolean simulate) {
        return gtu.receive(amount, simulate);
    }

    // ─────────────────────────── тик (сервер) ───────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, StirlingBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        boolean changed = false;

        boolean chain = Sinks.hasNeighbor(server, pos, BoilerBlockEntity.class);
        if (chain != be.chainOk) {
            be.chainOk = chain;
            changed = true;
        }

        boolean run = false;
        if (chain
            && be.steam.has(MachineDefs.STIRLING_STEAM_PER_TICK)
            && be.gtu.space() >= MachineDefs.STIRLING_GTU_PER_TICK) {
            be.steam.extract(MachineDefs.STIRLING_STEAM_PER_TICK, false);
            be.gtu.receive(MachineDefs.STIRLING_GTU_PER_TICK, false);
            run = true;
            changed = true;
        }
        if (run != be.running) {
            be.running = run;
            changed = true;
        }

        if (be.pushGtu(server, pos)) {
            changed = true;
        }

        if (changed) {
            be.setChanged();
        }
    }

    /** Толкнуть GTU любому соседу-приёмнику (электропечи). */
    private boolean pushGtu(Level level, BlockPos pos) {
        if (gtu.isEmpty()) return false;
        boolean moved = false;
        for (Direction dir : Direction.values()) {
            if (gtu.isEmpty()) break;
            BlockEntity be = level.getBlockEntity(pos.relative(dir));
            if (be instanceof GtuSink sink && !(be instanceof StirlingBlockEntity)) {
                int can = Math.min(MachineDefs.GTU_TRANSFER, gtu.amount());
                int accepted = sink.receiveGtu(can, false);
                if (accepted > 0) {
                    gtu.extract(accepted, false);
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
        steam.save(tag, "Steam");
        gtu.save(tag, "Gtu");
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        steam.load(tag, "Steam");
        gtu.load(tag, "Gtu");
    }

    // ─────────────────────────── Menu ───────────────────────────

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.gonzotech.stirling_generator");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new StirlingMenu(id, inv, this, data);
    }
}
