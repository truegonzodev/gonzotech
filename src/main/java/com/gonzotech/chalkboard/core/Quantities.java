package com.gonzotech.chalkboard.core;

import com.gonzotech.chalkboard.core.Quantity.Category;
import com.gonzotech.chalkboard.core.Quantity.Kind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Canonical registry of physical nodes with SI dimension vectors and tiers.
 */
public final class Quantities {

    public static final List<Quantity> ALL;
    public static final Map<String, Quantity> BY_ID;

    private Quantities() {
    }

    private static final List<Quantity> BUILD = new ArrayList<>();

    private static void q(String id, String symbol, String ru, String en, String unit,
                          DimVec v, Category cat, Kind kind, int tier) {
        q(id, symbol, ru, en, unit, v, cat, kind, 1.0, -1.0, tier);
    }

    private static void q(String id, String symbol, String ru, String en, String unit,
                          DimVec v, Category cat, Kind kind, double value, int tier) {
        q(id, symbol, ru, en, unit, v, cat, kind, value, -1.0, tier);
    }

    private static void q(String id, String symbol, String ru, String en, String unit,
                          DimVec v, Category cat, Kind kind, double value, double complexity, int tier) {
        double c = complexity > 0 ? complexity : Quantity.complexityOf(v, kind);
        int w = Quantity.weightOf(v, cat, kind);
        BUILD.add(new Quantity(id, symbol, ru, en, unit, v, cat, kind, c, value, w, tier));
    }

    private static DimVec v(double l, double m, double t, double i, double th, double n, double j) {
        return DimVec.of(l, m, t, i, th, n, j);
    }

    static {
        // ─────────── Tier 0: SI base (weight 0) & Math Numbers ───────────
        q("length", "L", "Длина", "Length", "м", v(1, 0, 0, 0, 0, 0, 0), Category.SI, Kind.SCALAR, 0);
        q("mass", "m", "Масса", "Mass", "кг", v(0, 1, 0, 0, 0, 0, 0), Category.SI, Kind.SCALAR, 0);
        q("time", "t", "Время", "Time", "с", v(0, 0, 1, 0, 0, 0, 0), Category.SI, Kind.SCALAR, 0);
        q("current", "I", "Сила тока", "Electric current", "А", v(0, 0, 0, 1, 0, 0, 0), Category.SI, Kind.SCALAR, 0);
        q("temperature", "T", "Температура", "Temperature", "К", v(0, 0, 0, 0, 1, 0, 0), Category.SI, Kind.SCALAR, 0);
        q("amount", "n", "Количество вещества", "Amount of substance", "моль", v(0, 0, 0, 0, 0, 1, 0), Category.SI, Kind.SCALAR, 0);
        q("luminous", "Iv", "Сила света", "Luminous intensity", "кд", v(0, 0, 0, 0, 0, 0, 1), Category.SI, Kind.SCALAR, 0);

        q("num_1", "1", "Единица", "One", "1", v(0, 0, 0, 0, 0, 0, 0), Category.NUMBERS, Kind.NUMBER, 1.0, 0);
        q("num_2", "2", "Двойка", "Two", "1", v(0, 0, 0, 0, 0, 0, 0), Category.NUMBERS, Kind.NUMBER, 2.0, 0);
        q("num_half", "1/2", "Одна вторая", "One half", "1", v(0, 0, 0, 0, 0, 0, 0), Category.NUMBERS, Kind.NUMBER, 0.5, 0);

        // ─────────── Tier 1: Mechanics & Basic Thermodynamics ───────────
        q("displacement", "s", "Перемещение", "Displacement", "м", v(1, 0, 0, 0, 0, 0, 0), Category.MECHANICS, Kind.VECTOR, 1);
        q("radius", "r", "Радиус", "Radius", "м", v(1, 0, 0, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR, 1);
        q("height", "h", "Высота", "Height", "м", v(1, 0, 0, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR, 1);
        q("wavelength", "λ", "Длина волны", "Wavelength", "м", v(1, 0, 0, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR, 1);
        q("area", "S", "Площадь", "Area", "м²", v(2, 0, 0, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR, 1);
        q("volume", "V", "Объём", "Volume", "м³", v(3, 0, 0, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR, 1);
        q("velocity", "v", "Скорость", "Velocity", "м/с", v(1, 0, -1, 0, 0, 0, 0), Category.MECHANICS, Kind.VECTOR, 1);
        q("accel", "a", "Ускорение", "Acceleration", "м/с²", v(1, 0, -2, 0, 0, 0, 0), Category.MECHANICS, Kind.VECTOR, 1);
        q("force", "F", "Сила", "Force", "Н", v(1, 1, -2, 0, 0, 0, 0), Category.MECHANICS, Kind.VECTOR, 1);
        q("energy", "E", "Энергия", "Energy", "Дж", v(2, 1, -2, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR, 1);
        q("power", "P", "Мощность", "Power", "Вт", v(2, 1, -3, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR, 1);
        q("pressure", "p", "Давление", "Pressure", "Па", v(-1, 1, -2, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR, 1);
        q("density", "ρ", "Плотность", "Density", "кг/м³", v(-3, 1, 0, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR, 1);
        q("momentum", "p⃗", "Импульс", "Momentum", "кг·м/с", v(1, 1, -1, 0, 0, 0, 0), Category.MECHANICS, Kind.VECTOR, 1);
        q("impulse", "J", "Импульс силы", "Impulse", "Н·с", v(1, 1, -1, 0, 0, 0, 0), Category.MECHANICS, Kind.VECTOR, 1);
        q("frequency", "f", "Частота", "Frequency", "Гц", v(0, 0, -1, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR, 1);
        q("period", "τ", "Период", "Period", "с", v(0, 0, 1, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR, 1);
        q("angular_a", "α", "Угловое ускорение", "Angular acceleration", "рад/с²", v(0, 0, -2, 0, 0, 0, 0), Category.MECHANICS, Kind.VECTOR, 1);
        q("spring_k", "k", "Жёсткость", "Spring constant", "Н/м", v(0, 1, -2, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR, 1);
        q("stress", "σ", "Механическое напряжение", "Stress", "Па", v(-1, 1, -2, 0, 0, 0, 0), Category.MECHANICS, Kind.TENSOR, 1.0, 2.5, 1);
        q("surface_tension", "γ", "Поверхностное натяжение", "Surface tension", "Н/м", v(0, 1, -2, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR, 1);
        q("viscosity", "η", "Динамическая вязкость", "Dynamic viscosity", "Па·с", v(-1, 1, -1, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR, 1);
        q("kin_viscosity", "ν", "Кинематическая вязкость", "Kinematic viscosity", "м²/с", v(2, 0, -1, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR, 1);
        q("mass_flow", "ṁ", "Массовый расход", "Mass flow rate", "кг/с", v(0, 1, -1, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR, 1);
        q("vol_flow", "Qv", "Объёмный расход", "Volume flow rate", "м³/с", v(3, 0, -1, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR, 1);
        q("specific_vol", "υ", "Удельный объём", "Specific volume", "м³/кг", v(3, -1, 0, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR, 1);
        q("linear_density", "μl", "Линейная плотность", "Linear density", "кг/м", v(-1, 1, 0, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR, 1);
        q("surface_density", "σm", "Поверхностная плотность", "Surface density", "кг/м²", v(-2, 1, 0, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR, 1);
        q("wavenumber", "k⃗", "Волновое число", "Wavenumber", "м⁻¹", v(-1, 0, 0, 0, 0, 0, 0), Category.MECHANICS, Kind.VECTOR, 1);
        q("energy_density", "u", "Плотность энергии", "Energy density", "Дж/м³", v(-1, 1, -2, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR, 1);
        q("specific_energy", "es", "Удельная энергия", "Specific energy", "Дж/кг", v(2, 0, -2, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR, 1);
        q("young", "Y", "Модуль Юнга", "Young's modulus", "Па", v(-1, 1, -2, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR, 1);

        // ─────────── Tier 2: Thermo, Electromagnetism, Nuclear, Quantum ───────────
        q("entropy", "St", "Энтропия", "Entropy", "Дж/К", v(2, 1, -2, 0, -1, 0, 0), Category.THERMO, Kind.SCALAR, 2);
        q("heat_capacity", "C", "Теплоёмкость", "Heat capacity", "Дж/К", v(2, 1, -2, 0, -1, 0, 0), Category.THERMO, Kind.SCALAR, 2);
        q("specific_heat", "c", "Удельная теплоёмкость", "Specific heat", "Дж/(кг·К)", v(2, 0, -2, 0, -1, 0, 0), Category.THERMO, Kind.SCALAR, 2);
        q("thermal_k", "κ", "Теплопроводность", "Thermal conductivity", "Вт/(м·К)", v(1, 1, -3, 0, -1, 0, 0), Category.THERMO, Kind.SCALAR, 2);
        q("thermal_exp", "αe", "Тепловое расширение", "Thermal expansion", "К⁻¹", v(0, 0, 0, 0, -1, 0, 0), Category.THERMO, Kind.SCALAR, 2);
        q("enthalpy", "H", "Энтальпия", "Enthalpy", "Дж", v(2, 1, -2, 0, 0, 0, 0), Category.THERMO, Kind.SCALAR, 2);
        q("chem_pot", "μc", "Химический потенциал", "Chemical potential", "Дж/моль", v(2, 1, -2, 0, 0, -1, 0), Category.THERMO, Kind.SCALAR, 2);
        q("heat_flux", "q⃗", "Тепловой поток", "Heat flux", "Вт/м²", v(0, 1, -3, 0, 0, 0, 0), Category.THERMO, Kind.VECTOR, 2);
        q("temp_grad", "∇T", "Градиент температуры", "Temperature gradient", "К/м", v(-1, 0, 0, 0, 1, 0, 0), Category.THERMO, Kind.VECTOR, 2);

        q("charge", "q", "Заряд", "Charge", "Кл", v(0, 0, 1, 1, 0, 0, 0), Category.EM, Kind.SCALAR, 2);
        q("voltage", "U", "Напряжение", "Voltage", "В", v(2, 1, -3, -1, 0, 0, 0), Category.EM, Kind.SCALAR, 2);
        q("conductance", "Ge", "Проводимость", "Conductance", "См", v(-2, -1, 3, 2, 0, 0, 0), Category.EM, Kind.SCALAR, 2);
        q("capacitance", "Ce", "Ёмкость", "Capacitance", "Ф", v(-2, -1, 4, 2, 0, 0, 0), Category.EM, Kind.SCALAR, 2);
        q("inductance", "Le", "Индуктивность", "Inductance", "Гн", v(2, 1, -2, -2, 0, 0, 0), Category.EM, Kind.SCALAR, 2);
        q("e_field", "E⃗", "Электрическое поле", "Electric field", "В/м", v(1, 1, -3, -1, 0, 0, 0), Category.EM, Kind.FIELD, 1.0, 3.0, 2);
        q("b_field", "B", "Магнитная индукция", "Magnetic field B", "Тл", v(0, 1, -2, -1, 0, 0, 0), Category.EM, Kind.FIELD, 1.0, 3.0, 2);
        q("h_field", "H⃗", "Напряжённость H", "Magnetic field H", "А/м", v(-1, 0, 0, 1, 0, 0, 0), Category.EM, Kind.FIELD, 1.0, 3.0, 2);
        q("mag_flux", "ΦB", "Магнитный поток", "Magnetic flux", "Вб", v(2, 1, -2, -1, 0, 0, 0), Category.EM, Kind.SCALAR, 2);
        q("e_flux", "ΦE", "Электрический поток", "Electric flux", "В·м", v(3, 1, -3, -1, 0, 0, 0), Category.EM, Kind.SCALAR, 2);
        q("current_density", "j", "Плотность тока", "Current density", "А/м²", v(-2, 0, 0, 1, 0, 0, 0), Category.EM, Kind.VECTOR, 2);
        q("charge_density", "ρe", "Плотность заряда", "Charge density", "Кл/м³", v(-3, 0, 1, 1, 0, 0, 0), Category.EM, Kind.SCALAR, 2);
        q("permittivity", "ε", "Диэлектрическая проницаемость", "Permittivity", "Ф/м", v(-3, -1, 4, 2, 0, 0, 0), Category.EM, Kind.SCALAR, 2);
        q("permeability", "μ0", "Магнитная проницаемость", "Permeability", "Гн/м", v(1, 1, -2, -2, 0, 0, 0), Category.EM, Kind.SCALAR, 2);
        q("conductivity", "σe", "Удельная проводимость", "Conductivity", "См/м", v(-3, -1, 3, 2, 0, 0, 0), Category.EM, Kind.SCALAR, 2);
        q("impedance", "Z", "Импеданс", "Impedance", "Ом", v(2, 1, -3, -2, 0, 0, 0), Category.EM, Kind.SCALAR, 2);
        q("e_dipole", "pe", "Электрический диполь", "Electric dipole", "Кл·м", v(1, 0, 1, 1, 0, 0, 0), Category.EM, Kind.VECTOR, 2);
        q("mag_moment", "m⃗", "Магнитный момент", "Magnetic moment", "А·м²", v(2, 0, 0, 1, 0, 0, 0), Category.EM, Kind.VECTOR, 2);
        q("e_disp", "D⃗", "Электрическая индукция", "Electric displacement", "Кл/м²", v(-2, 0, 1, 1, 0, 0, 0), Category.EM, Kind.VECTOR, 2);
        q("vec_potential", "A⃗", "Векторный потенциал", "Vector potential", "Вб/м", v(1, 1, -2, -1, 0, 0, 0), Category.EM, Kind.FIELD, 1.0, 3.0, 2);
        q("poynting", "S⃗", "Вектор Пойнтинга", "Poynting vector", "Вт/м²", v(0, 1, -3, 0, 0, 0, 0), Category.EM, Kind.VECTOR, 2);
        q("admittance", "Y", "Адмиттанс", "Admittance", "См", v(-2, -1, 3, 2, 0, 0, 0), Category.EM, Kind.SCALAR, 2);
        q("reluctance", "Rm", "Магнитное сопротивление", "Reluctance", "Гн⁻¹", v(-2, -1, 2, 2, 0, 0, 0), Category.EM, Kind.SCALAR, 2);

        q("neutron_flux", "Φ", "Поток нейтронов", "Neutron flux", "м⁻²·с⁻¹", v(-2, 0, -1, 0, 0, 0, 0), Category.NUCLEAR, Kind.FIELD, 1.0, 3.0, 2);
        q("macro_xs", "Σ", "Макроскопическое сечение", "Macroscopic XS", "м⁻¹", v(-1, 0, 0, 0, 0, 0, 0), Category.NUCLEAR, Kind.SCALAR, 2);
        q("number_density", "Nv", "Числовая плотность", "Number density", "м⁻³", v(-3, 0, 0, 0, 0, 0, 0), Category.NUCLEAR, Kind.SCALAR, 2);
        q("absorbed_dose", "D", "Поглощённая доза", "Absorbed dose", "Гр", v(2, 0, -2, 0, 0, 0, 0), Category.NUCLEAR, Kind.SCALAR, 2);
        q("reaction_rate", "Rn", "Скорость реакции", "Reaction rate", "м⁻³·с⁻¹", v(-3, 0, -1, 0, 0, 0, 0), Category.NUCLEAR, Kind.SCALAR, 2);
        q("fluence", "Ψ", "Флюенс", "Fluence", "м⁻²", v(-2, 0, 0, 0, 0, 0, 0), Category.NUCLEAR, Kind.SCALAR, 2);
        q("decay_const", "λn", "Постоянная распада", "Decay constant", "с⁻¹", v(0, 0, -1, 0, 0, 0, 0), Category.NUCLEAR, Kind.SCALAR, 2);
        q("wavefn", "ψ", "Волновая функция", "Wave function", "м^-3/2", v(-1.5, 0, 0, 0, 0, 0, 0), Category.QUANTUM, Kind.FIELD, 1.0, 3.0, 2);
        q("prob_density", "|ψ|²", "Плотность вероятности", "Probability density", "м⁻³", v(-3, 0, 0, 0, 0, 0, 0), Category.QUANTUM, Kind.FIELD, 1.0, 3.0, 2);
        q("action_h", "ħ", "Приведённый квант", "Reduced Planck", "Дж·с", v(2, 1, -1, 0, 0, 0, 0), Category.QUANTUM, Kind.CONSTANT, 2);

        // ─────────── Tier 3: Optics, Chemistry, Relativistic, Advanced Constants ───────────
        q("illuminance", "Ev", "Освещённость", "Illuminance", "лк", v(-2, 0, 0, 0, 0, 0, 1), Category.OPTICS, Kind.SCALAR, 3);
        q("lum_flux", "Φv", "Световой поток", "Luminous flux", "лм", v(0, 0, 0, 0, 0, 0, 1), Category.OPTICS, Kind.SCALAR, 3);
        q("radiance", "Lr", "Яркость энергетическая", "Radiance", "Вт/м²", v(0, 1, -3, 0, 0, 0, 0), Category.OPTICS, Kind.SCALAR, 3);
        q("irradiance", "Ee", "Облучённость", "Irradiance", "Вт/м²", v(0, 1, -3, 0, 0, 0, 0), Category.OPTICS, Kind.SCALAR, 3);
        q("optical_power", "Po", "Оптическая сила", "Optical power", "дптр", v(-1, 0, 0, 0, 0, 0, 0), Category.OPTICS, Kind.SCALAR, 3);

        q("molar_mass", "M", "Молярная масса", "Molar mass", "кг/моль", v(0, 1, 0, 0, 0, -1, 0), Category.CHEMISTRY, Kind.SCALAR, 3);
        q("molar_vol", "Vm", "Молярный объём", "Molar volume", "м³/моль", v(3, 0, 0, 0, 0, -1, 0), Category.CHEMISTRY, Kind.SCALAR, 3);
        q("concentration", "cn", "Молярная концентрация", "Concentration", "моль/м³", v(-3, 0, 0, 0, 0, 1, 0), Category.CHEMISTRY, Kind.SCALAR, 3);
        q("catalytic", "kat", "Каталитическая активность", "Catalytic activity", "кат", v(0, 0, -1, 0, 0, 1, 0), Category.CHEMISTRY, Kind.SCALAR, 3);
        q("avogadro_q", "NA", "Число Авогадро", "Avogadro number", "моль⁻¹", v(0, 0, 0, 0, 0, -1, 0), Category.CHEMISTRY, Kind.CONSTANT, 3);

        q("G_grav", "G", "Гравитационная постоянная", "Gravitational G", "м³/(кг·с²)", v(3, -1, -2, 0, 0, 0, 0), Category.CONSTANTS, Kind.CONSTANT, 3);
        q("h_planck", "h", "Постоянная Планка", "Planck constant", "Дж·с", v(2, 1, -1, 0, 0, 0, 0), Category.CONSTANTS, Kind.CONSTANT, 3);
        q("k_boltzmann", "kB", "Постоянная Больцмана", "Boltzmann constant", "Дж/К", v(2, 1, -2, 0, -1, 0, 0), Category.CONSTANTS, Kind.CONSTANT, 3);
        q("R_gas", "R", "Газовая постоянная", "Gas constant", "Дж/(моль·К)", v(2, 1, -2, 0, -1, -1, 0), Category.CONSTANTS, Kind.CONSTANT, 3);
        q("stefan", "σB", "Постоянная Стефана–Больцмана", "Stefan-Boltzmann", "Вт/(м²·К⁴)", v(0, 1, -3, 0, -4, 0, 0), Category.CONSTANTS, Kind.CONSTANT, 3);
        q("faraday", "Fa", "Постоянная Фарадея", "Faraday constant", "Кл/моль", v(0, 0, 1, 1, 0, -1, 0), Category.CONSTANTS, Kind.CONSTANT, 3);

        q("stress_tensor", "σij", "Тензор напряжений", "Stress tensor", "Па", v(-1, 1, -2, 0, 0, 0, 0), Category.TENSORS, Kind.TENSOR, 1.0, 2.5, 3);
        q("ricci", "Rij", "Тензор Риччи", "Ricci tensor", "м⁻²", v(-2, 0, 0, 0, 0, 0, 0), Category.TENSORS, Kind.TENSOR, 1.0, 2.5, 3);
        q("riemann", "Rpijk", "Тензор Римана", "Riemann tensor", "м⁻²", v(-2, 0, 0, 0, 0, 0, 0), Category.TENSORS, Kind.TENSOR, 1.0, 2.5, 3);
        q("em_tensor", "Fμν", "Тензор ЭМ-поля", "EM field tensor", "Тл", v(0, 1, -2, -1, 0, 0, 0), Category.TENSORS, Kind.TENSOR, 1.0, 2.5, 3);
        q("emt_tensor", "Tμν", "Тензор энергии-импульса", "Energy-momentum tensor", "Дж/м³", v(-1, 1, -2, 0, 0, 0, 0), Category.TENSORS, Kind.TENSOR, 1.0, 2.5, 3);
        q("inertia_tensor", "Iij", "Тензор инерции", "Inertia tensor", "кг·м²", v(2, 1, 0, 0, 0, 0, 0), Category.TENSORS, Kind.TENSOR, 1.0, 2.5, 3);

        q("grav_potential", "Φg", "Гравитационный потенциал", "Gravitational potential", "Дж/кг", v(2, 0, -2, 0, 0, 0, 0), Category.FIELDS, Kind.FIELD, 1.0, 3.0, 3);
        q("higgs", "φH", "Поле Хиггса", "Higgs field", "ГэВ", v(2, 1, -2, 0, 0, 0, 0), Category.FIELDS, Kind.FIELD, 1.0, 3.0, 3);
        q("cosmo_const", "Λ", "Космологическая постоянная", "Cosmological constant", "м⁻²", v(-2, 0, 0, 0, 0, 0, 0), Category.FIELDS, Kind.FIELD, 1.0, 3.0, 3);
        q("hubble", "H0", "Постоянная Хаббла", "Hubble constant", "с⁻¹", v(0, 0, -1, 0, 0, 0, 0), Category.FIELDS, Kind.FIELD, 1.0, 3.0, 3);
        q("num_pi", "π", "Пи", "Pi", "1", v(0, 0, 0, 0, 0, 0, 0), Category.NUMBERS, Kind.NUMBER, Math.PI, 3);

        // ─────────── Tier 4: Secret quantities ───────────
        q("secret_K", "K", "Высокочастотная импедансная индукция", "HF Impedance Inductance", "Гн/с", v(2, 1, -3, -2, 0, 0, 0), Category.EM, Kind.SCALAR, 4);
        q("secret_O", "O", "Объёмный коэффициент динамической вязкости", "Volumetric Dynamic Viscosity", "Па·с/м³", v(-4, 1, -1, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR, 4);
        q("secret_M", "M", "Момент пространственного поглощения энергии", "Spatial Energy Absorption Moment", "Дж/с²", v(2, 1, -4, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR, 4);
        q("molar_rad_density", "D_rad", "Молярная радиационная плотность", "Molar Radiation Density", "К⁴·моль", v(0, 0, 0, 0, 4, 1, 0), Category.QUANTUM, Kind.SCALAR, 4);

        // ─────────── Tier 99: Super-Secret quantities ───────────
        q("secret_I_alpha", "Iα", "Градиент фотонно-плазменного насыщения", "Singularity Plasma Saturation Grad", "моль·кд/(Тл·К·с³)", v(-1, 1, -5, -1, -1, 1, 1), Category.FIELDS, Kind.FIELD, 1.0, 3.0, 99);
        q("secret_T_index", "Tindex", "Квантово-химический индекс упругости", "Quantum-Chemical Elasticity Index", "с/(Гн·м·Бц)", v(-1, 1, -1, 2, -4, -1, 0), Category.QUANTUM, Kind.FIELD, 1.0, 3.0, 99);
        q("secret_X_mu", "Xμ", "Коэффициент магнито-термического дрейфа", "Magneto-Thermal Drift Coeff", "Вт·К", v(2, 1, -3, 0, -1, 0, 0), Category.THERMO, Kind.SCALAR, 99);
        q("secret_Phi_dot", "Φ̇", "Торможение реакции аннигиляции", "Annihilation Effective Deceleration", "А²/(м³·моль)", v(-3, 0, 0, 2, 0, -1, 0), Category.NUCLEAR, Kind.SCALAR, 99);

        // New Tier 99 quantities:
        q("secret_Kappa_Tau", "κτ", "Градиент топологической жесткости струны", "String Topological Stiffness Grad", "Н³/(А·К·моль)", v(3, 3, -6, -1, -1, -1, 0), Category.FIELDS, Kind.FIELD, 1.0, 3.0, 99);
        q("secret_Gamma_Iota", "γι", "Коэффициент релятивистской электро-энтропийной вязкости", "Relativistic Electro-Entropic Viscosity Coeff", "См²·м·с/Бц²", v(-8, -2, 4, 4, -2, 2, 0), Category.QUANTUM, Kind.SCALAR, 99);
        q("secret_Etta_Omega", "ηω", "Молярный квантово-оптический барьер металлического водорода", "Molar Quantum-Optical Barrier", "Дж·кд²/(м²·К·моль)", v(0, 1, -2, 0, -1, -1, 2), Category.OPTICS, Kind.SCALAR, 99);
        q("secret_Zeta_dot", "ζ̇", "Порог радиационно-индуктивного пробоя водородной матрицы", "Radiation-Inductive Breakdown Threshold", "В³·А/(Гн·с·К)", v(1, 2, -4, 4, -1, 0, 0), Category.NUCLEAR, Kind.FIELD, 1.0, 3.0, 99);
        q("secret_Chi_Rho", "χρ", "Хи-Ро (Индекс квантово-фрактального удержания энергокристалла)", "Quantum-Fractal Confinement Index", "В·А²·м⁵/(с·моль)", v(7, 1, -4, 1, 1, -1, 0), Category.QUANTUM, Kind.SCALAR, 99);

        ALL = Collections.unmodifiableList(new ArrayList<>(BUILD));
        Map<String, Quantity> map = new LinkedHashMap<>();
        for (Quantity qq : ALL) map.put(qq.id(), qq);
        BY_ID = Collections.unmodifiableMap(map);
        BUILD.clear();
    }

    public static Quantity get(String id) {
        return id == null ? null : BY_ID.get(id);
    }
}
