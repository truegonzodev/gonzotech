package com.gonzotech.mixin;
import com.gonzotech.radiation.engine.CreativeRadiationBridge;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class CreativeRadiationTransferMixin {
 @Shadow public ServerPlayer player;
 @Inject(method="handleSetCreativeModeSlot",at=@At("HEAD"))
 private void gonzo$before(ServerboundSetCreativeModeSlotPacket packet,CallbackInfo ci) {
  if(player.getServer()!=null && player.getServer().isSameThread()) CreativeRadiationBridge.beforePacket(player);
 }
 @Redirect(method="handleSetCreativeModeSlot",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerPlayer;drop(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/item/ItemEntity;"))
 private ItemEntity gonzo$drop(ServerPlayer owner,ItemStack stack,boolean scatter) {
  ItemEntity entity=owner.drop(stack,scatter);
  if(entity!=null) CreativeRadiationBridge.dropped(owner,entity.getItem());
  return entity;
 }
}
