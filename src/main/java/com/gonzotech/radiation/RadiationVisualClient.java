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

    private static void spawnFaces(ClientLevel level, BlockPos source, float emission) {
        BlockState state = level.getBlockState(source);
        double strength = Math.min(1.0, Math.log1p(Math.max(0.0, emission) / 0.001) / Math.log1p(100.0));
        int attempts = Math.max(1, Math.min(8, 1 + (int) (strength * 7.0)));
        for (Direction face : Direction.values()) {
            BlockPos next = source.relative(face);
            BlockState neighbor = level.getBlockState(next);
            // Cheap pre-cull: never create a particle on a fully closed face.
            if (!neighbor.isAir() && neighbor.canOcclude()) continue;
            for (int i = 0; i < attempts / 2 + 1; i++) {
                double x = source.getX() + 0.5 + face.getStepX() * 0.52 + (level.random.nextDouble() - 0.5) * 0.45;
                double y = source.getY() + 0.5 + face.getStepY() * 0.52 + (level.random.nextDouble() - 0.5) * 0.45;
                double z = source.getZ() + 0.5 + face.getStepZ() * 0.52 + (level.random.nextDouble() - 0.5) * 0.45;
                double speed = 0.006 + strength * 0.035;
                level.addParticle(ModParticles.RADIATION_MIST.get(), x, y, z,
                        face.getStepX() * speed, face.getStepY() * speed, face.getStepZ() * speed);
            }
        }
    }
}
