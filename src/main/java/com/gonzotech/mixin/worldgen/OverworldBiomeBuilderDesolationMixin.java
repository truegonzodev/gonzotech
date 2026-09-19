package com.gonzotech.mixin.worldgen;

import com.gonzotech.GonzoTechMod;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * Дезоляция в оверворлде (автор, 2026-09-19): впрыск нашего биома в ванильный
 * {@link OverworldBiomeBuilder#addBiomes} миксином — БЕЗ TerraBlender и без
 * гигантского переопределения {@code multi_noise_biome_source_parameter_list}
 * (пресет остаётся ванильным). Точки параметров подобраны редкими пятнами
 * суши: умеренно тёплая, полусухая, не эродированная внутренняя суша.
 */
@Mixin(OverworldBiomeBuilder.class)
public abstract class OverworldBiomeBuilderDesolationMixin {

    private static final ResourceKey<Biome> DESOLATION = ResourceKey.create(
        Registries.BIOME,
        ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "desolation"));

    @Inject(method = "addBiomes", at = @At("HEAD"))
    private void gonzotech$addDesolation(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> consumer,
                                         CallbackInfo ci) {
        // Основное пятно и вторичное (более глубокая сушь) — как у ванильных редких биомов.
        consumer.accept(Pair.of(Climate.parameters(
            0.325F, -0.2F, 0.35F, 0.3F, 0.0F, 0.05F, 0.0F), DESOLATION));
        consumer.accept(Pair.of(Climate.parameters(
            0.325F, 0.1F, 0.55F, 0.5F, 0.0F, -0.3F, 0.0F), DESOLATION));

        // Прибрежные пятна (автор, второе задание: «чаще прилегая к рекам/
        // пляжам/океанам, не всегда, но чаще») — c ∈ [-0.19, -0.11]: это
        // ванильный пояс побережья (безымян. правило addCoast-биомов), через
        // него же проходят устья и дельты рек. Материковые точки выше
        // остаются, поэтому «не всегда у воды» — тоже сохраняется.
        consumer.accept(Pair.of(Climate.parameters(
            Climate.Parameter.point(0.325F), Climate.Parameter.point(-0.2F),
            Climate.Parameter.span(-0.19F, -0.11F), Climate.Parameter.point(0.3F),
            Climate.Parameter.point(0.0F), Climate.Parameter.point(0.05F), 0.0F), DESOLATION));
        consumer.accept(Pair.of(Climate.parameters(
            Climate.Parameter.point(0.325F), Climate.Parameter.point(0.1F),
            Climate.Parameter.span(-0.19F, -0.11F), Climate.Parameter.point(0.5F),
            Climate.Parameter.point(0.0F), Climate.Parameter.point(-0.1F), 0.0F), DESOLATION));
    }
}
