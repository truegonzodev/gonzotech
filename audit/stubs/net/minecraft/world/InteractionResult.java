package net.minecraft.world;
public class InteractionResult {
    public static final InteractionResult SUCCESS = new InteractionResult();
    public static final InteractionResult CONSUME = new InteractionResult();
    public static final InteractionResult PASS = new InteractionResult();
    public static final InteractionResult FAIL = new InteractionResult();
    public boolean consumesAction() { return false; }
}
