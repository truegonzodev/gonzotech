package com.gonzotech.radiation.engine;

import net.minecraft.world.item.ItemStack;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Runtime view of owner-persisted radiation. Never writes an ItemStack.
 * Copies are passive views, NOT registered sources: only real inventories/entities
 * are traversed by RadiationSystem. The copy hook is mandatory; this is not the old
 * deferred component cache. There is no quiet period or flush back into items.
 */
public final class ExternalRadiation {
    public record Dose(double rate, long tick) {}
    private static final Map<ItemStack, Dose> VIEWS = new WeakHashMap<>();
    private static final Map<ItemStack, Dose> CONTENTS = new WeakHashMap<>();
    private static final Dose CLEAN = new Dose(0, Long.MIN_VALUE);
    private ExternalRadiation() {}

    public static synchronized Dose read(ItemStack stack) {
        return VIEWS.getOrDefault(stack, CLEAN);
    }
    public static synchronized void write(ItemStack stack, double rate, long tick) {
        if (stack == ItemStack.EMPTY) return;
        rate = DoseMath.finite(rate);
        if (rate == 0) VIEWS.remove(stack);
        else VIEWS.put(stack, new Dose(rate, tick));
    }
    public static void rate(ItemStack stack, double value) { write(stack, value, read(stack).tick()); }
    public static void tick(ItemStack stack, long tick) {
        Dose old = read(stack);
        if (old.rate() > 0) write(stack, old.rate(), tick);
    }
    public static void copy(ItemStack from, ItemStack to) {
        if (to == ItemStack.EMPTY || from == to) return;
        Dose old = read(from);
        write(to, old.rate(), old.tick());
        writeContents(to, readContents(from));
        MenuDoseTransaction.copied(from, to);
    }
    public static void copyTree(ItemStack from, ItemStack to, int depth) {
        copy(from,to);
        if(depth>=4) return;
        var a=com.gonzotech.radiation.RadSources.contentsOf(from);
        var b=com.gonzotech.radiation.RadSources.contentsOf(to);
        if(a.size()!=b.size()) return;
        for(int i=0;i<a.size();i++) if(ItemStack.matches(a.get(i),b.get(i))) copyTree(a.get(i),b.get(i),depth+1);
    }
    /** Call BEFORE growing destination; does not consume or modify either item. */
    public static void merge(ItemStack destination, ItemStack source, int added) {
        if (added <= 0 || destination == source) return;
        Dose a = read(destination), b = read(source);
        write(destination, DoseMath.merge(a.rate(), destination.getCount(), b.rate(), added),
                Math.max(a.tick(), b.tick()));
    }
    /** Newly manufactured material has no induced radiation unless a recipe adapter supplies it. */
    public static void dilute(ItemStack destination, int added) {
        merge(destination, ItemStack.EMPTY, added);
    }
    public static synchronized Dose readContents(ItemStack carrier) { return CONTENTS.getOrDefault(carrier, CLEAN); }
    public static synchronized void writeContents(ItemStack carrier, Dose dose) {
        if (carrier == ItemStack.EMPTY) return;
        if (DoseMath.finite(dose.rate()) == 0) CONTENTS.remove(carrier);
        else CONTENTS.put(carrier, dose);
    }
    public static ItemStack previewContents(ItemStack carrier, ItemStack content) {
        Dose dose = readContents(carrier); write(content, dose.rate(), dose.tick()); return content;
    }
    public static void insertContents(ItemStack carrier, int oldCount, ItemStack source, int added) {
        Dose a = readContents(carrier), b = read(source);
        writeContents(carrier, new Dose(DoseMath.merge(a.rate(), oldCount, b.rate(), added), Math.max(a.tick(), b.tick())));
    }
    public static void clearContents(ItemStack carrier) { writeContents(carrier, CLEAN); }
    public static synchronized void clear() { VIEWS.clear(); CONTENTS.clear(); }
}
