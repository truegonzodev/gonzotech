package com.gonzotech.machines.client;

import com.gonzotech.machines.miner.MarkerNetwork;
import com.gonzotech.machines.miner.MinerMarker;

import java.util.ArrayList;
import java.util.List;

/**
 * Клиентский кэх меток автошахтера (0.3.256). Питается пейлоадом
 * {@link MarkerNetwork.MarkersPayload}; рисует {@link MarkerRenderer}.
 */
public final class MarkerClient {

    private static volatile List<MinerMarker> markers = List.of();

    private MarkerClient() {
    }

    public static void accept(MarkerNetwork.MarkersPayload payload) {
        List<MinerMarker> next = new ArrayList<>(payload.markers().size());
        for (MarkerNetwork.Entry entry : payload.markers()) {
            next.add(MarkerNetwork.toMarker(entry));
        }
        markers = List.copyOf(next);
    }

    public static List<MinerMarker> markers() {
        return markers;
    }
}
