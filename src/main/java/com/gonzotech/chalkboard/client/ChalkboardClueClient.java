package com.gonzotech.chalkboard.client;

import java.util.HashSet;
import java.util.Set;

/**
 * Клиентское состояние подсказки по доске резонанса (автор 22.09.2026).
 *
 * <p>Сервер присылает блок из решения ({@code CluePayload}) — здесь он живёт, пока
 * доска не покажет его в лотке:</p>
 * <ul>
 *   <li>{@link #hintedId()} — блок, который надо обвести толстой белой рамкой.
 *       Становится {@code null} при первом использовании блока (нажал/перетащил) —
 *       обводка пропадает сама, без участия сервера;</li>
 *   <li>{@link #isAvailable(String)} — блоки, которые подсказка «принесла» в лоток.
 *       Даже если сервер ещё не успел синхронизировать открытые блоки, плитка
 *       не исчезает из лотка.</li>
 * </ul>
 *
 * <p>Состояние живёт до перезапуска клиента: подсказка выдаётся командой (позже —
 * револьвером и водкой), и доску игрок открывает уже после неё.</p>
 */
public final class ChalkboardClueClient {

    private ChalkboardClueClient() {
    }

    private static final Set<String> AVAILABLE = new HashSet<>();
    private static String hintedId;
    private static int version;

    /** Пришла подсказка: подсветить блок и сделать его доступным в лотке. */
    public static void accept(String quantityId) {
        if (quantityId == null || quantityId.isEmpty()) return;
        hintedId = quantityId;
        AVAILABLE.add(quantityId);
        version++;
    }

    /**
     * Счётчик выданных подсказок. Экран доски сравнивает его со своим «виденным» —
     * так новая подсказка показывает блок в начале лотка, даже если предыдущая
     * подсказка была про тот же самый блок.
     */
    public static int version() {
        return version;
    }

    /** Блок, который подсвечивается сейчас ({@code null} — подсказки нет/уже использована). */
    public static String hintedId() {
        return hintedId;
    }

    /** Блок принесён подсказкой — лоток держит его, даже если он ещё не открыт игроку. */
    public static boolean isAvailable(String quantityId) {
        return quantityId != null && AVAILABLE.contains(quantityId);
    }

    /** Подсвечен ли сейчас этот блок (толстая белая обводка в лотке). */
    public static boolean isHinted(String quantityId) {
        return quantityId != null && quantityId.equals(hintedId);
    }

    /**
     * Блок использован (нажал/перетащил) — обводка пропадает. Сам блок остаётся
     * доступным: подсказка выдала его, и он нужен для решения.
     */
    public static void consume() {
        hintedId = null;
    }

    /** Полный сброс (задел под смену задачи/новую подсказку поверх старой). */
    public static void clear() {
        hintedId = null;
        AVAILABLE.clear();
        version++;
    }
}
