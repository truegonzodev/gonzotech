import com.gonzotech.machines.network.PipeLoss;

/**
 * Сценарий автора (0.3.58): батарея выдаёт 16 GTU; станок рядом получает всё,
 * через 1 провод — 15.92, через 3 провода — 15.76 (0.08 GTU за блок). Тир II:
 * 0.09 GTU и 0.18 GTH за блок; теплотруба I — 0.22 GTH. Источник платит пронос
 * (принятое + потеря), приёмник получает предложенное минус потеря; если
 * приёмник не взял ничего — потерь нет (трубы «не платят»).
 */
public class PipeLossSelfTest {
    static int checks = 0;

    static void eq(long expected, long actual, String what) {
        checks++;
        if (expected != actual) {
            throw new AssertionError(" #" + checks + ": " + what + " — ожидалось "
                    + expected + ", получено " + actual);
        }
    }

    public static void main(String[] args) {
        // ── Точные числа автора (milli-единицы) ──
        eq(80, PipeLoss.WIRE_T1, "провод тир1");
        eq(90, PipeLoss.WIRE_T2, "провод тир2");
        eq(220, PipeLoss.HEAT_T1, "теплотруба тир1");
        eq(180, PipeLoss.HEAT_T2, "теплотруба тир2");

        eq(80, PipeLoss.perCell(false, false), "perCell wire t1");
        eq(90, PipeLoss.perCell(true, false), "perCell wire t2");
        eq(220, PipeLoss.perCell(false, true), "perCell heat t1");
        eq(180, PipeLoss.perCell(true, true), "perCell heat t2");

        // ── Сценарий батареи 16 GTU = 16000 milli ──
        long battery = 16_000;
        eq(16_000, PipeLoss.delivered(battery, 0), "станок рядом: без потерь");
        eq(15_920, PipeLoss.delivered(battery, PipeLoss.WIRE_T1), "через 1 провод: 15.92 GTU");
        eq(15_760, PipeLoss.delivered(battery, PipeLoss.WIRE_T1 * 3), "через 3 провода: 15.76 GTU");
        eq(16_000, PipeLoss.flow(15_920, PipeLoss.WIRE_T1), "источник платит пронос (1 провод)");
        eq(16_000, PipeLoss.flow(15_760, PipeLoss.WIRE_T1 * 3), "источник платит пронос (3 провода)");

        // ── Потеря маршрута: сумма по клеткам ──
        eq(240, PipeLoss.sum(new long[]{80, 80, 80}), "сумма маршрута из 3 клеток");
        eq(440, PipeLoss.sum(new long[]{220, 220}), "2 теплотрубы тир1");
        eq(0, PipeLoss.sum(new long[0]), "пустой маршрут");

        // ── Тир II: капа 96 GTU/t через 10 блоков ──
        eq(95_100, PipeLoss.delivered(96_000, PipeLoss.WIRE_T2 * 10), "96 GTU через 10 блоков тир2");

        // ── Теплотруба тир1: 388 GTH/t через 2 блока ──
        eq(387_560, PipeLoss.delivered(388_000, PipeLoss.HEAT_T1 * 2), "388 GTH через 2 блока");

        // ── Края: меньше потери — не едет; приёмник отказал — потерь нет ──
        eq(0, PipeLoss.delivered(50, PipeLoss.WIRE_T1), "offered < loss: доезжает 0");
        eq(0, PipeLoss.flow(0, PipeLoss.WIRE_T1), "приёмник не взял — поток 0");
        eq(0, PipeLoss.flow(0, 240), "приёмник не взял — источник не платит");

        System.out.println("PipeLoss: " + checks
                + " проверок: 0.08/0.09 GTU и 0.22/0.18 GTH за блок, сценарий батареи 16 GTU, "
                + "источник платит пронос, отказ приёмника = без потерь");
    }
}
