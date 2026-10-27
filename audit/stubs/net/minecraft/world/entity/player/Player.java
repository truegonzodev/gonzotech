package net.minecraft.world.entity.player;
public class Player extends net.minecraft.world.entity.LivingEntity {
    public net.minecraft.world.item.ItemStack getMainHandItem() { return null; }
    public net.minecraft.world.item.ItemStack getOffhandItem() { return null; }
    public void startUsingItem(net.minecraft.world.InteractionHand hand) {}
    public <T> T getData(net.neoforged.neoforge.attachment.AttachmentType<T> type) { return null; }
    public void awardStat(net.minecraft.stats.Stat<?> stat) {}
    public boolean isSecondaryUseActive() { return false; }
    public Abilities getAbilities() { return null; }
    public String getName() { return ""; }
}
