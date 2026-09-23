package com.gonzotech.core.fluid;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.registry.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Расплавленный кориум — лаво-подобный серый расплав, появляющийся при
 * meltdown ядерной топки. Застывший кориум — отдельный блок
 * {@code gonzotech:corium}; жидкость и ведро кориума живут здесь.
 */
public final class ModFluids {

    private ModFluids() {
    }

    public static final DeferredRegister<FluidType> FLUID_TYPES =
        DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, GonzoTechMod.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS =
        DeferredRegister.create(Registries.FLUID, GonzoTechMod.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(GonzoTechMod.MOD_ID);

    public static final Supplier<FluidType> MOLTEN_CORIUM_TYPE = FLUID_TYPES.register("molten_corium", () ->
        new FluidType(FluidType.Properties.create()
            .density(3_000)
            .viscosity(6_000)
            .temperature(1_300)
            .lightLevel(15)
            .canDrown(false)
            .canConvertToSource(false)
            .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL_LAVA)
            .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY_LAVA)
        ));

    public static final Supplier<FlowingFluid> MOLTEN_CORIUM = FLUIDS.register("molten_corium",
        () -> new MoltenCoriumFluid.Source(moltenCoriumProperties()));
    public static final Supplier<FlowingFluid> FLOWING_MOLTEN_CORIUM = FLUIDS.register("flowing_molten_corium",
        () -> new MoltenCoriumFluid.Flowing(moltenCoriumProperties()));

    /** Ведро кориума: зачерпывает расплавленный кориум и разливает его источником. */
    public static final Supplier<BucketItem> CORIUM_BUCKET = ITEMS.registerItem("corium_bucket", props ->
        new BucketItem(MOLTEN_CORIUM.get(), props.stacksTo(1).craftRemainder(Items.BUCKET)));

    // ─────────────────────── Этанол (Ректификат) ───────────────────────
    public static final Supplier<FluidType> ETHANOL_TYPE = FLUID_TYPES.register("ethanol", () ->
        new FluidType(FluidType.Properties.create()
            .density(789)
            .viscosity(1200)
            .temperature(300)
            .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
            .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)
        ));

    public static final Supplier<FlowingFluid> ETHANOL = FLUIDS.register("ethanol",
        () -> new BaseFlowingFluid.Source(ethanolProperties()));
    public static final Supplier<FlowingFluid> FLOWING_ETHANOL = FLUIDS.register("flowing_ethanol",
        () -> new BaseFlowingFluid.Flowing(ethanolProperties()));

    public static final Supplier<BucketItem> ETHANOL_BUCKET = ITEMS.registerItem("ethanol_bucket", props ->
        new BucketItem(ETHANOL.get(), props.stacksTo(1).craftRemainder(Items.BUCKET)));

    // ─────────────────────── Формальдегид ───────────────────────
    public static final Supplier<FluidType> FORMALDEHYDE_TYPE = FLUID_TYPES.register("formaldehyde", () ->
        new FluidType(FluidType.Properties.create()
            .density(815)
            .viscosity(1400)
            .temperature(290)
            .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
            .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)
        ));

    public static final Supplier<FlowingFluid> FORMALDEHYDE = FLUIDS.register("formaldehyde",
        () -> new BaseFlowingFluid.Source(formaldehydeProperties()));
    public static final Supplier<FlowingFluid> FLOWING_FORMALDEHYDE = FLUIDS.register("flowing_formaldehyde",
        () -> new BaseFlowingFluid.Flowing(formaldehydeProperties()));

    public static final Supplier<BucketItem> FORMALDEHYDE_BUCKET = ITEMS.registerItem("formaldehyde_bucket", props ->
        new BucketItem(FORMALDEHYDE.get(), props.stacksTo(1).craftRemainder(Items.BUCKET)));

    /**
     * Свойства собираются в методе, а не в static-поле: поле-супплер жидкостей
     * ссылается на свойства и ведро, ведро — на источник жидкости, и цикличная
     * инициализация static-полей была бы незаконной (illegal forward reference).
     * Метод разбивает цикл: вызывается лениво, при регистрации жидкостей.
     */
    private static BaseFlowingFluid.Properties moltenCoriumProperties() {
        return new BaseFlowingFluid.Properties(MOLTEN_CORIUM_TYPE, MOLTEN_CORIUM, FLOWING_MOLTEN_CORIUM)
            // Медленный, неводоподобный профиль распространения как у лавы Overworld.
            .slopeFindDistance(2)
            .levelDecreasePerBlock(2)
            .explosionResistance(100.0F)
            .tickRate(30)
            .block(ModBlocks.MOLTEN_CORIUM)
            .bucket(CORIUM_BUCKET);
    }

    private static BaseFlowingFluid.Properties ethanolProperties() {
        return new BaseFlowingFluid.Properties(ETHANOL_TYPE, ETHANOL, FLOWING_ETHANOL)
            .slopeFindDistance(4)
            .levelDecreasePerBlock(1)
            .explosionResistance(100.0F)
            .tickRate(5)
            .block(ModBlocks.ETHANOL)
            .bucket(ETHANOL_BUCKET);
    }

    private static BaseFlowingFluid.Properties formaldehydeProperties() {
        return new BaseFlowingFluid.Properties(FORMALDEHYDE_TYPE, FORMALDEHYDE, FLOWING_FORMALDEHYDE)
            .slopeFindDistance(4)
            .levelDecreasePerBlock(1)
            .explosionResistance(100.0F)
            .tickRate(5)
            .block(ModBlocks.FORMALDEHYDE)
            .bucket(FORMALDEHYDE_BUCKET);
    }

    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
        ITEMS.register(modEventBus);
    }
}
