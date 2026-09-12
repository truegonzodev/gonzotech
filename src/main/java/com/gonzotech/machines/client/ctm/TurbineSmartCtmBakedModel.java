package com.gonzotech.machines.client.ctm;

import com.gonzotech.machines.block.TurbineCasingBlock;
import com.gonzotech.machines.block.TurbinePartBlock;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Texture-only Smart CTM for the formed turbine casing.
 *
 * <p>At chunk mesh build time {@link #getModelData(BlockAndTintGetter,
 * BlockPos, BlockState, ModelData)} calculates the eight face-local neighbours
 * of every cube side. {@link #getQuads(BlockState, Direction, RandomSource,
 * ModelData, RenderType)} then only looks up immutable cached quads by the
 * resulting eight-bit mask. No blockstate carries CTM variants and no work is
 * done per rendered frame.</p>
 *
 * <p>Each face is partitioned into non-overlapping 2/12/2 texel regions. A
 * region has exactly one quad using the base, line, outer-corner or
 * inner-corner sprite, so this is neither overlay geometry nor z-fighting.</p>
 */
public final class TurbineSmartCtmBakedModel implements IDynamicBakedModel {

    /** One immutable set of six 8-neighbour masks is attached to a chunk-build model request. */
    private static final ModelProperty<FaceMasks> FACE_MASKS = new ModelProperty<>();

    private static final int TOP = 1;
    private static final int RIGHT = 1 << 1;
    private static final int BOTTOM = 1 << 2;
    private static final int LEFT = 1 << 3;
    private static final int NORTH_WEST = 1 << 4;
    private static final int NORTH_EAST = 1 << 5;
    private static final int SOUTH_EAST = 1 << 6;
    private static final int SOUTH_WEST = 1 << 7;

    private static final Rect CENTER = new Rect(2, 2, 14, 14);
    private static final Rect TOP_EDGE = new Rect(2, 0, 14, 2);
    private static final Rect RIGHT_EDGE = new Rect(14, 2, 16, 14);
    private static final Rect BOTTOM_EDGE = new Rect(2, 14, 14, 16);
    private static final Rect LEFT_EDGE = new Rect(0, 2, 2, 14);
    private static final Rect NORTH_WEST_CORNER = new Rect(0, 0, 2, 2);
    private static final Rect NORTH_EAST_CORNER = new Rect(14, 0, 16, 2);
    private static final Rect SOUTH_EAST_CORNER = new Rect(14, 14, 16, 16);
    private static final Rect SOUTH_WEST_CORNER = new Rect(0, 14, 2, 16);

    private final TextureAtlasSprite base;
    private final TextureAtlasSprite line;
    private final TextureAtlasSprite outerCorner;
    private final TextureAtlasSprite innerCorner;
    private final TextureAtlasSprite particle;
    private final boolean ambientOcclusion;
    private final boolean blockLight;
    private final ItemTransforms transforms;

    /** Shared immutable result cache. Key has no world position, only the visual topology. */
    private final ConcurrentMap<FaceKey, List<BakedQuad>> cachedFaces = new ConcurrentHashMap<>();

    public TurbineSmartCtmBakedModel(TextureAtlasSprite base, TextureAtlasSprite line,
                                     TextureAtlasSprite outerCorner, TextureAtlasSprite innerCorner,
                                     TextureAtlasSprite particle, boolean ambientOcclusion,
                                     boolean blockLight, ItemTransforms transforms) {
        this.base = base;
        this.line = line;
        this.outerCorner = outerCorner;
        this.innerCorner = innerCorner;
        this.particle = particle;
        this.ambientOcclusion = ambientOcclusion;
        this.blockLight = blockLight;
        this.transforms = transforms;
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        if (!(state.getBlock() instanceof TurbineCasingBlock) || !state.getValue(TurbinePartBlock.FORMED)) {
            return modelData;
        }
        return modelData.derive().with(FACE_MASKS, FaceMasks.at(level, pos)).build();
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                                     RandomSource random, ModelData modelData,
                                     @Nullable RenderType renderType) {
        // This model deliberately contains only cullable cube faces. A null side
        // asks for unculled geometry, of which the casing has none.
        if (side == null || state == null || !state.getValue(TurbinePartBlock.FORMED)) {
            return List.of();
        }
        FaceMasks masks = modelData.get(FACE_MASKS);
        int mask = masks == null ? 0 : masks.forFace(side);
        return cachedFaces.computeIfAbsent(new FaceKey(side, mask), this::buildFace);
    }

    @Override
    public boolean useAmbientOcclusion() {
        return ambientOcclusion;
    }

    @Override
    public boolean isGui3d() {
        return true;
    }

    @Override
    public boolean usesBlockLight() {
        return blockLight;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return particle;
    }

    @Override
    public ItemTransforms getTransforms() {
        return transforms;
    }

    private List<BakedQuad> buildFace(FaceKey key) {
        int mask = key.mask;
        // The common fully-connected case needs just one ordinary cube-face quad.
        if (mask == 0xFF) {
            return List.of(quad(key.face, base, new Rect(0, 0, 16, 16), UvMap.BASE));
        }

        boolean top = (mask & TOP) != 0;
        boolean right = (mask & RIGHT) != 0;
        boolean bottom = (mask & BOTTOM) != 0;
        boolean left = (mask & LEFT) != 0;

        List<BakedQuad> quads = new ArrayList<>(9);
        addCorner(quads, key.face, NORTH_WEST_CORNER, top, left, (mask & NORTH_WEST) != 0, Edge.TOP, Edge.LEFT);
        addCorner(quads, key.face, NORTH_EAST_CORNER, top, right, (mask & NORTH_EAST) != 0, Edge.TOP, Edge.RIGHT);
        addCorner(quads, key.face, SOUTH_EAST_CORNER, bottom, right, (mask & SOUTH_EAST) != 0, Edge.BOTTOM, Edge.RIGHT);
        addCorner(quads, key.face, SOUTH_WEST_CORNER, bottom, left, (mask & SOUTH_WEST) != 0, Edge.BOTTOM, Edge.LEFT);

        quads.add(top ? quad(key.face, base, TOP_EDGE, UvMap.BASE) : lineQuad(key.face, TOP_EDGE, Edge.TOP));
        quads.add(right ? quad(key.face, base, RIGHT_EDGE, UvMap.BASE) : lineQuad(key.face, RIGHT_EDGE, Edge.RIGHT));
        quads.add(bottom ? quad(key.face, base, BOTTOM_EDGE, UvMap.BASE) : lineQuad(key.face, BOTTOM_EDGE, Edge.BOTTOM));
        quads.add(left ? quad(key.face, base, LEFT_EDGE, UvMap.BASE) : lineQuad(key.face, LEFT_EDGE, Edge.LEFT));
        quads.add(quad(key.face, base, CENTER, UvMap.BASE));
        return List.copyOf(quads);
    }

    /** Resolves one of the four 2×2 regions using the exact A/B/diagonal truth table. */
    private void addCorner(List<BakedQuad> target, Direction face, Rect region,
                           boolean firstConnected, boolean secondConnected, boolean diagonalConnected,
                           Edge firstEdge, Edge secondEdge) {
        if (!firstConnected && !secondConnected) {
            target.add(cornerQuad(face, outerCorner, region));
        } else if (firstConnected && secondConnected && !diagonalConnected) {
            // [у][к][у] / [к][д][к] / [у][к][у]: two casing neighbours
            // exist but the diagonal is a service-node hole.
            target.add(cornerQuad(face, innerCorner, region));
        } else if (!firstConnected) {
            target.add(lineQuad(face, region, firstEdge));
        } else if (!secondConnected) {
            target.add(lineQuad(face, region, secondEdge));
        } else {
            target.add(quad(face, base, region, UvMap.BASE));
        }
    }

    private BakedQuad lineQuad(Direction face, Rect region, Edge edge) {
        return quad(face, line, region, switch (edge) {
            case TOP -> UvMap.TOP_LINE;
            case RIGHT -> UvMap.RIGHT_LINE;
            case BOTTOM -> UvMap.BOTTOM_LINE;
            case LEFT -> UvMap.LEFT_LINE;
        });
    }

    /** Rotates the canonical top-left 2×2 corner sprite into this face-local corner. */
    private BakedQuad cornerQuad(Direction face, TextureAtlasSprite sprite, Rect region) {
        boolean left = region.minU == 0;
        boolean top = region.minV == 0;
        return quad(face, sprite, region, (u, v) -> new Uv(left ? u : 16 - u, top ? v : 16 - v));
    }

    private BakedQuad quad(Direction face, TextureAtlasSprite sprite, Rect region, UvMap mapping) {
        QuadBakingVertexConsumer builder = new QuadBakingVertexConsumer();
        builder.setSprite(sprite);
        builder.setDirection(face);
        builder.setShade(true);
        builder.setHasAmbientOcclusion(ambientOcclusion);

        // Counter-clockwise when viewed from the exterior: bottom-left,
        // bottom-right, top-right, top-left in face-local texture space.
        addVertex(builder, face, sprite, region.minU, region.maxV, mapping);
        addVertex(builder, face, sprite, region.maxU, region.maxV, mapping);
        addVertex(builder, face, sprite, region.maxU, region.minV, mapping);
        addVertex(builder, face, sprite, region.minU, region.minV, mapping);
        return builder.bakeQuad();
    }

    private static void addVertex(QuadBakingVertexConsumer builder, Direction face,
                                  TextureAtlasSprite sprite, float localU, float localV, UvMap mapping) {
        Position position = Position.onFace(face, localU, localV);
        Uv uv = mapping.map(localU, localV);
        builder.addVertex(position.x, position.y, position.z)
            .setColor(1.0F, 1.0F, 1.0F, 1.0F)
            .setUv(sprite.getU(uv.u), sprite.getV(uv.v))
            .setLight(0)
            .setNormal(face.getStepX(), face.getStepY(), face.getStepZ());
    }

    private static boolean connected(BlockAndTintGetter level, BlockPos pos) {
        BlockState neighbour = level.getBlockState(pos);
        return neighbour.getBlock() instanceof TurbineCasingBlock
            && neighbour.getValue(TurbinePartBlock.FORMED);
    }

    /**
     * World-space axes for a face-local image. The mapping intentionally
     * matches vanilla cube UV orientation, including the 180° DOWN-face turn.
     */
    private static FaceAxes axes(Direction face) {
        return switch (face) {
            case DOWN -> new FaceAxes(Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST);
            case UP -> new FaceAxes(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
            case NORTH -> new FaceAxes(Direction.UP, Direction.WEST, Direction.DOWN, Direction.EAST);
            case SOUTH -> new FaceAxes(Direction.UP, Direction.EAST, Direction.DOWN, Direction.WEST);
            case WEST -> new FaceAxes(Direction.UP, Direction.SOUTH, Direction.DOWN, Direction.NORTH);
            case EAST -> new FaceAxes(Direction.UP, Direction.NORTH, Direction.DOWN, Direction.SOUTH);
        };
    }

    /** Immutable six-face payload; no arrays escape into ModelData. */
    private record FaceMasks(int down, int up, int north, int south, int west, int east) {
        static FaceMasks at(BlockAndTintGetter level, BlockPos pos) {
            return new FaceMasks(
                mask(level, pos, Direction.DOWN),
                mask(level, pos, Direction.UP),
                mask(level, pos, Direction.NORTH),
                mask(level, pos, Direction.SOUTH),
                mask(level, pos, Direction.WEST),
                mask(level, pos, Direction.EAST)
            );
        }

        int forFace(Direction face) {
            return switch (face) {
                case DOWN -> down;
                case UP -> up;
                case NORTH -> north;
                case SOUTH -> south;
                case WEST -> west;
                case EAST -> east;
            };
        }

        private static int mask(BlockAndTintGetter level, BlockPos pos, Direction face) {
            FaceAxes axes = axes(face);
            int result = 0;
            if (connected(level, pos.relative(axes.top))) result |= TOP;
            if (connected(level, pos.relative(axes.right))) result |= RIGHT;
            if (connected(level, pos.relative(axes.bottom))) result |= BOTTOM;
            if (connected(level, pos.relative(axes.left))) result |= LEFT;
            if (connected(level, pos.relative(axes.top).relative(axes.left))) result |= NORTH_WEST;
            if (connected(level, pos.relative(axes.top).relative(axes.right))) result |= NORTH_EAST;
            if (connected(level, pos.relative(axes.bottom).relative(axes.right))) result |= SOUTH_EAST;
            if (connected(level, pos.relative(axes.bottom).relative(axes.left))) result |= SOUTH_WEST;
            return result;
        }
    }

    private record FaceKey(Direction face, int mask) {
    }

    private record FaceAxes(Direction top, Direction right, Direction bottom, Direction left) {
    }

    private record Rect(float minU, float minV, float maxU, float maxV) {
    }

    private record Uv(float u, float v) {
    }

    @FunctionalInterface
    private interface UvMap {
        Uv BASE = Uv::new;
        Uv TOP_LINE = Uv::new;
        Uv RIGHT_LINE = (u, v) -> new Uv(v, 16 - u);
        Uv BOTTOM_LINE = (u, v) -> new Uv(u, 16 - v);
        Uv LEFT_LINE = (u, v) -> new Uv(v, u);

        Uv map(float u, float v);
    }

    private enum Edge {
        TOP,
        RIGHT,
        BOTTOM,
        LEFT
    }

    /** A vertex position reconstructed from vanilla's face-local cube UV convention. */
    private record Position(float x, float y, float z) {
        static Position onFace(Direction face, float u, float v) {
            return switch (face) {
                case DOWN -> new Position(u / 16.0F, 0.0F, 1.0F - v / 16.0F);
                case UP -> new Position(u / 16.0F, 1.0F, v / 16.0F);
                case NORTH -> new Position(1.0F - u / 16.0F, 1.0F - v / 16.0F, 0.0F);
                case SOUTH -> new Position(u / 16.0F, 1.0F - v / 16.0F, 1.0F);
                case WEST -> new Position(0.0F, 1.0F - v / 16.0F, u / 16.0F);
                case EAST -> new Position(1.0F, 1.0F - v / 16.0F, 1.0F - u / 16.0F);
            };
        }
    }
}
