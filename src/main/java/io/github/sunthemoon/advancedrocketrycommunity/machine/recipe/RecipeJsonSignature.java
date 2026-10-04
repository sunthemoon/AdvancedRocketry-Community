package io.github.sunthemoon.advancedrocketrycommunity.machine.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeSet;

/** Bounded, registry-independent canonicalization of already validated recipe JSON. */
public final class RecipeJsonSignature {
    public static final int MAX_BYTES = 65_536;
    public static final int MAX_DEPTH = 16;
    public static final int MAX_NODES = 4_096;

    private RecipeJsonSignature() { }

    public static String signature(JsonElement json) {
        return sha256(canonical(json));
    }

    public static String canonical(JsonElement json) {
        Budget budget = new Budget();
        JsonElement normalized = normalize(json, 0, budget);
        String encoded = normalized.toString();
        if (encoded.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("recipe JSON exceeds the 64 KiB limit");
        }
        return encoded;
    }

    public static int requireInt(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return integer(value.getAsString());
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static JsonElement normalize(JsonElement value, int depth, Budget budget) {
        if (value == null || depth > MAX_DEPTH || ++budget.nodes > MAX_NODES) {
            throw new IllegalArgumentException("recipe JSON exceeds its structural bound");
        }
        if (value.isJsonObject()) {
            JsonObject result = new JsonObject();
            if (value.getAsJsonObject().size() > MAX_NODES) {
                throw new IllegalArgumentException("recipe JSON has too many object fields");
            }
            for (String key : new TreeSet<>(value.getAsJsonObject().keySet())) {
                budget.add(key);
                result.add(key, normalize(value.getAsJsonObject().get(key), depth + 1, budget));
            }
            return result;
        }
        if (value.isJsonArray()) {
            JsonArray result = new JsonArray();
            if (value.getAsJsonArray().size() > MAX_NODES) {
                throw new IllegalArgumentException("recipe JSON has too many array entries");
            }
            for (JsonElement element : value.getAsJsonArray()) {
                result.add(normalize(element, depth + 1, budget));
            }
            return result;
        }
        if (!value.isJsonPrimitive()) {
            throw new IllegalArgumentException("recipe JSON cannot contain null");
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isNumber()) {
            return new JsonPrimitive(integer(primitive.getAsString()));
        }
        if (primitive.isString()) {
            budget.add(primitive.getAsString());
        }
        return primitive.deepCopy();
    }

    private static int integer(String raw) {
        if (raw.length() > 64) {
            throw new IllegalArgumentException("recipe integer exceeds its encoding bound");
        }
        try {
            return new BigDecimal(raw).intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException("recipe number must be a 32-bit integer", exception);
        }
    }

    private static final class Budget {
        private int nodes;
        private int chars;

        void add(String value) {
            if (value.length() > MAX_BYTES || (chars += value.length()) > MAX_BYTES) {
                throw new IllegalArgumentException("recipe JSON text exceeds its bound");
            }
        }
    }
}
