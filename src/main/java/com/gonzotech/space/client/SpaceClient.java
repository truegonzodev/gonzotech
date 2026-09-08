package com.gonzotech.space.client;

import com.gonzotech.space.SpaceDimensions;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;

/**
 * Клиентская привязка скайбоксов космических измерений.
 *
 * <p>Вызывается из {@code GonzoTechMod} только на физическом клиенте
 * (см. {@code FMLEnvironment.dist.isClient()}), как и остальные клиентские
 * подписки мода. Каждый id ({@code gonzotech:*_sky}) должен совпадать с полем
 * {@code "effects"} соответствующего {@code dimension_type}.
 */
public final class SpaceClient {

    private SpaceClient() {
    }

    public static void onRegisterDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        // Луна — почти чёрный вакуум, лёгкая серая дымка у поверхности.
        event.register(SpaceDimensions.MOON_SKY, new SpaceSkyEffects(0.35F));
        // Марс — рыжеватая пылевая дымка, чуть плотнее.
        event.register(SpaceDimensions.MARS_SKY, new SpaceSkyEffects(0.6F));
        // Европа — холодный синеватый горизонт над ледяным панцирем.
        event.register(SpaceDimensions.EUROPA_SKY, new SpaceSkyEffects(0.5F));
    }
}
