package com.gonzotech.chalkboard.progress;

import com.gonzotech.GonzoTechMod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, GonzoTechMod.MOD_ID);

    public static final Supplier<AttachmentType<PlayerChalkboardProgress>> CHALKBOARD_PROGRESS =
            ATTACHMENT_TYPES.register("chalkboard_progress", () ->
                    AttachmentType.builder(PlayerChalkboardProgress::new)
                            .serialize(PlayerChalkboardProgress.CODEC)
                            .copyOnDeath()
                            .build()
            );

    public static void register(IEventBus eventBus) {
        ATTACHMENT_TYPES.register(eventBus);
    }
}
