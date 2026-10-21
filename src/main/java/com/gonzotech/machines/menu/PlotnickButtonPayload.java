package com.gonzotech.machines.menu;

import com.gonzotech.GonzoTechMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S «нажата кнопка GUI Плотника» (0.3.256): ванильный
 * {@code ServerboundContainerButtonClickPacket} не доезжает до модовых меню,
 * поэтому кнопки идут отдельным пакетом.
 *
 * @param menuId     id открытого меню
 * @param buttonId   0..5 по макету автора
 */
public record PlotnickButtonPayload(int menuId, int buttonId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PlotnickButtonPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "plotnick_button"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlotnickButtonPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PlotnickButtonPayload::menuId,
            ByteBufCodecs.VAR_INT, PlotnickButtonPayload::buttonId,
            PlotnickButtonPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PlotnickButtonPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            AbstractContainerMenu menu = player.containerMenu;
            if (menu instanceof PlotnickMenu plotnickMenu
                && plotnickMenu.containerId == payload.menuId()
                && menu.stillValid(player)) {
                plotnickMenu.handleButtonClick(payload.buttonId(), player);
            }
        });
    }
}
