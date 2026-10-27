package com.gonzotech.core.event;
/** Only the two existing gate hooks used by TierThreeCrafting; not their implementation. */
public final class Phase3Events {
 public static boolean extraGateMet(net.minecraft.server.level.ServerPlayer player, net.minecraft.world.item.Item item) { return false; }
 public static void grantBotchedMechanism(net.minecraft.server.level.ServerPlayer player, int count) {}
}
