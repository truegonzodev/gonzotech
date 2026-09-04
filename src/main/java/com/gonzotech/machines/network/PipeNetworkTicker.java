package com.gonzotech.machines.network;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Прогоняет энергосети раз в серверный тик каждого уровня. Здесь — единственное
 * место, где вообще что-то «тикает» в логистике: не провода, а
 * {@link NetworkManager}, который дальше зовёт {@link EnergyNetwork#tick} по
 * контурам. Так стоимость тика зависит от числа сетей/портов, а не от количества
 * труб.
 * <p>
 * Сам граф труб восстанавливается из {@link net.minecraft.world.level.saveddata.SavedData}
 * при загрузке мира (см. {@link NetworkManager#load}), поэтому тикеру не нужно
 * сканировать чанки — он лишь дергает {@link NetworkManager#tick} каждый тик.
 */
public final class PipeNetworkTicker {

    private PipeNetworkTicker() {
    }

    @SubscribeEvent
    public static void onLevelTick(final LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel server) {
            NetworkManager.get(server).tick();
        }
    }
}
