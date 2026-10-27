package net.neoforged.neoforge.event.tick;
public abstract class PlayerTickEvent {
 public net.minecraft.world.entity.player.Player getEntity() { return null; }
 public static class Post extends PlayerTickEvent {}
}
