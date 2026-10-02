package net.minecraft.world.item;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
public class Item {
    public Item(Item.Properties properties) { }
    public InteractionResult useOn(UseOnContext context) { return null; }
    public static class Properties { }
}
