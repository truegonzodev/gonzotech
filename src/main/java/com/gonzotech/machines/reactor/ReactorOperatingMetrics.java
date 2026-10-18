package com.gonzotech.machines.reactor;

/**
 * Static loaded-reactor metrics used by the Shift diagnostics. The equations and
 * fictional equilibrium model intentionally mirror ReaCalc 1.2 OperatingMetrics;
 * this class does not advance time or model reactor transients.
 */
public final class ReactorOperatingMetrics {
    public static final double TARGET_H = .99999;
    public static final double AMBIENT = 20.0;
    public static final double POWER_SCALE = 100.0;
    public static final double WATER_REMOVAL = .15;
    public static final double TEMP_SCALE = 2838.43365245;
    public static final double TEMP_EXPONENT = 1.37798040865;

    private ReactorOperatingMetrics() {
    }

    public record Water(double temperature, double pressure, double feedback, double output) {
    }

    public record Values(int sleeves, int rods, double ratio, double rawH, double rawL,
                         double htv, double ltv, double score, double targetRawH,
                         double deltaRawH, double qDry, double power, int waterUnits,
                         double meanH, double hotTemperature, double fuelTemperature,
                         double meanTemperature, double waterTemperature, double pressure,
                         double pressureFeedback, double output, double usefulOutput,
                         double efficiency, boolean overheat, String regime) {
    }

    /** ReaCalc's stylized temperature rule; it is not a material-property calculation. */
    public static double temperature(double h) {
        return h >= 1.0 ? Double.NaN
            : AMBIENT + TEMP_SCALE * Math.pow(Math.max(0.0, h), TEMP_EXPONENT);
    }

    public static double scaledLeak(double rawL, double ratio) {
        return ratio == 0.0 ? 0.0 : ratio * rawL / (1.0 - rawL + ratio * rawL);
    }

    /**
     * Calculate all steady operating values from one already-computed dry matrix.
     * Invalid persisted fuel loads are reported instead of silently clamped or lost.
     */
    public static Values calculate(MatrixCoefficients.Analysis analysis, int rods) {
        MatrixCoefficients.Grid grid = analysis.grid();
        int sleeves = countFuel(grid);
        int capacity = sleeves <= 0 ? 0 : Math.min(sleeves + 2, 18);
        if (rods < 0 || rods > capacity) {
            throw new IllegalArgumentException("Загрузка ТВЭЛов превышает число слотов или отрицательна: "
                + rods + "/" + capacity + ". Сохранённое топливо не изменено.");
        }

        double ratio = sleeves == 0 ? 0.0 : (double) rods / sleeves;
        MatrixCoefficients.Coefficients dry = analysis.coefficients();
        double htv = dry.heat() * ratio;
        double ltv = scaledLeak(dry.peakLeak(), ratio);
        boolean overheat = htv >= 1.0;
        double target = ratio == 0.0 ? Double.NaN : TARGET_H / ratio;
        double delta = target - dry.heat();

        MatrixCoefficients.Raw raw = analysis.raw();
        double qDry = 0.0;
        double hsum = 0.0;
        double tsum = 0.0;
        double fuelPeak = 0.0;
        int occupied = 0;
        for (int z = 0; z < grid.nz; z++) {
            for (int y = 0; y < grid.ny; y++) {
                for (int x = 0; x < grid.nx; x++) {
                    int i = grid.index(x, y, z);
                    qDry += raw.heatProduction()[i];
                    double hi = raw.load()[i] / analysis.calibration().heatBudget() * ratio;
                    // Formed in-game reactors have a material at every interior cell.
                    occupied++;
                    hsum += hi;
                    if (!overheat) tsum += temperature(hi);
                    if (grid.get(x, y, z) == MatrixCoefficients.Material.FUEL) {
                        fuelPeak = Math.max(fuelPeak, hi);
                    }
                }
            }
        }

        double meanH = occupied == 0 ? 0.0 : hsum / occupied;
        double meanTemperature = overheat ? Double.NaN
            : occupied == 0 ? AMBIENT : tsum / occupied;
        double power = POWER_SCALE * ratio * qDry;
        int waterUnits = Math.min(grid.nx, Math.min(grid.ny, grid.nz));
        Water water = overheat
            ? new Water(Double.NaN, Double.NaN, Double.NaN, Double.NaN)
            : water(power, ltv, waterUnits, meanTemperature);
        double score = overheat ? Double.NaN : sleeves * Math.pow(htv, 1.5) * (1.0 - ltv);
        double hotTemperature = overheat ? Double.NaN : temperature(htv);
        double fuelTemperature = overheat ? Double.NaN : temperature(fuelPeak);
        double usefulOutput = water.output() * quality(water.temperature());
        double efficiency = overheat ? Double.NaN : power > 0.0 ? 100.0 * usefulOutput / power : 0.0;
        String regime = overheat ? "Перегрев" : rods == 0 ? "Нет топлива"
            : water.temperature() < 90.0 ? "Неэффективно"
            : water.temperature() < 100.0 ? "Переход"
            : water.temperature() <= 300.0 ? "Рабочий диапазон" : "Горячая вода";

        return new Values(sleeves, rods, ratio, dry.heat(), dry.peakLeak(), htv, ltv,
            score, target, delta, qDry, power, waterUnits, meanH, hotTemperature,
            fuelTemperature, meanTemperature, water.temperature(), water.pressure(),
            water.feedback(), water.output(), usefulOutput, efficiency, overheat, regime);
    }

    public static int countFuel(MatrixCoefficients.Grid grid) {
        int count = 0;
        for (int z = 0; z < grid.nz; z++) {
            for (int y = 0; y < grid.ny; y++) {
                for (int x = 0; x < grid.nx; x++) {
                    if (grid.get(x, y, z) == MatrixCoefficients.Material.FUEL) count++;
                }
            }
        }
        return count;
    }

    public static double quality(double temperature) {
        return Double.isFinite(temperature)
            ? Math.max(0.0, Math.min(1.0, (temperature - 90.0) / 110.0))
            : Double.NaN;
    }

    // Pressure is a game feedback index, not bar or a steam-table calculation.
    public static double pressure(double temperature) {
        return 1.0 + Math.pow(Math.max(0.0, temperature - AMBIENT) / 100.0, 2.0);
    }

    public static double feedback(double pressure) {
        return 1.0 + .12 * (pressure - 1.0);
    }

    /** ReaCalc's finite abstract-bath equilibrium: 80 bisection steps, zero time advance. */
    public static Water water(double power, double leak, int units, double meanTemperature) {
        if (!Double.isFinite(meanTemperature)) {
            return new Water(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        }
        double available = power * (1.0 - leak);
        double lo = AMBIENT;
        double hi = Math.max(AMBIENT, meanTemperature);
        for (int step = 0; step < 80; step++) {
            double temperature = (lo + hi) * .5;
            double supply = Math.min(available, .6 * units * Math.max(0.0, meanTemperature - temperature));
            double removal = WATER_REMOVAL * units * (temperature - AMBIENT)
                * feedback(pressure(temperature));
            if (removal > supply) hi = temperature;
            else lo = temperature;
        }
        double temperature = (lo + hi) * .5;
        double pressure = pressure(temperature);
        double feedback = feedback(pressure);
        double output = WATER_REMOVAL * units * (temperature - AMBIENT) * feedback;
        return new Water(temperature, pressure, feedback, output);
    }
}
