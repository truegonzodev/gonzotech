package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.ResourceBuffer;
import com.gonzotech.machines.energy.Sinks;
import com.gonzotech.machines.energy.Sinks.GtuSink;
import com.gonzotech.machines.energy.Sinks.GtuSource;
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
 * Генератор Стирлинга: {@code 40 пара → 2 GTU + вода}. Пар превращается в GTU, а
 * часть воды возвращается В КОТЁЛ. БЕЗ конденсаторов возврат мал
 * ({@link MachineDefs#STIRLING_WATER_BASE_PER_TICK}/t), поэтому цикл сам себя не
 * держит; каждый примыкающий {@link CondenserBlockEntity} добавляет
 * +{@link MachineDefs#STIRLING_WATER_PER_CONDENSER}/t. Достаточно конденсаторов —
 * и цикл замыкается без долива воды.
 * <p>
 * Работает ТОЛЬКО если к одной из граней примыкает паровой котёл
 * ({@link BoilerBlockEntity}). Предметных слотов нет.
 * <p>
 * Паразитика: пар конденсируется в воду 1:1 ({@link MachineDefs#STIRLING_STEAM_LOSS}/t)
 * на возврат в котёл — ТОЛЬКО если пар есть (иначе при нескольких котлах-соседях
 * был бы дюп воды).
 */
public class StirlingBlockEntity extends BaseMachineBlockEntity implements SteamSink, GtuSink, GtuSource {

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

    // ─────────────────────────── GtuSource (для проводов) ───────────────────────────

    @Override
    public int extractGtu(int amount, boolean simulate) {
        return gtu.extract(amount, simulate);
    }

    // ─────────────────────────── тик (сервер) ───────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, StirlingBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        boolean changed = false;

        boolean chain = Sinks.hasNeighbor(server, pos, BoilerBlockEntity.class);

        // Паразитика: пар «стынет» в воду 1:1, ТОЛЬКО если пар есть.
        //    Вода конденсируется из своего же пара — ничего не создаётся из
        //    воздуха, поэтому дюпа воды при нескольких котлах-соседях нет.
        if (be.steam.amount() > 0) {
            int cooled = be.steam.extract(MachineDefs.STIRLING_STEAM_LOSS, false);
            be.water.receive(cooled, false);
            changed = true;
        }

        // Основной цикл: 40 пара → 2 GTU + вода на возврат.
        // Водоотдача = база (6) + 5 за каждый примыкающий конденсатор.
        int condensers = countCondensers(server, pos);
        int waterOut = MachineDefs.stirlingWaterPerTick(condensers);
        boolean run = false;
        if (chain
            && be.steam.has(MachineDefs.STIRLING_STEAM_PER_TICK)
            && be.gtu.space() >= MachineDefs.STIRLING_GTU_PER_TICK
            && be.water.space() >= waterOut) {
            be.steam.extract(MachineDefs.STIRLING_STEAM_PER_TICK, false);
            be.gtu.receive(MachineDefs.STIRLING_GTU_PER_TICK, false);
            be.water.receive(waterOut, false);
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

    /** Сколько конденсаторов примыкает к 6 граням. */
    private static int countCondensers(Level level, BlockPos pos) {
        int n = 0;
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            if (level.getBlockEntity(pos.relative(dir)) instanceof CondenserBlockEntity) {
                n++;
            }
        }
        return n;
    }

    /** Равномерно вернуть воду соседним котлам. */
    private boolean pushWater(Level level, BlockPos pos) {
        if (water.isEmpty()) return false;
        int budget = Math.min(MachineDefs.STIRLING_WATER_OUTPUT, water.amount());
        int moved = Transfer.distribute(level, pos, budget, level.getGameTime(), be -> {
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
        int moved = Transfer.distribute(level, pos, budget, level.getGameTime(), be -> {
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
