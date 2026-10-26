package com.gonzotech.radiation.engine;

import net.minecraft.world.item.ItemStack;
import java.util.List;

/** References are read-only. Radiation belongs to the external engine. */
public interface ContainerContentsAccess {
    List<ItemStack> gonzo$radiationItems();
}
