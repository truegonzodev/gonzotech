package com.gonzotech.space;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;

/**
 * СОН В КОСМИЧЕСКИХ МИРАХ (по фидбэку): кровати больше НЕ взрываются
 * ({@code bed_works: true} во всех 6 dimension_type), но и спать в них нельзя.
 *
 * <p>Ловим {@link CanPlayerSleepEvent} (сервер) и, если игрок в одном из наших
 * измерений, выставляем «проблему» {@link Player.BedSleepingProblem#NOT_POSSIBLE_HERE}
 * → ванильное сообщение «Здесь не получится уснуть», ночь не пропускается, точку
 * возрождения кровать при этом всё равно ставит (bed_works=true).
 *
 * <p>Регистрируется на {@code NeoForge.EVENT_BUS} в {@code GonzoTechMod}.
 */
public final class SpaceSleep {

    private SpaceSleep() {
    }

    @SubscribeEvent
    public static void onCanSleep(CanPlayerSleepEvent event) {
        Level level = event.getLevel();
        if (level == null) {
            return;
        }
        if (SpaceDimensions.DIMENSIONS.containsValue(level.dimension())) {
            event.setProblem(Player.BedSleepingProblem.NOT_POSSIBLE_HERE);
        }
    }
}
