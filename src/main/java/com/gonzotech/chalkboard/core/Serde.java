package com.gonzotech.chalkboard.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * JSON Serializer/Deserializer for Expr trees.
 */
public final class Serde {

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

    public static Expr fromJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            JsonElement el = JsonParser.parseString(json);
            return fromJsonElement(el);
        } catch (Exception e) {
            return null;
        }
    }

    public static Expr fromJsonElement(JsonElement el) {
        if (el == null || !el.isJsonObject()) return null;
        JsonObject obj = el.getAsJsonObject();
        String type = obj.has("type") ? obj.get("type").getAsString() : "";
        String id = obj.has("id") ? obj.get("id").getAsString() : Expr.nid("node");

        return switch (type) {
            case "slot" -> new Expr.Slot(
                    id,
                    obj.has("q") ? obj.get("q").getAsString() : null,
                    obj.has("locked") && obj.get("locked").getAsBoolean(),
                    obj.has("added") && obj.get("added").getAsBoolean()
            );
            case "num" -> new Expr.Num(
                    id,
                    obj.has("val") ? obj.get("val").getAsDouble() : 1.0,
                    obj.has("label") ? obj.get("label").getAsString() : ""
            );
            case "op" -> new Expr.Op(
                    id,
                    Expr.OpKind.valueOf(obj.get("op").getAsString()),
                    fromJsonElement(obj.get("left")),
                    fromJsonElement(obj.get("right")),
                    obj.has("added") && obj.get("added").getAsBoolean()
            );
            case "pow" -> new Expr.Pow(
                    id,
                    fromJsonElement(obj.get("base")),
                    obj.has("exp") ? obj.get("exp").getAsDouble() : 1.0
            );
            case "eq" -> new Expr.Eq(
                    id,
                    fromJsonElement(obj.get("left")),
                    fromJsonElement(obj.get("right"))
            );
            default -> null;
        };
    }
}
