package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.ExperienceOutput;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Слот результата переплавки: как {@link OutputOnlySlot} (игрок ничего в него
 * не кладёт), но при заборе результата выдаёт игроку накопленный опыт — как
 * ванильная печь. XP-логику держит {@link ExperienceOutput} на блок-сущности.
 */
public class SmeltResultSlot extends Slot {

    private final ExperienceOutput xpSource;

    public SmeltResultSlot(Container container, ExperienceOutput xpSource, int slot, int x, int y) {
        super(container, slot, x, y);
        this.xpSource = xpSource;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    /** Вызывается при заборе результата игроком (в т.ч. shift-click). */
    @Override
    public void onTake(Player player, ItemStack stack) {
        xpSource.awardExperienceTo(player);
        super.onTake(player, stack);
    }
}
