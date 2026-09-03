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
        event.register(ModMenus.ELECTRIC_FURNACE.get(), ElectricFurnaceScreen::new);
    }
}
