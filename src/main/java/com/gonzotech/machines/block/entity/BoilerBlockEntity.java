package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.ResourceBuffer;
import com.gonzotech.machines.energy.Sinks;
import com.gonzotech.machines.energy.Sinks.GthSink;
import com.gonzotech.machines.energy.Sinks.SteamSink;
import com.gonzotech.machines.energy.Sinks.WaterSink;
import com.gonzotech.machines.energy.WaterProviders;
import com.gonzotech.machines.network.PipeRouting;
import com.gonzotech.machines.network.PipeType;
import com.gonzotech.machines.menu.BoilerMenu;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Паровой котёл: {@code Water → Steam}, тратя GTH. Работает ТОЛЬКО если к одной
 * из граней примыкает топка ({@link FireboxBlockEntity}) — без топки пар не
 * делается даже при полном запасе GTH (это правильно).
 * <p>
 * Слот 0 — ведро-провайдер воды (осушается в буфер, пустое ведро уходит в слот 1).
 * Слот 1 — только забор пустых вёдер (игрок ничего туда положить не может).
 * <p>
 * Паразитика: пар конденсируется обратно в свою же воду 1:1
 * ({@link MachineDefs#BOILER_STEAM_LOSS}/t) — ТОЛЬКО если пар есть (ничего не
 * создаётся из воздуха); GTH рассеивается на {@link MachineDefs#BOILER_GTH_LOSS}/t.
 */
public class BoilerBlockEntity extends BaseMachineBlockEntity implements GthSink, SteamSink, WaterSink, WorldlyContainer {

    public static final int SLOT_WATER_IN = 0;
    public static final int SLOT_BUCKET_OUT = 1;

    private static final int[] SLOTS_ALL = { SLOT_WATER_IN, SLOT_BUCKET_OUT };

    /** Облачко пара «poof» (ванильный дымок), пока котёл РАБОТАЕТ. */
    private static final SimpleParticleType STEAM_PARTICLE = ParticleTypes.POOF;
    /** Раз в сколько тиков брызгать паром, пока котёл РАБОТАЕТ. */
    private static final int PARTICLE_INTERVAL = 5;

    private final ResourceBuffer gth = new ResourceBuffer(MachineDefs.BOILER_GTH_CAPACITY);
    private final ResourceBuffer water = new ResourceBuffer(MachineDefs.BOILER_WATER_CAPACITY);
    private final ResourceBuffer steam = new ResourceBuffer(MachineDefs.BOILER_STEAM_CAPACITY);

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> gth.amount();
                case 1 -> water.amount();
                case 2 -> steam.amount();
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {
            switch (i) {
                case 0 -> gth.set(v);
                case 1 -> water.set(v);
                case 2 -> steam.set(v);
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return 3;
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

    // ─────────────────────────── Sinks ───────────────────────────

    @Override
    public int receiveGth(int amount, boolean simulate) {
        return gth.receive(Math.min(amount, MachineDefs.BOILER_GTH_INTAKE), simulate);
    }

    @Override
    public int receiveSteam(int amount, boolean simulate) {
        // Котёл сам является источником пара, но реализует SteamSink на случай,
        // если понадобится приём извне; сейчас приём пара котлом не используется.
        return 0;
    }

    @Override
    public int receiveWater(int amount, boolean simulate) {
        return water.receive(Math.min(amount, MachineDefs.BOILER_WATER_INTAKE), simulate);
    }

    // ─────────────────────────── тик (сервер) ───────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, BoilerBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        boolean changed = false;

        // 1. Осушить ведро-провайдер воды в буфер воды (+ спавн рыбы).
        if (be.fillWaterFromBucket(server, pos)) {
            changed = true;
        }

        // 2. Паразитика: пар «стынет» обратно в воду 1:1, ТОЛЬКО если пар есть.
        //    Ничего не создаётся из воздуха — пар превращается в свою же воду.
        if (be.steam.amount() > 0) {
            int cooled = be.steam.extract(MachineDefs.BOILER_STEAM_LOSS, false);
            be.water.receive(cooled, false);
            changed = true;
        }
        // Паразитная потеря GTH — всегда (тепло просто рассеивается).
        if (be.gth.amount() > 0) {
            be.gth.extract(MachineDefs.BOILER_GTH_LOSS, false);
            changed = true;
        }

        // 3. Варить пар: нужна примыкающая топка + GTH + вода + место под пар.
        boolean chain = Sinks.hasNeighbor(server, pos, FireboxBlockEntity.class);
        boolean boiling = false;
        if (chain
            && be.gth.has(MachineDefs.BOILER_GTH_PER_TICK)
            && be.water.has(MachineDefs.BOILER_WATER_PER_TICK)
            && be.steam.space() >= MachineDefs.BOILER_STEAM_PER_TICK) {
            be.gth.extract(MachineDefs.BOILER_GTH_PER_TICK, false);
            be.water.extract(MachineDefs.BOILER_WATER_PER_TICK, false);
            be.steam.receive(MachineDefs.BOILER_STEAM_PER_TICK, false);
            changed = true;
            boiling = true;
        }

        // 3b. Пока котёл РАБОТАЕТ (делает пар) — раз в 5 тиков брызгать белым паром
        //     у всех граней кроме нижней (north/south/east/west/top).
        if (boiling && server.getGameTime() % PARTICLE_INTERVAL == 0) {
            spawnSteamParticles(server, pos);
        }

        // 4. Раздать пар соседям равномерно (макс. отдача).
        if (be.pushSteam(server, pos)) {
            changed = true;
        }

        if (changed) {
            be.setChanged();
        }
    }

    /**
     * По одному облачку пара (poof) у каждой грани, КРОМЕ нижней (north/south/east/west/top),
     * у которой нет непрозрачного соседа. Логика позиций — как у {@code IodineOreBlock}
     * (0.5625 = середина + ~половина блока наружу), но через {@link ServerLevel#sendParticles},
     * т.к. мы на сервере (частица разошлётся всем клиентам поблизости).
     */
    private static void spawnSteamParticles(ServerLevel server, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (direction == Direction.DOWN) continue; // нижнюю грань пропускаем
            BlockPos neighbor = pos.relative(direction);
            if (server.getBlockState(neighbor).isSolidRender()) continue;
            Direction.Axis axis = direction.getAxis();
            double dx = axis == Direction.Axis.X ? 0.5 + 0.5625 * direction.getStepX() : server.random.nextFloat();
            double dy = axis == Direction.Axis.Y ? 0.5 + 0.5625 * direction.getStepY() : server.random.nextFloat();
            double dz = axis == Direction.Axis.Z ? 0.5 + 0.5625 * direction.getStepZ() : server.random.nextFloat();
            server.sendParticles(
                STEAM_PARTICLE,
                pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz,
                1,          // count
                0.0, 0.0, 0.0,
                0.0         // speed
            );
        }
    }

    /** Ведро-провайдер → +mB воды, пустое ведро в выходной слот, спавн рыбы для fish-вёдер. */
    private boolean fillWaterFromBucket(ServerLevel server, BlockPos pos) {
        ItemStack in = items.get(SLOT_WATER_IN);
        int mb = WaterProviders.millibucketsFor(in);
        if (mb <= 0 || water.space() < mb) {
            return false;
        }
        ItemStack out = items.get(SLOT_BUCKET_OUT);
        if (out.isEmpty()) {
            items.set(SLOT_BUCKET_OUT, new ItemStack(Items.BUCKET));
        } else if (out.is(Items.BUCKET) && out.getCount() < out.getMaxStackSize()) {
            out.grow(1);
        } else {
            return false;
        }
        WaterProviders.spawnFishIfAny(server, pos, in);
        water.receive(mb, false);
        in.shrink(1);
        return true;
    }

    /**
     * Раздать пар приёмникам-{@link SteamSink}: прямым соседям (стирлинг вплотную)
     * ИЛИ дальше по паровым трубам ({@link PipeType#STEAM}) через
     * {@link PipeRouting#drain} — равномерно. Без труб рядом = прямая передача
     * соседу (прежнее поведение сохранено).
     */
    private boolean pushSteam(Level level, BlockPos pos) {
        if (steam.isEmpty()) return false;
        int budget = Math.min(MachineDefs.BOILER_STEAM_OUTPUT, steam.amount());
        int moved = PipeRouting.drain(level, pos, PipeType.STEAM, budget, level.getGameTime(), (be, p) -> {
            if (be instanceof BoilerBlockEntity) return null;
            if (be instanceof SteamSink sink) return sink::receiveSteam;
            return null;
        });
        if (moved > 0) {
            steam.extract(moved, false);
            return true;
        }
        return false;
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

    // ─────────────────────── Container / WorldlyContainer ───────────────────────
    //
    // Слот забора пустых вёдер (SLOT_BUCKET_OUT) — ТОЛЬКО на вывод: ни игрок (см.
    // OutputOnlySlot в меню), ни воронка/автоматизация не имеют права туда что-либо
    // класть. В слот воды (SLOT_WATER_IN) можно класть только валидные вёдра-провайдеры.

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == SLOT_BUCKET_OUT) return false;
        if (slot == SLOT_WATER_IN) return WaterProviders.isWaterProvider(stack);
        return false;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return SLOTS_ALL;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        // Забирать можно только пустые вёдра из выходного слота.
        return slot == SLOT_BUCKET_OUT;
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
