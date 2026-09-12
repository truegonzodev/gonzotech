package com.gonzotech.core.registry;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.component.AlloyComposition;
import com.gonzotech.core.component.AlloyTint;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Item data components owned by Gonzo Tech. */
public final class ModDataComponents {

    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
        DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, GonzoTechMod.MOD_ID);

    /** Normalized source-material ratio of one procedural custom alloy. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<AlloyComposition>> ALLOY_COMPOSITION =
        DATA_COMPONENTS.registerComponentType("alloy_composition",
            builder -> builder.persistent(AlloyComposition.CODEC).cacheEncoding());

    /** Server-computed opaque RGB tint used by the single neutral alloy sprite. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<AlloyTint>> ALLOY_TINT =
        DATA_COMPONENTS.registerComponentType("alloy_tint",
            builder -> builder.persistent(AlloyTint.CODEC).cacheEncoding());

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }

    private ModDataComponents() {
    }
}
