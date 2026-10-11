package com.gonzotech.mixin;

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
 * Инжект биома «Пустошь» (desolation) в ванильный список биомов оверворлда.
 *
 * <p>Точка входа — {@link OverworldBiomeBuilder#addBiomes(Consumer)}: именно сюда
 * набиваются все ~7000 пар «{@link Climate.ParameterPoint} → {@link ResourceKey}»
 * для оверворлда. Добавляем desolation ПОСЛЕ ванильных, теми же 4 климатическими
 * точками, что раньше задавал удалённый {@code data/minecraft/dimension/overworld.json}.
 *
 * <p>Почему НЕ dimension-JSON: оверрайд {@code minecraft:dimension/overworld.json}
 * подменяет генератор ЦЕЛИКОМ и ломает суперплоский/большие-биомы/расширенный.
 * Инжект в {@code addBiomes} добавляет биом в климатический шум, не трогая
 * генератор: суперплоский (FlatLevelSource) и «один биом» (FixedBiomeSource)
 * не используют OverworldBiomeBuilder и остаются собой; normal/large-biomes/amplified
 * получают desolation штатно, тем же климатом, что и раньше.
 */
@Mixin(OverworldBiomeBuilder.class)
public class OverworldBiomeBuilderDesolationMixin {

    private static final ResourceKey<Biome> DESOLATION = ResourceKey.create(
        Registries.BIOME,
        ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "desolation"));

    @Inject(method = "addBiomes", at = @At("TAIL"))
    private void gonzotech$appendDesolation(
            Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> consumer,
            CallbackInfo ci) {
        // 4 климатические точки desolation — 1:1 из прежнего dimension/overworld.json.
        consumer.accept(Pair.of(point(0.15f, 0.5f, -0.35f, -0.05f, 0.2f, 0.5f, 0.15f, 0.45f, 0.0f, -0.15f, 0.25f), DESOLATION));
        consumer.accept(Pair.of(point(0.15f, 0.5f, 0.0f, 0.25f, 0.4f, 0.7f, 0.35f, 0.65f, 0.0f, -0.45f, -0.15f), DESOLATION));
        consumer.accept(Pair.of(point(0.15f, 0.5f, -0.35f, -0.05f, -0.19f, -0.11f, 0.15f, 0.45f, 0.0f, -0.15f, 0.25f), DESOLATION));
        consumer.accept(Pair.of(point(0.15f, 0.5f, 0.0f, 0.25f, -0.19f, -0.11f, 0.35f, 0.65f, 0.0f, -0.45f, -0.15f), DESOLATION));
    }

    private static Climate.ParameterPoint point(float t0, float t1, float h0, float h1,
            float c0, float c1, float e0, float e1, float depth, float w0, float w1) {
        return new Climate.ParameterPoint(
            Climate.Parameter.span(t0, t1),
            Climate.Parameter.span(h0, h1),
            Climate.Parameter.span(c0, c1),
            Climate.Parameter.span(e0, e1),
            Climate.Parameter.point(depth),
            Climate.Parameter.span(w0, w1),
            0L);
    }
}
