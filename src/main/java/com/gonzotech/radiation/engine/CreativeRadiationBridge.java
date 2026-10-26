package com.gonzotech.radiation.engine;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Creative replaces slots from client snapshots. Only server-known removed material
 * can supply induced radiation; catalogue-created material starts clean. No item edits. */
public final class CreativeRadiationBridge {
    private static final Map<ServerPlayer, Session> ACTIVE = new WeakHashMap<>();
    private static final Map<ServerPlayer, List<Lot>> CURSOR = new WeakHashMap<>();
    private static final class Lot {
        final ItemStack identity, type;
        final ExternalRadiation.Dose dose;
        int remaining;
        Lot(ItemStack stack) { identity=stack; type=stack.copy(); dose=ExternalRadiation.read(stack); remaining=stack.getCount(); }
    }
    private static final class Target {
        final ItemStack stack;
        int need; double mass; long tick=Long.MIN_VALUE;
        Target(ItemStack stack) { this.stack=stack;need=stack.getCount(); }
        void take(Lot lot) {
            int n=Math.min(need,lot.remaining);
            if(n<=0) return;
            if(n==stack.getCount()) ExternalRadiation.copyTree(lot.type,stack,0);
            need-=n;lot.remaining-=n;mass+=lot.dose.rate()*n;tick=Math.max(tick,lot.dose.tick());
        }
    }
    private static final class Session {
        final List<Lot> lots=new ArrayList<>();
        final List<Lot> bySlot=new ArrayList<>();
        final List<ItemStack> drops=new ArrayList<>();
        boolean touched;
        Session(ServerPlayer player) {
            for(var slot:player.inventoryMenu.slots) {
                ItemStack s=slot.getItem(); Lot lot=s.isEmpty()?null:new Lot(s);
                bySlot.add(lot);if(lot!=null) lots.add(lot);
            }
            lots.addAll(CURSOR.getOrDefault(player,List.of()));
        }
    }
    public static void begin(ServerPlayer player) {
        if(!player.gameMode.isCreative() || player.containerMenu!=player.inventoryMenu) return;
        finish(player); ACTIVE.put(player,new Session(player));
    }
    public static void beforePacket(ServerPlayer player) {
        if(!player.gameMode.isCreative()) return;
        ACTIVE.computeIfAbsent(player,Session::new).touched=true;
    }
    public static void dropped(ServerPlayer player,ItemStack stack) {
        Session s=ACTIVE.get(player);if(s!=null && !stack.isEmpty()) s.drops.add(stack);
    }
    public static void finish(ServerPlayer player) { finish(player, 64, null); }
    public static void finish(ServerPlayer player, int cursorCount, Integer cursorFingerprint) {
        Session s=ACTIVE.remove(player);if(s==null || !s.touched) return;
        List<Target> targets=new ArrayList<>();
        Map<ItemStack,Target> indexed=new IdentityHashMap<>();
        for(var slot:player.inventoryMenu.slots) if(!slot.getItem().isEmpty()) {
            Target t=new Target(slot.getItem());targets.add(t);indexed.put(t.stack,t);
        }
        for(ItemStack drop:s.drops) if(!drop.isEmpty()) targets.add(new Target(drop));
        // Preserve unchanged objects first, then same-slot replacements, then transfers.
        for(Lot lot:s.lots) { Target t=indexed.get(lot.identity);if(t!=null) t.take(lot); }
        for(int i=0;i<s.bySlot.size();i++) {
            Lot lot=s.bySlot.get(i);Target t=indexed.get(player.inventoryMenu.slots.get(i).getItem());
            if(lot!=null && t!=null && ItemStack.isSameItemSameComponents(lot.type,t.stack)) t.take(lot);
        }
        for(Target t:targets) {
            for(Lot lot:s.lots) if(t.need>0 && lot.remaining>0 && ItemStack.isSameItemSameComponents(lot.type,t.stack)) t.take(lot);
            ExternalRadiation.write(t.stack,t.mass/t.stack.getCount(),t.tick);
        }
        // Vanilla creative cursor is client-owned. Retain bounded metadata, never an item.
        List<Lot> pending=new ArrayList<>();int budget=Math.clamp(cursorCount,0,64);
        for(Lot lot:s.lots) if(lot.remaining>0 && budget>0) {
            if(cursorFingerprint != null && RadiationViewNetwork.fingerprint(lot.type.copyWithCount(cursorCount),player.registryAccess()) != cursorFingerprint) continue;
            lot.remaining=Math.min(lot.remaining,budget);budget-=lot.remaining;pending.add(lot);
        }
        if(pending.isEmpty()) CURSOR.remove(player);else CURSOR.put(player,pending);
    }
    public static void forget(ServerPlayer player) { finish(player);CURSOR.remove(player); }
    public static void clear() { ACTIVE.clear();CURSOR.clear(); }
    private CreativeRadiationBridge() {}
}
