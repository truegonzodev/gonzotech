package com.gonzotech.chalkboard.client;

import com.gonzotech.chalkboard.network.NotesNetwork;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Клиентский вход в GUI «Заметок учёного». Запрашивает у сервера состояние для
 * gating страниц (наигранное время + разблокирован ли tier 1) и открывает экран.
 */
public final class ScholarNotesClientHandler {

    public static void openScreen() {
        PacketDistributor.sendToServer(new NotesNetwork.NotesRequestPayload());
        Minecraft.getInstance().setScreen(new ScholarNotesScreen());
    }

    private ScholarNotesClientHandler() {
    }
}
