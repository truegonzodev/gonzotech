package com.gonzotech.machines.registry;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.machines.menu.BoilerMenu;
import com.gonzotech.machines.menu.ElectricFurnaceMenu;
import com.gonzotech.machines.menu.FireboxMenu;
import com.gonzotech.machines.menu.StirlingMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Реестр MenuType'ов паровой ветки. Используем {@code IMenuTypeExtension.create},
 * чтобы клиент получал позицию блока через буфер и восстанавливал BE/ContainerData.
 */
public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, GonzoTechMod.MOD_ID);

    public static final Supplier<MenuType<FireboxMenu>> FIREBOX =
        MENUS.register("firebox", () -> IMenuTypeExtension.create(FireboxMenu::new));

    public static final Supplier<MenuType<BoilerMenu>> BOILER =
        MENUS.register("boiler", () -> IMenuTypeExtension.create(BoilerMenu::new));

    public static final Supplier<MenuType<StirlingMenu>> STIRLING =
        MENUS.register("stirling", () -> IMenuTypeExtension.create(StirlingMenu::new));

    public static final Supplier<MenuType<ElectricFurnaceMenu>> ELECTRIC_FURNACE =
        MENUS.register("electric_furnace", () -> IMenuTypeExtension.create(ElectricFurnaceMenu::new));

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }

    private ModMenus() {
    }
}
