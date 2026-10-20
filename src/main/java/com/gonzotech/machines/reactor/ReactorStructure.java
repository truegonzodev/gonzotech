package com.gonzotech.machines.reactor;

import com.gonzotech.core.block.ReactorCasingBlock;
import com.gonzotech.core.block.ReactorControlPanelBlock;
import com.gonzotech.core.registry.ModBlocks;
import com.gonzotech.machines.block.entity.ReactorControlPanelBlockEntity;
import com.gonzotech.machines.energy.Transfer;
import com.gonzotech.machines.network.NodeClumpIndex;
import com.gonzotech.machines.network.PipeCarrier;
import com.gonzotech.machines.network.PipeType;
import com.gonzotech.machines.registry.ModMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Event-driven validator and formed-visual initializer for the first uranium reactor.
 *
 * <p>The 4..16 limit is measured on the outside of the cubic casing. The shell is
 * one block thick. Its only permitted parts are casing, one control panel, and
 * shielded third-tier output nodes. The filled interior is deliberately a strict
 * whitelist; air and unknown blocks prevent formation.</p>
 *
 * <p>The validated matrix feeds the ReaCalc 1.7 thermal runtime; integrated
 * third-tier nodes are water inputs and GTH outputs. Stored buffer water is not
 * consumed by, or connected to, the simulation.</p>
 */
public final class ReactorStructure {

    public static final int MIN_SIZE = 4;
    /** Внешняя сторона: активная зона 16³ максимум + обшивка = 18³ (0.3.244). */
    public static final int MAX_SIZE = 18;
    private static final int MAX_SHELL_PARTS = 1_600;

    /** Loaded controller panels used only to discover unformed reactors after block placement. */
    private static final Map<Level, Set<Long>> PANEL_POSITIONS =
        Collections.synchronizedMap(new WeakHashMap<>());

    /** Every cell in a formed reactor's saved bounds -> its persistent panel controller. */
    private static final Map<Level, Map<Long, BlockPos>> MEMBER_INDEX =
        Collections.synchronizedMap(new WeakHashMap<>());

    private ReactorStructure() {
    }

    /** Only a placement event may initialize an unformed reactor. */
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level) {
            partChanged(level, event.getPos());
        }
    }

    /** Breaking a member may invalidate a formed snapshot, but never initializes a new one. */
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.isCanceled()) return;
        if (event.getState().is(ModMachines.THIRD_URANIUM_REACTOR_FUEL_SHEATH.get())) {
            if (!ReactorFuelStorage.canSafelyRemoveSheath(level, event.getPos())) {
                event.setCanceled(true);
                event.getPlayer().displayClientMessage(Component.literal(
                    "Нельзя сломать ТВЭЛ-чехол, пока не загружены все чанки его реакторной зоны."), false);
            }
            // The sheath block performs fuel reallocation only after removal is certain.
            return;
        }
        partRemoved(level, event.getPos());
    }

    /** Register a loaded panel and restore only a structure that was already saved as formed. */
    public static void panelLoaded(ServerLevel level, ReactorControlPanelBlockEntity panel) {
        PANEL_POSITIONS.computeIfAbsent(level, ignored -> new HashSet<>())
            .add(panel.getBlockPos().asLong());
        if (panel.isFormedController()) {
            restorePanel(level, panel);
        } else {
            panel.clearValidationPending();
        }
    }

    public static void panelUnloaded(Level level, BlockPos pos) {
        // Called while a chunk is clearing its block entities: do not query the
        // level here, or getBlockEntity may synchronously request the chunk being unloaded.
        Set<Long> panels = PANEL_POSITIONS.get(level);
        if (panels != null) {
            panels.remove(pos.asLong());
            if (panels.isEmpty()) PANEL_POSITIONS.remove(level);
        }
        removeIndexForRoot(level, pos);
    }

    /** Transient loaded-panel and formed-member indexes are rebuilt from saved panel bounds. */
    public static void clearAll() {
        PANEL_POSITIONS.clear();
        MEMBER_INDEX.clear();
        ReactorMatrix.clearCache();
    }

    /** A placement revalidates its formed member or tries to initialize a nearby unformed panel. */
    public static void partChanged(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return;
        registerPanelIfPresent(server, pos);
        revalidateOrForm(server, pos);
    }

    /** Removal only invalidates a formed snapshot; it deliberately does not retry formation. */
    public static void partRemoved(Level level, BlockPos pos) {
        if (level instanceof ServerLevel server) {
            invalidateAt(server, pos);
        }
    }

    /** Restore saved bounds after load; an unformed panel is never initialized by chunk loading. */
    public static void restorePanel(ServerLevel level, ReactorControlPanelBlockEntity panel) {
        if (!panel.isFormedController()) {
            panel.clearValidationPending();
            return;
        }
        revalidateFormedPanel(level, panel, null);
    }

    /** Diagnose the current geometry, static ReaCalc values, and live ReaCalc 1.7 runtime. */
    public static List<Component> debugReportComponents(Level level, BlockPos panelPos) {
        if (!(level instanceof ServerLevel server)) {
            return List.of(Component.literal("Реактор: диагностика доступна на серверной стороне.")
                .withStyle(ChatFormatting.GOLD));
        }
        if (!server.hasChunkAt(panelPos)
            || !(server.getBlockState(panelPos).getBlock() instanceof ReactorControlPanelBlock)
            || !(server.getBlockEntity(panelPos) instanceof ReactorControlPanelBlockEntity panel)) {
            return List.of(Component.literal("Реактор не собран: на позиции панели нет её BlockEntity.")
                .withStyle(ChatFormatting.GOLD));
        }

        PANEL_POSITIONS.computeIfAbsent(server, ignored -> new HashSet<>()).add(panelPos.asLong());
        boolean wasFormed = panel.isFormedController();
        Validation validation = wasFormed
            ? revalidateFormedPanel(server, panel, null)
            : inspectFromPanel(server, panelPos, null);
        if (validation.incomplete()) {
            return List.of(Component.literal("Реактор: проверка отложена — не все чанки куба загружены; принудительная загрузка не выполнялась.")
                .withStyle(ChatFormatting.GOLD));
        }
        if (validation.structure() == null) {
            return List.of(Component.literal("Реактор не сформирован: " + validation.failure()
                + (wasFormed && !panel.isFormedController() ? " (сохранённая форма снята)." : "."))
                .withStyle(ChatFormatting.RED));
        }
        if (!panel.isFormedController()) {
            return List.of(Component.literal("Реактор: геометрия корректна, но он не сформирован. Разместите блок внутри куба или на его оболочке, чтобы запустить инициализацию.")
                .withStyle(ChatFormatting.GOLD));
        }
        if (panel.accidentTriggered()) {
            return List.of(Component.literal("Реактор уничтожен аварией: показания обнулены навсегда. Выньте кориум и лаву из активной зоны и замените панель управления, чтобы собрать новый реактор.")
                .withStyle(ChatFormatting.RED));
        }

        StructureInfo info = validation.structure();
        String summary = "Реактор I поколения сформирован: внешний куб " + info.size() + "³"
            + ", обшивок=" + info.casingBlocks()
            + ", ТВЭЛ-чехлов=" + info.sheaths()
            + ", узел ГТХ=" + info.heatNodes()
            + ", узел мультижидкости=" + info.fluidNodes()
            + ", универсальных узлов=" + info.universalNodes()
            + ", графита=" + info.graphite()
            + ", бора=" + info.boron()
            + ", бария=" + info.barium()
            + ", воды=" + info.water() + ".";
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(summary).withStyle(ChatFormatting.GRAY));
        try {
            ReactorMatrix.OperatingSnapshot snapshot = ReactorMatrix.operatingSnapshot(server, panel);
            ReactorMatrix.Diagnostic matrix = snapshot.diagnostic();
            ReactorOperatingMetrics.Values values = snapshot.metrics();
            ReactorRuntime.Metrics runtime = panel.runtimeMetrics();
            ReactorNeutronMetrics.Values neutron = runtime.neutrons();
            int capacity = matrix.fuelSheaths() == 0 ? 0 : Math.min(matrix.fuelSheaths() + 2, 18);
            lines.add(joinStats(
                stat("R=" + snapshot.loadedTvels() + " ТВЭЛов", ChatFormatting.GOLD),
                stat("N=" + values.sleeves() + " чехлов", ChatFormatting.GOLD),
                stat("слотов=" + capacity, ChatFormatting.GRAY),
                stat("R/N=" + format(values.ratio(), 5), ChatFormatting.AQUA)));
            if (snapshot.loadedTvels() > capacity) {
                lines.add(joinStats(
                    stat("Перегрузка ТВЭЛами: ", ChatFormatting.RED),
                    stat(snapshot.loadedTvels() + "/" + capacity
                        + " — лишние видимы в чехлах, но не участвуют в матрице", ChatFormatting.GOLD)));
            }
            lines.add(joinStats(
                stat("rawH=", format(values.rawH(), 5), ChatFormatting.GOLD),
                stat("rawL=", format(values.rawL() * 100.0, 3) + " %", ChatFormatting.AQUA)));
            lines.add(joinStats(
                stat("Htv=", format(values.htv(), 5), ChatFormatting.GOLD),
                stat("Ltv=", format(values.ltv() * 100.0, 3) + " %", ChatFormatting.AQUA),
                stat("S=", values.overheat() ? "Саморазгон · Htv ≥ 1" : format(values.score(), 2), ChatFormatting.LIGHT_PURPLE)));
            lines.add(joinStats(
                stat("Целевой rawH*=", format(values.targetRawH(), 5), ChatFormatting.GOLD),
                stat("ΔH=", formatSigned(values.deltaRawH(), 5), ChatFormatting.GRAY)));
            lines.add(joinStats(
                stat("Средний Htv=", format(values.meanH(), 5), ChatFormatting.GOLD),
                stat("T горячей точки=", formatTemperature(runtime.hotTemperature()), ChatFormatting.GOLD)));
            lines.add(joinStats(
                stat("T топлива max=", formatTemperature(values.fuelTemperature()), ChatFormatting.GOLD),
                stat("T реактора средняя=", formatTemperature(runtime.meanTemperature()), ChatFormatting.GOLD),
                stat("Фаза=", runtime.phase(), ChatFormatting.LIGHT_PURPLE)));
            lines.add(joinStats(
                stat("Поток max/средний=", format(neutron.strongestFlow() * 100.0, 2) + " / "
                    + format(neutron.averageFlow() * 100.0, 2) + " %", ChatFormatting.LIGHT_PURPLE),
                stat("Утечка max/средняя=", format(neutron.strongestLeak() * 100.0, 2) + " / "
                    + format(neutron.averageLeak() * 100.0, 2) + " %", ChatFormatting.AQUA)));
            lines.add(joinStats(
                stat("qDry=", format(values.qDry(), 4), ChatFormatting.GRAY),
                stat("Pнагрева=", format(values.power(), 2) + " ед/тик", ChatFormatting.RED)));
            lines.add(joinStats(
                stat("W=", values.waterUnits() + " ед.", ChatFormatting.AQUA),
                stat("Tw=", formatTemperature(values.waterTemperature()), ChatFormatting.AQUA),
                stat("p=", pressure(values), ChatFormatting.AQUA),
                stat("feedback=", feedback(values), ChatFormatting.AQUA)));
            lines.add(joinStats(
                stat("Pтеплообмен=", format(values.output(), 2) + " ед/тик", ChatFormatting.RED),
                stat("Pполезный=", format(values.usefulOutput(), 2) + " ед/тик", ChatFormatting.GREEN),
                stat("Эффективность=", format(values.efficiency(), 2) + " %", ChatFormatting.LIGHT_PURPLE),
                stat("Режим=", values.regime(), ChatFormatting.AQUA)));
            lines.add(joinStats(
                stat("GTH выработка=", format(runtime.generationRate(), 2) + " GTH/т", ChatFormatting.RED),
                stat("GTH полезное=", format(runtime.usefulGenerationRate(), 2) + " GTH/т", ChatFormatting.GREEN)));
            lines.add(Component.literal("Статический расчёт ReaCalc 1.2 + динамика ReaCalc 1.7: 10000 °C — порог аварии (сброс показаний, слизь, кориум и взрыв). ТВЭЛы тикают на выработке (1 ед. прочности в секунду на стержень); шкала воды привязана к теплообмену и выработке, воду убирает только клапан.")
                .withStyle(ChatFormatting.DARK_GRAY));
        } catch (RuntimeException exception) {
            try {
                ReactorMatrix.Diagnostic matrix = ReactorMatrix.evaluate(server, panel);
                int loadedTvels = ReactorFuelStorage.totalTvels(server, panel);
                lines.add(joinStats(
                    stat("rawH=", format(matrix.coefficients().heat(), 5), ChatFormatting.GOLD),
                    stat("rawL=", format(matrix.coefficients().peakLeak() * 100.0, 3) + " %", ChatFormatting.AQUA),
                    stat("R=" + loadedTvels + " ТВЭЛов", ChatFormatting.GOLD)));
            } catch (RuntimeException ignored) {
                // Keep the original error visible even if a secondary dry diagnostic also fails.
            }
            lines.add(Component.literal("Рабочие статы не рассчитаны: " + exception.getMessage())
                .withStyle(ChatFormatting.RED));
        }
        return List.copyOf(lines);
    }

    /** Plain-text form retained for logs and audit tools. */
    public static String debugReport(Level level, BlockPos panelPos) {
        return String.join("\n", debugReportComponents(level, panelPos).stream()
            .map(Component::getString).toList());
    }

    /** Backward-compatible single-component form; Shift+ПКМ sends the lines separately. */
    public static Component debugReportComponent(Level level, BlockPos panelPos) {
        MutableComponent combined = Component.empty();
        boolean first = true;
        for (Component line : debugReportComponents(level, panelPos)) {
            if (!first) combined.append(Component.literal("\n"));
            combined.append(line);
            first = false;
        }
        return combined;
    }

    private static Component stat(String label, String value, ChatFormatting color) {
        return Component.literal(label).withStyle(ChatFormatting.GRAY)
            .append(Component.literal(value).withStyle(color));
    }

    private static Component stat(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(color);
    }

    private static Component joinStats(Component... stats) {
        MutableComponent line = Component.empty();
        for (int i = 0; i < stats.length; i++) {
            if (i > 0) line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(stats[i]);
        }
        return line;
    }

    private static String format(double value, int digits) {
        return Double.isFinite(value)
            ? String.format(Locale.ROOT, "%." + digits + "f", value)
            : "—";
    }

    private static String formatSigned(double value, int digits) {
        return Double.isFinite(value)
            ? String.format(Locale.ROOT, "%+." + digits + "f", value)
            : "—";
    }

    private static String formatTemperature(double value) {
        return Double.isFinite(value) ? format(value, 1) + " °C" : "NaN · перегрев";
    }

    private static String pressure(ReactorOperatingMetrics.Values values) {
        return Double.isFinite(values.pressure())
            ? format(values.pressure(), 3) + " усл." : "NaN · перегрев";
    }

    private static String feedback(ReactorOperatingMetrics.Values values) {
        return Double.isFinite(values.pressureFeedback())
            ? "× " + format(values.pressureFeedback(), 3) : "NaN";
    }

    private static String output(double value) {
        return Double.isFinite(value)
            ? format(value, 2) + " ед/тик" : "NaN · перегрев";
    }

    private static void registerPanelIfPresent(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof ReactorControlPanelBlock
            && level.getBlockEntity(pos) instanceof ReactorControlPanelBlockEntity) {
            PANEL_POSITIONS.computeIfAbsent(level, ignored -> new HashSet<>()).add(pos.asLong());
        }
    }

    private static void revalidateOrForm(ServerLevel level, BlockPos changed) {
        BlockPos indexedRoot = controllerAt(level, changed);
        if (indexedRoot != null) {
            if (level.getBlockEntity(indexedRoot) instanceof ReactorControlPanelBlockEntity panel
                && panel.isFormedController()) {
                ReactorMatrix.invalidate(panel);
                revalidateFormedPanel(level, panel, null);
            } else {
                removeIndexForRoot(level, indexedRoot);
            }
            return;
        }

        Set<Long> indexedPanels = PANEL_POSITIONS.get(level);
        if (indexedPanels == null || indexedPanels.isEmpty()) return;
        for (long packedPanel : Set.copyOf(indexedPanels)) {
            BlockPos panelPos = BlockPos.of(packedPanel);
            if (!near(changed, panelPos)) continue;
            if (!level.hasChunkAt(panelPos)
                || !(level.getBlockState(panelPos).getBlock() instanceof ReactorControlPanelBlock)) {
                panelUnloaded(level, panelPos);
                continue;
            }
            if (!(level.getBlockEntity(panelPos) instanceof ReactorControlPanelBlockEntity panel)) continue;

            if (panel.isFormedController()) {
                // A formed reactor owns a fixed box. Adjacent shell-looking blocks
                // cannot expand or replace that snapshot.
                if (panel.contains(changed)) {
                    revalidateFormedPanel(level, panel, null);
                }
                continue;
            }
            tryFormFromPlacement(level, panel, changed);
        }
    }

    private static void tryFormFromPlacement(ServerLevel level,
                                             ReactorControlPanelBlockEntity panel,
                                             BlockPos placedPos) {
        if (panel.isFormedController()) return;
        Validation validation = inspectFromPanel(level, panel.getBlockPos(), null);
        if (validation.incomplete() || validation.structure() == null
            || !contains(validation.structure(), placedPos)) {
            return;
        }
        applyFormedVisuals(level, panel, validation.structure());
    }

    private static boolean contains(StructureInfo structure, BlockPos pos) {
        return pos.getX() >= structure.min().getX() && pos.getX() <= structure.max().getX()
            && pos.getY() >= structure.min().getY() && pos.getY() <= structure.max().getY()
            && pos.getZ() >= structure.min().getZ() && pos.getZ() <= structure.max().getZ();
    }

    private static boolean near(BlockPos a, BlockPos b) {
        int reach = MAX_SIZE - 1;
        return Math.abs((long) a.getX() - b.getX()) <= reach
            && Math.abs((long) a.getY() - b.getY()) <= reach
            && Math.abs((long) a.getZ() - b.getZ()) <= reach;
    }

    private static Validation revalidateFormedPanel(ServerLevel level,
                                                    ReactorControlPanelBlockEntity panel,
                                                    BlockPos ignoredPos) {
        if (!panel.isFormedController()) {
            return Validation.failure("панель не хранит сформированную структуру");
        }
        BlockPos min = panel.min();
        BlockPos max = panel.max();
        if (!chunksLoaded(level, min, max)) {
            panel.markValidationPending();
            return Validation.incompleteResult();
        }

        Validation validation = validateBox(level, panel.getBlockPos(), new Bounds(min, max), ignoredPos);
        if (validation.incomplete()) {
            panel.markValidationPending();
            return validation;
        }
        panel.clearValidationPending();
        if (validation.structure() != null) {
            applyFormedVisuals(level, panel, validation.structure());
        } else {
            invalidateController(level, panel, ignoredPos);
        }
        return validation;
    }

    private static void invalidateAt(ServerLevel level, BlockPos member) {
        BlockPos root = controllerAt(level, member);
        if (root == null) return;
        if (level.getBlockEntity(root) instanceof ReactorControlPanelBlockEntity panel
            && panel.isFormedController()) {
            ReactorMatrix.invalidate(panel);
            revalidateFormedPanel(level, panel, member);
        } else {
            removeIndexForRoot(level, root);
        }
    }

    private static void invalidateController(ServerLevel level,
                                             ReactorControlPanelBlockEntity panel,
                                             BlockPos ignoredPos) {
        if (!panel.isFormedController()) return;
        ReactorMatrix.invalidate(panel);
        BlockPos min = panel.min();
        BlockPos max = panel.max();
        if (!chunksLoaded(level, min, max)) {
            panel.markValidationPending();
            return;
        }
        removeIndexForRoot(level, panel.getBlockPos());
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (pos.equals(ignoredPos) || !onBoundary(pos, min, max)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (state.getBlock() instanceof ReactorCasingBlock
                        && state.getValue(ReactorCasingBlock.FORMED)) {
                        level.setBlock(pos, state.setValue(ReactorCasingBlock.FORMED, false), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        panel.clearStructure();
    }

    private static BlockPos controllerAt(ServerLevel level, BlockPos pos) {
        Map<Long, BlockPos> index = MEMBER_INDEX.get(level);
        return index == null ? null : index.get(pos.asLong());
    }

    /** Virtual water receiver on an integrated fluid/universal node of a formed reactor. */
    public static Transfer.Receiver waterReceiverAt(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return null;
        BlockPos root = controllerAt(server, pos);
        if (root == null
            || !(server.getBlockEntity(root) instanceof ReactorControlPanelBlockEntity panel)
            || !panel.isWaterPort(pos)) {
            return null;
        }
        return (amount, simulate) -> panel.receiveWaterFromPort(pos, amount, simulate);
    }

    /** True for all positions inside the currently indexed reactor, including its controller. */
    public static boolean isMember(Level level, BlockPos pos) {
        return level instanceof ServerLevel server && controllerAt(server, pos) != null;
    }

    /**
     * Выдача реактора на один вывод — 16 364 GTH/т (0.3.250): потолок самого
     * реактора, а не узла. Экранированный узел в обшивке — формовочный блок,
     * обозначающий вывод тепла; дальше ограничивают только трубы, и вторая
     * независимая сеть на другом выводе получает свой собственный потолок.
     */
    public static long gthPortLimit(Level level, BlockPos pos) {
        if (level == null || pos == null || !level.hasChunkAt(pos)) return 0L;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PipeCarrier carrier)
            || !carrier.carries(state, PipeType.HEAT)) return 0L;
        return com.gonzotech.machines.energy.MachineDefs.REACTOR_GTH_PER_PORT_MILLI;
    }

    private static void indexStructure(ServerLevel level, BlockPos controller, BlockPos min, BlockPos max) {
        removeIndexForRoot(level, controller);
        Map<Long, BlockPos> index = MEMBER_INDEX.computeIfAbsent(level, ignored -> new HashMap<>());
        BlockPos root = controller.immutable();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    index.put(new BlockPos(x, y, z).asLong(), root);
                }
            }
        }
    }

    private static void removeIndexForRoot(Level level, BlockPos root) {
        Map<Long, BlockPos> index = MEMBER_INDEX.get(level);
        if (index == null) return;
        index.entrySet().removeIf(entry -> root.equals(entry.getValue()));
        if (index.isEmpty()) MEMBER_INDEX.remove(level);
    }

    private static Validation inspectFromPanel(ServerLevel level, BlockPos panelPos, BlockPos ignoredPos) {
        if (panelPos.equals(ignoredPos)) {
            return Validation.failure("панель управления вынута из оболочки");
        }
        if (!level.hasChunkAt(panelPos)) {
            return Validation.incompleteResult();
        }
        if (!(level.getBlockState(panelPos).getBlock() instanceof ReactorControlPanelBlock)) {
            return Validation.failure("панель управления должна находиться на внешней оболочке");
        }

        ComponentResult component = collectShellComponent(level, panelPos, ignoredPos);
        if (component.tooLarge()) {
            return Validation.failure("связная оболочка превышает защитный лимит деталей");
        }
        if (component.incomplete()) {
            return Validation.incompleteResult();
        }
        if (component.positions().isEmpty()) {
            return Validation.failure("не найдена связная оболочка из обшивки, панели и экранированных узлов");
        }
        Bounds bounds = boundsOf(component.positions());
        int sizeX = bounds.max().getX() - bounds.min().getX() + 1;
        int sizeY = bounds.max().getY() - bounds.min().getY() + 1;
        int sizeZ = bounds.max().getZ() - bounds.min().getZ() + 1;
        if (sizeX != sizeY || sizeX != sizeZ) {
            return Validation.failure("корпус должен быть кубическим, найдено "
                + sizeX + "×" + sizeY + "×" + sizeZ);
        }
        if (sizeX < MIN_SIZE || sizeX > MAX_SIZE) {
            return Validation.failure("внешняя сторона куба должна быть от "
                + MIN_SIZE + " до " + MAX_SIZE + ", найдено " + sizeX);
        }
        if (!onBoundary(panelPos, bounds.min(), bounds.max())) {
            return Validation.failure("панель управления должна стоять во внешнем слое оболочки");
        }
        if (!chunksLoaded(level, bounds.min(), bounds.max())) {
            return Validation.incompleteResult();
        }
        return validateBox(level, panelPos, bounds, ignoredPos);
    }

    private static ComponentResult collectShellComponent(ServerLevel level,
                                                         BlockPos seed,
                                                         BlockPos ignoredPos) {
        if (seed.equals(ignoredPos) || !isShellPart(level.getBlockState(seed))) {
            return new ComponentResult(List.of(), false, false);
        }

        List<BlockPos> positions = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Deque<BlockPos> pending = new ArrayDeque<>();
        boolean incomplete = false;
        pending.add(seed.immutable());
        seen.add(seed.asLong());

        while (!pending.isEmpty()) {
            BlockPos pos = pending.removeFirst();
            positions.add(pos);
            if (positions.size() > MAX_SHELL_PARTS) {
                return new ComponentResult(List.of(), true, false);
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                long packed = next.asLong();
                if ((ignoredPos != null && next.equals(ignoredPos)) || seen.contains(packed)) {
                    continue;
                }
                if (!level.hasChunkAt(next)) {
                    incomplete = true;
                    continue;
                }
                if (!next.equals(seed) && controllerAt(level, next) != null) continue;
                if (!isShellPart(level.getBlockState(next))) continue;
                seen.add(packed);
                pending.addLast(next.immutable());
            }
        }
        return new ComponentResult(List.copyOf(positions), false, incomplete);
    }

    private static Validation validateBox(ServerLevel level,
                                          BlockPos panelPos,
                                          Bounds bounds,
                                          BlockPos ignoredPos) {
        int size = bounds.max().getX() - bounds.min().getX() + 1;
        if (!chunksLoaded(level, bounds.min(), bounds.max())) return Validation.incompleteResult();

        int casing = 0;
        int panels = 0;
        int heatNodes = 0;
        int fluidNodes = 0;
        int universalNodes = 0;
        int valves = 0;
        int sheaths = 0;
        int graphite = 0;
        int boron = 0;
        int barium = 0;
        int water = 0;
        List<Long> heatPorts = new ArrayList<>();
        List<Long> waterPorts = new ArrayList<>();
        List<Long> valvePorts = new ArrayList<>();

        for (int x = bounds.min().getX(); x <= bounds.max().getX(); x++) {
            for (int y = bounds.min().getY(); y <= bounds.max().getY(); y++) {
                for (int z = bounds.min().getZ(); z <= bounds.max().getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = stateAt(level, pos, ignoredPos);
                    Block block = state.getBlock();
                    boolean shell = onBoundary(pos, bounds.min(), bounds.max());
                    if (shell) {
                        if (block instanceof ReactorCasingBlock) {
                            casing++;
                        } else if (block instanceof ReactorControlPanelBlock) {
                            panels++;
                        } else if (block == ModMachines.THIRD_HEAT_NODE.get()) {
                            heatNodes++;
                        } else if (block == ModMachines.THIRD_UNIVERSAL_FLUID_NODE.get()) {
                            fluidNodes++;
                        } else if (block == ModMachines.THIRD_UNIVERSAL_NODE.get()) {
                            universalNodes++;
                        } else if (block instanceof com.gonzotech.core.block.ReactorValveBlock) {
                            if (!valveFacesZone(state, pos, bounds.min(), bounds.max())) {
                                return Validation.failure("клапан должен стоять на грани обшивки, "
                                    + "а его face или back — смотреть на активную зону: "
                                    + pos.toShortString());
                            }
                            valves++;
                            valvePorts.add(pos.asLong());
                        } else {
                            return Validation.failure("оболочка не заполнена обшивкой, панелью и разрешёнными экранированными узлами: "
                                + pos.toShortString());
                        }
                        if (block instanceof PipeCarrier carrier) {
                            if (carrier.carries(state, PipeType.HEAT)) heatPorts.add(pos.asLong());
                            if (carrier.carries(state, PipeType.WATER)) waterPorts.add(pos.asLong());
                        }
                    } else {
                        if (state.isAir()) {
                            return Validation.failure("внутри оболочки есть воздух: " + pos.toShortString());
                        }
                        if (!isAllowedInterior(block)) {
                            return Validation.failure("недозволенный блок внутри оболочки: "
                                + BuiltInRegistries.BLOCK.getKey(block) + " в " + pos.toShortString());
                        }
                        if (block == ModMachines.THIRD_URANIUM_REACTOR_FUEL_SHEATH.get()) sheaths++;
                        else if (block == ModBlocks.GRAPHITE_BLOCK.get()) graphite++;
                        else if (isMetalBlock(block, "boron_block")) boron++;
                        else if (isMetalBlock(block, "barium_block")) barium++;
                        else if (block == Blocks.WATER) water++;
                    }
                }
            }
        }

        if (panels != 1 || !panelPos.equals(findPanelPosition(level, bounds, ignoredPos))) {
            return Validation.failure("во внешней оболочке должна быть ровно одна панель управления");
        }
        if (universalNodes < 1 && (heatNodes < 1 || fluidNodes < 1)) {
            return Validation.failure("нужен один экранированный универсальный узел либо одновременно узел ГТХ и узел мультижидкости");
        }

        return Validation.success(new StructureInfo(bounds.min(), bounds.max(), size,
            casing, sheaths, heatNodes, fluidNodes, universalNodes, valves,
            graphite, boron, barium, water,
            heatPorts.stream().mapToLong(Long::longValue).toArray(),
            waterPorts.stream().mapToLong(Long::longValue).toArray(),
            valvePorts.stream().mapToLong(Long::longValue).toArray()));
    }

    /**
     * Клапан валиден только на ГРАНИ (не ребре и не угле) обшивки, и его
     * face/back должен быть перпендикулярен этой грани — то есть смотреть
     * внутрь активной зоны или наружу из реактора.
     */
    private static boolean valveFacesZone(net.minecraft.world.level.block.state.BlockState state,
                                          BlockPos pos, BlockPos min, BlockPos max) {
        int boundaryAxes = 0;
        net.minecraft.core.Direction.Axis boundaryAxis = null;
        for (net.minecraft.core.Direction.Axis axis : net.minecraft.core.Direction.Axis.values()) {
            int value = axis.choose(pos.getX(), pos.getY(), pos.getZ());
            int low = axis.choose(min.getX(), min.getY(), min.getZ());
            int high = axis.choose(max.getX(), max.getY(), max.getZ());
            if (value == low || value == high) {
                boundaryAxes++;
                boundaryAxis = axis;
            }
        }
        return boundaryAxes == 1
            && state.getValue(com.gonzotech.core.block.ReactorValveBlock.FACING).getAxis() == boundaryAxis;
    }

    private static BlockPos findPanelPosition(ServerLevel level, Bounds bounds, BlockPos ignoredPos) {
        for (int x = bounds.min().getX(); x <= bounds.max().getX(); x++) {
            for (int y = bounds.min().getY(); y <= bounds.max().getY(); y++) {
                for (int z = bounds.min().getZ(); z <= bounds.max().getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!pos.equals(ignoredPos) && onBoundary(pos, bounds.min(), bounds.max())
                        && level.getBlockState(pos).getBlock() instanceof ReactorControlPanelBlock) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private static boolean isShellPart(BlockState state) {
        Block block = state.getBlock();
        return block instanceof ReactorCasingBlock
            || block instanceof ReactorControlPanelBlock
            || block == ModMachines.THIRD_HEAT_NODE.get()
            || block == ModMachines.THIRD_UNIVERSAL_FLUID_NODE.get()
            || block == ModMachines.THIRD_UNIVERSAL_NODE.get()
            || block instanceof com.gonzotech.core.block.ReactorValveBlock;
    }

    private static boolean isAllowedInterior(Block block) {
        return block == ModMachines.THIRD_URANIUM_REACTOR_FUEL_SHEATH.get()
            || block == ModBlocks.GRAPHITE_BLOCK.get()
            || isMetalBlock(block, "boron_block")
            || isMetalBlock(block, "barium_block")
            || block == Blocks.WATER;
    }

    private static boolean isMetalBlock(Block block, String path) {
        var key = BuiltInRegistries.BLOCK.getKey(block);
        return key != null && "gonzotech".equals(key.getNamespace()) && path.equals(key.getPath());
    }

    private static BlockState stateAt(ServerLevel level, BlockPos pos, BlockPos ignoredPos) {
        return pos.equals(ignoredPos) ? Blocks.AIR.defaultBlockState() : level.getBlockState(pos);
    }

    private static boolean onBoundary(BlockPos pos, BlockPos min, BlockPos max) {
        return pos.getX() == min.getX() || pos.getX() == max.getX()
            || pos.getY() == min.getY() || pos.getY() == max.getY()
            || pos.getZ() == min.getZ() || pos.getZ() == max.getZ();
    }

    private static Bounds boundsOf(List<BlockPos> positions) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : positions) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new Bounds(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
    }

    private static boolean chunksLoaded(ServerLevel level, BlockPos min, BlockPos max) {
        int minChunkX = min.getX() >> 4;
        int maxChunkX = max.getX() >> 4;
        int minChunkZ = min.getZ() >> 4;
        int maxChunkZ = max.getZ() >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                BlockPos check = new BlockPos((chunkX << 4) + 8, min.getY(), (chunkZ << 4) + 8);
                if (!level.hasChunkAt(check)) return false;
            }
        }
        return true;
    }

    private static void applyFormedVisuals(ServerLevel level,
                                           ReactorControlPanelBlockEntity panel,
                                           StructureInfo info) {
        int storedFuel = ReactorFuelStorage.bindStructure(
            level, info.min(), info.max(), panel.getBlockPos(), panel.copyLegacyFuelItems());
        if (info.sheaths() > 0 && storedFuel >= 0) panel.clearLegacyFuelItems();
        long[] heatPorts = info.heatPorts();
        long[] waterPorts = info.waterPorts();
        panel.configureStructure(info.min(), info.max(), info.sheaths(), Math.max(0, storedFuel),
            heatPorts, waterPorts, info.valvePorts());
        indexStructure(level, panel.getBlockPos(), info.min(), info.max());
        List<BlockPos> ports = new ArrayList<>(heatPorts.length + waterPorts.length);
        for (long port : heatPorts) ports.add(BlockPos.of(port));
        for (long port : waterPorts) ports.add(BlockPos.of(port));
        NodeClumpIndex.detachPorts(level, ports);
        for (int x = info.min().getX(); x <= info.max().getX(); x++) {
            for (int y = info.min().getY(); y <= info.max().getY(); y++) {
                for (int z = info.min().getZ(); z <= info.max().getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!onBoundary(pos, info.min(), info.max())) continue;
                    BlockState state = level.getBlockState(pos);
                    if (state.getBlock() instanceof ReactorCasingBlock
                        && !state.getValue(ReactorCasingBlock.FORMED)) {
                        level.setBlock(pos, state.setValue(ReactorCasingBlock.FORMED, true), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    private record Bounds(BlockPos min, BlockPos max) {
    }

    private record ComponentResult(List<BlockPos> positions, boolean tooLarge, boolean incomplete) {
    }

    private record StructureInfo(BlockPos min, BlockPos max, int size,
                                 int casingBlocks, int sheaths,
                                 int heatNodes, int fluidNodes, int universalNodes,
                                 int valves,
                                 int graphite, int boron, int barium, int water,
                                 long[] heatPorts, long[] waterPorts, long[] valvePorts) {
        private StructureInfo {
            heatPorts = heatPorts.clone();
            waterPorts = waterPorts.clone();
            valvePorts = valvePorts.clone();
        }

        public long[] heatPorts() {
            return heatPorts.clone();
        }

        public long[] waterPorts() {
            return waterPorts.clone();
        }

        public long[] valvePorts() {
            return valvePorts.clone();
        }
    }

    private record Validation(StructureInfo structure, String failure, boolean incomplete) {
        static Validation success(StructureInfo structure) {
            return new Validation(structure, null, false);
        }

        static Validation failure(String reason) {
            return new Validation(null, reason, false);
        }

        static Validation incompleteResult() {
            return new Validation(null, null, true);
        }
    }
}
