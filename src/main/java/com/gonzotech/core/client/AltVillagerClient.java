package com.gonzotech.core.client;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Клиентская регистрация сущностей мода. Вызывается с mod-шины в клиентском
 * блоке конструктора мода (как {@code MachineClient::onRegisterScreens}).
 */
public final class AltVillagerClient {

    private AltVillagerClient() {
    }

    /** Рендерер «альт-жителя». */
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(com.gonzotech.core.registry.ModEntities.ALT.get(), AltVillagerRenderer::new);
    }
}
