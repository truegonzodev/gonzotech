package com.gonzotech.machines.client;

import com.gonzotech.machines.item.WrenchItem;
import com.gonzotech.machines.network.PipeBlock;
import com.gonzotech.machines.network.PipeMode;
import com.gonzotech.machines.network.PipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Клиентская подсказка гаечного ключа. Когда игрок держит {@link WrenchItem} и
 * смотрит на трубу, поверх HUD (по центру, чуть выше прицела) показывается её
 * тип и режим — чтобы видеть stance провода до переключения.
 * <p>
 * Пример строки: {@code Wire — mode: auto}. Числовой поток (GTU/t) провод не
 * хранит (он пассивный, без BlockEntity), поэтому здесь показываем тип+режим;
 * live-поток появится, когда/если у труб заведём отдельный учёт.
 */
public final class WrenchHud {

    private WrenchHud() {
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
        BlockState state = mc.level.getBlockState(((BlockHitResult) hit).getBlockPos());
        if (!(state.getBlock() instanceof PipeBlock pipe)) return;

        PipeMode mode = state.getValue(PipeBlock.MODE);
        Component line = Component.translatable(
            "hud.gonzotech.wrench_pipe",
            Component.translatable("block.gonzotech." + pipe.pipeType().id()),
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
    }

    private static String resourceLabel(PipeType type) {
        return switch (type) {
            case WIRE -> "GTU";
            case HEAT -> "GTH";
        };
    }
}
