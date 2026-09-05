package com.gonzotech.machines.client;

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

    // Кэш последнего ответа сервера.
    private static BlockPos flowPos;
    private static int flowTypeId = -1;
    private static int flowAxis;
    private static int flowPos3d;
    private static int flowNeg3d;
    private static long flowClientTick;

    // Троттлинг запросов.
    private static long lastRequestTick = Long.MIN_VALUE;
    private static long lastRequestPosKey = Long.MIN_VALUE;
    private static int lastRequestType = -1;
    private static long clientTick;

    public static void acceptFlow(PipeFlowNetwork.FlowPayload payload) {
        flowPos = payload.pos();
        flowTypeId = payload.typeId();
        flowAxis = payload.axis3d();
        flowPos3d = payload.posAmount();
        flowNeg3d = payload.negAmount();
        flowClientTick = clientTick;
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
        maybeRequestFlow(pos, part);

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

        // Строка 2: цветное имя ресурса + «поток: N» тем же цветом (сумма по концам,
        // без «влево/вправо» — глазами бежишь по трубам и видишь, куда течёт).
        Component flow = flowLine(pos, part);
        if (flow != null) {
            g.drawString(font, flow, (screenW - font.width(flow)) / 2, y + font.lineHeight + 1, 0xFFFFFF, true);
        }
    }

    /** Тип трубы пучка/одиночной трубы, в которую смотрит игрок, или {@code null}. */
    private static PipeType aimedPart(BlockState state, BlockPos pos, BlockHitResult hit) {
        if (state.getBlock() instanceof PipeBlock pipe) {
            return pipe.pipeType();
        }
        if (state.getBlock() instanceof CompositePipeBlock) {
            List<PipeType> present = new ArrayList<>();
            for (PipeType t : PipeType.values()) {
                if (state.getValue(CompositePipeBlock.PRESENT.get(t))) present.add(t);
            }
            if (present.isEmpty()) return null;
            Direction.Axis axis = state.getValue(RotatedPillarBlock.AXIS);
            return PipeGeometry.partAt(axis, pos, hit.getLocation(), present);
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
        return Component.translatable("block.gonzotech." + part.id());
    }

    private static PipeMode modeOf(BlockState state, PipeType part) {
        if (state.getBlock() instanceof PipeBlock) {
            return state.getValue(PipeBlock.MODE);
        }
        if (state.getBlock() instanceof CompositePipeBlock) {
            return state.getValue(CompositePipeBlock.MODE.get(part));
        }
        return PipeMode.AUTO;
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
        if (flowPos == null || !flowPos.equals(pos) || flowTypeId != part.ordinal()) return null;
        if (clientTick - flowClientTick > FLOW_STALE_TICKS) return null;
        int total = flowPos3d + flowNeg3d;
        if (total <= 0) return null;

        int color = part.color();
        Component name = Component.translatable("resource.gonzotech." + part.id())
            .setStyle(Style.EMPTY.withColor(color));
        Component sep = Component.literal(" | ").setStyle(Style.EMPTY.withColor(0xA0A0A0));
        Component unit = Component.translatable("resource.gonzotech." + part.id() + ".unit");
        Component amount = Component.translatable("hud.gonzotech.flow_amount", total, unit)
            .setStyle(Style.EMPTY.withColor(color));
        return Component.empty().append(name).append(sep).append(amount);
    }
}
