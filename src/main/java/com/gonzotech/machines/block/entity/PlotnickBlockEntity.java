package com.gonzotech.machines.block.entity;

import com.gonzotech.core.registry.ModBlocks;
import com.gonzotech.core.registry.ModItems;
import com.gonzotech.core.registry.ModSounds;
import com.gonzotech.machines.energy.GtBuffer;
import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.PlotnickDefs;
import com.gonzotech.machines.energy.Sinks.GtuSink;
import com.gonzotech.machines.item.MemoryCardItem;
import com.gonzotech.machines.menu.PlotnickMenu;
import com.gonzotech.machines.miner.MarkerService;
import com.gonzotech.machines.miner.MinerMarker;
import com.gonzotech.machines.miner.MinerOres;
import com.gonzotech.machines.miner.NetworkPlanner;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.gonzotech.machines.registry.ModMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.NumericTag;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Плотник автошахтера (0.3.256-0.3.257, автор).
 *
 * <p>Хранилище v3 (автор, 0.3.264): девять активных слотов — классические
 * ваниальные стопки по 64, «512-ячейки» отменены. Виртуальный буфер — ровно
 * 216 стопок: до 54 разных предмета по 4 стопки, в каждом типе две стопки
 * «добытое» (туда капают буры; извне только забирают) и две «депо» (внешние
 * кладут, машина ест). Порядок потребления: головы → депо → добытое. Воронки
 * и трубы мода ходят в хранилище через Container-индексы (CONTAINER_TOTAL),
 * правила зон — на методах лица. Два слота — карты памяти.</p>
 *
 * <p>Сектор 3×3 чанка — общая вотчина одного Плотника: лидер — поставленный
 * раньше, прочие впадают в анабиоз (молчат, GUI не открывают), при сломе
 * лидера просыпаются. Каждое действие с блоками (стройка, демонтаж,
 * скважины, ТНТ) проходит гейт приватных правил: ставятся стандартные
 * BlockEvent.EntityPlaceEvent / BlockEvent.BreakEvent от лица ближайшего
 * онлайн-игрока; отменили — клетка откладывается.</p>
 *
 * <p>Кнопка «Разведка» (id 3) без заполненной карты обследует сектор 3×3
 * чанка: чанки без источника колебаний получают метку с его изометрией;
 * источники без геофона сверху — метку геофона на самом источнике; собранные
 * пары меток не получают. С заполненной картой (после импульса) она же
 * отмечает места рудных залежей: до 9 точек бурения на чанк жадным
 * покрытием залежей сеткой 5×5. Итог уходит нажавшему игроку в чат.</p>
 *
 * <p>Кнопка «Построить связи» (id 2) тратит рабочие блоки из хранилища и
 * прокладывает паутину дорожек BFS от Плотника к каждому источнику (или к
 * бурам в точках руд, если карта заполнена). Клетки заменяются только из
 * воздуха/травы/снега; solid-блоки не трогаются, заставленный источник
 * исключается. Очередь кэшируется одним нажатием и расходуется со
 * случайной задержкой 4–8 тиков на блок. «Произвести импульс» (id 1) при
 * полной связности и пустой карте запускает сейсмику и записывает сектор
 * на карту. «Свернуться» (id 4) убирает разметку и снимает очередь.</p>
 */
public final class PlotnickBlockEntity extends BlockEntity
    implements GtuSink, WorldlyContainer, MenuProvider {

    /** Девять активных слотов — классические ваниальные стопки (автор, 0.3.264). */
    public static final int SLOTS_ACTIVE = 9;
    public static final int SLOT_CARD_A = 9;
    public static final int SLOT_CARD_B = 10;
    public static final int CONTAINER_SIZE = 11;
    /** Ваниальный размер стопки: потолок «512» из 0.3.262 автор отменил. */
    public static final int MAX_STACK = 64;
    /** Виртуальный буфер: максимум разных предметов (автор: 54). */
    public static final int VIRTUAL_TYPES = 54;
    /** Стопок по 64 на тип: две «добытое» (только вынимают) + две «депо». */
    public static final int STACKS_PER_TYPE = 4;
    public static final int OUT_STACKS_PER_TYPE = 2;
    /** Виртуальные стопки всего: 54 × 4 = 216 (13 824 предмета). */
    public static final int VIRTUAL_SLOTS = VIRTUAL_TYPES * STACKS_PER_TYPE;
    /**
     * Слотов в Container всего: головы, карты, виртуаль. Трубы мода и ванильные
     * воронки работают по Container-индексам — правила зон висят на лице.
     */
    public static final int CONTAINER_TOTAL = CONTAINER_SIZE + VIRTUAL_SLOTS;

    /** Формат сейвов 0.3.262–0.3.263: виртуаль 432 стопки + подстопки ячеек. */
    private static final int LEGACY_VIRTUAL_SLOTS = 54 * 8;
    private static final int LEGACY_REST_SLOTS = SLOTS_ACTIVE * 7;

    public static final long GTU_CAPACITY_MILLI = PlotnickDefs.GTU_CAPACITY_MILLI;
    public static final long GTU_INTAKE_MILLI = PlotnickDefs.GTU_INTAKE_MILLI;

    /**
     * Кнопки GUI построчно, слева направо (раскладка автора):
     * бурение / произвести импульс; построить связи / разведка (разметка);
     * свернуться / ТНТ.
     */
    public static final int BTN_DRILL = 0;
    public static final int BTN_SEISMO = 1;
    public static final int BTN_NETWORK = 2;
    public static final int BTN_MARK = 3;
    public static final int BTN_PACK_UP = 4;
    public static final int BTN_DYNAMITE = 5;
    /** ПКМ по «Постройке»: лишь подсоединить уже поставленные буры (0.3.259). */
    public static final int BTN_NETWORK_LINK = 6;
    /** ПКМ по «Бурению»: остановить движок (0.3.261). */
    public static final int BTN_DRILL_STOP = 7;

    private final GtBuffer gtu = new GtBuffer(GTU_CAPACITY_MILLI);
    private final NonNullList<ItemStack> items =
        NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
    private final NonNullList<ItemStack> virtual =
        NonNullList.withSize(VIRTUAL_SLOTS, ItemStack.EMPTY);
    /**
     * Анабиоз (автор, 0.3.264): сектор 3×3 принадлежит одному Плотнику,
     * прочие молчат и GUI не открывают. placedAt — чей ход первый.
     */
    private boolean hibernating;
    private boolean sectorElectionDue = true;
    private long placedAt;
    /** Гейт привата дешёвый: одна постановка событий на тик. */
    private long lastTouchTick = Long.MIN_VALUE;
    private BlockPos lastTouchPos = BlockPos.ZERO;
    private boolean lastTouchAllowed = true;
    private boolean lastTouchPlacing;
    /** Что не влезло при свёртке v2-сейва в 216 слотов — роняем первым тиком. */
    private final List<ItemStack> legacyOverflow = new ArrayList<>();

    /** Id принадлежащих именно этому Плотнику секторных меток. */
    private final List<Long> sectorMarkerIds = new ArrayList<>(9);

    /** Точки бурения последней рудной разведки (поверхностный блок столбца). */
    private final List<BlockPos> drillPoints = new ArrayList<>();

    /** Очередь постройки: позиции к закладке, порядок — от сети наружу. */
    private final List<NetworkPlanner.Entry> buildQueue = new ArrayList<>();

    /** Пауза до следующей клетки (случайные 4–8 тиков между блоками). */
    private int placeCooldown;

    /** Игрок, запустивший постройку: ему придёт сообщение о завершении. */
    private ServerPlayer buildRequester;

    /** Очередь демонтажа при «свертывании»: от дальних клеток к Плотнику. */
    private final List<BlockPos> dismantleQueue = new ArrayList<>();
    /** Очередь закладки ТНТ в скваженные трубы: сверху вниз по скважинам. */
    private final List<BlockPos> tntQueue = new ArrayList<>();
    private ServerPlayer packRequester;
    private ServerPlayer tntRequester;
    private int dismantleReturned;

    /** Глубина скважин: клетка бура → Y следующей закладки трубы. */
    private final Map<BlockPos, Integer> drillTargetY = new HashMap<>();
    /** Счетчик цикла буров: 1-й тик — проход вниз, 39 тиков — сбор руд. */
    private int drillTick;
    /** Кэш связной сети рабочих блоков для проверки живости бура (раз в цикл). */
    private Set<BlockPos> drillRegion = Set.of();

    /** Флаг «Начать бурение» (0.3.260): движок молчит, пока кнопка не нажата. */
    private boolean drilling;
    /** Буровые установки, найденные в ленте на старте цикла (0.3.261). */
    private List<BlockPos> cycleDrillCells = List.of();
    /** Постройка съела дефицит: спим до пополнения склада, дырку не ждём. */
    private boolean buildStalled;
    /** Размер очереди на момент последнего «встала» — не повторять тот же текст. */
    private int stallReportedSize = -1;

    /** Полулента 9 чанков для сканов сети: глубже дорог всё равно нет. */
    // 0.3.290 #11-Y: половина ленты — одна константа с вертикальным окном планировщика.
    private static final int WORK_BELT_Y = NetworkPlanner.VERTICAL_BAND;

    // Числа тултипа «Постройки» (0.3.259): пересчитываются, пока открыт GUI.
    private int menuViewers;
    /**
     * 0.3.290 #17: событийный канал «мир правили руками». У LevelChunk в 1.21.4
     * нет runTime (проверено по зеркалу), поэтому setBlockState-миксин ставит
     * штамп игрового времени в эту таблицу — но только для чанков, которые кто-то
     * смотрит (лидер с открытым GUI регистрирует свой сектор). Сканов мира нет.
     */
    private static final java.util.concurrent.ConcurrentHashMap<Long, Long> SECTOR_PULSE =
        new java.util.concurrent.ConcurrentHashMap<>();
    private final long[] sectorPulseSeen = new long[9];
    private boolean networkStatsDirty = true;
    private int netDrillsToPlace;
    private int netRoadsFull;
    private int netDrillsFound;
    private int netRoadsLink;

    private long intakeBudgetTick = Long.MIN_VALUE;
    private int acceptedGtuThisTick;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> gtu.amountUnitsInt();
                case 1 -> occupiedCells();
                // Состояние GUI-тултипов: 1 — в центральном слоте заполненная
                // карта памяти (кнопки переключаются на рудный режим).
                case 2 -> MemoryCardItem.isFilled(items.get(SLOT_CARD_A)) ? 1 : 0;
                case 3 -> netDrillsToPlace;
                case 4 -> netRoadsFull;
                case 5 -> netDrillsFound;
                case 6 -> netRoadsLink;
                default -> 0; // 0.3.267: лор-тоталы 7..15 больше не нужны
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) gtu.set(MachineDefs.toMilli(Math.max(0, value)));
        }

        @Override
        public int getCount() {
            return DATA_SLOTS;
        }
    };

    /** gtu, занято типов, карта, 4 числа «Постройки». Тоталы лора ушли (0.3.267). */
    public static final int DATA_SLOTS = 7;

    public PlotnickBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PLOTNICK.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PlotnickBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        be.dropLegacyOverflow();
        if (be.placedAt == 0L) {
            // «Кто раньше поставил» — время мира на момент первой прошивки.
            be.placedAt = Math.max(1L, server.getGameTime());
            be.setChanged();
        }
        if (be.sectorElectionDue) {
            be.sectorElectionDue = false;
            be.reelectSector(server);
        }
        if (be.hibernating) {
            // Анабиоз — тишина: ни очередей, ни буров, ни GUI. Раз в 2 с —
            // самопроверка: не освободилась ли вотчина (#аналог wake-сигнала,
            // страховка от взрывного слома лидера).
            if (server.getGameTime() % 40 == 0) be.sectorElectionDue = true;
            return;
        }
        be.processBuildQueue(server);
        be.processDismantleQueue(server);
        be.processTntQueue(server);
        be.tickDrills(server);
        // Тултипы GUI: пересчёт ТОЛЬКО по событиям (кнопки/постройка) и
        // только когда окно открыто. Периодического сканирования мира тут
        // больше нет — оно и было источником лаг-стены (0.3.261).
        if (be.buildStalled && !be.buildQueue.isEmpty() && server.getGameTime() % 40 == 0) {
            be.buildStalled = false; // редкая вежливая повторная проба (#10)
        }
        // 0.3.290 #17: GUI открыт — сравниваем девять штампов чанков сектора;
        // свежий штамп = ручная правка (команда/поршень/взрыв) -> однократный пересчёт.
        if (be.menuViewers > 0) be.tickSectorPulse(server);
        if (be.menuViewers > 0 && be.networkStatsDirty) {
            be.networkStatsDirty = false;
            be.refreshNetworkStats(server);
        }
    }

    /** Кто смотрит в GUI: держим счёт, чтобы не сканировать мир вхолостую. */
    public void menuOpened() {
        // Первый зритель: один раз освежить числа ленточным сканом; ручные
        // правки мира вне кнопок иначе вечно держали бы устаревший план (#18).
        if (menuViewers++ == 0) networkStatsDirty = true;
    }

    public void menuClosed() {
        menuViewers = Math.max(0, menuViewers - 1);
    }

    /** 0.3.290 #17: штамп от setBlockState-миксина; горячий путь — одна запись в таблице. */
    public static void pulseBlockChange(ServerLevel level, BlockPos pos) {
        SECTOR_PULSE.computeIfPresent(sectorPulseKey(pos.getX() >> 4, pos.getZ() >> 4),
            (key, stamp) -> level.getGameTime());
    }

    /**
     * Регистрация и сверка штампов девяти чанков своего сектора. Ключ — только
     * координаты чанка: совпадение секторов в разных измерениях способно вызвать
     * лишний пересчёт (безвредно). Таблица растёт числом открытых когда-либо GUI —
     * не удалена намеренно, размер ограничен числом лидеров.
     */
    private void tickSectorPulse(ServerLevel server) {
        int cx = worldPosition.getX() >> 4;
        int cz = worldPosition.getZ() >> 4;
        long tick = server.getGameTime();
        boolean changed = false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int idx = (dx + 1) * 3 + (dz + 1);
                long stamp = SECTOR_PULSE.computeIfAbsent(sectorPulseKey(cx + dx, cz + dz), key -> tick);
                if (stamp != sectorPulseSeen[idx]) {
                    sectorPulseSeen[idx] = stamp;
                    changed = true;
                }
            }
        }
        if (changed) networkStatsDirty = true;
    }

    private static long sectorPulseKey(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    /**
     * Числа для тултипа: БУРЫ = сколько ещё ставить (по точкам разметки),
     * ДОРОГИ_ЛКМ = рабочих блоков для полной постройки, НАЙДЕНО = уже
     * стоящих буров в секторе, ДОРОГИ_ПКМ = блоков, чтобы их соединить.
     */
    private void refreshNetworkStats(ServerLevel server) {
        SectorScan scan = collectSectorScan(server);
        netDrillsFound = scan.drills().size();
        boolean drillMode = MemoryCardItem.isFilled(items.get(SLOT_CARD_A))
            && isOwnSector(MemoryCardItem.readSector(items.get(SLOT_CARD_A)))
            && MemoryCardItem.dimensionMatches(items.get(SLOT_CARD_A), server);
        List<BlockPos> fullAnchors = new ArrayList<>();
        int missing = 0;
        if (drillMode) {
            for (BlockPos point : drillPoints) {
                BlockPos cell = drillCellFor(server, point);
                if (cell == null) continue;
                if (!server.getBlockState(cell).is(ModMachines.MINER_DRILL.get())) missing++;
                fullAnchors.add(cell);
            }
        } else {
            fullAnchors.addAll(findSectorSources(server));
        }
        netDrillsToPlace = missing;
        netRoadsFull = NetworkPlanner.plan(server, worldPosition, scan.workCells(), fullAnchors)
            .entries().size();
        netRoadsLink = netDrillsFound == 0 ? 0
            : NetworkPlanner.plan(server, worldPosition, scan.workCells(), scan.drills())
                .entries().size();
    }

    /** Одна полновысотная pass по 9 чанкам: рабочие клетки и поставленные буры. */
    private record SectorScan(Set<BlockPos> workCells, List<BlockPos> drills) {
    }

    private SectorScan collectSectorScan(ServerLevel server) {
        return scanSurface(server, WORK_BELT_Y);
    }

    /**
     * Лента 9 чанков вокруг Плотника: рабочие клетки и буровые установки.
     * Сеть живёт у поверхности — полно-высотный скан 3×3 чанка (~880 тыс.
     * чтений) был главной лаг-стеной GUI; лента в 48 уровней в обе стороны
     * дешевле на порядок и покрывает любые разумные дороги.
     */
    private SectorScan scanSurface(ServerLevel server, int halfBand) {
        int cx = worldPosition.getX() >> 4;
        int cz = worldPosition.getZ() >> 4;
        net.minecraft.world.level.block.Block work = ModMachines.WORK_BLOCK.get();
        net.minecraft.world.level.block.Block drill = ModMachines.MINER_DRILL.get();
        Set<BlockPos> workCells = new HashSet<>();
        List<BlockPos> drills = new ArrayList<>();
        int y0 = Math.max(server.getMinY(), worldPosition.getY() - halfBand);
        int y1 = Math.min(server.getMaxY(), worldPosition.getY() + halfBand);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                LevelChunk chunk = server.getChunk(cx + dx, cz + dz);
                int baseX = (cx + dx) << 4;
                int baseZ = (cz + dz) << 4;
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        for (int y = y0; y <= y1; y++) {
                            net.minecraft.world.level.block.state.BlockState st =
                                chunk.getBlockState(new BlockPos(lx, y, lz));
                            if (st.is(work)) {
                                workCells.add(new BlockPos(baseX + lx, y, baseZ + lz).immutable());
                            } else if (st.is(drill)) {
                                drills.add(new BlockPos(baseX + lx, y, baseZ + lz).immutable());
                            }
                        }
                    }
                }
            }
        }
        return new SectorScan(workCells, drills);
    }

    public GtBuffer gtuBuffer() {
        return gtu;
    }

    @Override
    public long receiveGtu(long amount, boolean simulate) {
        resetIntakeLedgerIfNeeded();
        long remaining = Math.max(0L, GTU_INTAKE_MILLI - acceptedGtuThisTick);
        long accepted = gtu.receive(Math.min(amount, remaining), simulate);
        if (!simulate) acceptedGtuThisTick += (int) accepted;
        if (!simulate && accepted > 0) setChanged(); // #29: приём энергии помечает BE к сейву
        return accepted;
    }

    private void resetIntakeLedgerIfNeeded() {
        long tick = level == null ? Long.MIN_VALUE : level.getGameTime();
        if (tick == intakeBudgetTick) return;
        intakeBudgetTick = tick;
        acceptedGtuThisTick = 0;
    }

    // ─────────────────────────── кнопки ───────────────────────────

    /** @return кнопка обработана */
    public boolean handleButton(int id) {
        return handleButton(id, null);
    }

    /** @param requester игрок, нажавший кнопку; ему уходят отчёт и уведомления */
    public boolean handleButton(int id, ServerPlayer requester) {
        if (!(level instanceof ServerLevel server)) return false;
        if (hibernating) {
            // Спящий Плотник кнопок не слушает («не открывает GUI» — та же
            // логика, что и в useWithoutItem; дублируем на сетевой путь).
            report(requester, "message.gonzotech.plotnick.hibernating");
            return true;
        }
        return switch (id) {
            case BTN_PACK_UP -> packUp(server, requester);
            case BTN_MARK -> placeSectorMarks(server, requester);
            case BTN_NETWORK -> buildNetwork(server, requester);
            case BTN_SEISMO -> seismicImpulse(server, requester);
            case BTN_DYNAMITE -> rigTnt(server, requester);
            case BTN_NETWORK_LINK -> linkExistingDrills(server, requester);
            case BTN_DRILL -> startDrilling(server, requester);
            case BTN_DRILL_STOP -> stopDrilling(requester);
            default -> false;
        };
    }

    /**
     * «Разведка (разметка)»: обследует 9 чанков сектора 3×3.
     *
     * <p>Чанк без источника колебаний получает обычную поверхностную метку,
     * но на дисплее вымпела вместо красного флажка — изометрия источника.
     * Источник без геофона непосредственно сверху получает новую метку на
     * самом источнике с изометрией геофона (вымпел стоит над ним, ровно там,
     * куда нужно поставить второй блок). Собранная пара
     * «геофон на источнике» метки не получает.</p>
     *
     * <p>В соседних чанках поверхностная метка — ближайший именно к Плотнику
     * столбец поверхности (clamp XZ Плотника в целевой чанк); в своём чанке —
     * столбец вплотную рядом, но никогда не уходящий за его границу.</p>
     */
    private boolean placeSectorMarks(ServerLevel server, ServerPlayer requester) {
        ItemStack card = items.get(SLOT_CARD_A);
        if (MemoryCardItem.isFilled(card)) {
            if (!isOwnSector(MemoryCardItem.readSector(card))
                || !MemoryCardItem.dimensionMatches(card, server)) {
                // Плотник размечает только свой сектор своего мира: чужая
                // карта — повод встать, а не перекраивать чужие земли.
                report(requester, "message.gonzotech.plotnick.ore.wrongsector");
                return true;
            }
            return mineSurvey(server, requester, card);
        }
        return markSeismicSector(server, requester);
    }

    /** Сектор карты должен совпадать с сектором 3×3 чанка вокруг этого Плотника. */
    private boolean isOwnSector(MemoryCardItem.Sector sector) {
        if (sector == null) return false;
        int cx = worldPosition.getX() >> 4;
        int cz = worldPosition.getZ() >> 4;
        return sector.minX() == cx - 1 && sector.maxX() == cx + 1
            && sector.minZ() == cz - 1 && sector.maxZ() == cz + 1;
    }

    private boolean markSeismicSector(ServerLevel server, ServerPlayer requester) {
        MarkerService service = MarkerService.get(server);
        clearSectorMarks(server, service);

        BlockPos base = worldPosition;
        int ownChunkX = base.getX() >> 4;
        int ownChunkZ = base.getZ() >> 4;
        int ownChunkMinX = ownChunkX << 4;
        int ownChunkMinZ = ownChunkZ << 4;
        List<Item> sourceDisplay = List.of(ModMachines.SEISMIC_SOURCE_ITEM.get());
        List<Item> geophoneDisplay = List.of(ModMachines.GEOPHONE_ITEM.get());

        int markedChunks = 0;
        int sourceCount = 0;
        int assembledCount = 0;
        boolean markedMissingGeophones = false;
        Set<BlockPos> geophonePositions = new HashSet<>();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int targetChunkMinX = ownChunkMinX + dx * 16;
                int targetChunkMinZ = ownChunkMinZ + dz * 16;

                // Источники ищем по BlockEntity чанка — установка может стоять
                // не только в расчётном поверхностном столбце.
                LevelChunk chunk = server.getChunk(ownChunkX + dx, ownChunkZ + dz);
                List<BlockPos> sources = new ArrayList<>();
                for (BlockEntity chunkEntity : chunk.getBlockEntities().values()) {
                    if (chunkEntity instanceof SeismicSourceBlockEntity) {
                        sources.add(chunkEntity.getBlockPos().immutable());
                    }
                }
                sourceCount += sources.size();

                // Отдельно стоящие геофоны на поверхности тоже попадают в отчёт;
                // парные дополнительно проверим прямо над источниками ниже.
                for (int localX = 0; localX < 16; localX++) {
                    for (int localZ = 0; localZ < 16; localZ++) {
                        BlockPos surfaceTop = server.getHeightmapPos(
                            net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            new BlockPos(targetChunkMinX + localX, base.getY(),
                                targetChunkMinZ + localZ)).below();
                        if (server.getBlockState(surfaceTop).is(ModMachines.GEOPHONE.get())) {
                            geophonePositions.add(surfaceTop.immutable());
                        }
                    }
                }

                if (sources.isEmpty()) {
                    int x;
                    int z;
                    if (dx == 0 && dz == 0) {
                        int localX = base.getX() - ownChunkMinX;
                        x = localX < 15 ? base.getX() + 1 : base.getX() - 1;
                        z = base.getZ();
                    } else {
                        x = Mth.clamp(base.getX(), targetChunkMinX, targetChunkMinX + 15);
                        z = Mth.clamp(base.getZ(), targetChunkMinZ, targetChunkMinZ + 15);
                    }
                    BlockPos surface = server.getHeightmapPos(
                        net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        new BlockPos(x, base.getY(), z)).below();
                    MinerMarker marker = service.add(
                        server, surface, MinerMarker.Kind.STATION, 0, sourceDisplay,
                        worldPosition.asLong());
                    sectorMarkerIds.add(marker.id());
                    markedChunks++;
                    continue;
                }

                for (BlockPos sourcePos : sources) {
                    BlockPos above = sourcePos.above();
                    if (server.getBlockState(above).is(ModMachines.GEOPHONE.get())) {
                        geophonePositions.add(above.immutable());
                        assembledCount++;
                    } else {
                        MinerMarker marker = service.add(server, sourcePos,
                            MinerMarker.Kind.STATION, 0, geophoneDisplay,
                            worldPosition.asLong());
                        sectorMarkerIds.add(marker.id());
                        markedMissingGeophones = true;
                    }
                }
            }
        }
        setChanged();

        if (requester != null) {
            // «Отмечены» в формулировке автора — чанки без источника; метки
            // недостающих геофонов оговариваются отдельным хвостом сообщения.
            var message = Component.translatable("message.gonzotech.plotnick.survey",
                9, markedChunks, sourceCount, geophonePositions.size(), assembledCount);
            if (markedMissingGeophones) {
                message.append(Component.translatable(
                    "message.gonzotech.plotnick.survey.geophone_marks"));
            }
            requester.sendSystemMessage(message);
        }
        return true;
    }

    /**
     * Убрать разметку этого Плотника. Для записанных в BE меток удаление
     * точечное по id. Если id нет (метки поставлены первой версией 0.3.256),
     * убираем все STATION-метки в секторе этого Плотника.
     */
    private boolean clearSectorMarks(ServerLevel server) {
        boolean changed = clearSectorMarks(server, MarkerService.get(server));
        if (changed) setChanged();
        return changed;
    }

    private boolean clearSectorMarks(ServerLevel server, MarkerService service) {
        if (!sectorMarkerIds.isEmpty()) {
            boolean changed = service.removeAll(server, sectorMarkerIds);
            sectorMarkerIds.clear();
            return changed;
        }
        return service.removeStationMarkersInSector(server, worldPosition);
    }

    // ─────────────────────────── рудная разведка ───────────────────────────

    /**
     * «Отметить места рудных залежей»: сканирует сектор с карты памяти до
     * бедрока и жадно выбирает до 9 точек бурения на чанк так, чтобы каждая
     * следующая закрывала максимум ещё не покрытой руды (сечение забора
     * туннеля 5×5 по XZ, по Y туннель проходит всю толщу). Точки
     * запоминаются в BE: по ним потом строятся дорожки и ставятся буры.
     */
    private boolean mineSurvey(ServerLevel server, ServerPlayer requester, ItemStack card) {
        MemoryCardItem.Sector sector = MemoryCardItem.readSector(card);
        if (sector == null) {
            int ownCx = worldPosition.getX() >> 4;
            int ownCz = worldPosition.getZ() >> 4;
            sector = new MemoryCardItem.Sector(ownCx - 1, ownCx + 1, ownCz - 1, ownCz + 1);
        }
        MarkerService service = MarkerService.get(server);
        clearSectorMarks(server, service);
        drillPoints.clear();

        int oreBlocks = 0;
        int marks = 0;
        int sourceMask = MemoryCardItem.readSourceMask(card);
        int centerCx = worldPosition.getX() >> 4;
        int centerCz = worldPosition.getZ() >> 4;
        for (int cx = sector.minX(); cx <= sector.maxX(); cx++) {
            for (int cz = sector.minZ(); cz <= sector.maxZ(); cz++) {
                if (sourceMask != 0) {
                    int dx = cx - centerCx;
                    int dz = cz - centerCz;
                    if (dx < -1 || dx > 1 || dz < -1 || dz > 1
                        || (sourceMask & (1 << ((dx + 1) * 3 + (dz + 1)))) == 0) {
                        continue; // чанк не простучан — информации о нём нет
                    }
                }
                LevelChunk chunk = server.getChunk(cx, cz);
                int baseX = cx << 4;
                int baseZ = cz << 4;
                List<BlockPos> ores = new ArrayList<>();
                List<Item> oreItems = new ArrayList<>();
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        for (int y = server.getMinY(); y <= server.getMaxY(); y++) {
                            BlockState state = chunk.getBlockState(new BlockPos(lx, y, lz));
                            if (state.isAir()) continue;
                            if (!MinerOres.isOre(state)) continue;
                            ores.add(new BlockPos(baseX + lx, y, baseZ + lz));
                            Item item = state.getBlock().asItem();
                            if (item != null && !oreItems.contains(item)) oreItems.add(item);
                        }
                    }
                }
                oreBlocks += ores.size();
                if (ores.isEmpty()) continue;

                // Для каждой клетки-кандидата чанка — список индексов руд,
                // которые она закрывает пятном 5×5.
                Map<Integer, List<Integer>> cover = new HashMap<>();
                for (int i = 0; i < ores.size(); i++) {
                    BlockPos ore = ores.get(i);
                    int lx = ore.getX() - baseX;
                    int lz = ore.getZ() - baseZ;
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            int px = lx + dx;
                            int pz = lz + dz;
                            if (px < 0 || px > 15 || pz < 0 || pz > 15) continue;
                            cover.computeIfAbsent(px * 16 + pz, k -> new ArrayList<>()).add(i);
                        }
                    }
                }
                boolean[] coveredOre = new boolean[ores.size()];
                int coveredTotal = 0;
                for (int pick = 0; pick < 9 && coveredTotal < ores.size(); pick++) {
                    int bestKey = -1;
                    int bestGain = 0;
                    for (Map.Entry<Integer, List<Integer>> entry : cover.entrySet()) {
                        if (entry.getValue().isEmpty()) continue;
                        int gain = 0;
                        for (int index : entry.getValue()) {
                            if (!coveredOre[index]) gain++;
                        }
                        if (gain > bestGain) {
                            bestGain = gain;
                            bestKey = entry.getKey();
                        }
                    }
                    if (bestKey < 0) break;
                    List<Integer> bestCover = cover.get(bestKey);
                    for (int index : bestCover) {
                        if (!coveredOre[index]) {
                            coveredOre[index] = true;
                            coveredTotal++;
                        }
                    }
                    bestCover.clear();

                    int px = baseX + bestKey / 16;
                    int pz = baseZ + bestKey % 16;
                    BlockPos surface = server.getHeightmapPos(
                        net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        new BlockPos(px, worldPosition.getY(), pz)).below().immutable();
                    drillPoints.add(surface);
                    List<Item> display = oreItems.size() > 3 ? oreItems.subList(0, 3) : oreItems;
                    MinerMarker marker =
                        service.add(server, surface, MinerMarker.Kind.ORE, 0, display,
                            worldPosition.asLong());
                    // id рудной метки в секторном списке: «Свернуться» и
                    // следующая разметка убирают их тем же механизмом.
                    sectorMarkerIds.add(marker.id());
                    marks++;
                }
            }
        }
        setChanged();
        networkStatsDirty = true;
        if (requester != null) {
            requester.sendSystemMessage(oreBlocks == 0
                ? Component.translatable("message.gonzotech.plotnick.ore.none")
                : Component.translatable("message.gonzotech.plotnick.ore.done",
                    oreBlocks, drillPoints.size(), marks));
        }
        return true;
    }

    // ─────────────────────────── постройка сети ───────────────────────────

    /**
     * «Построить связи»: один снимок мира → кэш очереди. Без заполненной
     * карты цели — все источники колебаний сектора (пять из девяти — значит
     * строим пять, остальные чанки просто ждут разметки). С картой цели —
     * точки руд: недостающие буры берутся из хранилища первыми клетками
     * очереди, дорожки переиспользуют уже построенные ветви сети источников.
     */
    private boolean buildNetwork(ServerLevel server, ServerPlayer requester) {
        if (!buildQueue.isEmpty()) {
            report(requester, "message.gonzotech.plotnick.network.busy", buildQueue.size());
            return true;
        }
        boolean drillMode = MemoryCardItem.isFilled(items.get(SLOT_CARD_A));
        if (drillMode && (!isOwnSector(MemoryCardItem.readSector(items.get(SLOT_CARD_A)))
            || !MemoryCardItem.dimensionMatches(items.get(SLOT_CARD_A), server))) {
            report(requester, "message.gonzotech.plotnick.ore.wrongsector");
            return true;
        }
        List<BlockPos> anchors = new ArrayList<>();
        List<BlockPos> drillSlots = new ArrayList<>();
        int skipped = 0;

        if (drillMode) {
            if (drillPoints.isEmpty()) {
                report(requester, "message.gonzotech.plotnick.network.nopoints");
                return true;
            }
            for (BlockPos point : drillPoints) {
                BlockPos cell = drillCellFor(server, point);
                if (cell == null) {
                    skipped++; // нет ни места под бур, ни клетки-мишени
                    continue;
                }
                if (!server.getBlockState(cell).is(ModMachines.MINER_DRILL.get())) {
                    drillSlots.add(cell);
                }
                anchors.add(cell);
            }
        } else {
            anchors.addAll(findSectorSources(server));
            if (anchors.isEmpty()) {
                report(requester, "message.gonzotech.plotnick.network.nosources");
                return true;
            }
        }

        NetworkPlanner.Plan plan =
            NetworkPlanner.plan(server, worldPosition, scanSurface(server, WORK_BELT_Y).workCells(),
                anchors);
        // Порядок автора: сначала дорожки через рабочие блоки, буры — только
        // после того, как сеть дошла до точек.
        List<NetworkPlanner.Entry> queue = new ArrayList<>(plan.entries());
        for (BlockPos drillCell : drillSlots) queue.add(new NetworkPlanner.Entry(drillCell, true));
        netRoadsFull = queue.size();
        netDrillsToPlace = drillSlots.size();

        if (queue.isEmpty()) {
            report(requester, "message.gonzotech.plotnick.network.noneeded", plan.already());
            return true;
        }
        buildQueue.clear();
        buildQueue.addAll(queue);
        buildStalled = false; // новая работа — старое «спим» снимается (#10)
        stallReportedSize = -1;
        placeCooldown = 2;
        buildRequester = requester;
        setChanged();
        networkStatsDirty = true;
        report(requester, "message.gonzotech.plotnick.network.planned",
            plan.targets(), plan.already(), plan.unreachable() + skipped, queue.size(),
            countStored(ModMachines.WORK_BLOCK.get().asItem())
                + countStored(ModMachines.MINER_DRILL.get().asItem()));
        return true;
    }

    /**
     * ПКМ по «Постройке» (0.3.259): ничего не докладываем из буров — только
     * прокладываем дороги к уже поставленным буровым установкам в секторе и
     * берём их под управление движком (иначе плотник «не видит» руки игрока).
     */
    private boolean linkExistingDrills(ServerLevel server, ServerPlayer requester) {
        if (!buildQueue.isEmpty()) {
            report(requester, "message.gonzotech.plotnick.network.busy", buildQueue.size());
            return true;
        }
        SectorScan scan = collectSectorScan(server);
        List<BlockPos> placed = scan.drills();
        if (placed.isEmpty()) {
            report(requester, "message.gonzotech.plotnick.network.nodrills");
            return true;
        }
        NetworkPlanner.Plan plan =
            NetworkPlanner.plan(server, worldPosition, scan.workCells(), placed);
        netRoadsLink = plan.entries().size();
        if (plan.entries().isEmpty()) {
            report(requester, "message.gonzotech.plotnick.network.noneeded", plan.already());
            return true;
        }
        buildQueue.clear();
        buildQueue.addAll(plan.entries());
        buildStalled = false; // #10
        stallReportedSize = -1;
        placeCooldown = 2;
        buildRequester = requester;
        setChanged();
        // Движок (0.3.261) сам пасёт любой связный бур — усыновление точками
        // больше не нужно, ПКМ ведёт только дороги.
        report(requester, "message.gonzotech.plotnick.network.linked",
            placed.size(), plan.entries().size());
        return true;
    }

    /** Клетка под бур: воздух над точкой руды, либо сама точка, если она мягкая. */
    private static BlockPos drillCellFor(ServerLevel server, BlockPos point) {
        BlockPos above = point.above();
        if (NetworkPlanner.isBuildable(server.getBlockState(above), server.getBlockState(point))) {
            return above;
        }
        if (NetworkPlanner.isBuildable(server.getBlockState(point), server.getBlockState(point.below()))) {
            return point;
        }
        return null;
    }

    /** Источники колебаний 9 чанков сектора — по BlockEntity чанков. */
    private List<BlockPos> findSectorSources(ServerLevel server) {
        int cx = worldPosition.getX() >> 4;
        int cz = worldPosition.getZ() >> 4;
        List<BlockPos> found = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (BlockEntity chunkEntity
                    : server.getChunk(cx + dx, cz + dz).getBlockEntities().values()) {
                    if (chunkEntity instanceof SeismicSourceBlockEntity) {
                        found.add(chunkEntity.getBlockPos().immutable());
                    }
                }
            }
        }
        return found;
    }

    /** Тик-расходование очереди: 4–8 тиков между блоками, звук и частицы на каждый. */
    private void processBuildQueue(ServerLevel server) {
        if (buildQueue.isEmpty() || buildStalled) return;
        if (placeCooldown > 0) {
            placeCooldown--;
            return;
        }
        // Один проход по очереди: готовые/неподходящие клетки молча уходят,
        // дефицитная — в хвост. Замерзать на первой дырке нельзя: дальние
        // буры и объезженные участки достроятся, когда позволят блоки.
        int cycle = buildQueue.size();
        for (int i = 0; i < cycle; i++) {
            NetworkPlanner.Entry entry = buildQueue.remove(0);
            BlockState target = entry.drill()
                ? ModMachines.MINER_DRILL.get().defaultBlockState()
                : ModMachines.WORK_BLOCK.get().defaultBlockState();
            BlockPos pos = entry.pos();
            BlockState current = server.getBlockState(pos);
            if (current.is(target.getBlock())) continue; // уже стоит
            if (!NetworkPlanner.isBuildable(current, server.getBlockState(pos.below()))) continue;
            if (!mayTouchPlace(server, pos)) {
                buildQueue.add(entry); // приват запретил постановку — клетка ждёт в хвосте
                continue;
            }
            Item needed = target.getBlock().asItem();
            if (countStored(needed) == 0) {
                buildQueue.add(entry); // нечем — переезжает в хвост
                continue;
            }
            consumeStored(needed, 1);
            placeQueueBlockNow(server, pos, target);
            if (entry.drill()) {
                netDrillsToPlace = Math.max(0, netDrillsToPlace - 1);
            } else {
                netRoadsFull = Math.max(0, netRoadsFull - 1);
                netRoadsLink = Math.max(0, netRoadsLink - 1);
            }
            setChanged();
            placeCooldown = 4 + server.random.nextInt(5);
            if (buildQueue.isEmpty()) {
                networkStatsDirty = true;
                if (buildRequester != null) {
                    report(buildRequester, "message.gonzotech.plotnick.network.done");
                }
                buildRequester = null;
            }
            return;
        }
        // Цикл без единой установки — склад пуст. Спим; разбудит insert(),
        // ручное пополнение или мягкий retry раз в 2 с (#10: клик-мердж в
        // GUI проходит мимо insert).
        buildStalled = true;
        setChanged();
        if (buildQueue.size() != stallReportedSize) {
            stallReportedSize = buildQueue.size();
            report(buildRequester, "message.gonzotech.plotnick.network.stalled", buildQueue.size());
        }
    }

    /**
     * Одна установка блока: звук из SoundType (в 1.21.4 SoundEvents.BLOCK_*_PLACE
     * нет) и «усадка» слома-частицами самого рабочего блока (block_crack
     * по старому имени). Заменённые блоки частиц не дают.
     */
    private void placeQueueBlockNow(ServerLevel server, BlockPos pos, BlockState target) {
        server.setBlock(pos, target, net.minecraft.world.level.block.Block.UPDATE_ALL);
        server.playSound(null, pos, SoundType.METAL.getPlaceSound(), SoundSource.BLOCKS,
            0.65F, 0.9F + 0.2F * server.random.nextFloat());
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        BlockParticleOption crumble = new BlockParticleOption(ParticleTypes.BLOCK, target);
        server.sendParticles(crumble, cx, cy, cz, 14, 0.22, 0.22, 0.22, 0.05D);
        server.sendParticles(crumble, cx, cy, cz, 6, 0.38, 0.38, 0.38, 0.015D);
    }

    // ─────────────────────────── сейсмический импульс ───────────────────────────

    /**
     * «Произвести импульс»: все найденные источники сектора подключены
     * рабочими дорожками к Плотнику и заряжены динамитом, в центральном
     * слоте лежит пустая карта памяти. Каждый источник срабатывает глухим
     * ударом по площадке (звук geo_impact + поднимающийся вверх пол),
     * динамит расходуется, а сектор записывается на карту.
     */
    private boolean seismicImpulse(ServerLevel server, ServerPlayer requester) {
        ItemStack card = items.get(SLOT_CARD_A);
        if (!card.is(ModMachines.MEMORY_CARD.get())) {
            report(requester, "message.gonzotech.plotnick.impulse.nocard");
            return true;
        }
        if (MemoryCardItem.isFilled(card)) {
            report(requester, "message.gonzotech.plotnick.impulse.filled");
            return true;
        }
        List<BlockPos> sources = findSectorSources(server);
        if (sources.isEmpty()) {
            report(requester, "message.gonzotech.plotnick.impulse.nosources");
            return true;
        }
        Set<BlockPos> region = NetworkPlanner.connectedWorkRegion(
            server, worldPosition, scanSurface(server, WORK_BELT_Y).workCells());
        List<SeismicSourceBlockEntity> armed = new ArrayList<>();
        int notConnected = 0;
        int notLoaded = 0;
        int noGeophone = 0;
        for (BlockPos sourcePos : sources) {
            boolean linked = sourcePos.distManhattan(worldPosition) == 1
                || NetworkPlanner.touching(sourcePos, region);
            if (!linked) {
                notConnected++;
                continue;
            }
            // Сборка «источник + геофон сверху» — условие записи чанка в карту
            // (0.3.263, замечание автора #13): голый источник не стучит.
            if (!server.getBlockState(sourcePos.above()).is(ModMachines.GEOPHONE.get())) {
                noGeophone++;
                continue;
            }
            if (server.getBlockEntity(sourcePos) instanceof SeismicSourceBlockEntity source) {
                // Автозарядка: разряженный источник получает одну единицу
                // динамита из активных слотов Плотника, если она там есть.
                if (!source.hasDynamite() && consumeActive(Items.TNT, 1) == 1) {
                    source.setItem(SeismicSourceBlockEntity.SLOT_DYNAMITE,
                        new ItemStack(Items.TNT));
                }
                if (source.hasDynamite()) {
                    armed.add(source);
                } else {
                    notLoaded++;
                }
            } else {
                notLoaded++;
            }
        }
        if (notConnected > 0) {
            report(requester, "message.gonzotech.plotnick.impulse.notconnected", notConnected);
            return true;
        }
        if (notLoaded > 0) {
            report(requester, "message.gonzotech.plotnick.impulse.noloaded", notLoaded);
            return true;
        }
        if (noGeophone > 0) {
            report(requester, "message.gonzotech.plotnick.impulse.nogeophone", noGeophone);
            return true;
        }

        int cx = worldPosition.getX() >> 4;
        int cz = worldPosition.getZ() >> 4;
        // Маска: чанки, где реально сработал источник. Разметка руд later
        // ограничится ими — карта честнее и скан дешевле (0.3.261, автор).
        int sourceMask = 0;
        for (SeismicSourceBlockEntity source : armed) {
            int dx = (source.getBlockPos().getX() >> 4) - cx;
            int dz = (source.getBlockPos().getZ() >> 4) - cz;
            if (dx >= -1 && dx <= 1 && dz >= -1 && dz <= 1) {
                sourceMask |= 1 << ((dx + 1) * 3 + (dz + 1));
            }
        }
        MemoryCardItem.writeSector(card,
            new MemoryCardItem.Sector(cx - 1, cx + 1, cz - 1, cz + 1), sourceMask,
            server.dimension()); // #14: карта помнит мир, где простучана
        setItem(SLOT_CARD_A, card);

        // Один глухой удар на весь импульс — у Плотника, а не девятикратное
        // наслоение по источникам.
        server.playSound(null, worldPosition, ModSounds.GEO_IMPACT.get(), SoundSource.BLOCKS,
            1.6F, 0.95F + 0.1F * server.random.nextFloat());
        for (SeismicSourceBlockEntity source : armed) {
            source.setItem(SeismicSourceBlockEntity.SLOT_DYNAMITE, ItemStack.EMPTY);
            source.setChanged();
            spawnSeismicPulse(server, source.getBlockPos());
        }
        report(requester, "message.gonzotech.plotnick.impulse.done", armed.size());
        return true;
    }

    /** Подъём пола кольцом вокруг точки источника (звук — общий, один). */
    private void spawnSeismicPulse(ServerLevel server, BlockPos sourcePos) {
        double x = sourcePos.getX() + 0.5;
        double z = sourcePos.getZ() + 0.5;
        BlockPos ground = sourcePos.below();
        BlockState groundState = server.getBlockState(ground);
        if (groundState.isAir()) return;
        BlockParticleOption dust = new BlockParticleOption(ParticleTypes.DUST_PILLAR, groundState);
        int count = 40;
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0 * i / count;
            server.sendParticles(dust,
                x + Math.cos(angle) * 0.9, ground.getY() + 1.01, z + Math.sin(angle) * 0.9,
                1, 0.08, 0.04, 0.08, 0.02);
        }
    }

    // ─────────────────────────── работа буров (0.3.258) ───────────────────────────

    /**
     * Буры живут цифровым тиком Плотника — физической сети труб не нужно,
     * только маршрут (целостность сети рабочих блоков). Каждый работающий
     * бур сдвигается на блок вниз раз в 2 секунды, подменяя блок под
     * скважиной новой скваженной трубой (труба берется из хранилища), а
     * остальные 39 тиков выкачивает руду с текущего Y пятном 5×5. Всё
     * добытое (руда, порода, грунт) телепортируется в Плотник и не
     * выпадает. Бур, упоршийся в бедрок/обсидиан/барьер и иные сверхпрочные
     * блоки, не работает и энергию не жрёт.
     */
    private void tickDrills(ServerLevel server) {
        drillTick = (drillTick + 1) % PlotnickDefs.DRILL_CYCLE_TICKS;
        boolean cycleStart = drillTick == 0 && drilling;
        if (cycleStart) {
            // «Маршрут» бура и перекличка установок — одной лентой, что и у
            // планировщика сети: разная полоса рождала «невидимые» буры (#12).
            SectorScan belt = scanSurface(server, WORK_BELT_Y);
            drillRegion = NetworkPlanner.connectedWorkRegion(
                server, worldPosition, belt.workCells());
            cycleDrillCells = belt.drills();
        }
        if (!drilling || cycleDrillCells.isEmpty()) return;
        DrillRun run = planDrillRun(server);
        if (run.running().isEmpty()) return;

        // Равномерное деление не нужно: просто цифровой вычет баланса
        // (6.5 GTU/т × число работающих буров). Нечем платить — цикл стоит.
        long want = PlotnickDefs.DRILL_GTU_MILLI * run.running().size();
        if (gtu.extract(want, true) < want) return;
        gtu.extract(want, false);

        for (BlockPos drillCell : run.running()) {
            if (cycleStart) digStep(server, drillCell);
            collectOreRow(server, drillCell);
        }
    }

    /**
     * Перекличка скважин (0.3.261): бур жив, если примыкает к сети (или к
     * самому Плотнику), не упёрся и обеспечен трубой со склада. Смотрим на
     * фактические MINER_DRILL ленты — метки и карта не участвуют: плотник
     * + прилегающий бур обязаны работать. Каждый активный бур тратит на
     * цикл ровно одну трубу — платим только за обеспеченные скважины.
     */
    private DrillRun planDrillRun(ServerLevel server) {
        int pipesAvailable = countStored(ModMachines.BORE_PIPE.get().asItem());
        List<BlockPos> candidates = new ArrayList<>();
        int linked = 0;
        int stuck = 0;
        for (BlockPos drillCell : cycleDrillCells) {
            boolean alive = drillCell.distManhattan(worldPosition) <= 1
                || NetworkPlanner.touching(drillCell, drillRegion);
            if (!alive) continue;
            linked++;
            int targetY = drillTargetY.computeIfAbsent(drillCell,
                cell -> deepestTargetY(server, cell));
            BlockPos target = new BlockPos(drillCell.getX(), targetY, drillCell.getZ());
            if (isImmovable(server.getBlockState(target), server, target)) {
                stuck++;
                continue;
            }
            candidates.add(drillCell);
        }
        List<BlockPos> running = candidates.size() > pipesAvailable
            ? new ArrayList<>(candidates.subList(0, Math.max(0, pipesAvailable)))
            : candidates;
        return new DrillRun(running, linked, stuck, pipesAvailable);
    }

    /** Итог переклички: что бурит, что мешается. */
    private record DrillRun(List<BlockPos> running, int linked, int stuck, int pipes) {
    }

    /**
     * ЛКМ «Начать бурение» (0.3.261): либо старт с числом скважин и ставкой,
     * либо честный ответ, чего не хватает. ПКМ по той же кнопке — стоп.
     */
    private boolean startDrilling(ServerLevel server, ServerPlayer requester) {
        if (!dismantleQueue.isEmpty()) {
            // Разборка и бурение одновременно — противоречивые режимы (#9).
            report(requester, "message.gonzotech.plotnick.drill.dismantle");
            return true;
        }
        // Перекличка по факту мира: любой бур в ленте, а не только меченый.
        SectorScan belt = scanSurface(server, WORK_BELT_Y);
        drillRegion = NetworkPlanner.connectedWorkRegion(
            server, worldPosition, belt.workCells());
        cycleDrillCells = belt.drills();
        if (cycleDrillCells.isEmpty()) {
            report(requester, "message.gonzotech.plotnick.drill.nodrills");
            return true;
        }
        DrillRun run = planDrillRun(server);
        if (run.pipes() == 0) {
            report(requester, "message.gonzotech.plotnick.drill.nopipes");
            return true;
        }
        if (run.linked() == 0) {
            report(requester, "message.gonzotech.plotnick.drill.nolink");
            return true;
        }
        if (run.running().isEmpty() && run.stuck() > 0) {
            report(requester, "message.gonzotech.plotnick.drill.stuck", run.stuck());
            return true;
        }
        drilling = true;
        drillTick = 0;
        setChanged();
        int n = Math.max(1, run.running().size());
        long want = PlotnickDefs.DRILL_GTU_MILLI * run.running().size();
        // Ставка в отчёте — GTU в секунду (6.5 GTU/т × 20), целое без потерь.
        long costPerSecond = PlotnickDefs.DRILL_GTU_MILLI * n * 20L / MachineDefs.MILLI;
        if (gtu.extract(want, true) < want) {
            report(requester, "message.gonzotech.plotnick.drill.lowgtu", n, costPerSecond);
        } else {
            report(requester, "message.gonzotech.plotnick.drill.started", n, costPerSecond);
        }
        return true;
    }

    /** ПКМ «Начать бурение»: остановить движок; сети и склад не трогаем. */
    private boolean stopDrilling(ServerPlayer requester) {
        if (!drilling) {
            report(requester, "message.gonzotech.plotnick.drill.idle");
            return true;
        }
        drilling = false;
        setChanged();
        report(requester, "message.gonzotech.plotnick.drill.stopped");
        return true;
    }

    /** Y первой клетки под колонной уже уложенных труб. */
    private int deepestTargetY(ServerLevel server, BlockPos drillCell) {
        int y = drillCell.getY() - 1;
        while (y > server.getMinY()
            && server.getBlockState(new BlockPos(drillCell.getX(), y, drillCell.getZ()))
                .is(ModMachines.BORE_PIPE.get())) {
            y--;
        }
        return y;
    }

    /** бедрок/обсидиан/укрепленный дипслейт/барьер и все «нелегальные» блоки. */
    private static boolean isImmovable(BlockState state, ServerLevel server, BlockPos pos) {
        if (state.isAir()) return false;
        // 0.3.263 (#19): блок с BlockEntity — не порода. Буру он не «добыча»:
        // asItem() потерял бы содержимое, а destroyBlock вывалил бы его на
        // землю. Такой шарх выводит скважину в «упёрлась» честно.
        if (state.hasBlockEntity()) return true;
        float hardness = state.getDestroySpeed(server, pos);
        return hardness < 0F || hardness >= 50F;
    }

    private void digStep(ServerLevel server, BlockPos drillCell) {
        int targetY = drillTargetY.getOrDefault(drillCell, drillCell.getY() - 1);
        if (targetY < server.getMinY()) return;
        BlockPos target = new BlockPos(drillCell.getX(), targetY, drillCell.getZ());
        BlockState ts = server.getBlockState(target);
        if (isImmovable(ts, server, target)) {
            drillTargetY.remove(drillCell); // дальнейшее углубление запрещено
            return;
        }
        Item mined = ts.isAir() ? null : ts.getBlock().asItem();
        if (mined != null && !hasRoom(new ItemStack(mined))) return; // склад не примет — бур ждёт, порода не теряется (#5)
        if (!mayTouchPlace(server, target)) return; // приват против: трубу не кладём, труба не тратится
        if (consumeStored(ModMachines.BORE_PIPE.get().asItem(), 1) == 0) return;
        server.setBlock(target, ModMachines.BORE_PIPE.get().defaultBlockState(),
            net.minecraft.world.level.block.Block.UPDATE_ALL);
        if (mined != null) {
            // Порода/грунт — в зону «добытое»: наружу оттуда только берут.
            insertMinedOrDrop(new ItemStack(mined), target);
        }
        drillTargetY.put(drillCell, targetY - 1);
    }

    /** Сбор руд на текущем Y скважины: квадрат 5×5 с трубой в центре. */
    private void collectOreRow(ServerLevel server, BlockPos drillCell) {
        int y = drillTargetY.getOrDefault(drillCell, drillCell.getY()) + 1;
        if (y < server.getMinY() || y > server.getMaxY()) return;
        // Всос работает только на уровне уложенной трубы: без трубы — пусто.
        if (!server.getBlockState(new BlockPos(drillCell.getX(), y, drillCell.getZ()))
            .is(ModMachines.BORE_PIPE.get())) {
            return;
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos pos = new BlockPos(drillCell.getX() + dx, y, drillCell.getZ() + dz);
                BlockState state = server.getBlockState(pos);
                if (state.isAir() || !MinerOres.isOre(state)) continue;
                Item item = state.getBlock().asItem();
                if (item == null) continue;
                if (!hasRoom(new ItemStack(item))) continue; // нет места — руда ждёт в породе (#5)
                if (!mayTouchBreak(server, pos)) continue; // приват против слома — руда ждёт
                server.destroyBlock(pos, false, null);
                insertMinedOrDrop(new ItemStack(item), pos);
            }
        }
    }

    // ─────────────────────────── сворачивание ───────────────────────────

    /**
     * «Свернуться» (работает всегда): разметка и точки бурения — долой, а
     * вся построенная сейсмобашня (рабочие блоки, трубы, буры, геофоны,
     * источники с их динамитом) разбирается в обратном порядке — от дальних
     * клеток к Плотнику, максимум 3 блока в тик, с партиклами слома. Всё
     * возвращается в хранилище: в мире остаётся один Плотник.
     */
    private boolean packUp(ServerLevel server, ServerPlayer requester) {
        boolean changed = clearSectorMarks(server);
        if (!buildQueue.isEmpty()) {
            buildQueue.clear();
            placeCooldown = 0;
            buildRequester = null;
            buildStalled = false;
            changed = true;
        }
        if (!drillPoints.isEmpty() || !drillTargetY.isEmpty()) {
            drillPoints.clear();
            drillTargetY.clear();
            cycleDrillCells = List.of();
            changed = true;
        }
        List<BlockPos> lootable = collectNetworkBlocks(server);
        if (lootable.isEmpty()) {
            if (changed) setChanged();
            report(requester, "message.gonzotech.plotnick.pack.nothing");
            return true;
        }
        networkStatsDirty = true;
        drilling = false;
        dismantleQueue.clear();
        dismantleQueue.addAll(lootable);
        dismantleReturned = 0;
        packRequester = requester;
        setChanged();
        report(requester, "message.gonzotech.plotnick.pack.dismantling", dismantleQueue.size());
        return true;
    }

    /** Все блоки сейсмобашни в 9 чанках, отсортированные «дальние — первые». */
    private List<BlockPos> collectNetworkBlocks(ServerLevel server) {
        int cx = worldPosition.getX() >> 4;
        int cz = worldPosition.getZ() >> 4;
        List<BlockPos> found = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                LevelChunk chunk = server.getChunk(cx + dx, cz + dz);
                int baseX = (cx + dx) << 4;
                int baseZ = (cz + dz) << 4;
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        for (int y = server.getMinY(); y <= server.getMaxY(); y++) {
                            BlockPos pos = new BlockPos(baseX + lx, y, baseZ + lz);
                            if (isPlotnickNetworkBlock(server.getBlockState(pos).getBlock())) {
                                found.add(pos.immutable());
                            }
                        }
                    }
                }
            }
        }
        found.sort(java.util.Comparator
            .comparingInt((BlockPos p) -> p.distManhattan(worldPosition)).reversed());
        return found;
    }

    private boolean isPlotnickNetworkBlock(net.minecraft.world.level.block.Block block) {
        return block == ModMachines.WORK_BLOCK.get()
            || block == ModMachines.BORE_PIPE.get()
            || block == ModMachines.MINER_DRILL.get()
            || block == ModMachines.GEOPHONE.get()
            || block == ModMachines.SEISMIC_SOURCE.get();
    }

    private void processDismantleQueue(ServerLevel server) {
        if (dismantleQueue.isEmpty()) return;
        int budget = PlotnickDefs.PACK_BLOCKS_PER_TICK;
        while (!dismantleQueue.isEmpty() && budget-- > 0) {
            BlockPos pos = dismantleQueue.remove(0);
            BlockState state = server.getBlockState(pos);
            if (!isPlotnickNetworkBlock(state.getBlock())) continue;
            if (!mayTouchBreak(server, pos)) {
                dismantleQueue.add(pos); // ломать нельзя — клетка ждёт в хвосте
                continue;
            }
            // Динамит из разряжаемого источника — тоже домой, в Плотник.
            // Берем ТОЛЬКО если приняли (insert вернул true) и до destroyBlock
            // чистим слот: иначе MachineBlock.onRemove источника вывалит TNT
            // ещё раз (замечание автора #3).
            if (server.getBlockEntity(pos) instanceof SeismicSourceBlockEntity source
                && source.hasDynamite()) {
                ItemStack tnt = new ItemStack(Items.TNT);
                if (insert(tnt)) {
                    source.setItem(SeismicSourceBlockEntity.SLOT_DYNAMITE, ItemStack.EMPTY);
                }
            }
            server.levelEvent(2001, pos, net.minecraft.world.level.block.Block.getId(state));
            server.destroyBlock(pos, false, null);
            Item item = state.getBlock().asItem();
            if (item != null) {
                insertOrDrop(new ItemStack(item), pos); // полный склад — на землю, не в void (#5)
                dismantleReturned++;
            }
        }
        if (dismantleQueue.isEmpty() && packRequester != null) {
            report(packRequester, "message.gonzotech.plotnick.pack.done", dismantleReturned);
            packRequester = null;
            dismantleReturned = 0;
        }
        setChanged();
    }

    // ─────────────────────────── закладка ТНТ ───────────────────────────

    /**
     * «Заменить на ТНТ»: трубы заменяются взрывчаткой сверху вниз (от бура),
     * столько, сколько хватит динамита — сначала промышленный ТНТ из
     * слотов, затем ванильный. Скорость — 3 блока в тик, на месте укладки
     * — block_crack-партиклы. Заменённые трубы в Плотник не возвращаются.
     */
    private boolean rigTnt(ServerLevel server, ServerPlayer requester) {
        if (!tntQueue.isEmpty() || !dismantleQueue.isEmpty()) {
            report(requester, "message.gonzotech.plotnick.network.busy",
                Math.max(tntQueue.size(), dismantleQueue.size()));
            return true;
        }
        List<BlockPos> pipes = collectPipesTopDown(server);
        if (pipes.isEmpty()) {
            report(requester, "message.gonzotech.plotnick.tnt.nopipes");
            return true;
        }
        int stock = countStored(ModItems.INDUSTRIAL_TNT_ITEM.get()) + countStored(Items.TNT);
        if (stock == 0) {
            report(requester, "message.gonzotech.plotnick.tnt.noexplosive");
            return true;
        }
        tntQueue.addAll(pipes.subList(0, Math.min(pipes.size(), stock)));
        tntRequester = requester;
        setChanged();
        report(requester, "message.gonzotech.plotnick.tnt.planned", tntQueue.size(), stock);
        return true;
    }

    /** Трубы сектора сверху вниз: Y по убыванию, внутри уровня — от ближних. */
    private List<BlockPos> collectPipesTopDown(ServerLevel server) {
        // 0.3.263, #8 (автор): вместо полного прохода 9 чанков (884 тыс.
        // чтений) — только колонны 1×1 известных буров, от Y бура до низа
        // мира. Трубы живут исключительно под бурами.
        net.minecraft.world.level.block.Block pipe = ModMachines.BORE_PIPE.get();
        java.util.Set<BlockPos> cells = new java.util.LinkedHashSet<>(cycleDrillCells);
        cells.addAll(collectSectorScan(server).drills());
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos cell : cells) {
            for (int y = cell.getY() - 1; y >= server.getMinY(); y--) {
                BlockPos pos = new BlockPos(cell.getX(), y, cell.getZ());
                if (server.getBlockState(pos).is(pipe)) found.add(pos.immutable());
            }
        }
        found.sort((a, b) -> a.getY() != b.getY()
            ? b.getY() - a.getY()
            : a.distManhattan(worldPosition) - b.distManhattan(worldPosition));
        return found;
    }

    private void processTntQueue(ServerLevel server) {
        if (tntQueue.isEmpty()) return;
        int budget = PlotnickDefs.PACK_BLOCKS_PER_TICK;
        while (!tntQueue.isEmpty() && budget-- > 0) {
            BlockPos pos = tntQueue.remove(0);
            if (!server.getBlockState(pos).is(ModMachines.BORE_PIPE.get())) continue;
            Item explosive = pickExplosive();
            if (explosive == null) {
                tntQueue.add(0, pos);
                break; // взрывчатка кончилась — очередь дожидается пополнения
            }
            BlockState tnt = explosive == ModItems.INDUSTRIAL_TNT_ITEM.get()
                ? ModBlocks.INDUSTRIAL_TNT.get().defaultBlockState()
                : net.minecraft.world.level.block.Blocks.TNT.defaultBlockState();
            if (!mayTouchPlace(server, pos)) {
                tntQueue.add(0, pos); // приват против взрывчатки в трубе — пауза очереди
                break;
            }
            consumeStored(explosive, 1);
            server.setBlock(pos, tnt, net.minecraft.world.level.block.Block.UPDATE_ALL);
            BlockParticleOption crumble = new BlockParticleOption(ParticleTypes.BLOCK, tnt);
            server.sendParticles(crumble, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                12, 0.24, 0.24, 0.24, 0.04D);
            server.playSound(null, pos, SoundType.METAL.getPlaceSound(), SoundSource.BLOCKS,
                0.7F, 0.85F + 0.2F * server.random.nextFloat());
        }
        if (tntQueue.isEmpty()) {
            if (tntRequester != null) {
                report(tntRequester, "message.gonzotech.plotnick.tnt.done");
                tntRequester = null;
            }
            return;
        }
        setChanged();
    }

    /** Сначала промышленный ТНТ из слотов, затем ванильный. */
    private Item pickExplosive() {
        if (countStored(ModItems.INDUSTRIAL_TNT_ITEM.get()) > 0) {
            return ModItems.INDUSTRIAL_TNT_ITEM.get();
        }
        if (countStored(Items.TNT) > 0) return Items.TNT;
        return null;
    }

    private void report(ServerPlayer requester, String key, Object... args) {
        if (requester != null) requester.sendSystemMessage(Component.translatable(key, args));
    }

    // ─────────────────────────── хранилище ───────────────────────────

    /**
     * Вставка материала (воронки/трубы/Shift-клик/демонтаж): головы →
     * депо → «добытое» (перелив). Тип ячейки = предмет+компоненты; новых
     * типов больше 54 не появляется — отказ, стопка остаётся у источника.
     */
    public boolean insert(ItemStack incoming) {
        return insertImpl(incoming, false);
    }

    /** Буровой поток: сначала «добытое» (2 стопки типа), перелив — в депо. */
    public boolean insertMined(ItemStack incoming) {
        return insertImpl(incoming, true);
    }

    private boolean insertImpl(ItemStack incoming, boolean mined) {
        if (incoming.isEmpty()) return false;
        int cap = capacityFor(incoming);
        // 1. Головы активных слотов — классика до 64.
        for (int slot = 0; slot < SLOTS_ACTIVE && !incoming.isEmpty(); slot++) {
            ItemStack head = items.get(slot);
            if (head.isEmpty()) {
                items.set(slot, incoming.split(Math.min(cap, incoming.getCount())));
            } else if (ItemStack.isSameItemSameComponents(head, incoming)) {
                int room = cap - head.getCount();
                if (room > 0) head.grow(incoming.split(Math.min(room, incoming.getCount())).getCount());
            }
        }
        if (incoming.isEmpty()) {
            buildStalled = false; // пополнение склада будит постройку
            networkStatsDirty = true;
            setChanged();
            return true;
        }
        // 2. Ячейка типа: недолитая или первая полностью пустая (новый id).
        int cell = cellFor(incoming);
        if (cell < 0) cell = freeCell();
        if (cell < 0) return false; // 54 типа заполнены / 4×64 малы — отказ
        // Приоритет зон: материал — депо, добыча — «добытое»; перелив — в другую.
        int[] order = mined
            ? new int[] {0, 1, OUT_STACKS_PER_TYPE, OUT_STACKS_PER_TYPE + 1}
            : new int[] {OUT_STACKS_PER_TYPE, OUT_STACKS_PER_TYPE + 1, 0, 1};
        for (int oi = 0; oi < STACKS_PER_TYPE && !incoming.isEmpty(); oi++) {
            int idx = cell * STACKS_PER_TYPE + order[oi];
            ItemStack cur = virtual.get(idx);
            if (cur.isEmpty()) {
                virtual.set(idx, incoming.split(Math.min(cap, incoming.getCount())));
            } else if (ItemStack.isSameItemSameComponents(cur, incoming)) {
                int room = cap - cur.getCount();
                if (room > 0) cur.grow(incoming.split(Math.min(room, incoming.getCount())).getCount());
            }
        }
        if (incoming.getCount() == 0) {
            buildStalled = false;
        }
        networkStatsDirty = true;
        setChanged();
        return incoming.isEmpty();
    }

    /** Индекс ячейки типа (любая стопка ячейки совпадает со стопкой-пробой). */
    private int cellFor(ItemStack probe) {
        for (int t = 0; t < VIRTUAL_TYPES; t++) {
            for (int j = 0; j < STACKS_PER_TYPE; j++) {
                if (ItemStack.isSameItemSameComponents(virtual.get(t * STACKS_PER_TYPE + j), probe)) {
                    return t;
                }
            }
        }
        return -1;
    }

    /** Первая полностью пустая ячейка — под новый из 54 типов. */
    private int freeCell() {
        for (int t = 0; t < VIRTUAL_TYPES; t++) {
            boolean free = true;
            for (int j = 0; j < STACKS_PER_TYPE; j++) {
                if (!virtual.get(t * STACKS_PER_TYPE + j).isEmpty()) {
                    free = false;
                    break;
                }
            }
            if (free) return t;
        }
        return -1;
    }

    /**
     * Запись в виртуальный Container-слот — контракт setItem: ЗАМЕНА
     * (0.3.298 N5, постановка автора). До этого здесь был «долив» —
     * ванильные воронки, пишущие getItem+setItem(слиток), получали
     * перемещение в голову вместо атома замены. Вставка по правилам
     * склада — отдельный метод insert() (трубы/автоматика идут через
     * него); merge-копию перед записью делает сам контейнер-донор
     * (ItemRouting.tryPlace), поэтому замена здесь ничего не ломает.
     * Прежний тип из вытесненной ячейки уходит общим роутингом, затем
     * на землю — ни одного предмета в void. Совпадение типа — молчаливая
     * замена (так же ведёт себя SimpleContainer; caller владеет вытесненным).
     */
    private void setVirtual(int idx, ItemStack stack) {
        if (idx < 0 || idx >= VIRTUAL_SLOTS) return;
        ItemStack cur = virtual.get(idx);
        if (stack.isEmpty()) {
            if (!cur.isEmpty()) {
                virtual.set(idx, ItemStack.EMPTY);
                networkStatsDirty = true;
                setChanged();
            }
            return;
        }
        int cap = capacityFor(stack);
        if (stack.getCount() > cap) {
            // Неизвестный потолку caller: ячейка принимает норму, излишек
            // дописываем в общий роутинг сразу (стопка caller'а — как в
            // heads-ветке setItem: split, а не тихая обрезка).
            ItemStack over = stack.split(stack.getCount() - cap);
            insert(over);
            if (!over.isEmpty()) dump(over, worldPosition);
        }
        boolean foreignType =
            !cur.isEmpty() && !ItemStack.isSameItemSameComponents(cur, stack);
        virtual.set(idx, stack);
        if (foreignType) {
            insert(cur); // вытесненный — на склад, не в void
            if (!cur.isEmpty()) dump(cur, worldPosition);
        }
        networkStatsDirty = true;
        setChanged();
    }

    /** Потолок стопки: ваниальный 64, но не больше лимита самого предмета (#17).
     *  0.3.298 (N6): единая мерка для меню-мерджа и склада — PlotnickMenu
     *  берёт её, а не Slot.getMaxStackSize(): пустой слот ничего о потолке
     *  предмета не знает и наливал ванильные 64 в лимитированные стопки. */
    public static int capacityFor(ItemStack stack) {
        int cap = stack.getMaxStackSize();
        return cap > 0 ? Math.min(MAX_STACK, cap) : MAX_STACK;
    }


    /**
     * Вставка с наземным запасным аэродромом: склад может не принять конкретный
     * предмет (нет свободной ячейки/долитого типа) — тогда стопка падает у
     * клетки, а не исчезает (замечание автора #5).
     */
    /**
     * Есть ли куда положить эту стопку: пустая ячейка или недолив того же
     * типа (0.3.263, #5): мир меняется только когда результат гарантированно
     * куда-то денется.
     */
    public boolean hasRoom(ItemStack stack) {
        if (stack.isEmpty()) return true;
        int cap = capacityFor(stack);
        for (int slot = 0; slot < SLOTS_ACTIVE; slot++) {
            ItemStack head = items.get(slot);
            if (head.isEmpty()) return true;
            if (ItemStack.isSameItemSameComponents(head, stack) && head.getCount() < cap) return true;
        }
        int cell = cellFor(stack);
        if (cell < 0) return freeCell() >= 0; // новый тип — нужна свободная ячейка
        for (int j = 0; j < STACKS_PER_TYPE; j++) {
            ItemStack st = virtual.get(cell * STACKS_PER_TYPE + j);
            if (st.isEmpty()
                || (ItemStack.isSameItemSameComponents(st, stack) && st.getCount() < cap)) return true;
        }
        return false;
    }

    private void insertOrDrop(ItemStack stack, BlockPos near) {
        if (stack.isEmpty() || insert(stack)) return;
        dump(stack, near);
    }

    /** Буровой поток: в «добытое» (наружу только вынимать), отказ — на землю. */
    private void insertMinedOrDrop(ItemStack stack, BlockPos near) {
        if (stack.isEmpty() || insertMined(stack)) return;
        dump(stack, near);
    }

    private void dump(ItemStack stack, BlockPos near) {
        if (level != null) level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
            level, near.getX() + 0.5, near.getY() + 0.5, near.getZ() + 0.5, stack));
    }

    // ─────────────────────── владение сектором и приват ───────────────────────

    /** Анабиоз — состояние, которое блок проверяет перед открытием GUI. */
    public boolean isHibernating() {
        return hibernating;
    }

    /**
     * Анабиоз по пересечению покрытия (автор, 0.3.266): вотчина Плотника —
     * сектор 3×3 чанка вокруг него, и сон накладывается, когда чужой сектор
     * пересекается с нашим хоть на один чанк (чанки на расстоянии ≤2 по
     * Чебышёву). Лидер — старший по placedAt (tie — позиция) из бодрствующих;
     * сонный сосед вотчину не держит, иначе цепочка A→B→C укладывала бы спать
     * и C, чья зона от A свободна. Переворот флага будит окрестность.
     */
    private void reelectSector(ServerLevel server) {
        for (PlotnickBlockEntity peer : sectorPeers(server)) {
            if (peer == this || peer.hibernating) continue;
            boolean senior = peer.placedAt < placedAt
                || (peer.placedAt == placedAt
                    && peer.worldPosition.asLong() < worldPosition.asLong());
            if (senior) {
                if (!hibernating) {
                    hibernating = true;
                    setChanged();
                    notifySectorChanged(server, worldPosition);
                }
                return;
            }
        }
        if (hibernating) {
            hibernating = false;
            setChanged();
            notifySectorChanged(server, worldPosition);
        }
    }

    /** Живые Плотники с пересекающимся покрытием: чанки ±2 (грузить не будем). */
    private List<PlotnickBlockEntity> sectorPeers(ServerLevel server) {
        int cx = worldPosition.getX() >> 4;
        int cz = worldPosition.getZ() >> 4;
        List<PlotnickBlockEntity> out = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                LevelChunk chunk = server.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (chunk == null) continue;
                for (BlockEntity peer : chunk.getBlockEntities().values()) {
                    if (peer instanceof PlotnickBlockEntity plotnick) out.add(plotnick);
                }
            }
        }
        return out;
    }

    /**
     * Слом/установка Плотника (и чужой переход в сон) будят окрестность:
     * перевыборы нужны всем, чьё покрытие пересекается с изменившейся клеткой —
     * это чанки ±2.
     */
    public static void notifySectorChanged(ServerLevel server, BlockPos near) {
        int cx = near.getX() >> 4;
        int cz = near.getZ() >> 4;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                LevelChunk chunk = server.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (chunk == null) continue;
                for (BlockEntity peer : chunk.getBlockEntities().values()) {
                    if (peer instanceof PlotnickBlockEntity plotnick) {
                        plotnick.sectorElectionDue = true;
                    }
                }
            }
        }
    }

    /**
     * «Путь блока» (автор, 0.3.264): разрешение клетки меряется серверными
     * стандартными событиями — ставим BlockEvent.EntityPlaceEvent /
     * BlockEvent.BreakEvent от лица ближайшего онлайн-игрока, приваты и
     * защитные плагины в праве отказать. Игрока рядом нет — проверять не от
     * кого, разрешаем (чанки всё равно держат игроки, а machine self-move
     * приватами не считается). Результат кэшируется на тик: поток клеток
     * стоит, а не спамит события.
     */
    private boolean mayTouchPlace(ServerLevel server, BlockPos pos) {
        long tick = server.getGameTime();
        if (touchCached(pos, tick, true)) return lastTouchAllowed;
        lastTouchTick = tick;
        lastTouchPos = pos.immutable();
        lastTouchPlacing = true;
        ServerPlayer actor = actionActor(server);
        if (actor == null) {
            lastTouchAllowed = true; // спросить некого — не считаем это отказом
            return true;
        }
        // Снимок 1.21.4 фиксирует ТЕКУЩЕЕ состояние pos (конструктор приватен,
        // берём фабрику) — приваты сравнивают именно его с placedAgainst.
        BlockSnapshot snapshot = BlockSnapshot.create(server.dimension(), server, pos);
        BlockEvent.EntityPlaceEvent event = new BlockEvent.EntityPlaceEvent(
            snapshot, server.getBlockState(pos), actor);
        NeoForge.EVENT_BUS.post(event);
        lastTouchAllowed = !event.isCanceled();
        return lastTouchAllowed;
    }

    private boolean mayTouchBreak(ServerLevel server, BlockPos pos) {
        long tick = server.getGameTime();
        if (touchCached(pos, tick, false)) return lastTouchAllowed;
        lastTouchTick = tick;
        lastTouchPos = pos.immutable();
        lastTouchPlacing = false;
        ServerPlayer actor = actionActor(server);
        if (actor == null) {
            lastTouchAllowed = true; // спросить некого — не считаем это отказом
            return true;
        }
        BlockEvent.BreakEvent event =
            new BlockEvent.BreakEvent(server, pos, server.getBlockState(pos), actor);
        NeoForge.EVENT_BUS.post(event);
        lastTouchAllowed = !event.isCanceled();
        return lastTouchAllowed;
    }

    /**
     * Кэш «одна постановка событий на клетку за тик» (анти-спам приватов).
     * Ключ — (тик, клетка, тип проверки): отказ на break не должен просачиваться
     * в place той же клетки в том же тике (dig переставляет блок на сломанном).
     */
    private boolean touchCached(BlockPos pos, long tick, boolean placing) {
        return tick == lastTouchTick && lastTouchPlacing == placing
            && pos.equals(lastTouchPos);
    }

    /** Кого спросить: ближайший онлайн-игрок не дальше сектора соседних чанков. */
    private ServerPlayer actionActor(ServerLevel server) {
        ServerPlayer best = null;
        double bestDist = Double.MAX_VALUE;
        for (ServerPlayer player : server.players()) {
            if (player.level() != server || player.isSpectator()) continue;
            double d = player.distanceToSqr(
                worldPosition.getX() + 0.5, worldPosition.getY(), worldPosition.getZ() + 0.5);
            if (d < bestDist) {
                bestDist = d;
                best = player;
            }
        }
        return bestDist <= 192D * 192D ? best : null;
    }

    /** Остатки v2-сейва, не влезшие в 216 стаков: наружу, когда мир готов. */
    private void dropLegacyOverflow() {
        if (legacyOverflow.isEmpty()) return;
        Level level = getLevel();
        for (ItemStack stack : legacyOverflow) {
            if (stack.isEmpty()) continue;
            net.minecraft.world.Containers.dropItemStack(level,
                worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
                stack.copy());
        }
        legacyOverflow.clear();
    }

    public NonNullList<ItemStack> virtualStacks() {
        return virtual;
    }

    /** Сколько ячеек типа занято (0..54) — тултип «занято стаков». */
    public int occupiedCells() {
        int used = 0;
        for (int t = 0; t < VIRTUAL_TYPES; t++) {
            for (int j = 0; j < STACKS_PER_TYPE; j++) {
                if (!virtual.get(t * STACKS_PER_TYPE + j).isEmpty()) {
                    used++;
                    break;
                }
            }
        }
        return used;
    }

    /** Сколько предметов вещи в Плотнике: головы (карты не в счёт) + весь виртуаль. */
    public int countStored(Item item) {
        int total = 0;
        for (int slot = 0; slot < SLOTS_ACTIVE; slot++) {
            if (items.get(slot).is(item)) total += items.get(slot).getCount();
        }
        for (ItemStack stack : virtual) {
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    /**
     * Расход «на нужды машины»: активные головы, затем депо-стопки виртуала
     * (депо и создано, чтобы кормить слоты). «Добытое» этой дорогой не тратится.
     */
    public int consumeActive(Item item, int count) {
        int left = Math.max(0, count);
        for (int slot = 0; slot < SLOTS_ACTIVE && left > 0; slot++) {
            left -= takeFrom(items, slot, item, left);
        }
        for (int t = 0; t < VIRTUAL_TYPES && left > 0; t++) {
            for (int j = OUT_STACKS_PER_TYPE; j < STACKS_PER_TYPE && left > 0; j++) {
                left -= takeFrom(virtual, t * STACKS_PER_TYPE + j, item, left);
            }
        }
        if (left != count) {
            networkStatsDirty = true;
            setChanged();
        }
        return count - left;
    }

    /** @return сколько сняли из одной ячейки списка */
    private static int takeFrom(NonNullList<ItemStack> list, int index, Item item, int want) {
        ItemStack stack = list.get(index);
        if (stack.isEmpty() || !stack.is(item)) return 0;
        int take = Math.min(want, stack.getCount());
        stack.shrink(take);
        if (stack.isEmpty()) list.set(index, ItemStack.EMPTY);
        return take;
    }

    /** @return сколько реально сняли: головы → депо → «добытое» (порядок автора). */
    public int consumeStored(Item item, int count) {
        int left = Math.max(0, count);
        for (int slot = 0; slot < SLOTS_ACTIVE && left > 0; slot++) {
            left -= takeFrom(items, slot, item, left);
        }
        for (int pass = 0; pass < 2 && left > 0; pass++) {
            for (int t = 0; t < VIRTUAL_TYPES && left > 0; t++) {
                if (pass == 0) {
                    for (int j = OUT_STACKS_PER_TYPE; j < STACKS_PER_TYPE && left > 0; j++) {
                        left -= takeFrom(virtual, t * STACKS_PER_TYPE + j, item, left);
                    }
                } else {
                    for (int j = 0; j < OUT_STACKS_PER_TYPE && left > 0; j++) {
                        left -= takeFrom(virtual, t * STACKS_PER_TYPE + j, item, left);
                    }
                }
            }
        }
        if (left != count) {
            networkStatsDirty = true;
            setChanged();
        }
        return count - left;
    }

    // ─────────────────────────── Container ───────────────────────────

    /**
     * Container-размер ВМЕСТЕ с виртуалем (0.3.264): воронки и трубы мода
     * ходят по индексам контейнера, правила «что кому разрешено на лице» —
     * ниже. GUI трогает только 0..10 (BaseMachineMenu фиксирует CONTAINER_SIZE).
     */
    @Override
    public int getContainerSize() {
        return CONTAINER_TOTAL;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot < 0 || slot >= CONTAINER_TOTAL) return ItemStack.EMPTY;
        return slot < CONTAINER_SIZE ? items.get(slot) : virtual.get(slot - CONTAINER_SIZE);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot >= CONTAINER_SIZE) {
            int vi = slot - CONTAINER_SIZE;
            if (vi < 0 || vi >= VIRTUAL_SLOTS) return ItemStack.EMPTY;
            ItemStack out = virtual.get(vi);
            if (out.isEmpty()) return ItemStack.EMPTY;
            ItemStack taken = out.split(Math.min(Math.max(0, amount), out.getCount()));
            networkStatsDirty = true;
            setChanged();
            return taken;
        }
        // ВАЖНО (замечание автора #4): никакие пополнения головы здесь и в
        // setItem недопустимы — воронка извлекает «пробно» и при отказе
        // откатывает setItem(исходный стек), затёр бы поднятый из хранилища
        // кусок. Головы — классика, подстопок больше нет.
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot >= CONTAINER_TOTAL) return ItemStack.EMPTY;
        if (slot >= CONTAINER_SIZE) {
            int vi = slot - CONTAINER_SIZE;
            ItemStack out = virtual.get(vi);
            virtual.set(vi, ItemStack.EMPTY);
            networkStatsDirty = true;
            setChanged();
            return out;
        }
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= CONTAINER_TOTAL) return;
        if (slot >= CONTAINER_SIZE) {
            setVirtual(slot - CONTAINER_SIZE, stack); // трубы/воронки → хранилище
            return;
        }
        if (stack.getCount() > capacityFor(stack)) {
            // Сторонний код (клик-мердж) не должен рождать стопку сверх лимита.
            ItemStack overflow = stack.split(stack.getCount() - capacityFor(stack));
            insertOrDrop(overflow, worldPosition);
        }
        // Слот карт не принимает всё подряд (Shift-клик и внешние записи).
        if (slot >= SLOTS_ACTIVE && !stack.isEmpty() && !isAllowedCard(slot, stack)) {
            stack = ItemStack.EMPTY;
        }
        items.set(slot, stack);
        if (slot == SLOT_CARD_A || slot == SLOT_CARD_B) {
            networkStatsDirty = true; // режим кнопок зависит от карты (#18-часть)
        }
        setChanged();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) return false;
        }
        for (ItemStack stack : virtual) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    /**
     * Карты памяти живут только в слотах 9/10 (0.3.263 держал это правило,
     * v3-переписывание потеряло определение). «Не карта» в слот карт и
     * «карта» в витринный слот — отказ; виртуальные слоты решают свои
     * правила выше по тексту.
     */
    private static boolean isAllowedCard(int slot, ItemStack stack) {
        boolean cardSlot = slot == SLOT_CARD_A || slot == SLOT_CARD_B;
        boolean card = stack.getItem() instanceof MemoryCardItem;
        return cardSlot == card;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= CONTAINER_TOTAL) return false;
        if (stack.getCount() > MAX_STACK) return false; // классика, 512 отменены
        if (slot >= CONTAINER_SIZE) {
            // Внешняя вставка — только в депо (стопки 2..3) и только в свою
            // либо в совершенно пустую ячейку: чужой тип в занятую ячейку
            // не пускаем — иначе «один тип = одна ячейка» разваливается.
            int idx = slot - CONTAINER_SIZE;
            if (idx % STACKS_PER_TYPE < OUT_STACKS_PER_TYPE) return false;
            ItemStack cur = virtual.get(idx);
            if (!cur.isEmpty()) return ItemStack.isSameItemSameComponents(cur, stack);
            int cell = idx / STACKS_PER_TYPE;
            for (int j = 0; j < STACKS_PER_TYPE; j++) {
                ItemStack own = virtual.get(cell * STACKS_PER_TYPE + j);
                if (!own.isEmpty()) return ItemStack.isSameItemSameComponents(own, stack);
            }
            return true; // пустая ячейка принимает новый тип (до 54 — за счёт freeCell)
        }
        if (slot >= SLOTS_ACTIVE) return isAllowedCard(slot, stack);
        ItemStack head = items.get(slot);
        return head.isEmpty()
            || (ItemStack.isSameItemSameComponents(head, stack) && head.getCount() < capacityFor(stack));
    }

    @Override
    public void clearContent() {
        // NonNullList.withSize фиксирован: set-петли, не clear()/add().
        for (int i = 0; i < items.size(); i++) items.set(i, ItemStack.EMPTY);
        for (int i = 0; i < virtual.size(); i++) virtual.set(i, ItemStack.EMPTY);
    }

    @Override
    public int getMaxStackSize() {
        return MAX_STACK;
    }

    // Карты памяти — только руками. Воронки/трубы видят головы и весь виртуаль,
    // но вставляют строго в депо, а «добытое» умеют лишь вынимать (автор 0.3.264).
    @Override
    public int[] getSlotsForFace(Direction side) {
        // (Автор, 0.3.266) Ваниальная воронка под блоком смотрит ровно в DOWN
        // грань и canTakeItemThroughFace не спрашивает — поэтому снизу отдаём
        // только невидимое хранилище: хотбар и карты снаружи неприкосновенны.
        // Остальные грани — витрина для кладки («куда угодно, кроме слотов
        // карт»): стражи сидят в canPlaceItem (карты — только карты, зона
        // «добытое» — только отдавать, чужая ячейка — не кладётся).
        if (side == Direction.DOWN) {
            int[] out = new int[VIRTUAL_SLOTS];
            for (int v = 0; v < VIRTUAL_SLOTS; v++) out[v] = CONTAINER_SIZE + v;
            return out;
        }
        int[] all = new int[CONTAINER_TOTAL];
        for (int i = 0; i < CONTAINER_TOTAL; i++) all[i] = i;
        return all;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        // Вытягивать наружу — только невидимое хранилище (0.3.266): головы и
        // карты трубы тоже не трогают, хоть и видят их в списке граней.
        if (slot < CONTAINER_SIZE) return false;
        int vi = slot - CONTAINER_SIZE;
        return vi >= 0 && vi < VIRTUAL_SLOTS && !virtual.get(vi).isEmpty();
    }

    // ─────────────────────────── persistence / menu ───────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        gtu.save(tag, "Gtu");
        // Хранилище v3 (0.3.264): все стопки ваниальные ≤64, сайдкара
        // сверх-стеков больше нет. Оба списка — собственный формат: int «I»
        // = ЛОКАЛЬНЫЙ индекс (ContainerHelper писал байт — для 216-слотового
        // виртуала это снова переполнение, не смотрим на него).
        saveStackList(items, "Items", tag, registries);
        saveStackList(virtual, "Virtual", tag, registries);
        tag.putLong("PlacedAt", placedAt);
        if (hibernating) tag.putBoolean("Hibernating", true);
        if (!sectorMarkerIds.isEmpty()) {
            tag.putLongArray("SectorMarkers", sectorMarkerIds.stream()
                .mapToLong(Long::longValue).toArray());
        }
        if (!drillPoints.isEmpty()) {
            tag.putLongArray("DrillPoints", drillPoints.stream()
                .mapToLong(BlockPos::asLong).toArray());
        }
        if (!buildQueue.isEmpty()) {
            ListTag queue = new ListTag();
            for (NetworkPlanner.Entry entry : buildQueue) {
                CompoundTag cell = new CompoundTag();
                cell.putLong("Pos", entry.pos().asLong());
                cell.putBoolean("Drill", entry.drill());
                queue.add(cell);
            }
            tag.put("BuildQueue", queue);
        }
        if (placeCooldown > 0) tag.putInt("PlaceCooldown", placeCooldown);
        if (drilling) tag.putBoolean("Drilling", true);
        // Счётчики тултипа переживают перезагрузку вместе с очередью.
        tag.putInt("NetRoadsFull", netRoadsFull);
        tag.putInt("NetRoadsLink", netRoadsLink);
        tag.putInt("NetDrillsToPlace", netDrillsToPlace);
        savePosList(tag, "DismantleQueue", dismantleQueue);
        savePosList(tag, "TntQueue", tntQueue);
    }

    /** Свой формат длинного списка: int-локальный индекс + «Item»-стопка. */
    private static void saveStackList(NonNullList<ItemStack> source, String key,
            CompoundTag root, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (int i = 0; i < source.size(); i++) {
            ItemStack stack = source.get(i);
            if (stack.isEmpty()) continue;
            CompoundTag cell = new CompoundTag();
            cell.putInt("I", i);
            cell.put("Item", stack.save(registries));
            list.add(cell);
        }
        if (!list.isEmpty()) root.put(key, list);
    }

    /**
     * Чтение своего формата с fallback: ContainerHelper-записи несут «Slot»
     * (byte/short/int — любое число) — он ЛОКАЛЬНЫЙ, вычитать нечего.
     */
    private static void loadStackList(CompoundTag root, String key, NonNullList<ItemStack> target,
            HolderLookup.Provider registries) {
        for (int i = 0; i < target.size(); i++) target.set(i, ItemStack.EMPTY);
        ListTag list = root.getList(key, Tag.TAG_COMPOUND);
        int seq = 0;
        for (Tag entry : list) {
            if (!(entry instanceof CompoundTag cell)) continue;
            int index;
            if (cell.contains("I")) index = cell.getInt("I");
            // 0.3.290 #1: ContainerHelper (хранилище v1, до 0.3.262) писал «Slot»
            // байтом: 128..255 приезжают знаковыми, а старый 432-виртуаль ещё и
            // алиасился (256+k -> k). Байт читаем без знака — ровно как ваниль.
            else if (cell.get("Slot") instanceof net.minecraft.nbt.ByteTag legacyByte)
                index = legacyByte.getAsInt() & 0xFF;
            else if (cell.get("Slot") instanceof NumericTag numeric) index = numeric.getAsInt();
            else index = seq;
            seq++;
            ItemStack stack = cell.contains("Item")
                ? ItemStack.parseOptional(registries, cell.getCompound("Item"))
                : ItemStack.parseOptional(registries, cell);
            if (stack.isEmpty()) continue;
            // 0.3.290 #1: коллизия побайтового индекса — не перезапись, а ближайшее
            // пустое место: при свёртке v1 в новую схему порядок слотов неважен,
            // важна сохранность — «last wins» съедал чужие стопки.
            // 0.3.302 (#1-остаток): выход за границы (битый «I», мусор после
            // арифметических сдвигов) — тот же free-probe с нуля, а не «continue»:
            // раньше такие записи молча исчезали, что ровно та же потеря, что и
            // затирание. Байтовый «Slot» уже прочитан без знака (&0xFF) выше.
            boolean offGrid = index < 0 || index >= target.size();
            if (offGrid || !target.get(index).isEmpty()) {
                int free = -1;
                for (int probe = offGrid ? 0 : index + 1; probe < target.size(); probe++) {
                    if (target.get(probe).isEmpty()) { free = probe; break; }
                }
                if (free < 0 && !offGrid) {
                    for (int probe = 0; probe < index; probe++) {
                        if (target.get(probe).isEmpty()) { free = probe; break; }
                    }
                }
                if (free < 0) continue; // физически битком: перелив не затираем
                index = free;
            }
            target.set(index, stack);
        }
    }

    /** 0.3.290 #1: сигнатура v1 — записи с «Slot» (ContainerHelper), без «I»/«Virtual»/«BucketRest». */
    private static boolean isLegacySlotFormat(CompoundTag tag) {
        if (!tag.contains("Items") || tag.contains("Virtual") || tag.contains("BucketRest")) return false;
        for (Tag raw : tag.getList("Items", Tag.TAG_COMPOUND)) {
            if (raw instanceof CompoundTag cell) {
                if (cell.contains("Slot")) return true;
                if (cell.contains("I")) return false;
            }
        }
        return false;
    }

    /** Сейв 0.3.262–0.3.263: 432-слотовый виртуаль (индексы за 216). */
    private static boolean isLegacyVirtual(CompoundTag tag) {
        for (Tag raw : tag.getList("Virtual", Tag.TAG_COMPOUND)) {
            if (raw instanceof CompoundTag cell && cell.contains("I")
                && cell.getInt("I") >= VIRTUAL_SLOTS) return true;
        }
        return false;
    }

    /**
     * Свёртка хранилища v2 в v3 (решение автора 22.10): подстопки ячеек и
     * старый виртуаль раскладываются в последовательность стопок и вставляются
     * по новым правилам — головы, депо, перелив в «добытое»; лимиты 54 типов
     * и 4 стопок решают, что не влезло — падает под ноги первым тиком
     * (в void ничего не теряем).
     */
    private void migrateLegacyStorage(CompoundTag tag, HolderLookup.Provider registries) {
        NonNullList<ItemStack> legacyRest =
            NonNullList.withSize(LEGACY_REST_SLOTS, ItemStack.EMPTY);
        NonNullList<ItemStack> legacyVirtual =
            NonNullList.withSize(LEGACY_VIRTUAL_SLOTS, ItemStack.EMPTY);
        loadStackList(tag, "BucketRest", legacyRest, registries);
        loadStackList(tag, "Virtual", legacyVirtual, registries);
        foldIntoStorage(legacyRest);
        foldIntoStorage(legacyVirtual);
    }

    /** 0.3.290 #1: общая свёртка legacy-таблицы: что не влезло — в legacyOverflow (0.3.263-правило). */
    private void foldIntoStorage(NonNullList<ItemStack> source) {
        for (ItemStack stack : source) {
            if (stack.isEmpty()) continue;
            ItemStack rest = stack.copy();
            while (!rest.isEmpty()) {
                if (insert(rest)) continue;
                legacyOverflow.add(rest.split(rest.getCount()));
            }
        }
    }

    /**
     * Активные слоты — классика ≤64 (0.3.264), но легаси-сейвы несут и
     * 512-стопы: голова режется до лимита, излишек уезжает общей вставкой.
     */
    private void normalizeStacks() {
        List<ItemStack> overflow = new ArrayList<>();
        for (int slot = 0; slot < SLOTS_ACTIVE; slot++) {
            ItemStack stack = items.get(slot);
            if (stack.getCount() > capacityFor(stack)) overflow.add(stack.split(stack.getCount() - capacityFor(stack)));
        }
        for (int slot = 0; slot < VIRTUAL_SLOTS; slot++) {
            ItemStack stack = virtual.get(slot);
            if (stack.getCount() > capacityFor(stack)) overflow.add(stack.split(stack.getCount() - capacityFor(stack)));
        }
        for (ItemStack rest : overflow) {
            insert(rest);
            // 0.3.298 (N7): что при загрузке сейва не влезло — не обрезается
            // в void, а становится legacyOverflow: свалится на землю первым
            // тиком (то же правило, что #1 у foldIntoStorage).
            if (!rest.isEmpty()) legacyOverflow.add(rest);
        }
    }

    private static void savePosList(CompoundTag tag, String key, List<BlockPos> positions) {
        if (positions.isEmpty()) return;
        long[] raw = new long[positions.size()];
        for (int i = 0; i < raw.length; i++) raw[i] = positions.get(i).asLong();
        tag.putLongArray(key, raw);
    }

    private static List<BlockPos> loadPosList(CompoundTag tag, String key, List<BlockPos> into) {
        into.clear();
        for (long raw : tag.getLongArray(key)) into.add(BlockPos.of(raw));
        return into;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        gtu.load(tag, "Gtu");
        // 0.3.290 #1: файлы v1 держали всё в одном «Items» байтовыми «Slot» и на
        // нынешние 11 активных слотов не ложатся (старый лист был длиннее). Такой
        // сейв разворачиваем во временную 432-таблицу и сворачиваем правилами
        // вставки — излишек уедет в legacyOverflow (падает под ноги, не в void).
        if (isLegacySlotFormat(tag)) {
            NonNullList<ItemStack> legacyItems =
                NonNullList.withSize(LEGACY_VIRTUAL_SLOTS, ItemStack.EMPTY);
            loadStackList(tag, "Items", legacyItems, registries);
            foldIntoStorage(legacyItems);
        } else {
            loadStackList(tag, "Items", items, registries);
        }
        sectorMarkerIds.clear();
        for (long markerId : tag.getLongArray("SectorMarkers")) {
            sectorMarkerIds.add(markerId);
        }
        // Хранилище v3 (0.3.264): сейвы 0.3.262–0.3.263 с «BucketRest» или
        // 432-слотовым виртуалем схлопываются по правилам вставки, прочие
        // читаются позиционно (свой формат «I»).
        if (tag.contains("BucketRest") || isLegacyVirtual(tag)) {
            migrateLegacyStorage(tag, registries);
        } else {
            loadStackList(tag, "Virtual", virtual, registries);
        }
        normalizeStacks(); // легаси-стопки >64 → классика
        placedAt = tag.getLong("PlacedAt"); // 0 → выставится первым тиком
        hibernating = tag.getBoolean("Hibernating");
        sectorElectionDue = true; // сектор всегда проверяем заново на загрузке
        drillPoints.clear();
        for (long raw : tag.getLongArray("DrillPoints")) {
            drillPoints.add(BlockPos.of(raw));
        }
        buildQueue.clear();
        for (Tag queueTag : tag.getList("BuildQueue", Tag.TAG_COMPOUND)) {
            CompoundTag cell = (CompoundTag) queueTag;
            buildQueue.add(new NetworkPlanner.Entry(
                BlockPos.of(cell.getLong("Pos")), cell.getBoolean("Drill")));
        }
        placeCooldown = tag.getInt("PlaceCooldown");
        drilling = tag.getBoolean("Drilling");
        netRoadsFull = tag.getInt("NetRoadsFull");
        netRoadsLink = tag.getInt("NetRoadsLink");
        netDrillsToPlace = tag.getInt("NetDrillsToPlace");
        loadPosList(tag, "DismantleQueue", dismantleQueue);
        loadPosList(tag, "TntQueue", tntQueue);
    }

    /** Слом Плотника: сначала убрать его секторную разметку, затем выронить содержимое. */
    public void onBroken() {
        if (level instanceof ServerLevel server) {
            clearSectorMarks(server);
            notifySectorChanged(server, worldPosition); // разбудить анабиозных (#анбиоз)
        }
        dropContentsForBreak();
    }

    public void dropContentsForBreak() {
        if (level == null || level.isClientSide()) return;
        // Дропим ОДИН раз и гасим контейнер: MachineBlock.onRemove после
        // super-вызова вывалил бы items повторно (замечание автора #2).
        for (ItemStack stack : items) dropSized(stack);
        for (ItemStack stack : virtual) dropSized(stack);
        clearContent();
    }

    /** Выдаёт содержимое дробными стопками ≤ лимита предмета — краш-защита. */
    private void dropSized(ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemStack rest = stack.copy();
        int cap = Math.max(1, rest.getMaxStackSize()); // ваниальный потолок и так ≤99
        while (!rest.isEmpty()) {
            ItemStack piece = rest.split(cap);
            net.minecraft.world.Containers.dropItemStack(level,
                worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), piece);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new PlotnickMenu(id, inventory, this, data);
    }
}
