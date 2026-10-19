package com.gonzotech.machines.reactor;

/** Fixed-geometry ReaCalc 1.7 temperature-to-output curve. TVEL count is not an input. */
public final class ReactorHeatOutputCurve {

    public record Output(double exchange, double useful,
                         double waterTemperature, double pressureIndex) {
        public Output(double exchange, double useful) {
            this(exchange, useful, ReactorOperatingMetrics.AMBIENT, 1.0);
        }
    }

    public static final Output ZERO = new Output(0.0, 0.0,
        ReactorOperatingMetrics.AMBIENT, 1.0);

    private final double rawH;
    private final double rawL;
    private final double qDry;
    private final double meanShape;
    private final int waterUnits;
    private final double anchorTemperature;
    private final Output anchor;

    public ReactorHeatOutputCurve(MatrixCoefficients.Analysis analysis) {
        MatrixCoefficients.Grid grid = analysis.grid();
        MatrixCoefficients.Raw raw = analysis.raw();
        rawH = analysis.coefficients().heat();
        rawL = analysis.coefficients().peakLeak();
        waterUnits = Math.min(grid.nx, Math.min(grid.ny, grid.nz));

        double shapeSum = 0.0;
        double heatSum = 0.0;
        int occupied = 0;
        for (int z = 0; z < grid.nz; z++) {
            for (int y = 0; y < grid.ny; y++) {
                for (int x = 0; x < grid.nx; x++) {
                    int index = grid.index(x, y, z);
                    heatSum += raw.heatProduction()[index];
                    // A validated in-game active zone has a material in every cell.
                    occupied++;
                    if (rawH > 0.0) {
                        double localH = raw.load()[index]
                            / analysis.calibration().heatBudget() / rawH;
                        shapeSum += Math.pow(Math.max(0.0, localH),
                            ReactorOperatingMetrics.TEMP_EXPONENT);
                    }
                }
            }
        }
        qDry = heatSum;
        meanShape = occupied == 0 ? 0.0 : shapeSum / occupied;
        anchorTemperature = ReactorOperatingMetrics.AMBIENT + ReactorOperatingMetrics.TEMP_SCALE;
        anchor = equilibrium(anchorTemperature);
    }

    /** Mean temperature follows the dry matrix's normalized spatial heat profile. */
    public double meanTemperature(double hotTemperature) {
        if (!Double.isFinite(hotTemperature)) return hotTemperature;
        return ReactorOperatingMetrics.AMBIENT
            + Math.max(0.0, hotTemperature - ReactorOperatingMetrics.AMBIENT) * meanShape;
    }

    private Output equilibrium(double temperature) {
        double rise = Math.max(0.0, temperature - ReactorOperatingMetrics.AMBIENT);
        if (rawH <= 0.0 || qDry <= 0.0 || rise == 0.0) return ZERO;

        double h = Math.pow(rise / ReactorOperatingMetrics.TEMP_SCALE,
            1.0 / ReactorOperatingMetrics.TEMP_EXPONENT);
        double ratio = h / rawH;
        double power = ReactorOperatingMetrics.POWER_SCALE * ratio * qDry;
        double leak = ReactorOperatingMetrics.scaledLeak(rawL, ratio);
        ReactorOperatingMetrics.Water water = ReactorOperatingMetrics.water(
            power, leak, waterUnits, meanTemperature(temperature));
        double exchange = ReactorOperatingMetrics.OUTPUT_GAIN * water.output();
        return new Output(exchange, exchange * ReactorOperatingMetrics.quality(water.temperature()),
            water.temperature(), water.pressure());
    }

    /**
     * The archived curve is linear above its reference point. In particular it
     * remains live above 10,000 °C; that value is a display scale, not a trip.
     */
    public Output at(double temperature) {
        if (!Double.isFinite(temperature) || temperature <= ReactorOperatingMetrics.AMBIENT) return ZERO;
        if (temperature <= anchorTemperature) return equilibrium(temperature);
        double scale = (temperature - ReactorOperatingMetrics.AMBIENT)
            / (anchorTemperature - ReactorOperatingMetrics.AMBIENT);
        // ReaCalc 1.7 defines linear output continuation above the reference
        // point, but no new water equilibrium; expose the last finite bath state.
        return new Output(anchor.exchange() * scale, anchor.useful() * scale,
            anchor.waterTemperature(), anchor.pressureIndex());
    }
}
