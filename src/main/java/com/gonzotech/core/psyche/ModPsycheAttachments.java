package com.gonzotech.core.psyche;

import com.gonzotech.GonzoTechMod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** Регистрация attachment «психики» игрока (3 шкалы состояния). */
public final class ModPsycheAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, GonzoTechMod.MOD_ID);

    public static final Supplier<AttachmentType<PlayerPsyche>> PSYCHE =
            ATTACHMENT_TYPES.register("psyche", () ->
                    AttachmentType.builder(PlayerPsyche::new)
                            .serialize(PlayerPsyche.CODEC)
                            .copyOnDeath()
                            .build()
            );

    public static void register(IEventBus eventBus) {
        ATTACHMENT_TYPES.register(eventBus);
    }

    private ModPsycheAttachments() {
    }
}
