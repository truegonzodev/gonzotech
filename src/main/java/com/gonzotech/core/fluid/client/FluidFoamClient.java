package com.gonzotech.core.fluid.client;

import com.gonzotech.core.fluid.FoamPopulation;
import com.gonzotech.core.fluid.FoamSurface;
import com.gonzotech.core.fluid.ModFluidBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Random;

/** One client-local foam population per connected exposed surface, not animateTick bursts. */
@EventBusSubscriber(modid = "gonzotech", value = Dist.CLIENT)
public final class FluidFoamClient {
    private static final ModFluidBlock.Kind[] KINDS = ModFluidBlock.Kind.values();
    private static final Random RANDOM = new Random();
    private static final FoamPopulation POPULATION = new FoamPopulation();
    private static ClientLevel world;
    private static FoamSurface.Scan scan;
    private static FoamSurface.Snapshot surface = FoamSurface.EMPTY;
    private static FoamSurface.Pos centre;
    private static int[] budgets = new int[0];
    private static double density = -1;

    private FluidFoamClient() {}

    private record DustLife(Particle particle) implements FoamPopulation.Life {
        public boolean alive() { return particle.isAlive(); }
        public int lifetime() { return particle.getLifetime(); }
        public void remove() { particle.remove(); }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (world != mc.level) {
            reset();
            world = mc.level;
        }
        if (world == null || mc.player == null || mc.isPaused()) return;
        // createParticle gives us the real handle, but bypasses LevelRenderer's density
        // filtering. Apply the user's setting once here, not as repeated failed retries.
        double requestedDensity = switch (mc.options.particles().get()) {
            case ALL -> 1.0;
            case DECREASED -> 0.5;
            case MINIMAL -> 0.0;
        };
        if (requestedDensity == 0) { reset(); return; }
        var camera = mc.getCameraEntity();
        BlockPos observerBlock = camera == null ? mc.player.blockPosition() : camera.blockPosition();
        var observer = new FoamSurface.Pos(observerBlock.getX(), observerBlock.getY(), observerBlock.getZ());
        if (centre != null && !centre.near(observer)) reset(); // teleport/large displacement
        if (scan == null) scan = new FoamSurface.Scan(observer);
        if (!scan.centre().near(observer)) scan = new FoamSurface.Scan(observer);
        scan.advance(FluidFoamClient::surfaceKind);
        boolean updated = false;
        if (scan.done()) {
            surface = scan.snapshot();
            centre = scan.centre();
            scan = null;
            updated = true;
        }
        if (updated || density != requestedDensity) {
            density = requestedDensity;
            budgets = FoamPopulation.budgets(surface, density);
        }
        POPULATION.tick(surface, budgets, RANDOM, (cell, kind, scale) -> spawn(mc, observer, cell, kind, scale));
    }

    private static void reset() {
        POPULATION.clear();
        scan = null;
        surface = FoamSurface.EMPTY;
        centre = null;
        budgets = new int[0];
        density = -1;
    }

    private static int surfaceKind(FoamSurface.Pos cell) {
        BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
        if (world.isOutsideBuildHeight(pos) || !world.hasChunkAt(pos)) return -1;
        var state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof ModFluidBlock fluid) || state.getFluidState().isEmpty()) return -1;
        BlockPos above = pos.above();
        var roof = world.getBlockState(above);
        if (!roof.getFluidState().isEmpty() || roof.isFaceSturdy(world, above, Direction.DOWN)) return -1;
        return fluid.foamKind().ordinal();
    }

    private static FoamPopulation.Life spawn(Minecraft mc, FoamSurface.Pos observer, FoamSurface.Pos cell,
                                              int kind, float scale) {
        // Revalidate the selected cell immediately: a cached puddle must not spawn
        // over a removed/replaced/covered fluid or in an unloaded/distant chunk.
        if (!cell.near(observer) || surfaceKind(cell) != kind) return null;
        BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
        double height = world.getFluidState(pos).getHeight(world, pos);
        double x = pos.getX() + RANDOM.nextDouble();
        double z = pos.getZ() + RANDOM.nextDouble();
        double y = pos.getY() + height + RANDOM.nextDouble() * 0.05;
        // Preserve vanilla Dust physics, sprites, colour variation and scale-dependent
        // lifetime. Do not override velocity/lifetime after the vanilla factory runs.
        Particle particle = mc.particleEngine.createParticle(new DustParticleOptions(KINDS[kind].particleColor, scale),
                x, y, z, 0.0, 0.015, 0.0);
        return particle == null ? null : new DustLife(particle);
    }
}
