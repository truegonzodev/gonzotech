package com.gonzotech.machines.reactor;

/** Independent regression checks for the ReaCalc 1.7 loaded operating-metric layer. */
final class ReactorOperatingMetricsTest {
    private static void check(boolean value, String text) {
        MatrixCoefficientsTest.check(value, text);
    }

    private static void near(double actual, double expected, double tolerance, String text) {
        MatrixCoefficientsTest.near(actual, expected, tolerance, text);
    }

    static void run() {
        var engine = MatrixCoefficients.balancedDefault();
        near(ReactorOperatingMetrics.temperature(.77), 2000.0, 1e-6, "temperature 0.77");
        near(ReactorOperatingMetrics.temperature(.88), 2400.0, 1e-6, "temperature 0.88");
        check(Double.isNaN(ReactorOperatingMetrics.temperature(1.0)), "no plateau at Htv threshold");

        var reference = MatrixCoefficients.referenceLayout();
        var referenceAnalysis = engine.analyzeDetailed(reference);
        var full = ReactorOperatingMetrics.calculate(referenceAnalysis, 2);
        near(full.htv(), referenceAnalysis.coefficients().heat() * full.ratio(), 1e-12,
            "loaded H scales by rods per sleeve");
        near(full.targetRawH(), .99999 / full.ratio(), 1e-12, "target raw H at loaded ratio");
        near(full.deltaRawH(), full.targetRawH() - full.rawH(), 1e-12, "delta sign");
        double qSum = java.util.Arrays.stream(referenceAnalysis.raw().heatProduction()).sum();
        near(full.qDry(), qSum, 1e-10, "total qDry");
        near(full.power(), 100.0 * full.ratio() * qSum, 1e-10, "loaded heating power");
        check(full.waterUnits() == 3 && full.meanTemperature() <= full.hotTemperature(),
            "water capacity and average-temperature bounds");
        check(full.fuelTemperature() <= full.hotTemperature(), "fuel temperature below global hot spot");
        near(full.efficiency(), 100.0 * full.usefulOutput()
            / (ReactorOperatingMetrics.OUTPUT_GAIN * full.power()), 1e-12,
            "efficiency removes the output-unit gain from its denominator");

        // The loaded H/L layer must equal a direct solve with the same per-sheath source scaling.
        var scaledRules = new MatrixCoefficients.Rules(full.ratio(), .05, .15, .05, 1.0, 2000, 1e-10);
        var scaledEngine = new MatrixCoefficients(scaledRules, engine.calibration());
        var loadedDry = scaledEngine.analyzeDetailed(reference);
        near(loadedDry.raw().peakLoad() / engine.calibration().heatBudget(), full.htv(), 1e-9,
            "linear H matches scaled loaded solve");
        near(loadedDry.raw().peakEscape() / (loadedDry.raw().peakEscape() + engine.calibration().leakHalfScale()),
            full.ltv(), 1e-10, "loaded L normalization matches scaled solve");

        var off = ReactorOperatingMetrics.calculate(referenceAnalysis, 0);
        check(off.htv() == 0.0 && off.ltv() == 0.0 && off.score() == 0.0 && off.power() == 0.0,
            "zero rods zero loaded source values");
        check(off.hotTemperature() == 20.0 && off.meanTemperature() == 20.0
            && off.waterTemperature() == 20.0 && off.pressure() == 1.0,
            "zero rods ambient equilibrium");
        check(Double.isNaN(off.targetRawH()) && off.efficiency() == 0.0
            && off.regime().equals("Нет топлива"), "zero-load target, efficiency and regime");

        var fullFuel = new MatrixCoefficients.Grid(3, 3, 3, MatrixCoefficients.Material.FUEL);
        var overheated = ReactorOperatingMetrics.calculate(engine.analyzeDetailed(fullFuel), 18);
        check(overheated.overheat() && Double.isNaN(overheated.hotTemperature())
            && Double.isNaN(overheated.meanTemperature()) && Double.isNaN(overheated.waterTemperature())
            && Double.isNaN(overheated.pressure()) && Double.isNaN(overheated.output())
            && Double.isNaN(overheated.usefulOutput()) && Double.isNaN(overheated.efficiency()),
            "all steady temperatures/outputs undefined during overheat");
        check(overheated.power() > 0.0 && overheated.regime().equals("Перегрев"),
            "overheat retains heating power and is labeled");

        var noFuelGrid = new MatrixCoefficients.Grid(16, 4, 8, MatrixCoefficients.Material.WATER);
        var noFuel = ReactorOperatingMetrics.calculate(engine.analyzeDetailed(noFuelGrid), 0);
        check(noFuel.waterUnits() == 4 && noFuel.meanTemperature() == 20.0,
            "rectangular full-bath capacity and empty-reactor average");

        var nominal = ReactorOperatingMetrics.water(300.0, .05, 9, 1000.0);
        check(nominal.temperature() > 100.0 && nominal.temperature() < 300.0,
            "water nominal calibration");
        double previous = 20.0;
        for (double power : new double[]{0, 1, 100, 1000, 3000, 10000, 100000}) {
            var water = ReactorOperatingMetrics.water(power, .05, 9, 1000.0);
            check(water.temperature() >= previous && water.temperature() <= 1000.0,
                "water temperature monotone and bounded");
            previous = water.temperature();
            near(water.output(), Math.min(power * .95,
                .6 * 9 * (1000.0 - water.temperature())), 1e-7, "water equilibrium balance");
            check(water.output() <= power * .95 + 1e-8, "output below available heating");
            double withoutFeedback = 20.0 + Math.min(power * .95 / (9 * ReactorOperatingMetrics.WATER_REMOVAL),
                .6 * (1000.0 - 20.0) / (.6 + ReactorOperatingMetrics.WATER_REMOVAL));
            check(water.temperature() <= withoutFeedback + 1e-8,
                "pressure feedback lowers equilibrium temperature");
        }
        var hotWater = ReactorOperatingMetrics.water(1e6, 0.0, 16, 2500.0);
        check(hotWater.temperature() > 300.0 && Double.isFinite(hotWater.pressure()),
            "hot water is distinct from core overheat");

        near(ReactorOperatingMetrics.quality(89.0), 0.0, 0.0, "cold water quality");
        near(ReactorOperatingMetrics.quality(90.0), 0.0, 0.0, "quality threshold");
        near(ReactorOperatingMetrics.quality(145.0), .5, 1e-12, "quality ramp");
        near(ReactorOperatingMetrics.quality(250.0), 1.0, 0.0, "quality cap");
        check(full.usefulOutput() <= full.output() && off.usefulOutput() == 0.0
            && Double.isNaN(overheated.usefulOutput()), "useful output is bounded and handles off/overheat");

        double temperatureSum = 0.0;
        for (double load : referenceAnalysis.raw().load()) {
            temperatureSum += ReactorOperatingMetrics.temperature(
                load / engine.calibration().heatBudget() * full.ratio());
        }
        near(full.meanTemperature(), temperatureSum / reference.size, 1e-9,
            "mean temperature averages per-cell temperatures");

        boolean invalidLoad = false;
        try {
            ReactorOperatingMetrics.calculate(referenceAnalysis, 7);
        } catch (IllegalArgumentException expected) {
            invalidLoad = true;
        }
        check(invalidLoad, "over-capacity load is rejected without clamping");
    }
}
