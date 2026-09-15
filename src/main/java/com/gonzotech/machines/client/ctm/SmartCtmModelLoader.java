package com.gonzotech.machines.client.ctm;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.gonzotech.GonzoTechMod;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.block.model.TextureSlots;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.context.ContextMap;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.AbstractUnbakedModel;
import net.neoforged.neoforge.client.model.StandardModelParameters;
import net.neoforged.neoforge.client.model.UnbakedModelLoader;

/**
 * Читает компактный JSON Smart CTM-модели.
 *
 * <p>В конфигурации намеренно всего четыре texture slot: обычный материал
 * поверхности, 16×2 line, 2×2 outer corner и 2×2 inner corner. Все сочетания
 * собирает {@link TurbineSmartCtmBakedModel}; model JSON на комбинацию масок не
 * создаются.</p>
 */
public final class SmartCtmModelLoader implements UnbakedModelLoader<SmartCtmModelLoader.Unbaked> {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "smart_ctm");
    public static final SmartCtmModelLoader INSTANCE = new SmartCtmModelLoader();

    private SmartCtmModelLoader() {
    }

    public static void register(ModelEvent.RegisterLoaders event) {
        event.register(ID, INSTANCE);
    }

    @Override
    public Unbaked read(JsonObject json, JsonDeserializationContext context) throws JsonParseException {
        if (!json.has("textures") || !json.getAsJsonObject("textures").has("base")
            || !json.getAsJsonObject("textures").has("line")
            || !json.getAsJsonObject("textures").has("outer_corner")
            || !json.getAsJsonObject("textures").has("inner_corner")) {
            throw new JsonParseException("gonzotech:smart_ctm requires base, line, outer_corner and inner_corner texture slots");
        }
        return new Unbaked(StandardModelParameters.parse(json, context));
    }

    /** Custom unbaked root which delegates standard texture/transform parsing to NeoForge. */
    public static final class Unbaked extends AbstractUnbakedModel {

        private Unbaked(StandardModelParameters parameters) {
            super(parameters);
        }

        @Override
        public BakedModel bake(TextureSlots textures, ModelBaker baker, ModelState modelState,
                                boolean useAmbientOcclusion, boolean usesBlockLight,
                                ItemTransforms itemTransforms, ContextMap additionalProperties) {
            TextureAtlasSprite base = baker.findSprite(textures, "base");
            TextureAtlasSprite line = baker.findSprite(textures, "line");
            TextureAtlasSprite outerCorner = baker.findSprite(textures, "outer_corner");
            TextureAtlasSprite innerCorner = baker.findSprite(textures, "inner_corner");
            TextureAtlasSprite particle = baker.findSprite(textures, "particle");
            return new TurbineSmartCtmBakedModel(base, line, outerCorner, innerCorner, particle,
                useAmbientOcclusion, usesBlockLight, itemTransforms);
        }
    }
}
