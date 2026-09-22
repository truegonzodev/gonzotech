package com.gonzotech.machines.registry;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.machines.menu.AccumulatorMenu;
import com.gonzotech.machines.menu.AlloyFoundryMenu;
import com.gonzotech.machines.menu.BoilerMenu;
import com.gonzotech.machines.menu.CentrifugeMenu;
import com.gonzotech.machines.menu.CrusherMenu;
import com.gonzotech.machines.menu.CobbleGeneratorMenu;
import com.gonzotech.machines.menu.DistillerMenu;
import com.gonzotech.machines.menu.ElectricFurnaceMenu;
import com.gonzotech.machines.menu.FermentationVatMenu;
import com.gonzotech.machines.menu.FireboxMenu;
import com.gonzotech.machines.menu.NuclearFireboxMenu;
import com.gonzotech.machines.menu.PumpMenu;
import com.gonzotech.machines.menu.RectifierMenu;
import com.gonzotech.machines.menu.SteamGenMenu;
import com.gonzotech.machines.menu.StirlingMenu;
import com.gonzotech.machines.menu.SecondAccumulatorMenu;
import com.gonzotech.machines.menu.SecondCobbleGeneratorMenu;
import com.gonzotech.machines.menu.SecondElectricFurnaceMenu;
import com.gonzotech.machines.menu.SecondGrinderMenu;
import com.gonzotech.machines.menu.SecondPressMenu;
import com.gonzotech.machines.menu.SecondPumpMenu;
import com.gonzotech.machines.menu.TurbineMenu;
import com.gonzotech.machines.menu.WortKettleMenu;
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

    public static final Supplier<MenuType<NuclearFireboxMenu>> SECOND_NUCLEAR_FIREBOX =
        MENUS.register("second_nuclear_firebox", () -> IMenuTypeExtension.create(NuclearFireboxMenu::new));

    public static final Supplier<MenuType<BoilerMenu>> BOILER =
        MENUS.register("boiler", () -> IMenuTypeExtension.create(BoilerMenu::new));

    public static final Supplier<MenuType<StirlingMenu>> STIRLING =
        MENUS.register("stirling", () -> IMenuTypeExtension.create(StirlingMenu::new));

    public static final Supplier<MenuType<TurbineMenu>> TURBINE =
        MENUS.register("turbine", () -> IMenuTypeExtension.create(TurbineMenu::new));

    public static final Supplier<MenuType<SteamGenMenu>> STEAMGEN =
        MENUS.register("steamgen", () -> IMenuTypeExtension.create(SteamGenMenu::new));

    public static final Supplier<MenuType<ElectricFurnaceMenu>> FIRST_ELECTRIC_FURNACE =
        MENUS.register("first_electric_furnace", () -> IMenuTypeExtension.create(ElectricFurnaceMenu::new));

    public static final Supplier<MenuType<SecondElectricFurnaceMenu>> SECOND_ELECTRIC_FURNACE =
        MENUS.register("second_electric_furnace", () -> IMenuTypeExtension.create(SecondElectricFurnaceMenu::new));

    public static final Supplier<MenuType<PumpMenu>> FIRST_PUMP =
        MENUS.register("first_pump", () -> IMenuTypeExtension.create(PumpMenu::new));

    public static final Supplier<MenuType<SecondPumpMenu>> SECOND_PUMP =
        MENUS.register("second_pump", () -> IMenuTypeExtension.create(SecondPumpMenu::new));

    public static final Supplier<MenuType<AccumulatorMenu>> FIRST_ACCUMULATOR =
        MENUS.register("first_accumulator", () -> IMenuTypeExtension.create(AccumulatorMenu::new));

    public static final Supplier<MenuType<SecondAccumulatorMenu>> SECOND_ACCUMULATOR =
        MENUS.register("second_accumulator", () -> IMenuTypeExtension.create(SecondAccumulatorMenu::new));

    public static final Supplier<MenuType<CobbleGeneratorMenu>> FIRST_COBBLE_GENERATOR =
        MENUS.register("first_cobble_generator", () -> IMenuTypeExtension.create(CobbleGeneratorMenu::new));

    public static final Supplier<MenuType<SecondCobbleGeneratorMenu>> SECOND_COBBLE_GENERATOR =
        MENUS.register("second_cobble_generator", () -> IMenuTypeExtension.create(SecondCobbleGeneratorMenu::new));

    public static final Supplier<MenuType<AlloyFoundryMenu>> ALLOY_FOUNDRY =
        MENUS.register("second_alloy_foundry", () -> IMenuTypeExtension.create(AlloyFoundryMenu::new));

    public static final Supplier<MenuType<SecondGrinderMenu>> SECOND_GRINDER =
        MENUS.register("second_grinder", () -> IMenuTypeExtension.create(SecondGrinderMenu::new));

    public static final Supplier<MenuType<SecondPressMenu>> SECOND_PRESS =
        MENUS.register("second_press", () -> IMenuTypeExtension.create(SecondPressMenu::new));

    public static final Supplier<MenuType<CrusherMenu>> SECOND_CRUSHER =
        MENUS.register("second_crusher", () -> IMenuTypeExtension.create(CrusherMenu::new));

    public static final Supplier<MenuType<CentrifugeMenu>> SECOND_CENTRIFUGE =
        MENUS.register("second_centrifuge", () -> IMenuTypeExtension.create(CentrifugeMenu::new));

    public static final Supplier<MenuType<com.gonzotech.machines.menu.ItemFilterMenu>> FIRST_ITEM_FILTER =
        MENUS.register("first_item_filter", () -> IMenuTypeExtension.create(com.gonzotech.machines.menu.ItemFilterMenu::new));

    public static final Supplier<MenuType<FermentationVatMenu>> THIRD_FERMENTATION_VAT =
        MENUS.register("third_fermentation_vat", () -> IMenuTypeExtension.create(FermentationVatMenu::new));

    public static final Supplier<MenuType<WortKettleMenu>> THIRD_WORT_KETTLE =
        MENUS.register("third_wort_kettle", () -> IMenuTypeExtension.create(WortKettleMenu::new));

    public static final Supplier<MenuType<DistillerMenu>> THIRD_DISTILLER =
        MENUS.register("third_distiller", () -> IMenuTypeExtension.create(DistillerMenu::new));

    public static final Supplier<MenuType<RectifierMenu>> THIRD_RECTIFIER =
        MENUS.register("third_rectifier", () -> IMenuTypeExtension.create(RectifierMenu::new));

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }

    private ModMenus() {
    }
}
