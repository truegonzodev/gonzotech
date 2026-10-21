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
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Плотник автошахтера (0.3.256-0.3.257, автор).
 *
 * <p>Хранилище: 9 активных слотов по 512 (любой предмет) + 54 виртуальных стака
 * по 512. Добыча «капает» сначала в активные слоты, переполнение — в виртуальные
 * (кнопка выдачи в воронки/трубы — пакет добычи). Два слота — карты памяти.</p>
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

    /** 9 активных слотов ×512 (автор). */
    public static final int SLOTS_ACTIVE = 9;
    public static final int SLOT_CARD_A = 9;
    public static final int SLOT_CARD_B = 10;
    public static final int CONTAINER_SIZE = 11;
    /** 54 виртуальных стака ×512 (автор) — невидимый буфер переполнения. */
    public static final int VIRTUAL_SLOTS = 54;
    /** Стак слотов Плотника — до 512 предметов одного типа (автор). */
    public static final int MAX_STACK = 512;

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

    private final GtBuffer gtu = new GtBuffer(GTU_CAPACITY_MILLI);
    private final NonNullList<ItemStack> items =
        NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
    private final NonNullList<ItemStack> virtual =
        NonNullList.withSize(VIRTUAL_SLOTS, ItemStack.EMPTY);

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

    private long intakeBudgetTick = Long.MIN_VALUE;
    private int acceptedGtuThisTick;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> gtu.amountUnitsInt();
                case 1 -> (int) virtual.stream().filter(stack -> !stack.isEmpty()).count();
                // Состояние GUI-тултипов: 1 — в центральном слоте заполненная
                // карта памяти (кнопки переключаются на рудный режим).
                case 2 -> MemoryCardItem.isFilled(items.get(SLOT_CARD_A)) ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) gtu.set(MachineDefs.toMilli(Math.max(0, value)));
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    public PlotnickBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PLOTNICK.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PlotnickBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        be.processBuildQueue(server);
        be.processDismantleQueue(server);
        be.processTntQueue(server);
        be.tickDrills(server);
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
        return switch (id) {
            case BTN_PACK_UP -> packUp(server, requester);
            case BTN_MARK -> placeSectorMarks(server, requester);
            case BTN_NETWORK -> buildNetwork(server, requester);
            case BTN_SEISMO -> seismicImpulse(server, requester);
            case BTN_DYNAMITE -> rigTnt(server, requester);
            default -> false; // бурение — следующий пакет добычи
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
            if (!isOwnSector(MemoryCardItem.readSector(card))) {
                // Плотник размечает только свой сектор: чужая карта — повод
                // встать, а не перекраивать чужие земли.
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
                        server, surface, MinerMarker.Kind.STATION, 0, sourceDisplay);
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
                            MinerMarker.Kind.STATION, 0, geophoneDisplay);
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
        for (int cx = sector.minX(); cx <= sector.maxX(); cx++) {
            for (int cz = sector.minZ(); cz <= sector.maxZ(); cz++) {
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
                        service.add(server, surface, MinerMarker.Kind.ORE, 0, display);
                    // id рудной метки в секторном списке: «Свернуться» и
                    // следующая разметка убирают их тем же механизмом.
                    sectorMarkerIds.add(marker.id());
                    marks++;
                }
            }
        }
        setChanged();
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
        if (drillMode && !isOwnSector(MemoryCardItem.readSector(items.get(SLOT_CARD_A)))) {
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
            NetworkPlanner.plan(server, worldPosition, collectWorkCells(server), anchors);
        // Порядок автора: сначала дорожки через рабочие блоки, буры — только
        // после того, как сеть дошла до точек.
        List<NetworkPlanner.Entry> queue = new ArrayList<>(plan.entries());
        for (BlockPos drillCell : drillSlots) queue.add(new NetworkPlanner.Entry(drillCell, true));

        if (queue.isEmpty()) {
            report(requester, "message.gonzotech.plotnick.network.noneeded", plan.already());
            return true;
        }
        buildQueue.clear();
        buildQueue.addAll(queue);
        placeCooldown = 2;
        buildRequester = requester;
        setChanged();
        report(requester, "message.gonzotech.plotnick.network.planned",
            plan.targets(), plan.already(), plan.unreachable() + skipped, queue.size(),
            countStored(ModMachines.WORK_BLOCK.get().asItem())
                + countStored(ModMachines.MINER_DRILL.get().asItem()));
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

    /** Все рабочие блоки 9 чанков сектора (flood-fill сети стартует от Плотника). */
    private Set<BlockPos> collectWorkCells(ServerLevel server) {
        int cx = worldPosition.getX() >> 4;
        int cz = worldPosition.getZ() >> 4;
        net.minecraft.world.level.block.Block work = ModMachines.WORK_BLOCK.get();
        Set<BlockPos> cells = new HashSet<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                LevelChunk chunk = server.getChunk(cx + dx, cz + dz);
                int baseX = (cx + dx) << 4;
                int baseZ = (cz + dz) << 4;
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        for (int y = server.getMinY(); y <= server.getMaxY(); y++) {
                            if (chunk.getBlockState(new BlockPos(lx, y, lz)).is(work)) {
                                cells.add(new BlockPos(baseX + lx, y, baseZ + lz).immutable());
                            }
                        }
                    }
                }
            }
        }
        return cells;
    }

    /** Тик-расходование очереди: 4–8 тиков между блоками, звук и частицы на каждый. */
    private void processBuildQueue(ServerLevel server) {
        if (buildQueue.isEmpty()) return;
        if (placeCooldown > 0) {
            placeCooldown--;
            return;
        }
        NetworkPlanner.Entry entry = buildQueue.remove(0);
        placeQueueBlock(server, entry);
        setChanged();
        placeCooldown = 4 + server.random.nextInt(5);
        if (buildQueue.isEmpty()) {
            if (buildRequester != null) {
                report(buildRequester, "message.gonzotech.plotnick.network.done");
            }
            buildRequester = null;
        }
    }

    /**
     * Одна клетка очереди: уже стоит — пропускаем; solid/чужой блок — без
     * пересчёта плана тоже пропускаем; не хватает предмета — ждём на месте
     * (очередь сохраняется, пополнение хранилища продолжает стройку).
     */
    private void placeQueueBlock(ServerLevel server, NetworkPlanner.Entry entry) {
        BlockPos pos = entry.pos();
        BlockState target = entry.drill()
            ? ModMachines.MINER_DRILL.get().defaultBlockState()
            : ModMachines.WORK_BLOCK.get().defaultBlockState();
        BlockState current = server.getBlockState(pos);
        if (current.is(target.getBlock())) return;
        if (!NetworkPlanner.isBuildable(current, server.getBlockState(pos.below()))) return;
        Item needed = target.getBlock().asItem();
        if (countStored(needed) == 0) {
            buildQueue.add(0, entry);
            return;
        }
        consumeStored(needed, 1);
        server.setBlock(pos, target, net.minecraft.world.level.block.Block.UPDATE_ALL);
        // В 1.21.4 звук размещения блока берётся из SoundType (констант
        // SoundEvents.BLOCK_*_PLACE больше нет); корпус рабочих блоков — METAL.
        server.playSound(null, pos, SoundType.METAL.getPlaceSound(), SoundSource.BLOCKS,
            0.65F, 0.9F + 0.2F * server.random.nextFloat());
        // Красивая «усадка»: в самой поставленной клетке — слома-частицы
        // самого рабочего блока (block_crack по старому имени) и второй,
        // более широкий крошащий залп. Заменённые блоки частиц не дают.
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
            server, worldPosition, collectWorkCells(server));
        List<SeismicSourceBlockEntity> armed = new ArrayList<>();
        int notConnected = 0;
        int notLoaded = 0;
        for (BlockPos sourcePos : sources) {
            boolean linked = sourcePos.distManhattan(worldPosition) == 1
                || NetworkPlanner.touching(sourcePos, region);
            if (!linked) {
                notConnected++;
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

        int cx = worldPosition.getX() >> 4;
        int cz = worldPosition.getZ() >> 4;
        MemoryCardItem.writeSector(card,
            new MemoryCardItem.Sector(cx - 1, cx + 1, cz - 1, cz + 1));
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
        boolean cycleStart = drillTick == 0;
        if (cycleStart) {
            // «Маршрут» бура: связность сети рабочих блоков с Плотником.
            drillRegion = NetworkPlanner.connectedWorkRegion(
                server, worldPosition, collectSurfaceWorkCells(server));
        }
        if (drillPoints.isEmpty()) return;
        // Нет скваженных труб в хранилище — скважины стоят (требование автора).
        int pipesAvailable = countStored(ModMachines.BORE_PIPE.get().asItem());
        if (pipesAvailable == 0) return;

        List<BlockPos> running = new ArrayList<>();
        for (BlockPos point : drillPoints) {
            if (drillCell == null) continue; // бур ещё не доложен/разобран
            boolean linked = drillCell.distManhattan(worldPosition) <= 1
                || NetworkPlanner.touching(drillCell, drillRegion);
            if (!linked) continue;
            int targetY = drillTargetY.computeIfAbsent(drillCell,
                cell -> deepestTargetY(server, cell));
            BlockPos target = new BlockPos(drillCell.getX(), targetY, drillCell.getZ());
            if (isImmovable(server.getBlockState(target), server, target)) continue;
            running.add(drillCell);
        }
        // Каждый активный бур тратит на цикл ровно одну трубу — платим
        // только за те скважины, которым труб и так хватает.
        if (running.size() > pipesAvailable) {
            running = new ArrayList<>(running.subList(0, pipesAvailable));
        }
        if (running.isEmpty()) return;

        // Равномерное деление не нужно: просто цифровой вычет баланса
        // (6.5 GTU/т × число работающих буров). Нечем платить — цикл стоит.
        long want = PlotnickDefs.DRILL_GTU_MILLI * running.size();
        if (gtu.extract(want, true) < want) return;
        gtu.extract(want, false);

        for (BlockPos drillCell : running) {
            if (cycleStart) digStep(server, drillCell);
            collectOreRow(server, drillCell);
        }
    }

    /** Стоит ли бур именно в этой клетке (точка разметки или блок над ней). */
    private static BlockPos drillCellAt(ServerLevel server, BlockPos point) {
        if (server.getBlockState(point).is(ModMachines.MINER_DRILL.get())) return point;
        BlockPos above = point.above();
        if (server.getBlockState(above).is(ModMachines.MINER_DRILL.get())) return above;
        return null;
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
        if (consumeStored(ModMachines.BORE_PIPE.get().asItem(), 1) == 0) return;
        server.setBlock(target, ModMachines.BORE_PIPE.get().defaultBlockState(),
            net.minecraft.world.level.block.Block.UPDATE_ALL);
        if (!ts.isAir()) {
            // Порода/грунт сразу в Плотник — без выпадания предмета.
            Item mined = ts.getBlock().asItem();
            if (mined != null) insert(new ItemStack(mined));
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
                server.destroyBlock(pos, false, null);
                Item item = state.getBlock().asItem();
                if (item != null) insert(new ItemStack(item));
            }
        }
    }

    /** Рабочие блоки в приповерхностной ленте 9 чанков — дешевый кэш сети. */
    private Set<BlockPos> collectSurfaceWorkCells(ServerLevel server) {
        int cx = worldPosition.getX() >> 4;
        int cz = worldPosition.getZ() >> 4;
        net.minecraft.world.level.block.Block work = ModMachines.WORK_BLOCK.get();
        Set<BlockPos> cells = new HashSet<>();
        int y0 = Math.max(server.getMinY(), worldPosition.getY() - 8);
        int y1 = Math.min(server.getMaxY(), worldPosition.getY() + 8);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                LevelChunk chunk = server.getChunk(cx + dx, cz + dz);
                int baseX = (cx + dx) << 4;
                int baseZ = (cz + dz) << 4;
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        for (int y = y0; y <= y1; y++) {
                            if (chunk.getBlockState(new BlockPos(lx, y, lz)).is(work)) {
                                cells.add(new BlockPos(baseX + lx, y, baseZ + lz).immutable());
                            }
                        }
                    }
                }
            }
        }
        return cells;
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
            changed = true;
        }
        if (!drillPoints.isEmpty() || !drillTargetY.isEmpty()) {
            drillPoints.clear();
            drillTargetY.clear();
            changed = true;
        }
        List<BlockPos> lootable = collectNetworkBlocks(server);
        if (lootable.isEmpty()) {
            if (changed) setChanged();
            report(requester, "message.gonzotech.plotnick.pack.nothing");
            return true;
        }
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
            // Динамит из разряжаемого источника — тоже домой, в Плотник.
            if (server.getBlockEntity(pos) instanceof SeismicSourceBlockEntity source
                && source.hasDynamite()) {
                insert(new ItemStack(Items.TNT));
            }
            server.levelEvent(2001, pos, net.minecraft.world.level.block.Block.getId(state));
            server.destroyBlock(pos, false, null);
            Item item = state.getBlock().asItem();
            if (item != null) {
                insert(new ItemStack(item));
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
        int cx = worldPosition.getX() >> 4;
        int cz = worldPosition.getZ() >> 4;
        net.minecraft.world.level.block.Block pipe = ModMachines.BORE_PIPE.get();
        List<BlockPos> found = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                LevelChunk chunk = server.getChunk(cx + dx, cz + dz);
                int baseX = (cx + dx) << 4;
                int baseZ = (cz + dz) << 4;
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        for (int y = server.getMinY(); y <= server.getMaxY(); y++) {
                            if (chunk.getBlockState(new BlockPos(lx, y, lz)).is(pipe)) {
                                found.add(new BlockPos(baseX + lx, y, baseZ + lz).immutable());
                            }
                        }
                    }
                }
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
            consumeStored(explosive, 1);
            BlockState tnt = explosive == ModItems.INDUSTRIAL_TNT_ITEM.get()
                ? ModBlocks.INDUSTRIAL_TNT.get().defaultBlockState()
                : net.minecraft.world.level.block.Blocks.TNT.defaultBlockState();
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
     * Правило автора: сначала активные слоты, переполнение — в виртуальные
     * (54 ×512). Используется будущей добычей и выкладкой сетки.
     */
    public boolean insert(ItemStack incoming) {
        if (incoming.isEmpty()) return false;
        ItemStack rest = incoming.copy();
        for (int slot = 0; slot < SLOTS_ACTIVE; slot++) {
            rest = insertInto(items, slot, rest);
            if (rest.isEmpty()) {
                setChanged();
                return true;
            }
        }
        for (int slot = 0; slot < VIRTUAL_SLOTS; slot++) {
            rest = insertInto(virtual, slot, rest);
            if (rest.isEmpty()) {
                setChanged();
                return true;
            }
        }
        if (rest.getCount() != incoming.getCount()) {
            incoming.setCount(rest.getCount());
            setChanged();
            return true;
        }
        return false;
    }

    private static ItemStack insertInto(NonNullList<ItemStack> list, int slot, ItemStack incoming) {
        ItemStack current = list.get(slot);
        if (current.isEmpty()) {
            list.set(slot, incoming);
            return ItemStack.EMPTY;
        }
        if (!ItemStack.isSameItemSameComponents(current, incoming)) return incoming;
        int space = MAX_STACK - current.getCount();
        if (space <= 0) return incoming;
        int moved = Math.min(space, incoming.getCount());
        current.grow(moved);
        incoming.shrink(moved);
        return incoming.isEmpty() ? ItemStack.EMPTY : incoming;
    }

    public NonNullList<ItemStack> virtualStacks() {
        return virtual;
    }

    /** Сколько предметов типа лежит в Плотнике: сначала активные слоты, затем виртуальные. */
    public int countStored(Item item) {
        int total = 0;
        for (ItemStack stack : items) {
            if (stack.is(item)) total += stack.getCount();
        }
        for (ItemStack stack : virtual) {
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    /** Расход только из 9 активных слотов (карты и виртуальный буфер не трогаем). */
    public int consumeActive(Item item, int count) {
        int left = Math.max(0, count);
        for (int slot = 0; slot < SLOTS_ACTIVE && left > 0; slot++) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty() || !stack.is(item)) continue;
            int take = Math.min(left, stack.getCount());
            stack.shrink(take);
            if (stack.isEmpty()) items.set(slot, ItemStack.EMPTY);
            left -= take;
        }
        if (left != count) setChanged();
        return count - left;
    }

    /** @return сколько реально сняли (активные слоты расходуются первыми) */
    public int consumeStored(Item item, int count) {
        int left = Math.max(0, count);
        for (int slot = 0; slot < items.size() && left > 0; slot++) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty() || !stack.is(item)) continue;
            int take = Math.min(left, stack.getCount());
            stack.shrink(take);
            if (stack.isEmpty()) items.set(slot, ItemStack.EMPTY);
            left -= take;
        }
        for (int slot = 0; slot < virtual.size() && left > 0; slot++) {
            ItemStack stack = virtual.get(slot);
            if (stack.isEmpty() || !stack.is(item)) continue;
            int take = Math.min(left, stack.getCount());
            stack.shrink(take);
            if (stack.isEmpty()) virtual.set(slot, ItemStack.EMPTY);
            left -= take;
        }
        if (left != count) setChanged();
        return count - left;
    }

    // ─────────────────────────── Container ───────────────────────────

    @Override
    public int getContainerSize() {
        return CONTAINER_SIZE;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.getCount() <= MAX_STACK;
    }

    @Override
    public void clearContent() {
        items.clear();
        for (int i = 0; i < CONTAINER_SIZE; i++) items.add(ItemStack.EMPTY);
        virtual.clear();
        for (int i = 0; i < VIRTUAL_SLOTS; i++) virtual.add(ItemStack.EMPTY);
    }

    @Override
    public int getMaxStackSize() {
        return MAX_STACK;
    }

    // Карты памяти — только руками; воронки/трубы ходят по активным слотам.
    @Override
    public int[] getSlotsForFace(Direction side) {
        int[] slots = new int[SLOTS_ACTIVE];
        for (int i = 0; i < slots.length; i++) slots[i] = i;
        return slots;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot < SLOTS_ACTIVE && canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return true;
    }

    // ─────────────────────────── persistence / menu ───────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        gtu.save(tag, "Gtu");
        ContainerHelper.saveAllItems(tag, items, registries);
        ListTag virtualTag = new ListTag();
        for (ItemStack stack : virtual) {
            if (!stack.isEmpty()) virtualTag.add(stack.save(registries));
        }
        tag.put("Virtual", virtualTag);
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
        savePosList(tag, "DismantleQueue", dismantleQueue);
        savePosList(tag, "TntQueue", tntQueue);
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
        ContainerHelper.loadAllItems(tag, items, registries);
        sectorMarkerIds.clear();
        for (long markerId : tag.getLongArray("SectorMarkers")) {
            sectorMarkerIds.add(markerId);
        }
        ListTag virtualTag = tag.getList("Virtual", Tag.TAG_COMPOUND);
        for (int i = 0; i < VIRTUAL_SLOTS; i++) {
            virtual.set(i, i < virtualTag.size()
                ? ItemStack.parseOptional(registries, virtualTag.getCompound(i))
                : ItemStack.EMPTY);
        }
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
        loadPosList(tag, "DismantleQueue", dismantleQueue);
        loadPosList(tag, "TntQueue", tntQueue);
    }

    /** Слом Плотника: сначала убрать его секторную разметку, затем выронить содержимое. */
    public void onBroken() {
        if (level instanceof ServerLevel server) {
            clearSectorMarks(server);
        }
        dropContentsForBreak();
    }

    public void dropContentsForBreak() {
        if (level == null || level.isClientSide()) return;
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                net.minecraft.world.Containers.dropItemStack(level,
                    worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack.copy());
            }
        }
        for (ItemStack stack : virtual) {
            if (!stack.isEmpty()) {
                net.minecraft.world.Containers.dropItemStack(level,
                    worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack.copy());
            }
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
