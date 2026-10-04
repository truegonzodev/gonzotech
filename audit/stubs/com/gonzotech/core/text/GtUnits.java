package com.gonzotech.core.text;
import net.minecraft.network.chat.Component;
/** Только члены, которых касается PipeType и локальный SteamGenScreen typecheck. */
public final class GtUnits {
    public static final int GTU = 0xFFD84A;
    public static final int GTH = 0xFF6A4A;
    public static final int WATER = 0x4AA3FF;
    public static final int STEAM = 0xE8E8E8;
    public static final int ITEM = 0xC08A4A;
    public static final String U_GTU = "resource.gonzotech.first_wire.unit";
    public static final String U_GTH = "resource.gonzotech.first_heat_pipe.unit";
    public static final String U_MB = "resource.gonzotech.fluid.unit";
    public static final String U_ITEMS = "resource.gonzotech.first_item_pipe.unit";
    public static Component gthPair(Object value, Object capacity) { return null; }
    public static Component steamGenMaxGthIntake(Object value) { return null; }
    public static Component steamGenGthPerSteam(Object value) { return null; }
    public static Component steamGenWaterPerSteam(Object value) { return null; }
    public static Component steamGenRated(Object value) { return null; }
    public static Component waterPair(Object value, Object capacity) { return null; }
    public static Component steamPair(Object value, Object capacity) { return null; }
    private GtUnits() { }
}
