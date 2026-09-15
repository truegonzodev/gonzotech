package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.energy.GtBuffer;
import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.ResourceBuffer;
import com.gonzotech.machines.energy.Transfer;
import com.gonzotech.machines.menu.TurbineMenu;
import com.gonzotech.machines.network.PipeRouting;
import com.gonzotech.machines.network.PipeType;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.gonzotech.machines.turbine.TurbineMath;
import com.gonzotech.machines.turbine.TurbineStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

/**
 * Единственный активный ротор сформированной турбины.
 *
 * <p>Контроллер выбирается детерминированно: это ротор в
 * {@code (minX+1, minY+1, minZ+1)}. Остальные роторы имеют тот же BE для
 * простоты сохранения/синхронизации блока, но не получают тикер и не хранят
 * состояние всей установки.</p>
 */
public final class TurbineRotorBlockEntity extends BaseMachineBlockEntity {

    private static final int STEAM_SYNC_BASE = 10_000;

    private ResourceBuffer steam = new ResourceBuffer(0);
    private GtBuffer gtu = new GtBuffer(0);

    private boolean formed;
    private int rotors;
    private BlockPos min;
    private BlockPos max;
    private long[] steamPorts = new long[0];
    private long[] wirePorts = new long[0];
    private transient Set<Long> steamPortSet = Set.of();

    /** Остаток числителя конверсии Steam→mGTU, 0..55. */
    private int conversionRemainder;
    private long intakeBudgetTick = Long.MIN_VALUE;
    private int acceptedSteamThisTick;
    private int outputPortCursor;
    private boolean indexRegistered;
    /** Не сканировать границы каждый тик, если дальний чанк структуры временно выгружен. */
    private long nextRestoreAttemptTick;
    private int lastSteamConsumed;
    private long lastGtuGeneratedMilli;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                // Steam может достигать ~36k и не должен полагаться на размер
                // пакета ContainerData: отправляем его как две безопасные части.
                case 0 -> steam.amount() % STEAM_SYNC_BASE;
                case 1 -> steam.amount() / STEAM_SYNC_BASE;
                case 2 -> gtu.amountUnitsInt();
                case 3 -> rotors;
                case 4 -> formed ? 1 : 0;
                case 5 -> lastSteamConsumed;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Клиентская SimpleContainerData принимает синхронизацию; серверное
            // состояние изменяется только ресурсными методами контроллера.
        }

        @Override
        public int getCount() {
            return 6;
        }
    };

    public TurbineRotorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TURBINE_ROTOR.get(), pos, state, 0);
    }

    public boolean isFormedController() {
        return formed && min != null && max != null && rotors > 0;
    }

    public int rotors() {
        return rotors;
    }

    public BlockPos min() {
        return min;
    }

    public BlockPos max() {
        return max;
    }

    public ResourceBuffer steamBuffer() {
        return steam;
    }

    public GtBuffer gtuBuffer() {
        return gtu;
    }

    public ContainerData data() {
        return data;
    }

    public int lastSteamConsumed() {
        return lastSteamConsumed;
    }

    public long lastGtuGeneratedMilli() {
        return lastGtuGeneratedMilli;
    }

    public long[] steamPorts() {
        return steamPorts.clone();
    }

    public long[] wirePorts() {
        return wirePorts.clone();
    }

    /** Вызывается только валидатором после полной проверки прямоугольника. */
    public void configureStructure(BlockPos min, BlockPos max, int rotors,
                                   long[] steamPorts, long[] wirePorts) {
        this.min = min.immutable();
        this.max = max.immutable();
        this.rotors = rotors;
        this.steamPorts = steamPorts.clone();
        this.wirePorts = wirePorts.clone();
        rebuildPortSets();
        this.steam = new ResourceBuffer(TurbineMath.steamCapacity(rotors));
        this.gtu = new GtBuffer(TurbineMath.gtuCapacityMilli(rotors));
        this.formed = true;
        this.conversionRemainder = 0;
        this.acceptedSteamThisTick = 0;
        this.intakeBudgetTick = Long.MIN_VALUE;
        this.outputPortCursor = 0;
        this.lastSteamConsumed = 0;
        this.lastGtuGeneratedMilli = 0;
        this.indexRegistered = true;
        this.nextRestoreAttemptTick = 0L;
        setChanged();
    }

    /** Разрушение любой части снимает состояние и намеренно очищает внутренние баки. */
    public void clearStructure() {
        formed = false;
        rotors = 0;
        min = null;
        max = null;
        steamPorts = new long[0];
        wirePorts = new long[0];
        steamPortSet = Set.of();
        steam = new ResourceBuffer(0);
        gtu = new GtBuffer(0);
        conversionRemainder = 0;
        acceptedSteamThisTick = 0;
        intakeBudgetTick = Long.MIN_VALUE;
        outputPortCursor = 0;
        lastSteamConsumed = 0;
        lastGtuGeneratedMilli = 0;
        indexRegistered = false;
        nextRestoreAttemptTick = 0L;
        setChanged();
    }

    /** Приём Steam из виртуального endpoint-а конкретной встроенной ноды. */
    public long receiveSteamFromPort(BlockPos port, long amount, boolean simulate) {
        if (!isFormedController() || !steamPortSet.contains(port.asLong()) || amount <= 0) return 0;
        resetIntakeLedger();
        int remain = Math.max(0, TurbineMath.maxSteamIntake(rotors) - acceptedSteamThisTick);
        int accepted = steam.receive(Math.min(amount, (long) remain), simulate);
        if (!simulate && accepted > 0) {
            acceptedSteamThisTick += accepted;
            setChanged();
        }
        return accepted;
    }

    private void resetIntakeLedger() {
        long tick = level == null ? Long.MIN_VALUE : level.getGameTime();
        if (tick != intakeBudgetTick) {
            intakeBudgetTick = tick;
            acceptedSteamThisTick = 0;
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TurbineRotorBlockEntity turbine) {
        if (!(level instanceof ServerLevel server)) return;
        if (!turbine.isFormedController()) return;

        // После перезапуска мира transient-индекс портов восстанавливается лишь
        // раз в 20 тиков, пока дальний чанк конструкции ещё выгружен.
        if (!turbine.indexRegistered) {
            if (server.getGameTime() < turbine.nextRestoreAttemptTick) return;
            if (!TurbineStructure.restoreController(server, pos, turbine)) {
                turbine.nextRestoreAttemptTick = server.getGameTime() + 20L;
                return;
            }
        }

        boolean changed = false;
        turbine.lastSteamConsumed = 0;
        turbine.lastGtuGeneratedMilli = 0;

        int wantedSteam = Math.min(turbine.steam.amount(), TurbineMath.maxSteamConsumption(turbine.rotors));
        if (wantedSteam > 0) {
            long freeGtu = turbine.gtu.space() == null ? Long.MAX_VALUE : turbine.gtu.space().longValue();
            int consuming = turbine.steamThatFits(wantedSteam, freeGtu);
            if (consuming > 0) {
                long made = TurbineMath.gtuMilliForSteam(consuming, turbine.conversionRemainder);
                // steamThatFits гарантирует, что место есть; receive оставлен как
                // последний безопасный clamp на случай ручного/NBT вмешательства.
                long accepted = turbine.gtu.receive(made, false);
                if (accepted == made) {
                    turbine.steam.extract(consuming, false);
                    turbine.conversionRemainder = TurbineMath.conversionRemainderAfter(
                        consuming, turbine.conversionRemainder);
                    turbine.lastSteamConsumed = consuming;
                    turbine.lastGtuGeneratedMilli = made;
                    changed = true;
                }
            }
        }

        if (turbine.pushGtu(server)) changed = true;
        if (changed) turbine.setChanged();
    }

    /** Бинарный поиск: сколько целых mB ещё поместится с учётом дробного остатка. */
    private int steamThatFits(int upper, long freeGtuMilli) {
        int lo = 0;
        int hi = Math.max(0, upper);
        while (lo < hi) {
            int mid = lo + (hi - lo + 1) / 2;
            if (TurbineMath.gtuMilliForSteam(mid, conversionRemainder) <= freeGtuMilli) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    /**
     * Выпускает GTU через несколько wire-портов. У каждого порта свой маршрут и
     * лимит первого тира; максимум восемь дорогостоящих BFS за тик, после чего
     * cursor продолжит со следующего порта в следующем тике.
     */
    private boolean pushGtu(ServerLevel level) {
        if (gtu.isEmpty() || wirePorts.length == 0) return false;
        long remaining = Math.min(gtu.amountAsLong(), TurbineMath.maxGtuOutputMilli(rotors));
        if (remaining <= 0) return false;

        boolean movedAnything = false;
        int attempts = Math.min(wirePorts.length, MachineDefs.TURBINE_MAX_OUTPUT_ROUTE_ATTEMPTS);
        for (int tried = 0; tried < attempts && remaining > 0; tried++) {
            int index = Math.floorMod(outputPortCursor++, wirePorts.length);
            BlockPos port = BlockPos.of(wirePorts[index]);
            long portLimit = TurbineStructure.outputPortLimit(level, port);
            if (portLimit <= 0) continue;
            long budget = Math.min(remaining, portLimit);
            long moved = PipeRouting.drainFromTurbinePort(level, port, PipeType.WIRE, budget,
                level.getGameTime(), (target, ignored) -> {
                    if (target instanceof TurbineRotorBlockEntity) return null;
                    if (target instanceof com.gonzotech.machines.energy.Sinks.GtuSink sink) {
                        return sink::receiveGtu;
                    }
                    return null;
                });
            if (moved > 0) {
                gtu.extract(moved, false);
                remaining -= moved;
                movedAnything = true;
            }
        }
        return movedAnything;
    }

    /** Данные после загрузки уже есть в NBT, остаётся восстановить быстрые lookup set'ы. */
    public void markIndexRestored() {
        rebuildPortSets();
        indexRegistered = true;
        nextRestoreAttemptTick = 0L;
    }

    private void rebuildPortSets() {
        Set<Long> steamSet = new HashSet<>();
        for (long value : steamPorts) steamSet.add(value);
        steamPortSet = steamSet;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.gonzotech.turbine_rotor");
    }

    @Override
    public TurbineMenu createMenu(int id, Inventory inv, net.minecraft.world.entity.player.Player player) {
        return new TurbineMenu(id, inv, this, data);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // В этот момент соседние чанки могут ещё не быть загружены. serverTick
        // спокойно повторит restore позднее, не делая постоянного full-scan.
        indexRegistered = false;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Formed", formed);
        tag.putInt("Rotors", rotors);
        if (min != null) tag.putLong("Min", min.asLong());
        if (max != null) tag.putLong("Max", max.asLong());
        tag.putLongArray("SteamPorts", steamPorts);
        tag.putLongArray("WirePorts", wirePorts);
        tag.putInt("ConversionRemainder", conversionRemainder);
        steam.save(tag, "Steam");
        gtu.save(tag, "Gtu");
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        formed = tag.getBoolean("Formed");
        rotors = tag.getInt("Rotors");
        min = tag.contains("Min") ? BlockPos.of(tag.getLong("Min")) : null;
        max = tag.contains("Max") ? BlockPos.of(tag.getLong("Max")) : null;
        steamPorts = tag.getLongArray("SteamPorts");
        wirePorts = tag.getLongArray("WirePorts");
        conversionRemainder = Math.floorMod(tag.getInt("ConversionRemainder"),
            MachineDefs.TURBINE_REFERENCE_STEAM_MB);
        if (!formed || rotors <= 0 || rotors >= MachineDefs.TURBINE_MAX_ROTORS_EXCLUSIVE) {
            formed = false;
            rotors = 0;
            min = null;
            max = null;
            steamPorts = new long[0];
            wirePorts = new long[0];
        }
        steam = new ResourceBuffer(formed ? TurbineMath.steamCapacity(rotors) : 0);
        gtu = new GtBuffer(formed ? TurbineMath.gtuCapacityMilli(rotors) : 0);
        steam.load(tag, "Steam");
        gtu.load(tag, "Gtu");
        rebuildPortSets();
        indexRegistered = false;
    }
}
