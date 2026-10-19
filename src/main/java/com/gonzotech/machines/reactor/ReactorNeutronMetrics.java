package com.gonzotech.machines.reactor;

/**
 * Spatial neutron-flow/leak indicators derived from the live transport matrix.
 * ReaCalc 1.7 has no separate time-dependent neutron solver, so these values
 * respond to material geometry and loaded TVEL count, not reactor temperature.
 */
public final class ReactorNeutronMetrics {

    public record Values(double strongestFlow, double averageFlow,
                         double strongestLeak, double averageLeak) {
        public static final Values ZERO = new Values(0.0, 0.0, 0.0, 0.0);
    }

    private ReactorNeutronMetrics() {
    }

    public static Values calculate(MatrixCoefficients.Analysis analysis,
                                   ReactorOperatingMetrics.Values operating) {
        MatrixCoefficients.Grid grid = analysis.grid();
        MatrixCoefficients.Raw raw = analysis.raw();
        double[] heatProduction = raw.heatProduction();
        double[] escapeFaces = raw.escapeFaces();

        double peakProduction = 0.0;
        for (double production : heatProduction) {
            peakProduction = Math.max(peakProduction, Math.max(0.0, production));
        }

        double maxFlow = 0.0;
        double totalFlow = 0.0;
        double maxLeak = 0.0;
        double totalLeak = 0.0;
        double ratio = operating.ratio();
        double halfScale = analysis.calibration().leakHalfScale();
        for (int cell = 0; cell < grid.size; cell++) {
            // The matrix's local exposure score is the transport model's neutron-flow proxy.
            double flow = peakProduction > 0.0
                ? Math.max(0.0, heatProduction[cell]) / peakProduction * operating.htv()
                : 0.0;
            maxFlow = Math.max(maxFlow, flow);
            totalFlow += flow;

            // Match the solver's peak-leak convention: strongest single exterior face per cell.
            double localEscape = 0.0;
            for (int face = 0; face < 6; face++) {
                localEscape = Math.max(localEscape,
                    Math.max(0.0, escapeFaces[cell * 6 + face]));
            }
            double scaledEscape = localEscape * ratio;
            double leak = scaledEscape <= 0.0
                ? 0.0
                : scaledEscape / (scaledEscape + halfScale);
            maxLeak = Math.max(maxLeak, leak);
            totalLeak += leak;
        }

        double count = Math.max(1, grid.size);
        return new Values(maxFlow, totalFlow / count, maxLeak, totalLeak / count);
    }
}
