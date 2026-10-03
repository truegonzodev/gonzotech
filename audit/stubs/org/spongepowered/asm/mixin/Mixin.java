package org.spongepowered.asm.mixin;
import java.lang.annotation.*;
@Target(ElementType.TYPE) @Retention(RetentionPolicy.RUNTIME)
public @interface Mixin { Class<?>[] value() default {}; }
