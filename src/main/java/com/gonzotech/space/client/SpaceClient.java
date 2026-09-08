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
        // Fog-множители низкие: при END-небе яркий туман биома даёт светящуюся
        // полосу у горизонта, которая не сливается с чёрным. Пока (до настоящих
        // скайбоксов в Части 2) держим дымку почти чёрной — горизонт сливается
        // с небом. Луна — вакуум без атмосферы; Марс/Европа чуть заметнее.
        event.register(SpaceDimensions.MOON_SKY, new SpaceSkyEffects(0.05F));
        event.register(SpaceDimensions.MARS_SKY, new SpaceSkyEffects(0.18F));
        event.register(SpaceDimensions.EUROPA_SKY, new SpaceSkyEffects(0.08F));
    }
}
