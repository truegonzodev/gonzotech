package com.gonzotech.core.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Local presentation preferences, never server gameplay rules. */
public final class GonzoClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue EXTENDED_ADVANCED_TOOLTIPS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        EXTENDED_ADVANCED_TOOLTIPS = builder
                .comment("Show Gonzo crafting and recipe-book gates at the bottom of tooltips.",
                        "Only in Creative mode with advanced tooltips (F3+H). Does not change gameplay gates.")
                .translation("gonzotech.configuration.extendedAdvancedTooltips")
                .define("extendedAdvancedTooltips", true);
        SPEC = builder.build();
    }

    private GonzoClientConfig() {}
}
