package com.gonzotech.machines.reactor;

/**
 * ReaCalc 1.7 game-time thermal integrator. This class has no Minecraft
 * dependency so its time-step equations can be checked independently.
 */
public final class ReactorHeatDynamics {

    public static final double AMBIENT = 20.0;
    public static final double TICKS_PER_SECOND = 20.0;
    public static final double K = 0.01;
    public static final double DELAY = 20.0;
    public static final double EPSILON = 0.0001;
    public static final double RUN_GAIN = 4.0 / (K * ReactorOperatingMetrics.TEMP_SCALE);

    // Independent game cooling clock: 1 s hold, 2 s ramp, then up to 250 °C/s.
    public static final double COOL_DELAY = 20.0;
    public static final double COOL_RAMP = 40.0;
    public static final double COOL_RATE = 12.5;
    public static final double COOL_TAU = 60.0;
    public static final double COOL_TOLERANCE = 5.0;

    private ReactorHeatDynamics() {
    }

    public static double coolingClock(double age) {
        double t = Math.max(0.0, age - COOL_DELAY);
        return t < COOL_RAMP ? t * t / (2.0 * COOL_RAMP) : t - COOL_RAMP / 2.0;
    }

    public static double coolingClockInverse(double elapsed) {
        return COOL_DELAY + (elapsed < COOL_RAMP / 2.0
            ? Math.sqrt(2.0 * COOL_RAMP * elapsed)
            : elapsed + COOL_RAMP / 2.0);
    }

    public static double coolGap(double gap, double elapsed) {
        double threshold = COOL_RATE * COOL_TAU;
        if (gap > threshold) {
            double linear = Math.min(elapsed, (gap - threshold) / COOL_RATE);
            gap -= COOL_RATE * linear;
            elapsed -= linear;
        }
        return gap * Math.exp(-elapsed / COOL_TAU);
    }

    public record Parameters(double h, double mass, double sourcePower,
                             double target, double tau, double acceleration,
                             double exchangeSlope, double usefulSlope,
                             ReactorHeatOutputCurve outputCurve) {
        /** Linear fixture constructor used by the integrator oracle tests. */
        public Parameters(double h, double mass, double sourcePower,
                          double target, double tau, double acceleration,
                          double exchangeSlope, double usefulSlope) {
            this(h, mass, sourcePower, target, tau, acceleration,
                exchangeSlope, usefulSlope, null);
        }
    }

    public static Parameters parameters(MatrixCoefficients.Analysis analysis,
                                        ReactorOperatingMetrics.Values values,
                                        ReactorHeatOutputCurve outputCurve) {
        MatrixCoefficients.Grid grid = analysis.grid();
        int mass = grid.size;
        double target = values.overheat()
            ? AMBIENT + ReactorOperatingMetrics.TEMP_SCALE
            : values.hotTemperature();
        double tau = values.power() > 0.0
            ? Math.max(0.001, K * Math.max(1, mass) * (target - AMBIENT) / values.power())
            : 4.0 * Math.pow(Math.max(1, mass), 2.0 / 3.0);
        double acceleration = values.overheat()
            ? RUN_GAIN * values.power() / Math.max(1, mass) * (values.htv() - 1.0 + EPSILON)
            : 0.0;
        return new Parameters(values.htv(), mass, values.power(), target, tau,
            acceleration, 0.0, 0.0, outputCurve);
    }

    /** Persistable state values; operating parameters are rebuilt from the live matrix on load. */
    public record Saved(boolean configured, double h, double base,
                        double excess, double velocity, double hold,
                        double coolingAge) {
    }

    public static final class State {
        private Parameters parameters;
        private double base = AMBIENT;
        private double excess;
        private double velocity;
        private double hold = DELAY;
        private double coolingAge = -1.0;
        private boolean restoredConfiguration;
        private double restoredH;
        private double exchangeSlope;
        private double usefulSlope;
        private ReactorHeatOutputCurve cachedCurve;
        private double cachedTemperature = Double.NaN;
        private ReactorHeatOutputCurve.Output cachedOutput = ReactorHeatOutputCurve.ZERO;

        public void configure(Parameters next) {
            boolean wasOvercritical = parameters != null
                ? parameters.h() >= 1.0
                : restoredConfiguration && restoredH >= 1.0;
            boolean continuing = wasOvercritical && next.h() >= 1.0;
            double current = temperature();
            if (!continuing) {
                base = current;
                excess = 0.0;
                velocity = 0.0;
                hold = DELAY;
            }
            // Re-entering the accelerating regime retains heat above the new reference target.
            if (!continuing && next.h() >= 1.0 && base > next.target()) {
                excess = base - next.target();
                base = next.target();
            }
            parameters = next;
            restoredConfiguration = false;

            if (next.h() < 1.0 && current > next.target()) {
                if (coolingAge < 0.0) coolingAge = 0.0;
            } else {
                coolingAge = -1.0;
            }
            if (next.sourcePower() > 0.0 || current <= AMBIENT + 1.0e-9) {
                exchangeSlope = next.exchangeSlope();
                usefulSlope = next.usefulSlope();
            }
            cachedCurve = null;
            cachedTemperature = Double.NaN;
        }

        public boolean configured() {
            return parameters != null;
        }

        public double temperature() {
            return base + excess;
        }

        /** (0.3.272, N10) Приёмка уголька: поднять показание не ниже hot (через excess). */
        public void raiseTo(double hot) {
            if (hot > temperature()) {
                excess = hot - base;
                cachedCurve = null;
                cachedTemperature = Double.NaN;
            }
        }

        public double meanTemperature() {
            return meanTemperature(temperature());
        }

        /** Mean reading for an explicit hot temperature (extra layers stack on the integrator). */
        public double meanTemperature(double hotTemperature) {
            return parameters != null && parameters.outputCurve() != null
                ? parameters.outputCurve().meanTemperature(hotTemperature)
                : hotTemperature;
        }

        public boolean cooling() {
            return parameters != null && parameters.h() < 1.0
                && temperature() > parameters.target();
        }

        public double coolingRemaining() {
            if (!cooling()) return 0.0;
            double gap = temperature() - parameters.target();
            if (gap <= COOL_TOLERANCE) return 0.0;
            double threshold = COOL_RATE * COOL_TAU;
            double work = Math.max(0.0, (gap - threshold) / COOL_RATE)
                + COOL_TAU * Math.log(Math.min(gap, threshold) / COOL_TOLERANCE);
            double age = Math.max(0.0, coolingAge);
            return Math.max(0.0,
                coolingClockInverse(coolingClock(age) + work) - age);
        }

        public ReactorHeatOutputCurve.Output output() {
            if (parameters == null) return ReactorHeatOutputCurve.ZERO;
            ReactorHeatOutputCurve curve = parameters.outputCurve();
            double current = temperature();
            if (curve == null) {
                return new ReactorHeatOutputCurve.Output(
                    Math.max(0.0, current - AMBIENT) * exchangeSlope,
                    Math.max(0.0, current - AMBIENT) * usefulSlope);
            }
            if (curve != cachedCurve || current != cachedTemperature) {
                cachedCurve = curve;
                cachedTemperature = current;
                cachedOutput = curve.at(current);
            }
            return cachedOutput;
        }

        /** Output at the integrator reading plus an extra temperature layer (GTH back-pressure). */
        public ReactorHeatOutputCurve.Output output(double extraHeat) {
            double extra = Double.isFinite(extraHeat) ? Math.max(0.0, extraHeat) : 0.0;
            return extra == 0.0 ? output() : outputAt(temperature() + extra);
        }

        /** Output at an explicit hot temperature without touching the cached reading. */
        public ReactorHeatOutputCurve.Output outputAt(double hotTemperature) {
            if (parameters == null) return ReactorHeatOutputCurve.ZERO;
            ReactorHeatOutputCurve curve = parameters.outputCurve();
            if (curve == null) {
                return new ReactorHeatOutputCurve.Output(
                    Math.max(0.0, hotTemperature - AMBIENT) * exchangeSlope,
                    Math.max(0.0, hotTemperature - AMBIENT) * usefulSlope);
            }
            return curve.at(hotTemperature);
        }

        public double exchange() {
            return output().exchange();
        }

        public double useful() {
            return output().useful();
        }

        public String phase() {
            if (parameters == null) return "Нет расчёта";
            if (cooling() && temperature() - parameters.target() > COOL_TOLERANCE) {
                return coolingAge < COOL_DELAY
                    ? "Рост остановлен · задержка охлаждения"
                    : "Медленное остывание";
            }
            if (parameters.sourcePower() == 0.0) {
                return temperature() > AMBIENT + 0.01 ? "Почти остыл" : "Холодный";
            }
            if (parameters.h() < 1.0) {
                return Math.abs(temperature() - parameters.target())
                    <= Math.max(0.01, (parameters.target() - AMBIENT) * 0.01)
                    ? "Около плато"
                    : temperature() > parameters.target() ? "Остывание к плато" : "Нагрев";
            }
            if (velocity > 0.0) return "Ускоряющийся разгон";
            return temperature() >= AMBIENT + 0.99 * (parameters.target() - AMBIENT)
                ? "Задержка перед разгоном" : "Нагрев · Htv ≥ 1";
        }

        private double warmTime() {
            double threshold = AMBIENT + 0.99 * (parameters.target() - AMBIENT);
            return base < threshold
                ? parameters.tau() * Math.log((parameters.target() - base)
                    / (parameters.target() - threshold))
                : 0.0;
        }

        private double predicted(double dt) {
            double nextBase = parameters.target()
                + (base - parameters.target()) * Math.exp(-dt / parameters.tau());
            if (parameters.h() < 1.0) return nextBase;
            double active = Math.max(0.0, dt - warmTime() - hold);
            return nextBase + excess + velocity * active
                + parameters.acceleration() * active * active;
        }

        /** Advance without a 10,000 °C trip: the core and its output keep running. */
        public boolean advance(double dt) {
            if (!Double.isFinite(dt) || dt < 0.0) {
                throw new IllegalArgumentException("Некорректный шаг времени");
            }
            if (parameters == null || dt == 0.0) return false;
            double before = temperature();
            if (cooling()) {
                coolingAge = Math.max(0.0, coolingAge);
                double elapsed = coolingClock(coolingAge + dt) - coolingClock(coolingAge);
                base = parameters.target() + coolGap(base - parameters.target(), elapsed);
                coolingAge += dt;
                return temperature() != before;
            }

            coolingAge = -1.0;
            if (parameters.h() >= 1.0) {
                double afterWarm = Math.max(0.0, dt - warmTime());
                double active = Math.max(0.0, afterWarm - hold);
                hold = Math.max(0.0, hold - afterWarm);
                excess += velocity * active + parameters.acceleration() * active * active;
                velocity += 2.0 * parameters.acceleration() * active;
            }
            base = parameters.target()
                + (base - parameters.target()) * Math.exp(-dt / parameters.tau());
            return temperature() != before;
        }

        public Saved save() {
            return new Saved(parameters != null || restoredConfiguration,
                parameters != null ? parameters.h() : restoredH,
                base, excess, velocity, hold, coolingAge);
        }

        public void restore(Saved saved) {
            parameters = null;
            base = finiteOr(saved.base(), AMBIENT);
            excess = Math.max(0.0, finiteOr(saved.excess(), 0.0));
            velocity = Math.max(0.0, finiteOr(saved.velocity(), 0.0));
            hold = Math.max(0.0, finiteOr(saved.hold(), DELAY));
            coolingAge = finiteOr(saved.coolingAge(), -1.0);
            restoredConfiguration = saved.configured();
            restoredH = Math.max(0.0, finiteOr(saved.h(), 0.0));
            cachedCurve = null;
            cachedTemperature = Double.NaN;
            cachedOutput = ReactorHeatOutputCurve.ZERO;
        }

        private static double finiteOr(double value, double fallback) {
            return Double.isFinite(value) ? value : fallback;
        }
    }
}
