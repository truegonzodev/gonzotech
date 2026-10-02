package net.neoforged.neoforge.event.tick;
import net.minecraft.world.level.Level;
public class LevelTickEvent {
    public Level getLevel() { return null; }
    public static class Post extends LevelTickEvent { }
}
