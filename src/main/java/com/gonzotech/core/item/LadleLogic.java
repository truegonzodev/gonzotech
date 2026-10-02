package com.gonzotech.core.item;

/**
 * Чистая логика ковша (0.3.80) — БЕЗ зависимостей от Minecraft.
 *
 * <p>Ковш — брат щипцов: хранит ПОРЦИЮ жидкости (1000 mB, один вид) ИЛИ
 * предметы ртути/цезия (те самые, что щипцы не берут — автор,
 * EPOCH3-BASE §2.4/§2.8). Защитные множители — те же, что у щипцов
 * ({@code CarrierItem}).
 *
 * <p>Правило носителей (автор, 02.10.2026): механика — как у ванильного
 * мешочка/связки, ничего нового не изобретать; единственный запрет — СОБСТВЕННЫЕ
 * носители не вкладываются друг в друга (щипцы↔щипцы, ковш↔ковш, щипцы↔ковш),
 * а ванильные контейнеры (шалкеры, связка) ковш берёт как обычные предметы.
 */
public final class LadleLogic {

    /** Порция жидкости ковша, mB (автор: «порция, например 1000 mB»). */
    public static final int CAPACITY_MB = 1000;

    /** Предметная вместимость для ртути/цезия — как у щипцов. */
    public static final int ITEM_CAPACITY = TongsLogic.CAPACITY;

    private LadleLogic() {
    }

    /** Ванильные контейнеры (шалкеры/связка) — обычные предметы для ковша. */
    public static boolean isContainerItem(String itemIdPath) {
        return itemIdPath.endsWith("shulker_box")
                || itemIdPath.equals("bundle") || itemIdPath.endsWith("_bundle");
    }

    /** Ковш: ртуть/цезий + ванильные контейнеры; свои носители — НИКОГДА. */
    public static boolean canPickItem(String itemIdPath) {
        return (TongsLogic.isMercuryCesium(itemIdPath) || isContainerItem(itemIdPath))
                && !TongsLogic.isCarrier(itemIdPath);
    }

    /** Сколько единиц ртути/цезия влезает в ковш (один вид). */
    public static int roomForItem(String currentPath, int currentCount,
                                  String incomingPath, int incomingCount) {
        if (incomingCount <= 0 || !canPickItem(incomingPath)) return 0;
        if (currentCount <= 0) return Math.min(incomingCount, ITEM_CAPACITY);
        if (!currentPath.equals(incomingPath)) return 0;
        return Math.min(incomingCount, ITEM_CAPACITY - currentCount);
    }

    /**
     * Сколько mB жидкости {@code incoming} влезает в ковш с текущим
     * содержимым (один вид, до 1000 mB); другая жидкость — ноль.
     */
    public static int roomForFluid(String currentFluid, int currentMb,
                                   String incomingFluid, int incomingMb) {
        if (incomingMb <= 0 || incomingFluid == null || incomingFluid.isEmpty()) return 0;
        if (currentMb <= 0) return Math.min(incomingMb, CAPACITY_MB);
        if (!currentFluid.equals(incomingFluid)) return 0;
        return Math.min(incomingMb, CAPACITY_MB - currentMb);
    }
}
