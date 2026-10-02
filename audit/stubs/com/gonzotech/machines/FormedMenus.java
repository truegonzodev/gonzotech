package com.gonzotech.machines;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
/** Стаб диспетчера openMenu (реальный зовёт 3 структуры — все с чистыми гейтами). */
public final class FormedMenus {
    public static boolean open(Level level, BlockPos pos, Player player) { return false; }
    private FormedMenus() { }
}
