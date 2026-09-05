package com.gonzotech.machines.registry;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.machines.block.entity.BoilerBlockEntity;
import com.gonzotech.machines.block.entity.CondenserBlockEntity;
import com.gonzotech.machines.block.entity.ElectricFurnaceBlockEntity;
import com.gonzotech.machines.block.entity.FireboxBlockEntity;
import com.gonzotech.machines.block.entity.PumpBlockEntity;
import com.gonzotech.machines.block.entity.StirlingBlockEntity;
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

    public static final Supplier<BlockEntityType<BoilerBlockEntity>> BOILER =
        BLOCK_ENTITIES.register("boiler", () -> new BlockEntityType<>(
            BoilerBlockEntity::new, false, ModMachines.BOILER.get()));

    public static final Supplier<BlockEntityType<StirlingBlockEntity>> STIRLING =
        BLOCK_ENTITIES.register("stirling", () -> new BlockEntityType<>(
            StirlingBlockEntity::new, false, ModMachines.STIRLING.get()));

    public static final Supplier<BlockEntityType<ElectricFurnaceBlockEntity>> ELECTRIC_FURNACE =
        BLOCK_ENTITIES.register("electric_furnace", () -> new BlockEntityType<>(
            ElectricFurnaceBlockEntity::new, false, ModMachines.ELECTRIC_FURNACE.get()));

    public static final Supplier<BlockEntityType<CondenserBlockEntity>> CONDENSER =
        BLOCK_ENTITIES.register("condenser", () -> new BlockEntityType<>(
            CondenserBlockEntity::new, false, ModMachines.CONDENSER.get()));

    public static final Supplier<BlockEntityType<PumpBlockEntity>> PUMP =
        BLOCK_ENTITIES.register("pump", () -> new BlockEntityType<>(
            PumpBlockEntity::new, false, ModMachines.PUMP.get()));

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }

    private ModBlockEntities() {
    }
}
