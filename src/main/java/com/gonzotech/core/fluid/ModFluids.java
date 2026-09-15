package com.gonzotech.core.fluid;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.registry.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** Corium is a non-renewable, lava-speed gray molten fluid made by nuclear meltdown. */
public final class ModFluids {

    private ModFluids() {
    }

    public static final DeferredRegister<FluidType> FLUID_TYPES =
        DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, GonzoTechMod.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS =
        DeferredRegister.create(Registries.FLUID, GonzoTechMod.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(GonzoTechMod.MOD_ID);

    public static final Supplier<FluidType> CORIUM_TYPE = FLUID_TYPES.register("corium", () ->
        new FluidType(FluidType.Properties.create()
            .density(3_000)
            .viscosity(6_000)
            .temperature(1_300)
            .lightLevel(15)
            .canConvertToSource(false))
    );

    public static final Supplier<FlowingFluid> CORIUM_SOURCE = FLUIDS.register("corium", () ->
        new BaseFlowingFluid.Source(CORIUM_PROPERTIES));
    public static final Supplier<FlowingFluid> FLOWING_CORIUM = FLUIDS.register("flowing_corium", () ->
        new BaseFlowingFluid.Flowing(CORIUM_PROPERTIES));

    /** Bucket is intentionally obtainable only through picking up a source / creative, like lava. */
    public static final Supplier<BucketItem> CORIUM_BUCKET = ITEMS.registerItem("corium_bucket", props ->
        new BucketItem(CORIUM_SOURCE, props.stacksTo(1).craftRemainder(Items.BUCKET)));

    private static final BaseFlowingFluid.Properties CORIUM_PROPERTIES = new BaseFlowingFluid.Properties(
        CORIUM_TYPE, CORIUM_SOURCE, FLOWING_CORIUM)
        // Match overworld lava's slow, non-waterlike propagation profile.
        .slopeFindDistance(2)
        .levelDecreasePerBlock(2)
        .explosionResistance(100.0F)
        .tickRate(30)
        .block(ModBlocks.CORIUM)
        .bucket(CORIUM_BUCKET);

    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
        ITEMS.register(modEventBus);
    }
}
