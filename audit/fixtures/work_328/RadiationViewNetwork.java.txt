package com.gonzotech.radiation.engine;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.radiation.RadSources;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import java.util.*;

/** Read-only presentation protocol. No item payloads, client rates or world addresses. */
@EventBusSubscriber(modid = GonzoTechMod.MOD_ID)
public final class RadiationViewNetwork {
    private static final int MAX_SLOTS = 1024;
    private static final Map<ServerPlayer, Long> ACK = new WeakHashMap<>();
    private static final Map<ServerPlayer, View> LAST = new WeakHashMap<>();
    private RadiationViewNetwork() {}
    public record Entry(int slot, int fingerprint, double rate) {}
    public record View(int menu, int state, long ack, List<Entry> inventory, List<Entry> slots, Entry carried)
            implements CustomPacketPayload {
        public static final Type<View> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "item_radiation_view"));
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = StreamCodec.of((b, v) -> {
            b.writeVarInt(v.menu); b.writeVarInt(v.state); b.writeVarLong(v.ack);
            writeEntries(b, v.inventory); writeEntries(b, v.slots); writeEntry(b, v.carried);
        }, b -> new View(b.readVarInt(), b.readVarInt(), b.readVarLong(), readEntries(b), readEntries(b), readEntry(b)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Fence(int menu, long sequence, boolean begin, int cursorCount, int cursorFingerprint) implements CustomPacketPayload {
        public static final Type<Fence> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "item_radiation_fence"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Fence> CODEC = StreamCodec.of((b, v) -> {
            b.writeVarInt(v.menu); b.writeVarLong(v.sequence); b.writeBoolean(v.begin); b.writeVarInt(v.cursorCount); b.writeInt(v.cursorFingerprint);
        }, b -> new Fence(b.readVarInt(), b.readVarLong(), b.readBoolean(), b.readVarInt(), b.readInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(View.TYPE, View.CODEC, (v, c) -> c.enqueueWork(() ->
                com.gonzotech.radiation.client.RadiationPanel.accept(v)));
        registrar.playToServer(Fence.TYPE, Fence.CODEC, (v, c) -> c.enqueueWork(() -> {
            if (c.player() instanceof ServerPlayer player
                    && v.sequence > ACK.getOrDefault(player, 0L) && v.sequence > 0
                    && v.cursorCount >= 0 && v.cursorCount <= 64) {
                if (v.begin) {
                    if (v.menu == player.containerMenu.containerId) CreativeRadiationBridge.begin(player);
                } else {
                    if (v.menu == player.inventoryMenu.containerId)
                        CreativeRadiationBridge.finish(player, v.cursorCount, v.cursorFingerprint);
                    // Closing a screen can change the menu before its end fence arrives.
                    // The barrier still acknowledges processing; it never writes a slot.
                    ACK.put(player, v.sequence);
                }
            }
        }));
    }
    private static void writeEntry(RegistryFriendlyByteBuf b, Entry e) {
        b.writeVarInt(e.slot); b.writeInt(e.fingerprint); b.writeDouble(e.rate);
    }
    private static Entry readEntry(RegistryFriendlyByteBuf b) {
        return new Entry(b.readVarInt(), b.readInt(), DoseMath.finite(b.readDouble()));
    }
    private static void writeEntries(RegistryFriendlyByteBuf b, List<Entry> list) {
        b.writeVarInt(list.size()); for (Entry e : list) writeEntry(b, e);
    }
    private static List<Entry> readEntries(RegistryFriendlyByteBuf b) {
        int n = b.readVarInt();
        if (n < 0 || n > MAX_SLOTS) throw new IllegalArgumentException("Invalid radiation view length");
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(readEntry(b));
        return List.copyOf(list);
    }
    private record Fingerprint(int count, net.minecraft.core.component.DataComponentPatch patch, int hash) {}
    private static final Map<ItemStack, Fingerprint> FINGERPRINTS = new WeakHashMap<>();
    public static int fingerprint(ItemStack stack, net.minecraft.core.HolderLookup.Provider registries) {
        if (stack.isEmpty()) return 0;
        var patch = stack.getComponentsPatch();
        synchronized (FINGERPRINTS) {
            var cached = FINGERPRINTS.get(stack);
            if (cached != null && cached.count == stack.getCount() && cached.patch.equals(patch)) return cached.hash;
            int hash = stack.save(registries).hashCode();
            FINGERPRINTS.put(stack, new Fingerprint(stack.getCount(), patch, hash));
            return hash;
        }
    }
    private static Entry entry(ServerPlayer player, int slot, ItemStack stack) {
        return new Entry(slot, fingerprint(stack, player.registryAccess()), RadSources.emissionDeep(stack));
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            var menu = player.containerMenu;
            View old = LAST.get(player);
            long ack = ACK.getOrDefault(player, 0L);
            boolean changedMenu = old == null || old.menu != menu.containerId || old.state != menu.getStateId() || old.ack != ack;
            if (!changedMenu && event.getServer().getTickCount() % 5 != 0) continue;
            List<Entry> inventory = new ArrayList<>(), slots = new ArrayList<>();
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) inventory.add(entry(player, i, player.getInventory().getItem(i)));
            for (int i = 0; i < Math.min(menu.slots.size(), MAX_SLOTS); i++) slots.add(entry(player, i, menu.slots.get(i).getItem()));
            View view = new View(menu.containerId, menu.getStateId(), ack, List.copyOf(inventory), List.copyOf(slots), entry(player, -1, menu.getCarried()));
            if (!view.equals(old)) { PacketDistributor.sendToPlayer(player, view); LAST.put(player, view); }
        }
    }
    @SubscribeEvent public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CreativeRadiationBridge.forget(player); ACK.remove(player); LAST.remove(player);
        }
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) {
        ACK.clear(); LAST.clear(); FINGERPRINTS.clear(); ExternalRadiation.clear(); CreativeRadiationBridge.clear();
    }
}
