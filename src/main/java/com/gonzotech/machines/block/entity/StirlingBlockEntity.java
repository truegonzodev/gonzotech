package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.ResourceBuffer;
import com.gonzotech.machines.energy.Sinks;
import com.gonzotech.machines.energy.Sinks.GtuSink;
import com.gonzotech.machines.energy.Sinks.SteamSink;
import com.gonzotech.machines.energy.Sinks.WaterSink;
import com.gonzotech.machines.energy.Transfer;
import com.gonzotech.machines.menu.StirlingMenu;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Генератор Стирлинга: {@code Steam → GTU + Water}. Пар превращается в GTU, а
 * равный объём воды возвращается В КОТЁЛ (1:1) — цикл замыкается без долива воды.
 * <p>
 * Работает ТОЛЬКО если к одной из граней примыкает паровой котёл
 * ({@link BoilerBlockEntity}). Предметных слотов нет.
 * <p>
 * Паразитика (всегда): −{@link MachineDefs#STIRLING_STEAM_LOSS} пара,
 * +{@link MachineDefs#STIRLING_WATER_GAIN} воды на возврат в котёл.
 */
public class StirlingBlockEntity extends BaseMachineBlockEntity implements SteamSink, GtuSink {

    private final ResourceBuffer steam = new ResourceBuffer(MachineDefs.STIRLING_STEAM_CAPACITY);
    private final ResourceBuffer gtu = new ResourceBuffer(MachineDefs.STIRLING_GTU_CAPACITY);
    /** Внутренний буфер воды на возврат в котёл (в GUI не показывается). */
    private final ResourceBuffer water = new ResourceBuffer(MachineDefs.STIRLING_WATER_CAPACITY);

    /** Идёт ли сейчас генерация — для анимации/индикатора. */
    private boolean running;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> steam.amount();
                case 1 -> gtu.amount();
                case 2 -> running ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {
            switch (i) {
                case 0 -> steam.set(v);
                case 1 -> gtu.set(v);
                case 2 -> running = v != 0;
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return 3;
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

    public boolean running() {
        return running;
    }

    // ─────────────────────────── Sinks ───────────────────────────

    @Override
    public int receiveSteam(int amount, boolean simulate) {
        return steam.receive(Math.min(amount, MachineDefs.STIRLING_STEAM_INTAKE), simulate);
    }

    @Override
    public int receiveGtu(int amount, boolean simulate) {
        // Стирлинг сам источник GTU; приём извне не используется.
        return 0;
    }

    // ─────────────────────────── тик (сервер) ───────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, StirlingBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        boolean changed = false;

        boolean chain = Sinks.hasNeighbor(server, pos, BoilerBlockEntity.class);

        // Паразитика (всегда): теряем пар, копим воду на возврат.
        if (be.steam.amount() > 0) {
            be.steam.extract(MachineDefs.STIRLING_STEAM_LOSS, false);
            changed = true;
        }
        if (!be.water.isFull()) {
            be.water.receive(MachineDefs.STIRLING_WATER_GAIN, false);
            changed = true;
        }

        // Основной цикл: пар → GTU + вода на возврат.
        boolean run = false;
        if (chain
            && be.steam.has(MachineDefs.STIRLING_STEAM_PER_TICK)
            && be.gtu.space() >= MachineDefs.STIRLING_GTU_PER_TICK
            && be.water.space() >= MachineDefs.STIRLING_WATER_PER_TICK) {
            be.steam.extract(MachineDefs.STIRLING_STEAM_PER_TICK, false);
            be.gtu.receive(MachineDefs.STIRLING_GTU_PER_TICK, false);
            be.water.receive(MachineDefs.STIRLING_WATER_PER_TICK, false);
            run = true;
            changed = true;
        }
        if (run != be.running) {
            be.running = run;
            changed = true;
        }

        // Возврат воды в котёл (равномерно).
        if (be.pushWater(server, pos)) {
            changed = true;
        }

        // Раздать GTU соседям равномерно.
        if (be.pushGtu(server, pos)) {
            changed = true;
        }

        if (changed) {
            be.setChanged();
        }
    }

    /** Равномерно вернуть воду соседним котлам. */
    private boolean pushWater(Level level, BlockPos pos) {
        if (water.isEmpty()) return false;
        int budget = Math.min(MachineDefs.STIRLING_WATER_OUTPUT, water.amount());
        int moved = Transfer.distribute(level, pos, budget, be -> {
            if (be instanceof StirlingBlockEntity) return null;
            if (be instanceof WaterSink sink) return sink::receiveWater;
            return null;
        });
        if (moved > 0) {
            water.extract(moved, false);
            return true;
        }
        return false;
    }

    /** Равномерно раздать GTU соседям-приёмникам (электропечам). */
    private boolean pushGtu(Level level, BlockPos pos) {
        if (gtu.isEmpty()) return false;
        int budget = Math.min(MachineDefs.STIRLING_GTU_OUTPUT, gtu.amount());
        int moved = Transfer.distribute(level, pos, budget, be -> {
            if (be instanceof StirlingBlockEntity) return null;
            if (be instanceof GtuSink sink) return sink::receiveGtu;
            return null;
        });
        if (moved > 0) {
            gtu.extract(moved, false);
            return true;
        }
        return false;
    }

    // ─────────────────────────── NBT ───────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        steam.save(tag, "Steam");
        gtu.save(tag, "Gtu");
        water.save(tag, "Water");
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        steam.load(tag, "Steam");
        gtu.load(tag, "Gtu");
        water.load(tag, "Water");
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
