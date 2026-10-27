package net.neoforged.neoforge.event.entity.player;
public abstract class PlayerEvent {
 public net.minecraft.world.entity.player.Player getEntity() { return null; }
 public static class PlayerLoggedOutEvent extends PlayerEvent {}
 public static class ItemCraftedEvent extends PlayerEvent {
  public net.minecraft.world.item.ItemStack getCrafting() { return null; }
 }
}
