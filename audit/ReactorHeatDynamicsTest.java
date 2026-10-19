package com.gonzotech.machines.reactor;

/** Runtime-math regressions for the ReaCalc 1.7 temperature/output adaptation. */
final class ReactorHeatDynamicsTest {
    private ReactorHeatDynamicsTest() {
    }

    static void run() {
        var engine = MatrixCoefficients.balancedDefault();
        var reference = engine.analyzeDetailed(MatrixCoefficients.referenceLayout());
        var curve = new ReactorHeatOutputCurve(reference);
        var anchor = curve.at(ReactorOperatingMetrics.AMBIENT + ReactorOperatingMetrics.TEMP_SCALE);
        var beyondScale = curve.at(10_000.0);
        MatrixCoefficientsTest.check(anchor.exchange() > 0.0 && anchor.useful() >= 0.0,
            "reference-temperature output curve is active");
        MatrixCoefficientsTest.check(Double.isFinite(beyondScale.exchange())
                && beyondScale.exchange() > anchor.exchange(),
            "10,000 C is a display scale, not an output cutoff");

        var plateau = new ReactorHeatDynamics.State();
        plateau.configure(new ReactorHeatDynamics.Parameters(
            .5, 10.0, 100.0, 1_000.0, 20.0, 0.0, .01, .005));
        for (int tick = 0; tick < 500; tick++) plateau.advance(1.0);
        MatrixCoefficientsTest.near(plateau.temperature(), 1_000.0, 1e-8,
            "subcritical core approaches its exponential plateau");
        MatrixCoefficientsTest.check(plateau.output().exchange() > 0.0,
            "plateau temperature drives GTH output");

        var runaway = new ReactorHeatDynamics.State();
        runaway.configure(new ReactorHeatDynamics.Parameters(
            1.2, 1.0, 100.0, ReactorOperatingMetrics.AMBIENT + ReactorOperatingMetrics.TEMP_SCALE,
            10.0, 2.0, .01, .005));
        for (int tick = 0; tick < 1_200; tick++) runaway.advance(1.0);
        double hot = runaway.temperature();
        MatrixCoefficientsTest.check(hot > 10_000.0,
            "overcritical temperature continues beyond the gauge scale without a trip");
        MatrixCoefficientsTest.check(runaway.output().exchange() > 0.0,
            "overcritical core keeps generating above 10,000 C");

        var resumed = new ReactorHeatDynamics.State();
        resumed.restore(runaway.save());
        resumed.configure(new ReactorHeatDynamics.Parameters(
            .5, 1.0, 0.0, 1_000.0, 30.0, 0.0, .01, .005));
        MatrixCoefficientsTest.check(resumed.cooling(),
            "saved overcritical core enters the cooling phase when fuel is removed");
        MatrixCoefficientsTest.near(resumed.temperature(), hot, 1e-12,
            "saved overcritical heat is retained when fuel is removed");
        resumed.advance(100.0);
        MatrixCoefficientsTest.check(resumed.temperature() < hot
                && resumed.temperature() > 1_000.0 && resumed.output().exchange() > 0.0,
            "fuel-free core cools gradually while residual heat still produces GTH");

        var empty = new MatrixCoefficients.Grid(3, 3, 3, MatrixCoefficients.Material.WATER);
        var emptyAnalysis = engine.analyzeDetailed(empty);
        var noNeutrons = ReactorNeutronMetrics.calculate(
            emptyAnalysis, ReactorOperatingMetrics.calculate(emptyAnalysis, 0));
        MatrixCoefficientsTest.check(noNeutrons.strongestFlow() == 0.0
                && noNeutrons.averageFlow() == 0.0
                && noNeutrons.strongestLeak() == 0.0
                && noNeutrons.averageLeak() == 0.0,
            "empty zone reports no neutron flow or leakage");

        var fuel = new MatrixCoefficients.Grid(3, 3, 3, MatrixCoefficients.Material.FUEL);
        var fuelAnalysis = engine.analyzeDetailed(fuel);
        var loaded = ReactorOperatingMetrics.calculate(fuelAnalysis, 18);
        var liveNeutrons = ReactorNeutronMetrics.calculate(fuelAnalysis, loaded);
        MatrixCoefficientsTest.check(liveNeutrons.strongestFlow() > 0.0
                && liveNeutrons.averageFlow() > 0.0
                && liveNeutrons.strongestLeak() > 0.0
                && liveNeutrons.averageLeak() > 0.0,
            "loaded fuel reports spatial flow and neutron leakage");
    }
}
