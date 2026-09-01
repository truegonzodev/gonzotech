package com.gonzotech.chalkboard.core;

/**
 * A single physical quantity node: its symbol, its 7D SI dimension vector,
 * its structural complexity (aura range) and its derivation weight.
 */
public record Quantity(
        String id,
        String symbol,
        String nameRu,
        String nameEn,
        String unit,
        DimVec vec,
        Category category,
        Kind kind,
        double complexity,
        double value,
        int weight
) {

    public enum Category {
        SI("\u0421\u0418"),
        MECHANICS("\u041c\u0435\u0445\u0430\u043d\u0438\u043a\u0430"),
        THERMO("\u0422\u0435\u0440\u043c\u043e"),
        EM("\u042d\u041c"),
        QUANTUM("\u041a\u0432\u0430\u043d\u0442"),
        NUCLEAR("\u042f\u0434\u0440\u043e"),
        OPTICS("\u041e\u043f\u0442\u0438\u043a\u0430"),
        CHEMISTRY("\u0425\u0438\u043c\u0438\u044f"),
        CONSTANTS("\u041a\u043e\u043d\u0441\u0442\u0430\u043d\u0442\u044b"),
        TENSORS("\u0422\u0435\u043d\u0437\u043e\u0440\u044b"),
        FIELDS("\u041f\u043e\u043b\u044f"),
        NUMBERS("\u0427\u0438\u0441\u043b\u0430");

        public final String label;

        Category(String label) {
            this.label = label;
        }
    }

    public enum Kind {
        SCALAR, VECTOR, TENSOR, FIELD, CONSTANT, NUMBER
    }

    /** Structural complexity → how far the aura reaches through the operator graph. */
    public static double complexityOf(DimVec v, Kind kind) {
        if (kind == Kind.NUMBER) return 1.0;
        if (kind == Kind.TENSOR) return 2.5;
        if (kind == Kind.FIELD) return 3.0;
        int axes = v.activeAxes();
        double rank = v.rank();
        if (axes <= 1 && rank <= 1) return 1.0;
        if (axes <= 2 && rank <= 3) return 1.5;
        if (axes >= 4 || rank >= 5) return 2.5;
        return 1.5;
    }

    /**
     * Derivation weight: 0 for SI base units, 1 for simple derivatives,
     * 2 for second-order derivatives, 3 for fields / tensors / quantum-nuclear.
     */
    public static int weightOf(DimVec v, Category category, Kind kind) {
        if (category == Category.SI || category == Category.NUMBERS) return 0;

        int axes = v.activeAxes();
        double rank = v.rank();
        int weight;
        if (axes <= 1 && rank <= 1) weight = 1;
        else if (axes <= 2 && rank <= 2) weight = 1;
        else if (axes <= 3 && rank <= 4) weight = 2;
        else weight = 3;

        if (kind == Kind.TENSOR || kind == Kind.FIELD
                || category == Category.TENSORS || category == Category.FIELDS
                || category == Category.QUANTUM || category == Category.NUCLEAR) {
            weight = Math.max(weight, 3);
        }
        return weight;
    }

    public String kindLabelRu() {
        return switch (kind) {
            case SCALAR -> "\u0441\u043a\u0430\u043b\u044f\u0440";
            case VECTOR -> "\u0432\u0435\u043a\u0442\u043e\u0440";
            case TENSOR -> "\u0442\u0435\u043d\u0437\u043e\u0440";
            case FIELD -> "\u043f\u043e\u043b\u0435";
            case CONSTANT -> "\u043a\u043e\u043d\u0441\u0442\u0430\u043d\u0442\u0430";
            case NUMBER -> "\u0447\u0438\u0441\u043b\u043e";
        };
    }
}
