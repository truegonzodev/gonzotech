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
        event.register(ModMenus.SECOND_NUCLEAR_FIREBOX.get(), NuclearFireboxScreen::new);
        event.register(ModMenus.BOILER.get(), BoilerScreen::new);
        event.register(ModMenus.STIRLING.get(), StirlingScreen::new);
        event.register(ModMenus.TURBINE.get(), TurbineScreen::new);
        event.register(ModMenus.STEAMGEN.get(), SteamGenScreen::new);
        event.register(ModMenus.FIRST_ELECTRIC_FURNACE.get(), ElectricFurnaceScreen::new);
        event.register(ModMenus.FIRST_PUMP.get(), PumpScreen::new);
        event.register(ModMenus.FIRST_ACCUMULATOR.get(), AccumulatorScreen::new);
        event.register(ModMenus.FIRST_COBBLE_GENERATOR.get(), CobbleGeneratorScreen::new);
        event.register(ModMenus.SECOND_ELECTRIC_FURNACE.get(), SecondElectricFurnaceScreen::new);
        event.register(ModMenus.SECOND_PUMP.get(), SecondPumpScreen::new);
        event.register(ModMenus.SECOND_ACCUMULATOR.get(), SecondAccumulatorScreen::new);
        event.register(ModMenus.SECOND_COBBLE_GENERATOR.get(), SecondCobbleGeneratorScreen::new);
        event.register(ModMenus.ALLOY_FOUNDRY.get(), AlloyFoundryScreen::new);
        event.register(ModMenus.SECOND_GRINDER.get(), SecondGrinderScreen::new);
        event.register(ModMenus.SECOND_PRESS.get(), SecondPressScreen::new);
        event.register(ModMenus.SECOND_CRUSHER.get(), CrusherScreen::new);
        event.register(ModMenus.SECOND_CENTRIFUGE.get(), CentrifugeScreen::new);
        event.register(ModMenus.FIRST_ITEM_FILTER.get(), ItemFilterScreen::new);
    }
}
