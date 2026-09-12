package com.gonzotech.machines.client;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.component.AlloyComposition;
import com.gonzotech.machines.processing.AlloyMaterialCatalog;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side synthesis and bounded GPU cache for procedural custom-alloy item
 * textures. The server continues to own {@code AlloyComposition} and the exact
 * weighted {@code AlloyTint}; this class only turns their synchronised values
 * into pixels for an item renderer.
 */
public final class AlloyDynamicTextureCache {

    /** The actual resource path, including the textures/ prefix and PNG suffix. */
    private static final ResourceLocation BASE_TEXTURE_FILE = ResourceLocation.fromNamespaceAndPath(
        GonzoTechMod.MOD_ID, "textures/item/custom_alloy.png"
    );
    private static final int MAX_CACHED_TEXTURES = 128;
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Access-ordered: an inventory full of one-off alloys cannot retain GPU textures forever. */
    private static final Map<CacheKey, CachedTexture> CACHE = new LinkedHashMap<>(16, 0.75F, true);
    private static long nextTextureSerial;
    @Nullable
    private static BaseSprite baseSprite;

    private AlloyDynamicTextureCache() {
    }

    /**
     * Gets an already uploaded texture for this immutable render argument, or
     * builds it once. This is called by the special renderer on the client
     * render path, so NativeImage/GL work never runs on the logical server.
     */
    public static synchronized ResourceLocation textureFor(AlloySpecialRenderer.RenderData data) {
        CacheKey key = new CacheKey(data.composition(), data.mixArgb());
        CachedTexture cached = CACHE.get(key);
        if (cached != null) return cached.location();

        BaseSprite source = loadBaseSprite();
        NativeImage image = synthesize(source, data.composition(), data.mixArgb());
        DynamicTexture texture = new DynamicTexture(image);
        texture.setClamp(true);
        texture.setFilter(false, false); // preserve the intended 16x16 mosaic pixels
        texture.upload();

        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
            GonzoTechMod.MOD_ID, "dynamic/custom_alloy/" + nextTextureSerial++
        );
        Minecraft.getInstance().getTextureManager().register(location, texture);
        CACHE.put(key, new CachedTexture(location));
        trimCache();
        return location;
    }

    /** Called from the client resource-reload listener so edited base art is re-read after F3+T. */
    public static synchronized void onResourceManagerReload(ResourceManager resourceManager) {
        releaseAll();
        baseSprite = null;
    }

    private static void trimCache() {
        Iterator<Map.Entry<CacheKey, CachedTexture>> entries = CACHE.entrySet().iterator();
        while (CACHE.size() > MAX_CACHED_TEXTURES && entries.hasNext()) {
            CachedTexture eldest = entries.next().getValue();
            entries.remove();
            Minecraft.getInstance().getTextureManager().release(eldest.location());
        }
    }

    private static void releaseAll() {
        if (CACHE.isEmpty()) return;

        var textureManager = Minecraft.getInstance().getTextureManager();
        for (CachedTexture texture : CACHE.values()) {
            textureManager.release(texture.location());
        }
        CACHE.clear();
    }

    private static BaseSprite loadBaseSprite() {
        if (baseSprite != null) return baseSprite;

        try (InputStream input = Minecraft.getInstance().getResourceManager().open(BASE_TEXTURE_FILE);
             NativeImage image = NativeImage.read(input)) {
            baseSprite = new BaseSprite(image.getWidth(), image.getHeight(), image.getPixels());
        } catch (IOException exception) {
            // A broken resource pack should not make an item stack unrenderable.
            // This path is intentionally only a last-resort neutral placeholder;
            // normal operation always reads the user-owned custom_alloy.png above.
            LOGGER.error("Could not load {} for dynamic custom-alloy rendering", BASE_TEXTURE_FILE, exception);
            baseSprite = BaseSprite.neutralFallback();
        }
        return baseSprite;
    }

    /**
     * Performs the five compositing stages on a copy of the grayscale source.
     * RGB operations deliberately use the ordinary sRGB component arithmetic of
     * the supplied blend recipe, not the server's material-property maths.
     */
    private static NativeImage synthesize(BaseSprite source, @Nullable AlloyComposition composition, int mixArgb) {
        int[] noiseRgb = createNoise(source, composition);
        Rgb mix = Rgb.fromArgb(mixArgb);
        NativeImage result = new NativeImage(source.width(), source.height(), false);

        for (int y = 0; y < source.height(); y++) {
            for (int x = 0; x < source.width(); x++) {
                int pixelIndex = y * source.width() + x;
                int original = source.pixels()[pixelIndex];
                int alpha = original >>> 24;
                if (alpha == 0) {
                    result.setPixel(x, y, original);
                    continue;
                }

                // Step 1: 10% normal blend of the neutral metal with C_mix.
                Rgb z1 = Rgb.normal(Rgb.fromArgb(original), mix, 0.10D);
                // Step 2: 17% normal blend of Z1 with Overlay(Z1, C_mix).
                Rgb z2 = Rgb.normal(z1, Rgb.overlay(z1, mix), 0.17D);
                // Step 3: Photoshop-style Color: mix hue/saturation, Z2 Rec.709 luminosity.
                Rgb z3 = Rgb.withLuminance(mix, z2.luminance709());

                int ingredientRgb = noiseRgb[pixelIndex];
                if (ingredientRgb >= 0) {
                    // Step 5: Hue(noise) + Saturation/Lightness(Z3), then 65% normal.
                    Rgb hueLayer = Rgb.hueOf(Rgb.fromRgb(ingredientRgb), z3);
                    z3 = Rgb.normal(z3, hueLayer, 0.65D);
                }
                result.setPixel(x, y, z3.toArgb(alpha));
            }
        }
        return result;
    }

    /**
     * Step 4. Exactly {@code round(70%)} of the visible sprite pixels receive
     * an ingredient colour; all remaining positions are empty and therefore do
     * not alter Z3 in step 5. Integer ownership is allocated by largest
     * remainder, making it both exact in total and as proportional to material
     * units as a finite 16x16 sprite permits.
     */
    private static int[] createNoise(BaseSprite source, @Nullable AlloyComposition composition) {
        int[] noiseRgb = new int[source.pixels().length];
        Arrays.fill(noiseRgb, -1);
        if (composition == null) return noiseRgb;

        List<MaterialWeight> materials = materialWeights(composition);
        if (materials.isEmpty()) return noiseRgb;

        List<Integer> visiblePixels = new ArrayList<>();
        for (int index = 0; index < source.pixels().length; index++) {
            if ((source.pixels()[index] >>> 24) != 0) visiblePixels.add(index);
        }
        if (visiblePixels.isEmpty()) return noiseRgb;

        long seed = compositionSeed(composition);
        DeterministicRandom random = new DeterministicRandom(seed);
        for (int index = visiblePixels.size() - 1; index > 0; index--) {
            int other = random.nextInt(index + 1);
            int value = visiblePixels.get(index);
            visiblePixels.set(index, visiblePixels.get(other));
            visiblePixels.set(other, value);
        }

        int coloredSlots = (int) Math.round(visiblePixels.size() * 0.70D);
        List<MaterialAllocation> allocations = allocateSlots(materials, coloredSlots);
        int slot = 0;
        for (MaterialAllocation allocation : allocations) {
            for (int count = 0; count < allocation.slots(); count++) {
                noiseRgb[visiblePixels.get(slot++)] = allocation.material().rgb();
            }
        }
        return noiseRgb;
    }

    private static List<MaterialWeight> materialWeights(AlloyComposition composition) {
        List<MaterialWeight> materials = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Integer> part : composition.parts().entrySet()) {
            AlloyMaterialCatalog.Material material = AlloyMaterialCatalog.material(part.getKey());
            // Malformed externally-created component values retain their server
            // tint but cannot invent an ingredient colour on the client.
            if (material != null) materials.add(new MaterialWeight(material, part.getValue()));
        }
        return materials;
    }

    private static List<MaterialAllocation> allocateSlots(List<MaterialWeight> materials, int coloredSlots) {
        long totalUnits = 0;
        for (MaterialWeight material : materials) totalUnits += material.units();
        if (totalUnits == 0 || coloredSlots == 0) return List.of();

        List<MaterialAllocation> allocations = new ArrayList<>(materials.size());
        int allocated = 0;
        for (MaterialWeight material : materials) {
            long numerator = (long) coloredSlots * material.units();
            int slots = (int) (numerator / totalUnits);
            allocations.add(new MaterialAllocation(material.material(), slots, numerator % totalUnits));
            allocated += slots;
        }

        // Stable material IDs resolve exact fractional ties, independent of map
        // implementation or stack count.
        allocations.sort(Comparator
            .comparingLong(MaterialAllocation::remainder).reversed()
            .thenComparing(allocation -> allocation.material().id().toString()));
        for (int index = 0; index < coloredSlots - allocated; index++) {
            MaterialAllocation old = allocations.get(index);
            allocations.set(index, old.withSlots(old.slots() + 1));
        }
        // Re-sort so the allocation-to-pixel assignment also has a canonical order.
        allocations.sort(Comparator.comparing(allocation -> allocation.material().id().toString()));
        return allocations;
    }

    /** Stable FNV-1a seed containing only canonical material IDs and their MU values. */
    private static long compositionSeed(AlloyComposition composition) {
        List<Map.Entry<ResourceLocation, Integer>> parts = new ArrayList<>(composition.parts().entrySet());
        parts.sort(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)));

        long hash = 0xCBF29CE484222325L;
        for (Map.Entry<ResourceLocation, Integer> part : parts) {
            String id = part.getKey().toString();
            for (int index = 0; index < id.length(); index++) {
                hash ^= id.charAt(index);
                hash *= 0x100000001B3L;
            }
            hash ^= 0xFFL;
            hash *= 0x100000001B3L;
            int units = part.getValue();
            for (int shift = 0; shift < Integer.SIZE; shift += Byte.SIZE) {
                hash ^= (units >>> shift) & 0xFFL;
                hash *= 0x100000001B3L;
            }
        }
        return hash;
    }

    private record CacheKey(@Nullable AlloyComposition composition, int mixArgb) {
    }

    private record CachedTexture(ResourceLocation location) {
    }

    private record BaseSprite(int width, int height, int[] pixels) {
        private static BaseSprite neutralFallback() {
            int[] pixels = new int[16 * 16];
            Arrays.fill(pixels, 0xFF808080);
            return new BaseSprite(16, 16, pixels);
        }
    }

    private record MaterialWeight(AlloyMaterialCatalog.Material material, int units) {
    }

    private record MaterialAllocation(AlloyMaterialCatalog.Material material, int slots, long remainder) {
        private MaterialAllocation withSlots(int slots) {
            return new MaterialAllocation(material, slots, remainder);
        }
    }

    /** Tiny SplitMix64 generator, seeded once per canonical composition. */
    private static final class DeterministicRandom {
        private long state;

        private DeterministicRandom(long seed) {
            state = seed;
        }

        private long nextLong() {
            long value = (state += 0x9E3779B97F4A7C15L);
            value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
            value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
            return value ^ (value >>> 31);
        }

        private int nextInt(int bound) {
            return (int) Long.remainderUnsigned(nextLong(), bound);
        }
    }

    private record Rgb(double red, double green, double blue) {
        private static Rgb fromArgb(int argb) {
            return new Rgb(
                ((argb >>> 16) & 0xFF) / 255.0D,
                ((argb >>> 8) & 0xFF) / 255.0D,
                (argb & 0xFF) / 255.0D
            );
        }

        private static Rgb fromRgb(int rgb) {
            return fromArgb(0xFF000000 | rgb);
        }

        private int toArgb(int alpha) {
            return (alpha << 24)
                | (toChannel(red) << 16)
                | (toChannel(green) << 8)
                | toChannel(blue);
        }

        private static Rgb normal(Rgb base, Rgb layer, double alpha) {
            double inverse = 1.0D - alpha;
            return new Rgb(
                base.red * inverse + layer.red * alpha,
                base.green * inverse + layer.green * alpha,
                base.blue * inverse + layer.blue * alpha
            );
        }

        private static Rgb overlay(Rgb base, Rgb layer) {
            return new Rgb(
                overlay(base.red, layer.red),
                overlay(base.green, layer.green),
                overlay(base.blue, layer.blue)
            );
        }

        private static double overlay(double base, double layer) {
            return base <= 0.5D ? 2.0D * base * layer : 1.0D - 2.0D * (1.0D - base) * (1.0D - layer);
        }

        /** Color blend mode using the requested Rec.709 luminance and gamut clipping. */
        private static Rgb withLuminance(Rgb color, double luminance) {
            double target = clamp(luminance);
            Rgb shifted = new Rgb(
                color.red + target - color.luminance709(),
                color.green + target - color.luminance709(),
                color.blue + target - color.luminance709()
            );
            return shifted.clipToGamut();
        }

        private Rgb clipToGamut() {
            double luminance = luminance709();
            double min = Math.min(red, Math.min(green, blue));
            double max = Math.max(red, Math.max(green, blue));
            Rgb clipped = this;
            if (min < 0.0D && luminance > min) {
                clipped = new Rgb(
                    luminance + (red - luminance) * luminance / (luminance - min),
                    luminance + (green - luminance) * luminance / (luminance - min),
                    luminance + (blue - luminance) * luminance / (luminance - min)
                );
            }
            max = Math.max(clipped.red, Math.max(clipped.green, clipped.blue));
            if (max > 1.0D && max > luminance) {
                clipped = new Rgb(
                    luminance + (clipped.red - luminance) * (1.0D - luminance) / (max - luminance),
                    luminance + (clipped.green - luminance) * (1.0D - luminance) / (max - luminance),
                    luminance + (clipped.blue - luminance) * (1.0D - luminance) / (max - luminance)
                );
            }
            return new Rgb(clamp(clipped.red), clamp(clipped.green), clamp(clipped.blue));
        }

        private double luminance709() {
            return 0.2126D * red + 0.7152D * green + 0.0722D * blue;
        }

        /** Hue(noise), saturation/lightness(base). Neutral noise has no hue and leaves base intact. */
        private static Rgb hueOf(Rgb noise, Rgb base) {
            Hsl noiseHsl = noise.toHsl();
            if (noiseHsl.saturation() < 0.000001D) return base;
            Hsl baseHsl = base.toHsl();
            return fromHsl(new Hsl(noiseHsl.hue(), baseHsl.saturation(), baseHsl.lightness()));
        }

        private Hsl toHsl() {
            double max = Math.max(red, Math.max(green, blue));
            double min = Math.min(red, Math.min(green, blue));
            double chroma = max - min;
            double lightness = (max + min) * 0.5D;
            if (chroma < 0.000001D) return new Hsl(0.0D, 0.0D, lightness);

            double saturation = chroma / (1.0D - Math.abs(2.0D * lightness - 1.0D));
            double hue;
            if (max == red) {
                hue = ((green - blue) / chroma) % 6.0D;
            } else if (max == green) {
                hue = (blue - red) / chroma + 2.0D;
            } else {
                hue = (red - green) / chroma + 4.0D;
            }
            hue /= 6.0D;
            if (hue < 0.0D) hue += 1.0D;
            return new Hsl(hue, saturation, lightness);
        }

        private static Rgb fromHsl(Hsl hsl) {
            double chroma = (1.0D - Math.abs(2.0D * hsl.lightness() - 1.0D)) * hsl.saturation();
            double hue = hsl.hue() * 6.0D;
            double secondary = chroma * (1.0D - Math.abs(hue % 2.0D - 1.0D));
            double red = 0.0D, green = 0.0D, blue = 0.0D;
            if (hue < 1.0D) {
                red = chroma;
                green = secondary;
            } else if (hue < 2.0D) {
                red = secondary;
                green = chroma;
            } else if (hue < 3.0D) {
                green = chroma;
                blue = secondary;
            } else if (hue < 4.0D) {
                green = secondary;
                blue = chroma;
            } else if (hue < 5.0D) {
                red = secondary;
                blue = chroma;
            } else {
                red = chroma;
                blue = secondary;
            }
            double match = hsl.lightness() - chroma * 0.5D;
            return new Rgb(red + match, green + match, blue + match);
        }

        private static int toChannel(double value) {
            return (int) Math.round(clamp(value) * 255.0D);
        }

        private static double clamp(double value) {
            return Math.max(0.0D, Math.min(1.0D, value));
        }
    }

    private record Hsl(double hue, double saturation, double lightness) {
    }
}
