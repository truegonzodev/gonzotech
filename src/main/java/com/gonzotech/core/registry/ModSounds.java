package com.gonzotech.core.registry;

import com.gonzotech.GonzoTechMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registered custom sounds; each holder has a matching event in assets/gonzotech/sounds.json. */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, GonzoTechMod.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> SKETCH_VIDEO = sound("videoplaybak");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWORD_IMPACT = sound("sword_impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWORD_READY = sound("sword_ready");

    public static final DeferredHolder<SoundEvent, SoundEvent> GT_OST_40000FT = sound("gt_ost_40000ft");
    public static final DeferredHolder<SoundEvent, SoundEvent> GT_OST_BITTER = sound("gt_ost_bitter");
    public static final DeferredHolder<SoundEvent, SoundEvent> GT_OST_CRADLE = sound("gt_ost_cradle");
    public static final DeferredHolder<SoundEvent, SoundEvent> GT_OST_ETHEREAL = sound("gt_ost_ethereal");
    public static final DeferredHolder<SoundEvent, SoundEvent> GT_OST_RAIDIN_BONES = sound("gt_ost_raidin_bones");

    public static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, name)));
    }
    public static void register(IEventBus bus) { SOUNDS.register(bus); }
    private ModSounds() {}
}
