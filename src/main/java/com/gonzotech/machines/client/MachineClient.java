package com.gonzotech.machines.client;

import com.gonzotech.machines.registry.ModMenus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Клиентская привязка меню машин к их экранам.
 * Вызывается из главного класса мода на клиентском шаге.
 */
public final class MachineClient {

    private MachineClient() {
    }

    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.FIREBOX.get(), FireboxScreen::new);
        event.register(ModMenus.BOILER.get(), BoilerScreen::new);
        event.register(ModMenus.STIRLING.get(), StirlingScreen::new);
        event.register(ModMenus.TURBINE.get(), TurbineScreen::new);
        event.register(ModMenus.ELECTRIC_FURNACE.get(), ElectricFurnaceScreen::new);
        event.register(ModMenus.PUMP.get(), PumpScreen::new);
        event.register(ModMenus.ACCUMULATOR.get(), AccumulatorScreen::new);
        event.register(ModMenus.COBBLE_GENERATOR.get(), CobbleGeneratorScreen::new);
        event.register(ModMenus.SECOND_ELECTRIC_FURNACE.get(), SecondElectricFurnaceScreen::new);
        event.register(ModMenus.SECOND_PUMP.get(), SecondPumpScreen::new);
        event.register(ModMenus.SECOND_ACCUMULATOR.get(), SecondAccumulatorScreen::new);
        event.register(ModMenus.SECOND_COBBLE_GENERATOR.get(), SecondCobbleGeneratorScreen::new);
        event.register(ModMenus.CRUSHER.get(), CrusherScreen::new);
        event.register(ModMenus.CENTRIFUGE.get(), CentrifugeScreen::new);
        event.register(ModMenus.ITEM_FILTER.get(), ItemFilterScreen::new);
    }
}
