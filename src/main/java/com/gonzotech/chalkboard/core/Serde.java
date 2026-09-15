package com.gonzotech.chalkboard.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * JSON Serializer/Deserializer for {@link Expr} trees.
 *
 * <p>The JSON is also received from a game client.  Keep its tree size bounded
 * here, before any recursive expression consumer (such as {@link Evaluator})
 * sees it.  The limits are deliberately well above a generated discovery
 * puzzle, while preventing a malformed packet from exhausting the server
 * stack.</p>
 */
public final class Serde {

    /** Maximum UTF-16 length accepted by the expression JSON parser. */
    public static final int MAX_JSON_CHARS = 32 * 1024;
    /** Generated puzzles are below this depth; client-side wrapping has room to grow. */
    public static final int MAX_TREE_DEPTH = 48;
    /** Hard cap for all expression nodes accepted from serialized JSON. */
    public static final int MAX_TREE_NODES = 192;

    private Serde() {
    }

    public static String toJson(Expr expr) {
        if (expr == null) return "";
        return toJsonElement(expr).toString();
    }

    public static JsonElement toJsonElement(Expr expr) {
        JsonObject obj = new JsonObject();
        if (expr instanceof Expr.Slot s) {
            obj.addProperty("type", "slot");
            obj.addProperty("id", s.id());
            if (s.quantityId() != null) obj.addProperty("q", s.quantityId());
            obj.addProperty("locked", s.locked());
            obj.addProperty("added", s.isAdded());
        } else if (expr instanceof Expr.Num n) {
            obj.addProperty("type", "num");
            obj.addProperty("id", n.id());
            obj.addProperty("val", n.value());
            if (n.label() != null) obj.addProperty("label", n.label());
        } else if (expr instanceof Expr.Op op) {
            obj.addProperty("type", "op");
            obj.addProperty("id", op.id());
            obj.addProperty("op", op.op().name());
            obj.add("left", toJsonElement(op.left()));
            obj.add("right", toJsonElement(op.right()));
            obj.addProperty("added", op.isAdded());
        } else if (expr instanceof Expr.Pow pow) {
            obj.addProperty("type", "pow");
            obj.addProperty("id", pow.id());
            obj.add("base", toJsonElement(pow.base()));
            obj.addProperty("exp", pow.exp());
        } else if (expr instanceof Expr.Eq eq) {
            obj.addProperty("type", "eq");
            obj.addProperty("id", eq.id());
            obj.add("left", toJsonElement(eq.left()));
            obj.add("right", toJsonElement(eq.right()));
        }
        return obj;
    }

    /**
     * Parses a bounded, structurally complete expression tree.  Invalid JSON,
     * missing binary children and oversized/deep trees are all rejected with a
     * {@code null} result rather than reaching the evaluator.
     */
    public static Expr fromJson(String json) {
        if (json == null || json.isBlank() || json.length() > MAX_JSON_CHARS || !hasSafeJsonNesting(json)) {
            return null;
        }
        try {
            JsonElement el = JsonParser.parseString(json);
            return parseElement(el, 0, new int[]{0});
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Bounded equivalent of the old public decoder.  Kept public for callers
     * which already hold a Gson element, with the same safety limits as
     * {@link #fromJson(String)}.
     */
    public static Expr fromJsonElement(JsonElement el) {
        try {
            return parseElement(el, 0, new int[]{0});
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Expr parseElement(JsonElement el, int depth, int[] nodeCount) {
        if (el == null || !el.isJsonObject() || depth > MAX_TREE_DEPTH || ++nodeCount[0] > MAX_TREE_NODES) {
            return null;
        }

        JsonObject obj = el.getAsJsonObject();
        String type = string(obj, "type");
        String id = string(obj, "id");
        if (type == null || id == null) return null;

        return switch (type) {
            case "slot" -> new Expr.Slot(
                    id,
                    optionalString(obj, "q"),
                    bool(obj, "locked"),
                    bool(obj, "added")
            );
            case "num" -> {
                Double value = number(obj, "val");
                if (value == null || !Double.isFinite(value)) yield null;
                yield new Expr.Num(id, value, optionalString(obj, "label"));
            }
            case "op" -> {
                String opName = string(obj, "op");
                Expr left = parseElement(obj.get("left"), depth + 1, nodeCount);
                Expr right = parseElement(obj.get("right"), depth + 1, nodeCount);
                if (opName == null || left == null || right == null) yield null;
                try {
                    yield new Expr.Op(id, Expr.OpKind.valueOf(opName), left, right, bool(obj, "added"));
                } catch (IllegalArgumentException ignored) {
                    yield null;
                }
            }
            case "pow" -> {
                Expr base = parseElement(obj.get("base"), depth + 1, nodeCount);
                Double exp = number(obj, "exp");
                if (base == null || exp == null || !Double.isFinite(exp)) yield null;
                yield new Expr.Pow(id, base, exp);
            }
            case "eq" -> {
                Expr left = parseElement(obj.get("left"), depth + 1, nodeCount);
                Expr right = parseElement(obj.get("right"), depth + 1, nodeCount);
                if (left == null || right == null) yield null;
                yield new Expr.Eq(id, left, right);
            }
            default -> null;
        };
    }

    private static String string(JsonObject obj, String key) {
        if (!obj.has(key) || !obj.get(key).isJsonPrimitive()) return null;
        try {
            return obj.get(key).getAsString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String optionalString(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return null;
        return string(obj, key);
    }

    private static Double number(JsonObject obj, String key) {
        if (!obj.has(key) || !obj.get(key).isJsonPrimitive()) return null;
        try {
            return obj.get(key).getAsDouble();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean bool(JsonObject obj, String key) {
        if (!obj.has(key) || !obj.get(key).isJsonPrimitive()) return false;
        try {
            return obj.get(key).getAsBoolean();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Fast depth preflight so Gson is never asked to process an arbitrarily deep input. */
    private static boolean hasSafeJsonNesting(String json) {
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{' || c == '[') {
                if (++depth > MAX_TREE_DEPTH + 4) return false;
            } else if (c == '}' || c == ']') {
                if (--depth < 0) return false;
            }
        }
        return !inString && depth == 0;
    }
}
