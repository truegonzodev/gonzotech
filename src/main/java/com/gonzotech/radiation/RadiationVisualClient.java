package com.gonzotech.radiation;

import com.gonzotech.core.registry.ModItems;
import com.gonzotech.core.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.List;

/** Client-only source-to-mist emitter. */
@EventBusSubscriber(modid = "gonzotech", value = Dist.CLIENT)
public final class RadiationVisualClient {
    private static List<RadiationVisualPayload.Source> sources = List.of();
    private static int pulse;

    private RadiationVisualClient() {}

    public static void accept(RadiationVisualPayload payload) {
        sources = List.copyOf(payload.sources());
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || !holdingInstrument(mc)) {
            return;
        }
        if ((pulse++ & 1) != 0) return;
        for (RadiationVisualPayload.Source source : sources) {
            BlockPos pos = BlockPos.of(source.pos());
            double distance = pos.distToCenterSqr(mc.player.getX(), mc.player.getY(), mc.player.getZ());
            if (distance > 18.0 * 18.0) continue;
            spawnFaces(level, pos, source.emission());
        }
    }

    private static boolean holdingInstrument(Minecraft mc) {
        return mc.player.getMainHandItem().is(ModItems.DOSIMETER.get())
                || mc.player.getMainHandItem().is(ModItems.TELIFON.get())
                || mc.player.getOffhandItem().is(ModItems.DOSIMETER.get())
                || mc.player.getOffhandItem().is(ModItems.TELIFON.get());
    }

    private static final double RADIUM_BLOCK = 675.0 * RadUnits.MILLI;

    private static void spawnFaces(ClientLevel level, BlockPos source, float emission) {
        double ratio = Math.max(0.0, emission) / RADIUM_BLOCK;
        // Reference point: a 675 mZt/s radium block remains exactly the
        // current visual. We interpolate in log space for weaker sources.
        double visualFactor;
        if (ratio >= 1.0) {
            visualFactor = Math.min(1.6, 1.0 + 0.6 * Math.log1p(ratio) / Math.log1p(20.0));
        } else if (ratio >= 1.0 / 20.0) {
            visualFactor = 0.30 + 0.70 * Math.log(ratio * 20.0) / Math.log(20.0);
        } else {
            visualFactor = 0.18 + 0.12 * Math.log(ratio * 3750.0) / Math.log(187.5);
        }
        visualFactor = Math.max(0.18, visualFactor);
        double speedFactor = ratio > 1.0 ? Math.sqrt(ratio) : visualFactor;
        int attempts = Math.max(1, Math.min(13, (int) Math.round(8.0 * visualFactor)));
        for (Direction face : Direction.values()) {
            BlockPos next = source.relative(face);
            BlockState neighbor = level.getBlockState(next);
            // Ordinary stone/earth is not a radiation shield: particles pass
            // through it. Only an actually occluding shielding material closes
            // a face before spawn; this is cheaper than spawning hidden mist.
            if (neighbor.canOcclude() && RadMaterials.blockFactor(neighbor) < 1.0) continue;
            for (int i = 0; i < attempts / 2 + 1; i++) {
                double x = source.getX() + 0.5 + face.getStepX() * 0.52 + (level.random.nextDouble() - 0.5) * 0.45;
                double y = source.getY() + 0.5 + face.getStepY() * 0.52 + (level.random.nextDouble() - 0.5) * 0.45;
                double z = source.getZ() + 0.5 + face.getStepZ() * 0.52 + (level.random.nextDouble() - 0.5) * 0.45;
                double speed = 0.006 + 0.035 * speedFactor;
                level.addParticle(ModParticles.RADIATION_MIST.get(), x, y, z,
                        face.getStepX() * speed, face.getStepY() * speed, face.getStepZ() * speed);
            }
        }
    }
}
