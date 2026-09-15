package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.energy.GtBuffer;
import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.ResourceBuffer;
import com.gonzotech.machines.energy.Sinks;
import com.gonzotech.machines.menu.SteamGenMenu;
import com.gonzotech.machines.network.PipeRouting;
import com.gonzotech.machines.network.PipeType;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.gonzotech.machines.steamgen.SteamGenMath;
import com.gonzotech.machines.steamgen.SteamGenStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

/**
 * Единое активное ядро сформированного продвинутого парогенератора.
 *
 * <p>Контроллер детерминированный: ядро в {@code (minX+1, minY+1, minZ+1)}.
 * Остальные ядра хранят тот же тип BlockEntity, но не тикают и не хранят
 * состояние установки.</p>
 *
 * <p>Вход — виртуальные приёмники на встроенных нодах (вода и GTH приходят
 * «из машин» через трубы, как пар к турбине). Выход — активный слив пара с
 * портов установки по тем же трубам. Пропускная способность входов и выхода
 * ограничена 128 mB/т на ядро.</p>
 */
public final class SteamGenCoreBlockEntity extends BaseMachineBlockEntity {

    private static final int FLUID_SYNC_BASE = 10_000;

    private ResourceBuffer water = new ResourceBuffer(0);
    private ResourceBuffer steam = new ResourceBuffer(0);
    private GtBuffer gth = new GtBuffer(0);

    private boolean formed;
    private int cores;
    private int sumCH;
    private int precious;
    private BlockPos min;
    private BlockPos max;
    private long[] heatPorts = new long[0];
    private long[] waterPorts = new long[0];
    private long[] steamPorts = new long[0];
    private transient Set<Long> heatPortSet = Set.of();
    private transient Set<Long> waterPortSet = Set.of();
    private transient Set<Long> steamPortSet = Set.of();

    /** Дробный остаток числа «циклов варки», milli-цикла, 0..999. */
    private int eventRemainderMilli;
    /** Дробный остаток вырабатываемого пара, milli-mB, 0..999. */
    private int steamRemainderMilli;

    private long intakeLedgerTick = Long.MIN_VALUE;
    private int acceptedWaterThisTick;
    private long acceptedGthMilliThisTick;
    private long outputLedgerTick = Long.MIN_VALUE;
    private int sentSteamThisTick;
    private int outputPortCursor;
    private boolean indexRegistered;
    /** Не сканировать границы каждый тик, если дальний чанк структуры временно выгружен. */
    private long nextRestoreAttemptTick;

    private int lastWaterIn;
    private int lastGthInUnits;
    private int lastSteamMade;
    private int lastSteamOut;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                // Ёмкости до ~41k mB: отправляем как две безопасные части.
                case 0 -> water.amount() % FLUID_SYNC_BASE;
                case 1 -> water.amount() / FLUID_SYNC_BASE;
                case 2 -> steam.amount() % FLUID_SYNC_BASE;
                case 3 -> steam.amount() / FLUID_SYNC_BASE;
                case 4 -> gth.amountUnitsInt();
                case 5 -> cores;
                case 6 -> formed ? 1 : 0;
                case 7 -> sumCH;
                case 8 -> precious;
                case 9 -> lastWaterIn;
                case 10 -> lastGthInUnits;
                case 11 -> lastSteamOut;
                case 12 -> lastSteamMade;
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
            return 13;
        }
    };

    public SteamGenCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STEAMGEN_CORE.get(), pos, state, 0);
    }

    public boolean isFormedController() {
        return formed && min != null && max != null && cores > 0;
    }

    public int cores() {
        return cores;
    }

    public int sumCH() {
        return sumCH;
    }

    public int precious() {
        return precious;
    }

    public BlockPos min() {
        return min;
    }

    public BlockPos max() {
        return max;
    }

    public ResourceBuffer waterBuffer() {
        return water;
    }

    public ResourceBuffer steamBuffer() {
        return steam;
    }

    public GtBuffer gthBuffer() {
        return gth;
    }

    public ContainerData data() {
        return data;
    }

    public int lastSteamMade() {
        return lastSteamMade;
    }

    public long[] heatPorts() {
        return heatPorts.clone();
    }

    public long[] waterPorts() {
        return waterPorts.clone();
    }

    public long[] steamPorts() {
        return steamPorts.clone();
    }

    /** Вызывается только валидатором после полной проверки объёма. */
    public void configureStructure(BlockPos min, BlockPos max, int cores, int sumCH, int precious,
                                   long[] steamPorts, long[] waterPorts, long[] heatPorts) {
        this.min = min.immutable();
        this.max = max.immutable();
        this.cores = cores;
        this.sumCH = sumCH;
        this.precious = precious;
        this.steamPorts = steamPorts.clone();
        this.waterPorts = waterPorts.clone();
        this.heatPorts = heatPorts.clone();
        rebuildPortSets();
        this.water = new ResourceBuffer(SteamGenMath.waterCapacity(cores));
        this.steam = new ResourceBuffer(SteamGenMath.steamCapacity(cores));
        this.gth = new GtBuffer(SteamGenMath.gthCapacityMilli(cores));
        this.formed = true;
        this.eventRemainderMilli = 0;
        this.steamRemainderMilli = 0;
        this.acceptedWaterThisTick = 0;
        this.acceptedGthMilliThisTick = 0L;
        this.intakeLedgerTick = Long.MIN_VALUE;
        this.sentSteamThisTick = 0;
        this.outputLedgerTick = Long.MIN_VALUE;
        this.outputPortCursor = 0;
        this.lastWaterIn = 0;
        this.lastGthInUnits = 0;
        this.lastSteamMade = 0;
        this.lastSteamOut = 0;
        this.indexRegistered = true;
        this.nextRestoreAttemptTick = 0L;
        setChanged();
    }

    /** Разрушение любой части снимает состояние и намеренно очищает внутренние баки. */
    public void clearStructure() {
        formed = false;
        cores = 0;
        sumCH = 0;
        precious = 0;
        min = null;
        max = null;
        steamPorts = new long[0];
        waterPorts = new long[0];
        heatPorts = new long[0];
        steamPortSet = Set.of();
        waterPortSet = Set.of();
        heatPortSet = Set.of();
        water = new ResourceBuffer(0);
        steam = new ResourceBuffer(0);
        gth = new GtBuffer(0);
        eventRemainderMilli = 0;
        steamRemainderMilli = 0;
        acceptedWaterThisTick = 0;
        acceptedGthMilliThisTick = 0L;
        intakeLedgerTick = Long.MIN_VALUE;
        sentSteamThisTick = 0;
        outputLedgerTick = Long.MIN_VALUE;
        outputPortCursor = 0;
        lastWaterIn = 0;
        lastGthInUnits = 0;
        lastSteamMade = 0;
        lastSteamOut = 0;
        indexRegistered = false;
        nextRestoreAttemptTick = 0L;
        setChanged();
    }

    /** Приём воды из виртуального endpoint-а конкретного встроенного водного порта. */
    public long receiveWaterFromPort(BlockPos port, long amount, boolean simulate) {
        if (!isFormedController() || !waterPortSet.contains(port.asLong()) || amount <= 0) return 0;
        resetIntakeLedger();
        int remain = Math.max(0, SteamGenMath.fluidIOLimit(cores) - acceptedWaterThisTick);
        int accepted = water.receive(Math.min(amount, (long) remain), simulate);
        if (!simulate && accepted > 0) {
            acceptedWaterThisTick += accepted;
            setChanged();
        }
        return accepted;
    }

    /** Приём GTH из виртуального endpoint-а конкретного встроенного теплового порта. */
    public long receiveGthFromPort(BlockPos port, long amount, boolean simulate) {
        if (!isFormedController() || !heatPortSet.contains(port.asLong()) || amount <= 0) return 0;
        resetIntakeLedger();
        long remain = gth.space() == null ? Long.MAX_VALUE : gth.space().longValue();
        long accepted = gth.receive(Math.min(amount, remain), simulate);
        if (!simulate && accepted > 0) {
            acceptedGthMilliThisTick += accepted;
            setChanged();
        }
        return accepted;
    }

    private void resetIntakeLedger() {
        long tick = level == null ? Long.MIN_VALUE : level.getGameTime();
        if (tick != intakeLedgerTick) {
            intakeLedgerTick = tick;
            acceptedWaterThisTick = 0;
            acceptedGthMilliThisTick = 0L;
        }
    }

    private void resetOutputLedger() {
        long tick = level == null ? Long.MIN_VALUE : level.getGameTime();
        if (tick != outputLedgerTick) {
            outputLedgerTick = tick;
            sentSteamThisTick = 0;
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SteamGenCoreBlockEntity boiler) {
        if (!(level instanceof ServerLevel server)) return;
        if (!boiler.isFormedController()) return;

        // После перезапуска мира transient-индекс портов восстанавливается лишь
        // раз в 20 тиков, пока дальний чанк конструкции ещё выгружен.
        if (!boiler.indexRegistered) {
            if (server.getGameTime() < boiler.nextRestoreAttemptTick) return;
            if (!SteamGenStructure.restoreController(server, pos, boiler)) {
                boiler.nextRestoreAttemptTick = server.getGameTime() + 20L;
                return;
            }
        }

        // Счётчики приёма за предыдущий тик становятся показателями для GUI.
        boiler.resetIntakeLedger();
        boiler.lastWaterIn = boiler.acceptedWaterThisTick;
        boiler.lastGthInUnits = (int) (boiler.acceptedGthMilliThisTick / MachineDefs.MILLI);
        boiler.resetOutputLedger();

        boiler.lastSteamMade = 0;
        boolean changed = boiler.burnSteam();
        if (boiler.pushSteam(server)) changed = true;
        if (changed) boiler.setChanged();
    }

    /**
     * Цикл варки: {@code 15 mB воды + 11 GTH → 12 mB пара × M} за событие.
     * За тик машина делает максимум событий, позволяемых потолком выработки,
     * запасом воды/GTH и местом в баке пара.
     *
     * @return true, если что-то изменилось
     */
    private boolean burnSteam() {
        int cores = this.cores;
        double mult = SteamGenMath.multiplier(sumCH, precious);

        // Доступное число событий, milli-цикла: минимум по четырём ограничениям.
        long capMilli = (long) cores * MachineDefs.STEAMGEN_STEAM_PER_TICK_PER_CORE * MachineDefs.MILLI
            / MachineDefs.STEAMGEN_STEAM_PER_UNIT;
        long waterMilli = water.amount() * MachineDefs.MILLI / MachineDefs.STEAMGEN_WATER_PER_UNIT;
        long gthMilli = gth.amountAsLong() * MachineDefs.MILLI / MachineDefs.STEAMGEN_GTH_PER_UNIT_MILLI;
        long spaceMilli = (long) Math.floor(steam.space() * MachineDefs.MILLI / (12.0D * mult));
        long availMilli = Math.min(Math.min(capMilli, waterMilli), Math.min(gthMilli, spaceMilli));
        if (availMilli <= 0) return false;

        long events = (availMilli + eventRemainderMilli) / MachineDefs.MILLI;
        eventRemainderMilli = (int) ((availMilli + eventRemainderMilli) % MachineDefs.MILLI);
        if (events <= 0) return false;

        long waterUsed = events * MachineDefs.STEAMGEN_WATER_PER_UNIT;
        long gthUsed = events * MachineDefs.STEAMGEN_GTH_PER_UNIT_MILLI;
        if (waterUsed > water.amount() || gthUsed > gth.amountAsLong()) return false;

        long madeMilli = (long) Math.floor(12.0D * mult * events * MachineDefs.MILLI) + steamRemainderMilli;
        long madeWhole = madeMilli / MachineDefs.MILLI;
        if (madeWhole > steam.space()) {
            madeWhole = steam.space();
            steamRemainderMilli = 0;
        } else {
            steamRemainderMilli = (int) (madeMilli % MachineDefs.MILLI);
        }
        if (madeWhole <= 0) {
            // Место меньше 1 mB: остаток копит, ничего не теряем.
            if (steamRemainderMilli >= MachineDefs.MILLI) steamRemainderMilli -= MachineDefs.MILLI;
            return false;
        }

        water.extract(waterUsed, false);
        gth.extract(gthUsed, false);
        steam.receive(madeWhole, false);
        this.lastSteamMade = (int) madeWhole;
        return true;
    }

    /**
     * Выпускает пар через несколько паровых портов. У каждого порта свой маршрут
     * и лимит первого тира; максимум восемь дорогостоящих BFS за тик.
     */
    private boolean pushSteam(ServerLevel level) {
        if (steam.isEmpty() || steamPorts.length == 0) return false;
        int limit = SteamGenMath.fluidIOLimit(cores);
        long remaining = Math.min(steam.amount(), Math.max(0, limit - sentSteamThisTick));
        if (remaining <= 0) return false;

        boolean movedAnything = false;
        int attempts = Math.min(steamPorts.length, MachineDefs.STEAMGEN_MAX_OUTPUT_ROUTE_ATTEMPTS);
        for (int tried = 0; tried < attempts && remaining > 0; tried++) {
            int index = Math.floorMod(outputPortCursor++, steamPorts.length);
            BlockPos port = BlockPos.of(steamPorts[index]);
            long portLimit = SteamGenStructure.steamPortLimit(level, port);
            if (portLimit <= 0) continue;
            long budget = Math.min(remaining, portLimit);
            long moved = PipeRouting.drainFromMultiblockPort(level, port, PipeType.STEAM, budget,
                level.getGameTime(), (target, ignored) -> {
                    if (target instanceof SteamGenCoreBlockEntity) return null;
                    if (target instanceof Sinks.SteamSink sink) {
                        return sink::receiveSteam;
                    }
                    return null;
                }, SteamGenStructure::isMember);
            if (moved > 0) {
                steam.extract(moved, false);
                remaining -= moved;
                sentSteamThisTick += (int) moved;
                lastSteamOut = (int) moved;
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
        steamPortSet = portSet(steamPorts);
        waterPortSet = portSet(waterPorts);
        heatPortSet = portSet(heatPorts);
    }

    private static Set<Long> portSet(long[] ports) {
        Set<Long> set = new HashSet<>();
        for (long value : ports) set.add(value);
        return set;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.gonzotech.steamgen_core");
    }

    @Override
    public SteamGenMenu createMenu(int id, Inventory inv, Player player) {
        return new SteamGenMenu(id, inv, this, data);
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
        tag.putInt("Cores", cores);
        tag.putInt("SumCH", sumCH);
        tag.putInt("Precious", precious);
        if (min != null) tag.putLong("Min", min.asLong());
        if (max != null) tag.putLong("Max", max.asLong());
        tag.putLongArray("HeatPorts", heatPorts);
        tag.putLongArray("WaterPorts", waterPorts);
        tag.putLongArray("SteamPorts", steamPorts);
        tag.putInt("EventRemainder", eventRemainderMilli);
        tag.putInt("SteamRemainder", steamRemainderMilli);
        water.save(tag, "Water");
        steam.save(tag, "Steam");
        gth.save(tag, "Gth");
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        formed = tag.getBoolean("Formed");
        cores = tag.getInt("Cores");
        sumCH = tag.getInt("SumCH");
        precious = tag.getInt("Precious");
        min = tag.contains("Min") ? BlockPos.of(tag.getLong("Min")) : null;
        max = tag.contains("Max") ? BlockPos.of(tag.getLong("Max")) : null;
        heatPorts = tag.getLongArray("HeatPorts");
        waterPorts = tag.getLongArray("WaterPorts");
        steamPorts = tag.getLongArray("SteamPorts");
        eventRemainderMilli = Math.floorMod(tag.getInt("EventRemainder"), MachineDefs.MILLI);
        steamRemainderMilli = Math.floorMod(tag.getInt("SteamRemainder"), MachineDefs.MILLI);
        if (!formed || cores <= 0 || sumCH < 0 || precious < 0) {
            formed = false;
            cores = 0;
            sumCH = 0;
            precious = 0;
            min = null;
            max = null;
            heatPorts = new long[0];
            waterPorts = new long[0];
            steamPorts = new long[0];
        }
        water = new ResourceBuffer(formed ? SteamGenMath.waterCapacity(cores) : 0);
        steam = new ResourceBuffer(formed ? SteamGenMath.steamCapacity(cores) : 0);
        gth = new GtBuffer(formed ? SteamGenMath.gthCapacityMilli(cores) : 0);
        water.load(tag, "Water");
        steam.load(tag, "Steam");
        gth.load(tag, "Gth");
        rebuildPortSets();
        indexRegistered = false;
    }
}
