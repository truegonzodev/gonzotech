package com.gonzotech.core.fluid;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.item.CorrosiveFluidBucketItem;
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
 * Реестр типов жидкостей, источников, потоков и вёдер мода Gonzo Tech.
 * Поддерживает как стандартные (water_type), так и густые (lava_type, waterlava_type) жидкости.
 */
public final class ModFluids {

    private ModFluids() {
    }

    public static final DeferredRegister<FluidType> FLUID_TYPES =
        DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, GonzoTechMod.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS =
        DeferredRegister.create(Registries.FLUID, GonzoTechMod.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(GonzoTechMod.MOD_ID);

    // ─────────────────────── 1. Расплавленный кориум ───────────────────────
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

    public static final Supplier<BucketItem> CORIUM_BUCKET = ITEMS.registerItem("corium_bucket", props ->
        new BucketItem(MOLTEN_CORIUM.get(), props.stacksTo(1).craftRemainder(Items.BUCKET)));

    // ─────────────────────── 2. Этанол (Ректификат) ───────────────────────
    public static final Supplier<FluidType> ETHANOL_TYPE = FLUID_TYPES.register("ethanol", () ->
        new FluidType(FluidType.Properties.create()
            .density(789)
            .viscosity(1200)
            .temperature(300)
            .canDrown(true)
            .canSwim(true)
            .canPushEntity(true)
            .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
            .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)
        ));

    public static final Supplier<FlowingFluid> ETHANOL = FLUIDS.register("ethanol",
        () -> new BaseFlowingFluid.Source(ethanolProperties()));
    public static final Supplier<FlowingFluid> FLOWING_ETHANOL = FLUIDS.register("flowing_ethanol",
        () -> new BaseFlowingFluid.Flowing(ethanolProperties()));

    public static final Supplier<BucketItem> ETHANOL_BUCKET = ITEMS.registerItem("ethanol_bucket", props ->
        new BucketItem(ETHANOL.get(), props.stacksTo(1).craftRemainder(Items.BUCKET)));

    // ─────────────────────── 3. Формальдегид ───────────────────────
    public static final Supplier<FluidType> FORMALDEHYDE_TYPE = FLUID_TYPES.register("formaldehyde", () ->
        new FluidType(FluidType.Properties.create()
            .density(815)
            .viscosity(4000)
            .temperature(290)
            .canDrown(true)
            .canSwim(true)
            .canPushEntity(true)
            .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
            .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)
        ));

    public static final Supplier<FlowingFluid> FORMALDEHYDE = FLUIDS.register("formaldehyde",
        () -> new BaseFlowingFluid.Source(formaldehydeProperties()));
    public static final Supplier<FlowingFluid> FLOWING_FORMALDEHYDE = FLUIDS.register("flowing_formaldehyde",
        () -> new BaseFlowingFluid.Flowing(formaldehydeProperties()));

    public static final Supplier<BucketItem> FORMALDEHYDE_BUCKET = ITEMS.registerItem("formaldehyde_bucket", props ->
        new BucketItem(FORMALDEHYDE.get(), props.stacksTo(1).craftRemainder(Items.BUCKET)));

    // ─────────────────────── 4. Серная кислота ───────────────────────
    public static final Supplier<FluidType> SULFURIC_ACID_TYPE = FLUID_TYPES.register("sulfuric_acid", () ->
        new FluidType(FluidType.Properties.create()
            .density(1840)
            .viscosity(1500)
            .temperature(300)
            .canDrown(true)
            .canSwim(true)
            .canPushEntity(true)
            .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
            .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)
        ));

    public static final Supplier<FlowingFluid> SULFURIC_ACID = FLUIDS.register("sulfuric_acid",
        () -> new BaseFlowingFluid.Source(sulfuricAcidProperties()));
    public static final Supplier<FlowingFluid> FLOWING_SULFURIC_ACID = FLUIDS.register("flowing_sulfuric_acid",
        () -> new BaseFlowingFluid.Flowing(sulfuricAcidProperties()));

    public static final Supplier<BucketItem> SULFURIC_ACID_BUCKET = ITEMS.registerItem("sulfuric_acid_bucket", props ->
        new CorrosiveFluidBucketItem(SULFURIC_ACID, props));

    // ─────────────────────── 5. Дистиллят ───────────────────────
    public static final Supplier<FluidType> DISTILLATE_TYPE = FLUID_TYPES.register("distillate", () ->
        new FluidType(FluidType.Properties.create()
            .density(900)
            .viscosity(1100)
            .temperature(300)
            .canDrown(true)
            .canSwim(true)
            .canPushEntity(true)
            .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
            .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)
        ));

    public static final Supplier<FlowingFluid> DISTILLATE = FLUIDS.register("distillate",
        () -> new BaseFlowingFluid.Source(distillateProperties()));
    public static final Supplier<FlowingFluid> FLOWING_DISTILLATE = FLUIDS.register("flowing_distillate",
        () -> new BaseFlowingFluid.Flowing(distillateProperties()));

    public static final Supplier<BucketItem> DISTILLATE_BUCKET = ITEMS.registerItem("distillate_bucket", props ->
        new BucketItem(DISTILLATE.get(), props.stacksTo(1).craftRemainder(Items.BUCKET)));

    // ─────────────────────── 6. Брага ───────────────────────
    public static final Supplier<FluidType> MASH_TYPE = FLUID_TYPES.register("mash", () ->
        new FluidType(FluidType.Properties.create()
            .density(1200)
            .viscosity(3000)
            .temperature(300)
            .canDrown(true)
            .canSwim(true)
            .canPushEntity(true)
            .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
            .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)
        ));

    public static final Supplier<FlowingFluid> MASH = FLUIDS.register("mash",
        () -> new BaseFlowingFluid.Source(mashProperties()));
    public static final Supplier<FlowingFluid> FLOWING_MASH = FLUIDS.register("flowing_mash",
        () -> new BaseFlowingFluid.Flowing(mashProperties()));

    public static final Supplier<BucketItem> MASH_BUCKET = ITEMS.registerItem("mash_bucket", props ->
        new com.gonzotech.core.item.MashBucketItem(MASH, props));

    // ─────────────────────── 7. Сусло ───────────────────────
    public static final Supplier<FluidType> WORT_TYPE = FLUID_TYPES.register("wort", () ->
        new FluidType(FluidType.Properties.create()
            .density(1050)
            .viscosity(1300)
            .temperature(300)
            .canDrown(true)
            .canSwim(true)
            .canPushEntity(true)
            .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
            .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)
        ));

    public static final Supplier<FlowingFluid> WORT = FLUIDS.register("wort",
        () -> new BaseFlowingFluid.Source(wortProperties()));
    public static final Supplier<FlowingFluid> FLOWING_WORT = FLUIDS.register("flowing_wort",
        () -> new BaseFlowingFluid.Flowing(wortProperties()));

    public static final Supplier<BucketItem> WORT_BUCKET = ITEMS.registerItem("wort_bucket", props ->
        new BucketItem(WORT.get(), props.stacksTo(1).craftRemainder(Items.BUCKET)));

    // ─────────────────────── Свойства жидкостей ───────────────────────

    private static BaseFlowingFluid.Properties moltenCoriumProperties() {
        return new BaseFlowingFluid.Properties(MOLTEN_CORIUM_TYPE, MOLTEN_CORIUM, FLOWING_MOLTEN_CORIUM)
            .slopeFindDistance(2)
            .levelDecreasePerBlock(2)
            .explosionResistance(100.0F)
            .tickRate(30)
            .block(ModBlocks.MOLTEN_CORIUM)
            .bucket(CORIUM_BUCKET);
    }

    private static BaseFlowingFluid.Properties ethanolProperties() {
        // water_type: текучесть как у воды
        return new BaseFlowingFluid.Properties(ETHANOL_TYPE, ETHANOL, FLOWING_ETHANOL)
            .slopeFindDistance(4)
            .levelDecreasePerBlock(1)
            .explosionResistance(100.0F)
            .tickRate(5)
            .block(ModBlocks.ETHANOL)
            .bucket(ETHANOL_BUCKET);
    }

    private static BaseFlowingFluid.Properties formaldehydeProperties() {
        // lava_type: текучесть как у лавы
        return new BaseFlowingFluid.Properties(FORMALDEHYDE_TYPE, FORMALDEHYDE, FLOWING_FORMALDEHYDE)
            .slopeFindDistance(2)
            .levelDecreasePerBlock(2)
            .explosionResistance(100.0F)
            .tickRate(30)
            .block(ModBlocks.FORMALDEHYDE)
            .bucket(FORMALDEHYDE_BUCKET);
    }

    private static BaseFlowingFluid.Properties sulfuricAcidProperties() {
        // water_type: текучесть как у воды
        return new BaseFlowingFluid.Properties(SULFURIC_ACID_TYPE, SULFURIC_ACID, FLOWING_SULFURIC_ACID)
            .slopeFindDistance(4)
            .levelDecreasePerBlock(1)
            .explosionResistance(100.0F)
            .tickRate(5)
            .block(ModBlocks.SULFURIC_ACID)
            .bucket(SULFURIC_ACID_BUCKET);
    }

    private static BaseFlowingFluid.Properties distillateProperties() {
        // water_type: текучесть как у воды
        return new BaseFlowingFluid.Properties(DISTILLATE_TYPE, DISTILLATE, FLOWING_DISTILLATE)
            .slopeFindDistance(4)
            .levelDecreasePerBlock(1)
            .explosionResistance(100.0F)
            .tickRate(5)
            .block(ModBlocks.DISTILLATE)
            .bucket(DISTILLATE_BUCKET);
    }

    private static BaseFlowingFluid.Properties mashProperties() {
        // waterlava_type: текучесть между лавой и водой (густая брага)
        return new BaseFlowingFluid.Properties(MASH_TYPE, MASH, FLOWING_MASH)
            .slopeFindDistance(3)
            .levelDecreasePerBlock(1)
            .explosionResistance(100.0F)
            .tickRate(15)
            .block(ModBlocks.MASH)
            .bucket(MASH_BUCKET);
    }

    private static BaseFlowingFluid.Properties wortProperties() {
        // water_type: текучесть как у воды
        return new BaseFlowingFluid.Properties(WORT_TYPE, WORT, FLOWING_WORT)
            .slopeFindDistance(4)
            .levelDecreasePerBlock(1)
            .explosionResistance(100.0F)
            .tickRate(5)
            .block(ModBlocks.WORT)
            .bucket(WORT_BUCKET);
    }

    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
        ITEMS.register(modEventBus);
    }
}
