package com.gonzotech.chalkboard.core;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Immutable expression tree. Mirrors the {@code Expr} union type of the TS engine.
 */
public sealed interface Expr permits Expr.Slot, Expr.Num, Expr.Op, Expr.Pow, Expr.Eq {

    String id();

    AtomicLong SEQ = new AtomicLong();

    static String nid(String prefix) {
        return prefix + "_" + SEQ.incrementAndGet();
    }

    enum OpKind {
        ADD("+"), SUB("\u2212"), MUL("\u00d7"), DIV("\u00f7");

        public final String symbol;

        OpKind(String symbol) {
            this.symbol = symbol;
        }
    }

    /** A draggable hole. {@code quantityId == null} renders as "?". */
    record Slot(String id, String quantityId, boolean locked, boolean isAdded) implements Expr {
        public static Slot empty() {
            return new Slot(nid("s"), null, false, false);
        }

        public static Slot of(String quantityId) {
            return new Slot(nid("s"), quantityId, false, false);
        }

        public Slot withQuantity(String q) {
            return new Slot(id, q, locked, isAdded);
        }

        public Slot withLocked(boolean l) {
            return new Slot(id, quantityId, l, isAdded);
        }
    }

    /** A bare dimensionless number — invisible to 7D, caught by the numeric layer. */
    record Num(String id, double value, String label) implements Expr {
        public static Num of(double v, String label) {
            return new Num(nid("n"), v, label);
        }
    }

    record Op(String id, OpKind op, Expr left, Expr right, boolean isAdded) implements Expr {
        public static Op of(OpKind op, Expr l, Expr r) {
            return new Op(nid("op"), op, l, r, false);
        }

        public static Op added(OpKind op, Expr l, Expr r) {
            return new Op(nid("op"), op, l, r, true);
        }
    }

    record Pow(String id, Expr base, double exp) implements Expr {
        public static Pow of(Expr base, double exp) {
            return new Pow(nid("pow"), base, exp);
        }
    }

    record Eq(String id, Expr left, Expr right) implements Expr {
        public static Eq of(Expr l, Expr r) {
            return new Eq(nid("eq"), l, r);
        }
    }
}
