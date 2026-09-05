package com.gonzotech.machines.client;

import com.gonzotech.machines.item.WrenchItem;
import com.gonzotech.machines.network.PipeBlock;
import com.gonzotech.machines.network.PipeFlowNetwork;
import com.gonzotech.machines.network.PipeMode;
import com.gonzotech.machines.network.PipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Клиентская подсказка гаечного ключа. Когда игрок держит {@link WrenchItem} и
 * смотрит на трубу, поверх HUD (по центру, чуть выше прицела) показывается её
 * тип, режим и ЖИВОЙ поток по двум концам оси.
 * <p>
 * Пример: {@code Wire — mode: auto (GTU)} и ниже {@code E: 60  W: 30} — сколько
 * ресурса реально прошло через этот провод за тик в каждую сторону оси (по
 * мировым направлениям, не «лево/право»: показания не зависят от того, откуда
 * смотрит игрок).
 * <p>
 * Провод пассивен и потока не хранит — фактический объём считает сервер
 * ({@code FlowTracker}). Пока игрок смотрит на трубу с ключом, клиент раз в
 * несколько тиков шлёт запрос ({@link PipeFlowNetwork}); ответ кэшируется здесь и
 * рисуется. Если поток прекратился, сервер вернёт нули и строка потока исчезнет.
 */
public final class WrenchHud {

    private WrenchHud() {
    }

    /** Как часто (тиков клиента) опрашивать поток у трубы под прицелом. */
    private static final int REQUEST_INTERVAL = 5;
    /** После скольких тиков без обновления считаем кэш потока устаревшим. */
    private static final int FLOW_STALE_TICKS = 15;

    // Кэш последнего ответа сервера (клиентский поток).
    private static BlockPos flowPos;
    private static int flowAxis;
    private static int flowPos3d;
    private static int flowNeg3d;
    private static long flowClientTick;

    // Троттлинг запросов.
    private static long lastRequestTick = Long.MIN_VALUE;
    private static long lastRequestPosKey = Long.MIN_VALUE;
    private static long clientTick;

    /** Приём ответа сервера (в игровом потоке клиента). */
    public static void acceptFlow(PipeFlowNetwork.FlowPayload payload) {
        flowPos = payload.pos();
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

        // Ключ в любой руке.
        if (!(player.getMainHandItem().getItem() instanceof WrenchItem)
            && !(player.getOffhandItem().getItem() instanceof WrenchItem)) {
            return;
        }

        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        if (!(state.getBlock() instanceof PipeBlock pipe)) return;

        // Троттлированный опрос потока у трубы под прицелом.
        clientTick = mc.level.getGameTime();
        maybeRequestFlow(pos);

        PipeMode mode = state.getValue(PipeBlock.MODE);
        Component line = Component.translatable(
            "hud.gonzotech.wrench_pipe",
            pipe.getName(),
            Component.translatable("message.gonzotech.pipe_mode_short." + mode.getSerializedName()),
            resourceLabel(pipe.pipeType()));

        GuiGraphics g = event.getGuiGraphics();
        Font font = mc.font;
        int screenW = g.guiWidth();
        int screenH = g.guiHeight();
        int textW = font.width(line);
        int x = (screenW - textW) / 2;
        int y = screenH / 2 - 30; // чуть выше прицела
        g.drawString(font, line, x, y, 0xFFFFFF, true);

        // Строка живого потока по концам оси (если поток свежий для ЭТОЙ трубы).
        Component flow = flowLine(pos);
        if (flow != null) {
            int fw = font.width(flow);
            g.drawString(font, flow, (screenW - fw) / 2, y + font.lineHeight + 1, 0xA0FFA0, true);
        }
    }

    private static void maybeRequestFlow(BlockPos pos) {
        long key = pos.asLong();
        if (key != lastRequestPosKey || clientTick - lastRequestTick >= REQUEST_INTERVAL) {
            lastRequestPosKey = key;
            lastRequestTick = clientTick;
            PacketDistributor.sendToServer(new PipeFlowNetwork.RequestPayload(pos));
        }
    }

    /**
     * Строка «E: 60  W: 30» по мировым концам оси провода, или {@code null}, если
     * актуальных данных для этой трубы нет / поток нулевой.
     */
    private static Component flowLine(BlockPos pos) {
        if (flowPos == null || !flowPos.equals(pos)) return null;
        if (clientTick - flowClientTick > FLOW_STALE_TICKS) return null;
        if (flowPos3d <= 0 && flowNeg3d <= 0) return null;

        // Узел ветвится во все стороны — показываем суммарный поток одним числом.
        if (flowAxis == PipeFlowNetwork.AXIS_NODE_SUM) {
            return Component.translatable("hud.gonzotech.wrench_node_flow", flowPos3d);
        }

        Direction.Axis axis = Direction.Axis.values()[flowAxis];
        Direction posDir = switch (axis) {
            case X -> Direction.EAST;
            case Y -> Direction.UP;
            case Z -> Direction.SOUTH;
        };
        Direction negDir = posDir.getOpposite();

        return Component.translatable(
            "hud.gonzotech.wrench_pipe_flow",
            dirAbbr(posDir), flowPos3d,
            dirAbbr(negDir), flowNeg3d);
    }

    private static Component dirAbbr(Direction dir) {
        return Component.translatable("hud.gonzotech.dir." + dir.getSerializedName());
    }

    private static String resourceLabel(PipeType type) {
        return switch (type) {
            case WIRE -> "GTU";
            case HEAT -> "GTH";
        };
    }
}
