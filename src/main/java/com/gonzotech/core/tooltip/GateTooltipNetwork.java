package com.gonzotech.core.tooltip;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.chalkboard.advancement.RecipeUnlocks;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Server recipes are not fully available on the 1.21.4 client. Sync only gate metadata. */
@EventBusSubscriber(modid = GonzoTechMod.MOD_ID)
public final class GateTooltipNetwork {
    private static volatile Map<ResourceLocation, List<GateRequirement>> clientGates;
    private GateTooltipNetwork() {}

    /** Null means the server has not synchronized yet, not "ungated". */
    public static List<GateRequirement> bookGates(ResourceLocation item) {
        var current = clientGates;
        return current == null ? null : current.getOrDefault(item, List.of(GateRequirement.NONE));
    }

    public static void clearClient() { clientGates = null; }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(Payload.TYPE, Payload.CODEC,
                (payload, context) -> context.enqueueWork(() -> { clientGates = payload.gates(); }));
    }

    @SubscribeEvent
    public static void onSync(OnDatapackSyncEvent event) {
        var players = event.getRelevantPlayers().toList();
        if (players.isEmpty()) return;
        var server = event.getPlayerList().getServer();
        var context = SlotDisplayContext.fromLevel(server.overworld());
        RecipeGateIndex<ResourceLocation> index = new RecipeGateIndex<>();
        // Join and /reload only. Never scan all recipes on hover, and never infer output
        // from recipe id (e.g. motor_copper -> electric_motor, energy_module_gold -> energy_module).
        for (var holder : server.getRecipeManager().getRecipes()) {
            var gate = RecipeUnlocks.gateForRecipe(holder.id().location());
            try {
                for (var display : holder.value().display()) {
                    for (var stack : display.result().resolveForStacks(context)) {
                        if (!stack.isEmpty()) index.add(BuiltInRegistries.ITEM.getKey(stack.getItem()), gate);
                    }
                }
            } catch (RuntimeException ex) {
                // Optional diagnostics must not break login because another mod's display is malformed.
                LogUtils.getLogger().warn("Cannot inspect recipe display {} for gate tooltip", holder.id(), ex);
            }
        }
        Payload payload = new Payload(index.snapshot());
        players.forEach(player -> PacketDistributor.sendToPlayer(player, payload));
    }

    public record Payload(Map<ResourceLocation, List<GateRequirement>> gates) implements CustomPacketPayload {
        private static final int MAX_ITEMS = 16_384;
        private static final int MAX_RULES = 64;
        public static final Type<Payload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "tooltip_gates"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeVarInt(payload.gates.size());
                    payload.gates.forEach((item, rules) -> {
                        buf.writeResourceLocation(item);
                        buf.writeVarInt(rules.size());
                        for (var rule : rules) {
                            buf.writeVarInt(rule.discovery());
                            buf.writeEnum(rule.extra());
                        }
                    });
                }, buf -> {
                    int size = bounded(buf.readVarInt(), MAX_ITEMS);
                    Map<ResourceLocation, List<GateRequirement>> entries = new HashMap<>();
                    for (int i = 0; i < size; i++) {
                        ResourceLocation item = buf.readResourceLocation();
                        int count = bounded(buf.readVarInt(), MAX_RULES);
                        var rules = new java.util.ArrayList<GateRequirement>(count);
                        for (int j = 0; j < count; j++) {
                            rules.add(new GateRequirement(buf.readVarInt(), buf.readEnum(GateRequirement.Extra.class)));
                        }
                        entries.put(item, List.copyOf(rules));
                    }
                    return new Payload(entries);
                });

        public Payload {
            Map<ResourceLocation, List<GateRequirement>> copy = new HashMap<>();
            gates.forEach((id, rules) -> copy.put(id, List.copyOf(rules)));
            gates = Map.copyOf(copy);
        }
        private static int bounded(int value, int max) {
            if (value < 0 || value > max) throw new IllegalArgumentException("Oversized tooltip gate metadata");
            return value;
        }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
