package com.gonzotech.machines.client;

import com.gonzotech.machines.energy.GtFormat;
import com.gonzotech.machines.item.WrenchItem;
import com.gonzotech.machines.network.CompositePipeBlock;
import com.gonzotech.machines.network.PipeBlock;
import com.gonzotech.machines.network.PipeFlowNetwork;
import com.gonzotech.machines.network.PipeGeometry;
import com.gonzotech.machines.network.PipeMode;
import com.gonzotech.machines.network.PipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Клиентская подсказка гаечного ключа. Когда игрок держит {@link WrenchItem} и
 * смотрит на КОНКРЕТНУЮ трубу пучка, поверх HUD показывается её тип, режим и
 * живой поток по двум концам оси. Это ЕДИНСТВЕННЫЙ вывод ключа — сообщений в
 * action-bar/чат при прокрутке режима нет (дублировало бы этот HUD).
 * <p>
 * Для блока-узла заголовок — имя самого узла ("Wire Node"), а не тип трубы:
 * узел концептуально не «отрезок трубы», а точка у механизма «забрать всё /
 * отдать всё / авто». Посреди цепи режим (у трубы и у узла) ни на что не влияет —
 * он значим только на стыке с механизмом.
 * <p>
 * Труба, в которую целится игрок, определяется по точке наведения через
 * {@link PipeGeometry#partAt} — так в связке нескольких типов ключ и HUD знают,
 * о какой именно трубе речь. Поток считает сервер ({@code FlowTracker}); клиент
 * раз в несколько тиков шлёт адресный запрос (позиция + тип).
 */
public final class WrenchHud {

    private WrenchHud() {
    }

    private static final int REQUEST_INTERVAL = 5;
    private static final int FLOW_STALE_TICKS = 15;

    /** Один закэшированный ответ сервера о потоке (по типу трубы). */
    private static final class FlowEntry {
        int axis;
        long pos3d;
        long neg3d;
        long clientTick;
    }

    // Кэш последних ответов сервера, по typeId. У универсальной трубы приходят два
    // (вода + пар) — потому Map, а не одно поле.
    private static BlockPos flowPos;
    private static final java.util.Map<Integer, FlowEntry> flowByType = new java.util.HashMap<>();

    // Троттлинг запросов (последний запрошенный тип; для нескольких типов сбрасываем
    // троттлинг между ними — они шлются подряд в одном тике).
    private static long lastRequestTick = Long.MIN_VALUE;
    private static long lastRequestPosKey = Long.MIN_VALUE;
    private static int lastRequestType = -1;
    private static long clientTick;

    // Кэш последнего ответа о потоке ПРЕДМЕТОВ (для предметной трубы).
    private static BlockPos itemFlowPos;
    private static java.util.List<net.minecraft.network.chat.Component> itemFlowLinesCache = java.util.List.of();
    private static long itemFlowClientTick = Long.MIN_VALUE;
    private static long lastItemRequestTick = Long.MIN_VALUE;
    private static long lastItemRequestPosKey = Long.MIN_VALUE;

    public static void acceptItemFlow(PipeFlowNetwork.ItemFlowPayload payload) {
        itemFlowPos = payload.pos();
        itemFlowClientTick = clientTick;
        java.util.List<net.minecraft.network.chat.Component> lines = new ArrayList<>();
        for (int i = 0; i < payload.items().size() && i < payload.counts().size(); i++) {
            net.minecraft.world.item.Item item =
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(payload.items().get(i));
            Component name = item.getName(new net.minecraft.world.item.ItemStack(item));
            // «Булыжник — 16/т»: имя предмета + количество, цветом предметной трубы.
            Component fl = Component.translatable(
                "hud.gonzotech.item_flow_line", name,
                Component.literal(Integer.toString(payload.counts().get(i))));
            lines.add(fl.copy().setStyle(Style.EMPTY.withColor(PipeType.ITEM.color())));
        }
        itemFlowLinesCache = lines;
    }

    public static void acceptFlow(PipeFlowNetwork.FlowPayload payload) {
        // Ответы разных типов приходят по одной позиции; при смене позиции чистим кэш.
        if (flowPos == null || !flowPos.equals(payload.pos())) {
            flowByType.clear();
            flowPos = payload.pos();
        }
        FlowEntry e = flowByType.computeIfAbsent(payload.typeId(), k -> new FlowEntry());
        e.axis = payload.axis3d();
        e.pos3d = payload.posAmount();
        e.neg3d = payload.negAmount();
        e.clientTick = clientTick;
    }

    @SubscribeEvent
    public static void onRenderGui(final RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;
        if (mc.options.hideGui) return;

        if (!(player.getMainHandItem().getItem() instanceof WrenchItem)
            && !(player.getOffhandItem().getItem() instanceof WrenchItem)) {
            return;
        }

        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;
        BlockHitResult bhit = (BlockHitResult) hit;
        BlockPos pos = bhit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);

        // Какую трубу пучка мы держим на прицеле?
        PipeType part = aimedPart(state, pos, bhit);
        if (part == null) return;

        clientTick = mc.level.getGameTime();

        PipeMode mode = modeOf(state, part);
        // Строка 1: имя трубы/узла + режим (напр. «Труба — режим: авто»).
        Component line = Component.translatable(
            "hud.gonzotech.wrench_pipe",
            partLabel(state, part),
            Component.translatable("message.gonzotech.pipe_mode_short." + mode.getSerializedName()));

        GuiGraphics g = event.getGuiGraphics();
        Font font = mc.font;
        int screenW = g.guiWidth();
        int screenH = g.guiHeight();
        int y = screenH / 2 - 30;
        g.drawString(font, line, (screenW - font.width(line)) / 2, y, 0xFFFFFF, true);

        int lineY = y + font.lineHeight + 1;

        // Предметная труба: показываем поток ПО ПРЕДМЕТАМ («Булыжник — 16/т»).
        if (part == PipeType.ITEM) {
            maybeRequestItemFlow(pos);
            for (Component fl : itemFlowLines(pos)) {
                g.drawString(font, fl, (screenW - font.width(fl)) / 2, lineY, 0xFFFFFF, true);
                lineY += font.lineHeight + 1;
            }
            return;
        }

        // Универсальная жидкостная труба несёт несколько ресурсов (вода+пар) — ключ
        // показывает ВСЕ компоненты потока. Для остальных — только наведённый тип.
        List<PipeType> parts = carriedParts(state, part);
        for (PipeType t : parts) maybeRequestFlow(pos, t);

        // Ниже — по строке на каждый компонент потока: цветное имя ресурса +
        // «поток: N» тем же цветом (сумма по концам, без «влево/вправо»).
        for (PipeType t : parts) {
            Component flow = flowLine(pos, t);
            if (flow != null) {
                g.drawString(font, flow, (screenW - font.width(flow)) / 2, lineY, 0xFFFFFF, true);
                lineY += font.lineHeight + 1;
            }
        }
    }

    /**
     * Список ресурсов, которые труба реально несёт в наведённой части. Для обычной
     * трубы/части связки — сам наведённый тип; для универсальной жидкостной трубы —
     * все жидкостные типы (вода + пар), которые она переносит.
     */
    private static List<PipeType> carriedParts(BlockState state, PipeType aimed) {
        // Универсальный узел несёт ВСЕ типы первого тира — показываем все.
        if (state.getBlock() instanceof com.gonzotech.machines.network.UniversalNodeBlock) {
            return List.of(PipeType.values());
        }
        boolean universalSingle =
            state.getBlock() instanceof com.gonzotech.machines.network.UniversalFluidPipeBlock;
        // В пучке универсальная труба = оба жидк.флага; показываем её, только когда
        // наведён именно жидкостный угол.
        boolean universalInBundle = aimed.isFluid()
            && com.gonzotech.machines.network.CompositePipeBlock.carriesUniversalFluid(state);
        if (universalSingle || universalInBundle) {
            List<PipeType> list = new ArrayList<>();
            for (PipeType t : PipeType.values()) {
                if (t.isFluid()) list.add(t);
            }
            return list;
        }
        return List.of(aimed);
    }

    /** Тип трубы пучка/одиночной трубы, в которую смотрит игрок, или {@code null}. */
    private static PipeType aimedPart(BlockState state, BlockPos pos, BlockHitResult hit) {
        // Универсальный узел несёт все типы — якорим на WIRE (реальный список
        // компонентов выдаёт carriedParts).
        if (state.getBlock() instanceof com.gonzotech.machines.network.UniversalNodeBlock) {
            return PipeType.WIRE;
        }
        if (state.getBlock() instanceof PipeBlock pipe) {
            return pipe.pipeType();
        }
        if (state.getBlock() instanceof CompositePipeBlock) {
            List<PipeType> present = new ArrayList<>();
            for (PipeType t : PipeType.values()) {
                if (state.getValue(CompositePipeBlock.PRESENT.get(t))) present.add(t);
            }
            if (present.isEmpty()) return null;
            return PipeGeometry.partAt(
                t -> CompositePipeBlock.axisOf(state, t), pos, hit.getLocation(), present);
        }
        return null;
    }

    /**
     * Заголовок строки HUD. Для узла ({@link PipeBlock#connectsAllSides()}) —
     * собственное имя блока ("Wire Node"), потому что узел концептуально не
     * «труба», а точка «забрать всё / отдать всё / авто» у механизма. Для трубы
     * и части пучка — имя типа трубы ("Wire" / "Heat Pipe").
     */
    private static Component partLabel(BlockState state, PipeType part) {
        if (state.getBlock() instanceof PipeBlock pipe && pipe.connectsAllSides()) {
            return state.getBlock().getName();
        }
        // Универсальный узел — собственное имя блока (несёт все типы).
        if (state.getBlock() instanceof com.gonzotech.machines.network.UniversalNodeBlock) {
            return state.getBlock().getName();
        }
        // Универсальная труба несёт несколько ресурсов — заголовком её собственное
        // имя блока, а не имя одного типа. Это же имя показываем для FLUID-угла
        // пучка, занятого универсальной трубой.
        if (state.getBlock() instanceof com.gonzotech.machines.network.UniversalFluidPipeBlock) {
            return state.getBlock().getName();
        }
        if (part.isFluid()
            && com.gonzotech.machines.network.CompositePipeBlock.carriesUniversalFluid(state)) {
            return Component.translatable("block.gonzotech.first_universal_fluid_pipe");
        }
        // Одиночная труба второго открытия должна показывать своё имя, хотя её
        // ресурс и сетевой лимит пока совпадают с первым уровнем.
        if (state.getBlock() instanceof PipeBlock) {
            return state.getBlock().getName();
        }
        return Component.translatable("block.gonzotech." + part.id());
    }

    private static PipeMode modeOf(BlockState state, PipeType part) {
        if (state.getBlock() instanceof PipeBlock) {
            return state.getValue(PipeBlock.MODE);
        }
        if (state.getBlock() instanceof CompositePipeBlock) {
            return state.getValue(CompositePipeBlock.MODE.get(part));
        }
        if (state.getBlock() instanceof com.gonzotech.machines.network.UniversalNodeBlock) {
            return state.getValue(com.gonzotech.machines.network.UniversalNodeBlock.MODE);
        }
        return PipeMode.AUTO;
    }

    private static void maybeRequestItemFlow(BlockPos pos) {
        long key = pos.asLong();
        if (key != lastItemRequestPosKey || clientTick - lastItemRequestTick >= REQUEST_INTERVAL) {
            lastItemRequestPosKey = key;
            lastItemRequestTick = clientTick;
            PacketDistributor.sendToServer(new PipeFlowNetwork.ItemFlowRequestPayload(pos));
        }
    }

    /** Строки потока предметов (кэш от сервера), пусто если данных нет/устарели. */
    private static List<Component> itemFlowLines(BlockPos pos) {
        if (itemFlowPos == null || !itemFlowPos.equals(pos)) return List.of();
        if (clientTick - itemFlowClientTick > FLOW_STALE_TICKS) return List.of();
        return itemFlowLinesCache;
    }

    private static void maybeRequestFlow(BlockPos pos, PipeType part) {
        long key = pos.asLong();
        int tid = part.ordinal();
        if (key != lastRequestPosKey || tid != lastRequestType || clientTick - lastRequestTick >= REQUEST_INTERVAL) {
            lastRequestPosKey = key;
            lastRequestType = tid;
            lastRequestTick = clientTick;
            PacketDistributor.sendToServer(new PipeFlowNetwork.RequestPayload(pos, tid));
        }
    }

    /**
     * Вторая строка HUD: «&lt;цвет&gt;Ресурс &7| &lt;цвет&gt;поток: N». Ресурс и число —
     * ЦВЕТОМ ресурса ({@link PipeType#color()}), разделитель серый. Поток —
     * СУММА по всем концам/граням (без «влево/вправо»): и для трубы (два конца
     * оси сложены), и для узла (сумма 6 граней). Так удобно взглядом бежать по
     * трубам и видеть, где сколько течёт, не думая про направление.
     */
    private static Component flowLine(BlockPos pos, PipeType part) {
        if (flowPos == null || !flowPos.equals(pos)) return null;
        FlowEntry e = flowByType.get(part.ordinal());
        if (e == null) return null;
        if (clientTick - e.clientTick > FLOW_STALE_TICKS) return null;
        long total = e.pos3d + e.neg3d;
        if (total <= 0) return null;

        int color = part.color();
        Component name = Component.translatable("resource.gonzotech." + part.id())
            .setStyle(Style.EMPTY.withColor(color));
        Component sep = Component.literal(" | ").setStyle(Style.EMPTY.withColor(0xA0A0A0));
        Component unit = Component.translatable("resource.gonzotech." + part.id() + ".unit");
        // GTU/GTH идут по проводам в МИЛЛИ (×1000) — показываем целые единицы с
        // суффиксом больших тиров и одной десятой (12.3M). Вода/пар в mB — как есть.
        Component value = part.isFluid()
            ? Component.literal(Long.toString(total))
            : Component.literal(GtFormat.formatRate(total));
        Component amount = Component.translatable("hud.gonzotech.flow_amount", value, unit)
            .setStyle(Style.EMPTY.withColor(color));
        return Component.empty().append(name).append(sep).append(amount);
    }
}
