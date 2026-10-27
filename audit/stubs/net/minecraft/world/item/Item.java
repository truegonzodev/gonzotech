package net.minecraft.world.item;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
public class Item {
    public InteractionResult use(net.minecraft.world.level.Level level,
        net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) { return null; }
    public int getUseDuration(ItemStack stack, net.minecraft.world.entity.LivingEntity entity) { return 0; }
    public ItemUseAnimation getUseAnimation(ItemStack stack) { return null; }
    public void onUseTick(net.minecraft.world.level.Level level, net.minecraft.world.entity.LivingEntity entity,
        ItemStack stack, int remainingUseDuration) {}
    public ItemStack finishUsingItem(ItemStack stack, net.minecraft.world.level.Level level,
        net.minecraft.world.entity.LivingEntity entity) { return stack; }
    public Item(Item.Properties properties) { }
    public InteractionResult useOn(UseOnContext context) { return null; }
    public static class Properties { }
}
