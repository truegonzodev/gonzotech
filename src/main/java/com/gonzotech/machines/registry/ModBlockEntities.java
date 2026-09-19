package com.gonzotech.machines.registry;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.machines.block.entity.AccumulatorBlockEntity;
import com.gonzotech.machines.block.entity.AlloyFoundryBlockEntity;
import com.gonzotech.machines.block.entity.BoilerBlockEntity;
import com.gonzotech.machines.block.entity.CentrifugeBlockEntity;
import com.gonzotech.machines.block.entity.CrusherBlockEntity;
import com.gonzotech.machines.block.entity.CobbleGeneratorBlockEntity;
import com.gonzotech.machines.block.entity.CondenserBlockEntity;
import com.gonzotech.machines.block.entity.ElectricFurnaceBlockEntity;
import com.gonzotech.machines.block.entity.FireboxBlockEntity;
import com.gonzotech.machines.block.entity.NuclearFireboxBlockEntity;
import com.gonzotech.core.block.entity.TungstenAbsorberBlockEntity;
import com.gonzotech.machines.block.entity.PumpBlockEntity;
import com.gonzotech.machines.block.entity.SingularEnergySourceBlockEntity;
import com.gonzotech.machines.block.entity.SingularHeatSourceBlockEntity;
import com.gonzotech.machines.block.entity.SolarPanelBlockEntity;
import com.gonzotech.machines.block.entity.SecondAccumulatorBlockEntity;
import com.gonzotech.machines.block.entity.SecondCobbleGeneratorBlockEntity;
import com.gonzotech.machines.block.entity.SecondElectricFurnaceBlockEntity;
import com.gonzotech.machines.block.entity.SecondGrinderBlockEntity;
import com.gonzotech.machines.block.entity.SecondPressBlockEntity;
import com.gonzotech.machines.block.entity.SecondPumpBlockEntity;
import com.gonzotech.machines.block.entity.StirlingBlockEntity;
import com.gonzotech.machines.block.entity.TurbineRotorBlockEntity;
import com.gonzotech.machines.energy.SecondTierDefs;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Реестр BlockEntityType'ов паровой ветки (Фаза 2).
 */
public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, GonzoTechMod.MOD_ID);

    public static final Supplier<BlockEntityType<FireboxBlockEntity>> FIREBOX =
        BLOCK_ENTITIES.register("firebox", () -> new BlockEntityType<>(
            FireboxBlockEntity::new, false, ModMachines.FIREBOX.get()));

    public static final Supplier<BlockEntityType<NuclearFireboxBlockEntity>> NUCLEAR_FIREBOX =
        BLOCK_ENTITIES.register("nuclear_firebox", () -> new BlockEntityType<>(
            NuclearFireboxBlockEntity::new, false, ModMachines.NUCLEAR_FIREBOX.get()));

    public static final Supplier<BlockEntityType<TungstenAbsorberBlockEntity>> TUNGSTEN_ABSORBER =
        BLOCK_ENTITIES.register("tungsten_absorber", () -> new BlockEntityType<>(
            TungstenAbsorberBlockEntity::new, false, com.gonzotech.core.registry.ModBlocks.TUNGSTEN_ABSORBER.get()));

    /** Миска для питомцев (автор 2026-09-19, жирные коты). */
    public static final Supplier<BlockEntityType<com.gonzotech.swag.PetBowlBlockEntity>> PET_BOWL =
        BLOCK_ENTITIES.register("pet_bowl", () -> new BlockEntityType<>(
            com.gonzotech.swag.PetBowlBlockEntity::new, false, com.gonzotech.core.registry.ModBlocks.PET_BOWL.get()));

    public static final Supplier<BlockEntityType<BoilerBlockEntity>> BOILER =
        BLOCK_ENTITIES.register("boiler", () -> new BlockEntityType<>(
            BoilerBlockEntity::new, false, ModMachines.BOILER.get()));

    public static final Supplier<BlockEntityType<StirlingBlockEntity>> STIRLING =
        BLOCK_ENTITIES.register("stirling", () -> new BlockEntityType<>(
            StirlingBlockEntity::new, false, ModMachines.STIRLING.get()));

    public static final Supplier<BlockEntityType<SolarPanelBlockEntity>> SOLAR_PANEL =
        BLOCK_ENTITIES.register("solar_panel", () -> new BlockEntityType<>(
            SolarPanelBlockEntity::new, false, ModMachines.SOLAR_PANEL.get()));

    public static final Supplier<BlockEntityType<ElectricFurnaceBlockEntity>> ELECTRIC_FURNACE =
        BLOCK_ENTITIES.register("electric_furnace", () -> new BlockEntityType<>(
            ElectricFurnaceBlockEntity::new, false, ModMachines.ELECTRIC_FURNACE.get()));

    public static final Supplier<BlockEntityType<SecondElectricFurnaceBlockEntity>> SECOND_ELECTRIC_FURNACE =
        BLOCK_ENTITIES.register("second_electric_furnace", () -> new BlockEntityType<>(
            SecondElectricFurnaceBlockEntity::new, false, ModMachines.SECOND_ELECTRIC_FURNACE.get()));

    public static final Supplier<BlockEntityType<CondenserBlockEntity>> CONDENSER =
        BLOCK_ENTITIES.register("condenser", () -> new BlockEntityType<>(
            CondenserBlockEntity::new, false, ModMachines.CONDENSER.get()));

    public static final Supplier<BlockEntityType<PumpBlockEntity>> PUMP =
        BLOCK_ENTITIES.register("pump", () -> new BlockEntityType<>(
            PumpBlockEntity::new, false, ModMachines.PUMP.get()));

    public static final Supplier<BlockEntityType<SecondPumpBlockEntity>> SECOND_PUMP =
        BLOCK_ENTITIES.register("second_pump", () -> new BlockEntityType<>(
            SecondPumpBlockEntity::new, false, ModMachines.SECOND_PUMP.get()));

    public static final Supplier<BlockEntityType<AccumulatorBlockEntity>> ACCUMULATOR =
        BLOCK_ENTITIES.register("accumulator", () -> new BlockEntityType<>(
            AccumulatorBlockEntity::new, false, ModMachines.ACCUMULATOR.get()));

    public static final Supplier<BlockEntityType<SecondAccumulatorBlockEntity>> SECOND_ACCUMULATOR =
        BLOCK_ENTITIES.register("second_accumulator", () -> new BlockEntityType<>(
            SecondAccumulatorBlockEntity::new, false, ModMachines.SECOND_ACCUMULATOR.get()));

    public static final Supplier<BlockEntityType<CobbleGeneratorBlockEntity>> COBBLE_GENERATOR =
        BLOCK_ENTITIES.register("cobble_generator", () -> new BlockEntityType<>(
            CobbleGeneratorBlockEntity::new, false, ModMachines.COBBLE_GENERATOR.get()));

    public static final Supplier<BlockEntityType<SecondCobbleGeneratorBlockEntity>> SECOND_COBBLE_GENERATOR =
        BLOCK_ENTITIES.register("second_cobble_generator", () -> new BlockEntityType<>(
            SecondCobbleGeneratorBlockEntity::new, false, ModMachines.SECOND_COBBLE_GENERATOR.get()));

    /** Functional 5×5 alloy foundry of the second discovery. */
    public static final Supplier<BlockEntityType<AlloyFoundryBlockEntity>> ALLOY_FOUNDRY =
        BLOCK_ENTITIES.register("second_alloy_foundry", () -> new BlockEntityType<>(
            AlloyFoundryBlockEntity::new, false, ModMachines.SECOND_ALLOY_FOUNDRY.get()));

    public static final Supplier<BlockEntityType<SecondGrinderBlockEntity>> SECOND_GRINDER =
        BLOCK_ENTITIES.register("second_grinder", () -> new BlockEntityType<>(
            SecondGrinderBlockEntity::new, false, ModMachines.SECOND_GRINDER.get()));

    public static final Supplier<BlockEntityType<SecondPressBlockEntity>> SECOND_PRESS =
        BLOCK_ENTITIES.register("second_press", () -> new BlockEntityType<>(
            SecondPressBlockEntity::new, false, ModMachines.SECOND_PRESS.get()));

    public static final Supplier<BlockEntityType<CrusherBlockEntity>> CRUSHER =
        BLOCK_ENTITIES.register("crusher", () -> new BlockEntityType<>(
            CrusherBlockEntity::new, false, ModMachines.CRUSHER.get()));

    public static final Supplier<BlockEntityType<CentrifugeBlockEntity>> CENTRIFUGE =
        BLOCK_ENTITIES.register("centrifuge", () -> new BlockEntityType<>(
            CentrifugeBlockEntity::new, false, ModMachines.CENTRIFUGE.get()));

    public static final Supplier<BlockEntityType<SingularHeatSourceBlockEntity>> SINGULAR_HEAT_SOURCE =
        BLOCK_ENTITIES.register("singular_heat_source", () -> new BlockEntityType<>(
            SingularHeatSourceBlockEntity::new, false, ModMachines.SINGULAR_HEAT_SOURCE.get()));

    public static final Supplier<BlockEntityType<SingularEnergySourceBlockEntity>> SINGULAR_ENERGY_SOURCE =
        BLOCK_ENTITIES.register("singular_energy_source", () -> new BlockEntityType<>(
            SingularEnergySourceBlockEntity::new, false, ModMachines.SINGULAR_ENERGY_SOURCE.get()));

    public static final Supplier<BlockEntityType<TurbineRotorBlockEntity>> TURBINE_ROTOR =
        BLOCK_ENTITIES.register("turbine_rotor", () -> new BlockEntityType<>(
            TurbineRotorBlockEntity::new, false, ModMachines.TURBINE_ROTOR.get()));

    public static final Supplier<BlockEntityType<com.gonzotech.machines.block.entity.SteamGenCoreBlockEntity>> STEAMGEN_CORE =
        BLOCK_ENTITIES.register("steamgen_core", () -> new BlockEntityType<>(
            com.gonzotech.machines.block.entity.SteamGenCoreBlockEntity::new, false, ModMachines.STEAMGEN_CORE.get()));

    public static final Supplier<BlockEntityType<com.gonzotech.machines.block.entity.ItemFilterBlockEntity>> ITEM_FILTER =
        BLOCK_ENTITIES.register("item_filter", () -> new BlockEntityType<>(
            com.gonzotech.machines.block.entity.ItemFilterBlockEntity::new, false, ModMachines.ITEM_FILTER.get()));

    public static final Supplier<BlockEntityType<com.gonzotech.machines.block.entity.ItemFilterBlockEntity>> SECOND_ITEM_FILTER =
        BLOCK_ENTITIES.register("second_item_filter", () -> new BlockEntityType<>(
            (pos, state) -> new com.gonzotech.machines.block.entity.ItemFilterBlockEntity(
                secondItemFilterType(), pos, state, SecondTierDefs.ITEM_FILTER_SLOTS),
            false, ModMachines.SECOND_ITEM_FILTER.get()));

    /**
     * The BE factory is invoked only after DeferredRegister has assigned the
     * supplier. Keeping the lookup behind a method avoids Java's illegal direct
     * self-reference in SECOND_ITEM_FILTER's initializer.
     */
    private static BlockEntityType<?> secondItemFilterType() {
        return SECOND_ITEM_FILTER.get();
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }

    private ModBlockEntities() {
    }
}
