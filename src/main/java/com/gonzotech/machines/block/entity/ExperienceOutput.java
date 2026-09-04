package com.gonzotech.machines.block.entity;

import net.minecraft.world.entity.player.Player;

/**
 * Реализуют плавильные машины (топка, электропечь), которые копят опыт за
 * переплавку и выдают его игроку при заборе результата из выходного слота —
 * ровно как ванильная печь.
 */
public interface ExperienceOutput {

    /** Выдать игроку накопленный опыт переплавки (целое — орбами, дробь — вероятностно). */
    void awardExperienceTo(Player player);
}
