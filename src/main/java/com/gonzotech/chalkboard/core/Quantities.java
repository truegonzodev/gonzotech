package com.gonzotech.chalkboard.core;

import com.gonzotech.chalkboard.core.Quantity.Category;
import com.gonzotech.chalkboard.core.Quantity.Kind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Canonical registry of physical nodes. Every entry carries a real SI dimension
 * vector — nothing here is invented, which is exactly why "love" is derivable
 * instead of hand-written in a relation table.
 */
public final class Quantities {

    public static final List<Quantity> ALL;
    public static final Map<String, Quantity> BY_ID;

    private Quantities() {
    }

    private static final List<Quantity> BUILD = new ArrayList<>();

    private static void q(String id, String symbol, String ru, String en, String unit,
                          DimVec v, Category cat, Kind kind) {
        q(id, symbol, ru, en, unit, v, cat, kind, 1.0, -1.0);
    }

    private static void q(String id, String symbol, String ru, String en, String unit,
                          DimVec v, Category cat, Kind kind, double value) {
        q(id, symbol, ru, en, unit, v, cat, kind, value, -1.0);
    }

    private static void q(String id, String symbol, String ru, String en, String unit,
                          DimVec v, Category cat, Kind kind, double value, double complexity) {
        double c = complexity > 0 ? complexity : Quantity.complexityOf(v, kind);
        int w = Quantity.weightOf(v, cat, kind);
        BUILD.add(new Quantity(id, symbol, ru, en, unit, v, cat, kind, c, value, w));
    }

    private static DimVec v(double l, double m, double t, double i, double th, double n, double j) {
        return DimVec.of(l, m, t, i, th, n, j);
    }

    static {
        // ─────────── SI base (weight 0) ───────────
        q("length", "L", "Длина", "Length", "м", v(1, 0, 0, 0, 0, 0, 0), Category.SI, Kind.SCALAR);
        q("mass", "m", "Масса", "Mass", "кг", v(0, 1, 0, 0, 0, 0, 0), Category.SI, Kind.SCALAR);
        q("time", "t", "Время", "Time", "с", v(0, 0, 1, 0, 0, 0, 0), Category.SI, Kind.SCALAR);
        q("current", "I", "Сила тока", "Electric current", "А", v(0, 0, 0, 1, 0, 0, 0), Category.SI, Kind.SCALAR);
        q("temperature", "T", "Температура", "Temperature", "К", v(0, 0, 0, 0, 1, 0, 0), Category.SI, Kind.SCALAR);
        q("amount", "n", "Количество вещества", "Amount of substance", "моль", v(0, 0, 0, 0, 0, 1, 0), Category.SI, Kind.SCALAR);
        q("luminous", "Iv", "Сила света", "Luminous intensity", "кд", v(0, 0, 0, 0, 0, 0, 1), Category.SI, Kind.SCALAR);

        // ─────────── Mechanics ───────────
        q("displacement", "s", "Перемещение", "Displacement", "м", v(1, 0, 0, 0, 0, 0, 0), Category.MECHANICS, Kind.VECTOR);
        q("radius", "r", "Радиус", "Radius", "м", v(1, 0, 0, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR);
        q("height", "h", "Высота", "Height", "м", v(1, 0, 0, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR);
        q("wavelength", "λ", "Длина волны", "Wavelength", "м", v(1, 0, 0, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR);
        q("area", "S", "Площадь", "Area", "м²", v(2, 0, 0, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR);
        q("volume", "V", "Объём", "Volume", "м³", v(3, 0, 0, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR);
        q("velocity", "v", "Скорость", "Velocity", "м/с", v(1, 0, -1, 0, 0, 0, 0), Category.MECHANICS, Kind.VECTOR);
        q("accel", "a", "Ускорение", "Acceleration", "м/с²", v(1, 0, -2, 0, 0, 0, 0), Category.MECHANICS, Kind.VECTOR);
        q("force", "F", "Сила", "Force", "Н", v(1, 1, -2, 0, 0, 0, 0), Category.MECHANICS, Kind.VECTOR);
        q("energy", "E", "Энергия", "Energy", "Дж", v(2, 1, -2, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR);
        q("work", "W", "Работа", "Work", "Дж", v(2, 1, -2, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR);
        q("power", "P", "Мощность", "Power", "Вт", v(2, 1, -3, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR);
        q("pressure", "p", "Давление", "Pressure", "Па", v(-1, 1, -2, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR);
        q("density", "ρ", "Плотность", "Density", "кг/м³", v(-3, 1, 0, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR);
        q("momentum", "p⃗", "Импульс", "Momentum", "кг·м/с", v(1, 1, -1, 0, 0, 0, 0), Category.MECHANICS, Kind.VECTOR);
        q("impulse", "J", "Импульс силы", "Impulse", "Н·с", v(1, 1, -1, 0, 0, 0, 0), Category.MECHANICS, Kind.VECTOR);
        q("frequency", "f", "Частота", "Frequency", "Гц", v(0, 0, -1, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR);
        q("period", "τ", "Период", "Period", "с", v(0, 0, 1, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR);
        q("angular_v", "ω", "Угловая скорость", "Angular velocity", "рад/с", v(0, 0, -1, 0, 0, 0, 0), Category.MECHANICS, Kind.VECTOR);
        q("angular_a", "α", "Угловое ускорение", "Angular acceleration", "рад/с²", v(0, 0, -2, 0, 0, 0, 0), Category.MECHANICS, Kind.VECTOR);
        q("torque", "Mτ", "Момент силы", "Torque", "Н·м", v(2, 1, -2, 0, 0, 0, 0), Category.MECHANICS, Kind.VECTOR);
        q("ang_momentum", "L⃗", "Момент импульса", "Angular momentum", "кг·м²/с", v(2, 1, -1, 0, 0, 0, 0), Category.MECHANICS, Kind.VECTOR);
        q("inertia", "Im", "Момент инерции", "Moment of inertia", "кг·м²", v(2, 1, 0, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR);
        q("spring_k", "k", "Жёсткость", "Spring constant", "Н/м", v(0, 1, -2, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR);
        q("stress", "σ", "Напряжение", "Stress", "Па", v(-1, 1, -2, 0, 0, 0, 0), Category.MECHANICS, Kind.TENSOR, 1.0, 2.5);
        q("strain", "ε", "Деформация", "Strain", "1", v(0, 0, 0, 0, 0, 0, 0), Category.MECHANICS, Kind.TENSOR, 1.0, 2.5);
        q("surface_tension", "γ", "Поверхностное натяжение", "Surface tension", "Н/м", v(0, 1, -2, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR);
        q("viscosity", "η", "Динамическая вязкость", "Dynamic viscosity", "Па·с", v(-1, 1, -1, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR);
        q("kin_viscosity", "ν", "Кинематическая вязкость", "Kinematic viscosity", "м²/с", v(2, 0, -1, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR);
        q("mass_flow", "ṁ", "Массовый расход", "Mass flow rate", "кг/с", v(0, 1, -1, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR);
        q("vol_flow", "Qv", "Объёмный расход", "Volume flow rate", "м³/с", v(3, 0, -1, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR);
        q("specific_vol", "υ", "Удельный объём", "Specific volume", "м³/кг", v(3, -1, 0, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR);
        q("linear_density", "μl", "Линейная плотность", "Linear density", "кг/м", v(-1, 1, 0, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR);
        q("surface_density", "σm", "Поверхностная плотность", "Surface density", "кг/м²", v(-2, 1, 0, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR);
        q("wavenumber", "k⃗", "Волновое число", "Wavenumber", "м⁻¹", v(-1, 0, 0, 0, 0, 0, 0), Category.MECHANICS, Kind.VECTOR);
        q("action", "Sa", "Действие", "Action", "Дж·с", v(2, 1, -1, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR);
        q("energy_density", "u", "Плотность энергии", "Energy density", "Дж/м³", v(-1, 1, -2, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR);
        q("specific_energy", "es", "Удельная энергия", "Specific energy", "Дж/кг", v(2, 0, -2, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR);
        q("intensity", "Ie", "Интенсивность", "Intensity", "Вт/м²", v(0, 1, -3, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR);
        q("young", "Y", "Модуль Юнга", "Young's modulus", "Па", v(-1, 1, -2, 0, 0, 0, 0), Category.MECHANICS, Kind.SCALAR);

        // ─────────── Thermodynamics ───────────
        q("heat", "Q", "Теплота", "Heat", "Дж", v(2, 1, -2, 0, 0, 0, 0), Category.THERMO, Kind.SCALAR);
        q("entropy", "St", "Энтропия", "Entropy", "Дж/К", v(2, 1, -2, 0, -1, 0, 0), Category.THERMO, Kind.SCALAR);
        q("heat_capacity", "C", "Теплоёмкость", "Heat capacity", "Дж/К", v(2, 1, -2, 0, -1, 0, 0), Category.THERMO, Kind.SCALAR);
        q("specific_heat", "c", "Удельная теплоёмкость", "Specific heat", "Дж/(кг·К)", v(2, 0, -2, 0, -1, 0, 0), Category.THERMO, Kind.SCALAR);
        q("thermal_k", "κ", "Теплопроводность", "Thermal conductivity", "Вт/(м·К)", v(1, 1, -3, 0, -1, 0, 0), Category.THERMO, Kind.SCALAR);
        q("thermal_diff", "αt", "Температуропроводность", "Thermal diffusivity", "м²/с", v(2, 0, -1, 0, 0, 0, 0), Category.THERMO, Kind.SCALAR);
        q("thermal_exp", "αe", "Тепловое расширение", "Thermal expansion", "К⁻¹", v(0, 0, 0, 0, -1, 0, 0), Category.THERMO, Kind.SCALAR);
        q("enthalpy", "H", "Энтальпия", "Enthalpy", "Дж", v(2, 1, -2, 0, 0, 0, 0), Category.THERMO, Kind.SCALAR);
        q("chem_pot", "μc", "Химический потенциал", "Chemical potential", "Дж/моль", v(2, 1, -2, 0, 0, -1, 0), Category.THERMO, Kind.SCALAR);
        q("latent_heat", "Lh", "Удельная теплота", "Latent heat", "Дж/кг", v(2, 0, -2, 0, 0, 0, 0), Category.THERMO, Kind.SCALAR);
        q("heat_flux", "q⃗", "Тепловой поток", "Heat flux", "Вт/м²", v(0, 1, -3, 0, 0, 0, 0), Category.THERMO, Kind.VECTOR);
        q("temp_grad", "∇T", "Градиент температуры", "Temperature gradient", "К/м", v(-1, 0, 0, 0, 1, 0, 0), Category.THERMO, Kind.VECTOR);
        q("internal_e", "U", "Внутренняя энергия", "Internal energy", "Дж", v(2, 1, -2, 0, 0, 0, 0), Category.THERMO, Kind.SCALAR);
        q("gibbs", "G", "Энергия Гиббса", "Gibbs energy", "Дж", v(2, 1, -2, 0, 0, 0, 0), Category.THERMO, Kind.SCALAR);

        // ─────────── Electromagnetism ───────────
        q("charge", "q", "Заряд", "Charge", "Кл", v(0, 0, 1, 1, 0, 0, 0), Category.EM, Kind.SCALAR);
        q("voltage", "U", "Напряжение", "Voltage", "В", v(2, 1, -3, -1, 0, 0, 0), Category.EM, Kind.SCALAR);
        q("resistance", "R", "Сопротивление", "Resistance", "Ом", v(2, 1, -3, -2, 0, 0, 0), Category.EM, Kind.SCALAR);
        q("conductance", "Ge", "Проводимость", "Conductance", "См", v(-2, -1, 3, 2, 0, 0, 0), Category.EM, Kind.SCALAR);
        q("capacitance", "Ce", "Ёмкость", "Capacitance", "Ф", v(-2, -1, 4, 2, 0, 0, 0), Category.EM, Kind.SCALAR);
        q("inductance", "Le", "Индуктивность", "Inductance", "Гн", v(2, 1, -2, -2, 0, 0, 0), Category.EM, Kind.SCALAR);
        q("e_field", "E⃗", "Электрическое поле", "Electric field", "В/м", v(1, 1, -3, -1, 0, 0, 0), Category.EM, Kind.FIELD, 1.0, 3.0);
        q("b_field", "B", "Магнитная индукция", "Magnetic field B", "Тл", v(0, 1, -2, -1, 0, 0, 0), Category.EM, Kind.FIELD, 1.0, 3.0);
        q("h_field", "H⃗", "Напряжённость H", "Magnetic field H", "А/м", v(-1, 0, 0, 1, 0, 0, 0), Category.EM, Kind.FIELD, 1.0, 3.0);
        q("mag_flux", "ΦB", "Магнитный поток", "Magnetic flux", "Вб", v(2, 1, -2, -1, 0, 0, 0), Category.EM, Kind.SCALAR);
        q("e_flux", "ΦE", "Электрический поток", "Electric flux", "В·м", v(3, 1, -3, -1, 0, 0, 0), Category.EM, Kind.SCALAR);
        q("current_density", "j", "Плотность тока", "Current density", "А/м²", v(-2, 0, 0, 1, 0, 0, 0), Category.EM, Kind.VECTOR);
        q("charge_density", "ρe", "Плотность заряда", "Charge density", "Кл/м³", v(-3, 0, 1, 1, 0, 0, 0), Category.EM, Kind.SCALAR);
        q("permittivity", "ε", "Диэлектрическая проницаемость", "Permittivity", "Ф/м", v(-3, -1, 4, 2, 0, 0, 0), Category.EM, Kind.SCALAR);
        q("permeability", "μ0", "Магнитная проницаемость", "Permeability", "Гн/м", v(1, 1, -2, -2, 0, 0, 0), Category.EM, Kind.SCALAR);
        q("conductivity", "σe", "Удельная проводимость", "Conductivity", "См/м", v(-3, -1, 3, 2, 0, 0, 0), Category.EM, Kind.SCALAR);
        q("impedance", "Z", "Импеданс", "Impedance", "Ом", v(2, 1, -3, -2, 0, 0, 0), Category.EM, Kind.SCALAR);
        q("e_dipole", "pe", "Электрический диполь", "Electric dipole", "Кл·м", v(1, 0, 1, 1, 0, 0, 0), Category.EM, Kind.VECTOR);
        q("mag_moment", "m⃗", "Магнитный момент", "Magnetic moment", "А·м²", v(2, 0, 0, 1, 0, 0, 0), Category.EM, Kind.VECTOR);
        q("e_disp", "D⃗", "Электрическая индукция", "Electric displacement", "Кл/м²", v(-2, 0, 1, 1, 0, 0, 0), Category.EM, Kind.VECTOR);
        q("vec_potential", "A⃗", "Векторный потенциал", "Vector potential", "Вб/м", v(1, 1, -2, -1, 0, 0, 0), Category.EM, Kind.FIELD, 1.0, 3.0);
        q("poynting", "S⃗", "Вектор Пойнтинга", "Poynting vector", "Вт/м²", v(0, 1, -3, 0, 0, 0, 0), Category.EM, Kind.VECTOR);
        q("admittance", "Y", "Адмиттанс", "Admittance", "См", v(-2, -1, 3, 2, 0, 0, 0), Category.EM, Kind.SCALAR);
        q("reluctance", "Rm", "Магнитное сопротивление", "Reluctance", "Гн⁻¹", v(-2, -1, 2, 2, 0, 0, 0), Category.EM, Kind.SCALAR);

        // ─────────── Nuclear / quantum ───────────
        q("neutron_flux", "Φ", "Поток нейтронов", "Neutron flux", "м⁻²·с⁻¹", v(-2, 0, -1, 0, 0, 0, 0), Category.NUCLEAR, Kind.FIELD, 1.0, 3.0);
        q("cross_section", "σn", "Сечение", "Cross section", "м²", v(2, 0, 0, 0, 0, 0, 0), Category.NUCLEAR, Kind.SCALAR);
        q("macro_xs", "Σ", "Макроскопическое сечение", "Macroscopic XS", "м⁻¹", v(-1, 0, 0, 0, 0, 0, 0), Category.NUCLEAR, Kind.SCALAR);
        q("number_density", "Nv", "Числовая плотность", "Number density", "м⁻³", v(-3, 0, 0, 0, 0, 0, 0), Category.NUCLEAR, Kind.SCALAR);
        q("activity", "A", "Активность", "Activity", "Бк", v(0, 0, -1, 0, 0, 0, 0), Category.NUCLEAR, Kind.SCALAR);
        q("absorbed_dose", "D", "Поглощённая доза", "Absorbed dose", "Гр", v(2, 0, -2, 0, 0, 0, 0), Category.NUCLEAR, Kind.SCALAR);
        q("fission_e", "Ef", "Энергия деления", "Fission energy", "Дж", v(2, 1, -2, 0, 0, 0, 0), Category.NUCLEAR, Kind.SCALAR);
        q("reaction_rate", "Rn", "Скорость реакции", "Reaction rate", "м⁻³·с⁻¹", v(-3, 0, -1, 0, 0, 0, 0), Category.NUCLEAR, Kind.SCALAR);
        q("fluence", "Ψ", "Флюенс", "Fluence", "м⁻²", v(-2, 0, 0, 0, 0, 0, 0), Category.NUCLEAR, Kind.SCALAR);
        q("decay_const", "λn", "Постоянная распада", "Decay constant", "с⁻¹", v(0, 0, -1, 0, 0, 0, 0), Category.NUCLEAR, Kind.SCALAR);
        q("wavefn", "ψ", "Волновая функция", "Wave function", "м^-3/2", v(-1.5, 0, 0, 0, 0, 0, 0), Category.QUANTUM, Kind.FIELD, 1.0, 3.0);
        q("prob_density", "|ψ|²", "Плотность вероятности", "Probability density", "м⁻³", v(-3, 0, 0, 0, 0, 0, 0), Category.QUANTUM, Kind.FIELD, 1.0, 3.0);
        q("compton", "λC", "Длина Комптона", "Compton wavelength", "м", v(1, 0, 0, 0, 0, 0, 0), Category.QUANTUM, Kind.SCALAR);
        q("action_h", "ħ", "Приведённый квант", "Reduced Planck", "Дж·с", v(2, 1, -1, 0, 0, 0, 0), Category.QUANTUM, Kind.CONSTANT);

        // ─────────── Optics ───────────
        q("illuminance", "Ev", "Освещённость", "Illuminance", "лк", v(-2, 0, 0, 0, 0, 0, 1), Category.OPTICS, Kind.SCALAR);
        q("lum_flux", "Φv", "Световой поток", "Luminous flux", "лм", v(0, 0, 0, 0, 0, 0, 1), Category.OPTICS, Kind.SCALAR);
        q("radiance", "Lr", "Яркость энергетическая", "Radiance", "Вт/м²", v(0, 1, -3, 0, 0, 0, 0), Category.OPTICS, Kind.SCALAR);
        q("irradiance", "Ee", "Облучённость", "Irradiance", "Вт/м²", v(0, 1, -3, 0, 0, 0, 0), Category.OPTICS, Kind.SCALAR);
        q("optical_power", "Po", "Оптическая сила", "Optical power", "дптр", v(-1, 0, 0, 0, 0, 0, 0), Category.OPTICS, Kind.SCALAR);
        q("refractive", "nr", "Показатель преломления", "Refractive index", "1", v(0, 0, 0, 0, 0, 0, 0), Category.OPTICS, Kind.SCALAR);

        // ─────────── Chemistry ───────────
        q("molar_mass", "M", "Молярная масса", "Molar mass", "кг/моль", v(0, 1, 0, 0, 0, -1, 0), Category.CHEMISTRY, Kind.SCALAR);
        q("molar_vol", "Vm", "Молярный объём", "Molar volume", "м³/моль", v(3, 0, 0, 0, 0, -1, 0), Category.CHEMISTRY, Kind.SCALAR);
        q("concentration", "cn", "Молярная концентрация", "Concentration", "моль/м³", v(-3, 0, 0, 0, 0, 1, 0), Category.CHEMISTRY, Kind.SCALAR);
        q("catalytic", "kat", "Каталитическая активность", "Catalytic activity", "кат", v(0, 0, -1, 0, 0, 1, 0), Category.CHEMISTRY, Kind.SCALAR);
        q("avogadro_q", "NA", "Число Авогадро", "Avogadro number", "моль⁻¹", v(0, 0, 0, 0, 0, -1, 0), Category.CHEMISTRY, Kind.CONSTANT);
        q("mole_fraction", "x", "Мольная доля", "Mole fraction", "1", v(0, 0, 0, 0, 0, 0, 0), Category.CHEMISTRY, Kind.SCALAR);

        // ─────────── Constants ───────────
        q("c_light", "c", "Скорость света", "Speed of light", "м/с", v(1, 0, -1, 0, 0, 0, 0), Category.CONSTANTS, Kind.CONSTANT);
        q("g_acc", "g", "Ускорение свободного падения", "Gravity g", "м/с²", v(1, 0, -2, 0, 0, 0, 0), Category.CONSTANTS, Kind.CONSTANT);
        q("G_grav", "G", "Гравитационная постоянная", "Gravitational G", "м³/(кг·с²)", v(3, -1, -2, 0, 0, 0, 0), Category.CONSTANTS, Kind.CONSTANT);
        q("h_planck", "h", "Постоянная Планка", "Planck constant", "Дж·с", v(2, 1, -1, 0, 0, 0, 0), Category.CONSTANTS, Kind.CONSTANT);
        q("k_boltzmann", "kB", "Постоянная Больцмана", "Boltzmann constant", "Дж/К", v(2, 1, -2, 0, -1, 0, 0), Category.CONSTANTS, Kind.CONSTANT);
        q("R_gas", "R", "Газовая постоянная", "Gas constant", "Дж/(моль·К)", v(2, 1, -2, 0, -1, -1, 0), Category.CONSTANTS, Kind.CONSTANT);
        q("e_charge", "e", "Элементарный заряд", "Elementary charge", "Кл", v(0, 0, 1, 1, 0, 0, 0), Category.CONSTANTS, Kind.CONSTANT);
        q("epsilon0", "ε0", "Электрическая постоянная", "Vacuum permittivity", "Ф/м", v(-3, -1, 4, 2, 0, 0, 0), Category.CONSTANTS, Kind.CONSTANT);
        q("stefan", "σB", "Постоянная Стефана–Больцмана", "Stefan-Boltzmann", "Вт/(м²·К⁴)", v(0, 1, -3, 0, -4, 0, 0), Category.CONSTANTS, Kind.CONSTANT);
        q("faraday", "Fa", "Постоянная Фарадея", "Faraday constant", "Кл/моль", v(0, 0, 1, 1, 0, -1, 0), Category.CONSTANTS, Kind.CONSTANT);

        // ─────────── Tensors ───────────
        q("stress_tensor", "σij", "Тензор напряжений", "Stress tensor", "Па", v(-1, 1, -2, 0, 0, 0, 0), Category.TENSORS, Kind.TENSOR, 1.0, 2.5);
        q("strain_tensor", "εij", "Тензор деформаций", "Strain tensor", "1", v(0, 0, 0, 0, 0, 0, 0), Category.TENSORS, Kind.TENSOR, 1.0, 2.5);
        q("metric_tensor", "gij", "Метрический тензор", "Metric tensor", "1", v(0, 0, 0, 0, 0, 0, 0), Category.TENSORS, Kind.TENSOR, 1.0, 2.5);
        q("ricci", "Rij", "Тензор Риччи", "Ricci tensor", "м⁻²", v(-2, 0, 0, 0, 0, 0, 0), Category.TENSORS, Kind.TENSOR, 1.0, 2.5);
        q("riemann", "Rpijk", "Тензор Римана", "Riemann tensor", "м⁻²", v(-2, 0, 0, 0, 0, 0, 0), Category.TENSORS, Kind.TENSOR, 1.0, 2.5);
        q("em_tensor", "Fμν", "Тензор ЭМ-поля", "EM field tensor", "Тл", v(0, 1, -2, -1, 0, 0, 0), Category.TENSORS, Kind.TENSOR, 1.0, 2.5);
        q("emt_tensor", "Tμν", "Тензор энергии-импульса", "Energy-momentum tensor", "Дж/м³", v(-1, 1, -2, 0, 0, 0, 0), Category.TENSORS, Kind.TENSOR, 1.0, 2.5);
        q("inertia_tensor", "Iij", "Тензор инерции", "Inertia tensor", "кг·м²", v(2, 1, 0, 0, 0, 0, 0), Category.TENSORS, Kind.TENSOR, 1.0, 2.5);
        q("stiffness_t", "Cijkl", "Тензор жёсткости", "Stiffness tensor", "Па", v(-1, 1, -2, 0, 0, 0, 0), Category.TENSORS, Kind.TENSOR, 1.0, 2.5);

        // ─────────── Fields ───────────
        q("grav_potential", "Φg", "Гравитационный потенциал", "Gravitational potential", "Дж/кг", v(2, 0, -2, 0, 0, 0, 0), Category.FIELDS, Kind.FIELD, 1.0, 3.0);
        q("e_potential", "φ", "Электрический потенциал", "Electric potential", "В", v(2, 1, -3, -1, 0, 0, 0), Category.FIELDS, Kind.FIELD, 1.0, 3.0);
        q("higgs", "φH", "Поле Хиггса", "Higgs field", "ГэВ", v(2, 1, -2, 0, 0, 0, 0), Category.FIELDS, Kind.FIELD, 1.0, 3.0);
        q("metric_field", "gμν", "Метрическое поле", "Metric field", "1", v(0, 0, 0, 0, 0, 0, 0), Category.FIELDS, Kind.FIELD, 1.0, 3.0);
        q("cosmo_const", "Λ", "Космологическая постоянная", "Cosmological constant", "м⁻²", v(-2, 0, 0, 0, 0, 0, 0), Category.FIELDS, Kind.FIELD, 1.0, 3.0);
        q("hubble", "H0", "Постоянная Хаббла", "Hubble constant", "с⁻¹", v(0, 0, -1, 0, 0, 0, 0), Category.FIELDS, Kind.FIELD, 1.0, 3.0);

        // ─────────── Dimensionless / numbers ───────────
        q("efficiency", "η", "КПД", "Efficiency", "1", v(0, 0, 0, 0, 0, 0, 0), Category.NUMBERS, Kind.NUMBER);
        q("reynolds", "Re", "Число Рейнольдса", "Reynolds number", "1", v(0, 0, 0, 0, 0, 0, 0), Category.NUMBERS, Kind.NUMBER);
        q("mach", "Ma", "Число Маха", "Mach number", "1", v(0, 0, 0, 0, 0, 0, 0), Category.NUMBERS, Kind.NUMBER);
        q("thermalization", "nth", "Коэффициент термализации", "Thermalization", "1", v(0, 0, 0, 0, 0, 0, 0), Category.NUCLEAR, Kind.NUMBER);
        q("leakage", "Lown", "Коэффициент утечки", "Leakage factor", "1", v(0, 0, 0, 0, 0, 0, 0), Category.NUCLEAR, Kind.NUMBER, 0.0);
        q("fine_structure", "αfs", "Постоянная тонкой структуры", "Fine structure", "1", v(0, 0, 0, 0, 0, 0, 0), Category.CONSTANTS, Kind.NUMBER);
        q("num_1", "1", "Единица", "One", "1", v(0, 0, 0, 0, 0, 0, 0), Category.NUMBERS, Kind.NUMBER, 1.0);
        q("num_2", "2", "Двойка", "Two", "1", v(0, 0, 0, 0, 0, 0, 0), Category.NUMBERS, Kind.NUMBER, 2.0);
        q("num_half", "1/2", "Одна вторая", "One half", "1", v(0, 0, 0, 0, 0, 0, 0), Category.NUMBERS, Kind.NUMBER, 0.5);
        q("num_pi", "π", "Пи", "Pi", "1", v(0, 0, 0, 0, 0, 0, 0), Category.NUMBERS, Kind.NUMBER, Math.PI);
        q("num_2pi", "2π", "Два пи", "Two pi", "1", v(0, 0, 0, 0, 0, 0, 0), Category.NUMBERS, Kind.NUMBER, 2 * Math.PI);
        q("num_4pi", "4π", "Четыре пи", "Four pi", "1", v(0, 0, 0, 0, 0, 0, 0), Category.NUMBERS, Kind.NUMBER, 4 * Math.PI);

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
