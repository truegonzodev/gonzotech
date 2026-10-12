package com.gonzotech.core.worldgen;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;

/**
 * Мост инъекции «Пустоши» в мультишумовой пресет (автор, 2026-10-12).
 *
 * <p>Реализуется двумя mixin'ами: над записью реестра
 * {@code MultiNoiseBiomeSourceParameterList} (мутация на
 * ServerAboutToStartEvent — когда датапак-реестры уже готовы) и над самим
 * {@code MultiNoiseBiomeSource} (дозаправка на LevelEvent.Load — покрывает
 * перезаход в мир, когда источник биома уже построен и мог закэшировать
 * ванильный список; /locate и новые чанки читают поле {@code parameters}
 * источника напрямую).
 *
 * <p>Идемпотентно: {@link #contains} не даёт добавить точки дважды, если оба
 * хука сработали в одном запуске.
 */
public interface DesolationPresetExt {

    /** Ключ биома «Пустошь» (data/gonzotech/worldgen/biome/desolation.json). */
    ResourceKey<Biome> DESOLATION_KEY = ResourceKey.create(
        Registries.BIOME,
        ResourceLocation.fromNamespaceAndPath("gonzotech", "desolation"));

    /**
     * Шесть климат-гиперкубов desolation. 0.3.194 (заявка автора: биом в 3–4
     * раза крупнее, в 2–3 раза чаще — ~1300 блоков между биомами, 16–40
     * чанков на биом): пролёты расширены ~в 1.7–2 раза против 0.3.193 и
     * добавлена холодная пара t[-0.45;0.02]. Тюнинг — только здесь.
     */
    Climate.ParameterPoint[] BOXES = {
        box(0.05F, 0.60F, -0.50F, -0.02F,  0.10F,  0.60F, 0.05F, 0.55F, -0.30F,  0.40F),
        box(0.05F, 0.60F, -0.02F,  0.45F,  0.30F,  0.80F, 0.25F, 0.75F, -0.60F, -0.05F),
        box(0.05F, 0.60F, -0.50F, -0.02F, -0.30F, -0.02F, 0.05F, 0.55F, -0.30F,  0.40F),
        box(0.05F, 0.60F, -0.02F,  0.45F, -0.30F, -0.02F, 0.25F, 0.75F, -0.60F, -0.05F),
        box(-0.45F, 0.02F, -0.50F, -0.02F,  0.10F,  0.60F, 0.05F, 0.55F, -0.30F,  0.40F),
        box(-0.45F, 0.02F, -0.02F,  0.45F,  0.30F,  0.80F, 0.25F, 0.75F, -0.60F, -0.05F),
    };

    /** Дописать 4+2 климат-точки desolation в живой список {@code parameters}. */
    void gonzotech$addDesolationPoints(Holder<Biome> desolation);

    /** Идемпотентность: точки уже добавлены одним из хуков? */
    static boolean contains(List<Pair<Climate.ParameterPoint, Holder<Biome>>> values) {
        for (Pair<Climate.ParameterPoint, Holder<Biome>> p : values) {
            if (p.getSecond().is(DESOLATION_KEY)) {
                return true;
            }
        }
        return false;
    }

    static Climate.ParameterPoint box(float t0, float t1, float h0, float h1,
                                      float c0, float c1, float e0, float e1,
                                      float w0, float w1) {
        return new Climate.ParameterPoint(
            Climate.Parameter.span(t0, t1),
            Climate.Parameter.span(h0, h1),
            Climate.Parameter.span(c0, c1),
            Climate.Parameter.span(e0, e1),
            Climate.Parameter.point(0.0F), // depth — как в исходном dimension-файле
            Climate.Parameter.span(w0, w1),
            0L);
    }
}
