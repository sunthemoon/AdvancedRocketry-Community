package io.github.sunthemoon.advancedrocketrycommunity.endgame.laser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.resource.ResourceAlgorithms;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/**
 * ADR-055 section 2: strict schema-1 decoding of {@code laser_drill_tables} files, by the rules of ADR-052 sections
 * 1–2. Unknown or missing fields, wrong types, non-integral or out-of-range numbers, duplicate bodies or items and
 * unknown items are rejected.
 */
public final class LaserDrillTableCodec {
    public static final String DIRECTORY = "laser_drill_tables";
    public static final int MAX_FILE_BYTES = 16 * 1024;
    public static final int MAX_TABLES = 32;
    public static final int MAX_ID_CHARS = 128;

    private static final Set<String> TABLE_KEYS = Set.of("schema_version", "bodies", "default", "entries");
    private static final Set<String> ENTRY_KEYS = Set.of("item", "count", "weight");

    private LaserDrillTableCodec() {
    }

    public static LaserDrillTable decode(ResourceLocation fileId, byte[] raw, Predicate<ResourceLocation> itemExists) {
        Objects.requireNonNull(fileId, "fileId");
        Objects.requireNonNull(raw, "raw");
        if (raw.length > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("File exceeds " + MAX_FILE_BYTES + " bytes");
        }
        JsonObject table = object(JsonParser.parseString(new String(raw, StandardCharsets.UTF_8)), TABLE_KEYS, "table");
        if (integer(table.get("schema_version"), "schema_version", 1, Integer.MAX_VALUE) != 1) {
            throw new IllegalArgumentException("Unsupported laser drill table schema");
        }
        List<ResourceLocation> bodies = new ArrayList<>();
        for (JsonElement body : array(table.get("bodies"), "bodies", 0, LaserDrillTable.MAX_BODIES)) {
            bodies.add(id(body, "body"));
        }
        if (!(table.get("default") instanceof JsonPrimitive flag) || !flag.isBoolean()) {
            throw new IllegalArgumentException("default must be a boolean");
        }
        List<LaserDrillTable.Entry> entries = new ArrayList<>();
        for (JsonElement element : array(table.get("entries"), "entries", 1, LaserDrillTable.MAX_ENTRIES)) {
            JsonObject entry = object(element, ENTRY_KEYS, "entry");
            ResourceLocation item = id(entry.get("item"), "item");
            if (!itemExists.test(item)) {
                throw new IllegalArgumentException("Item " + item + " does not exist or is air");
            }
            entries.add(new LaserDrillTable.Entry(item,
                    integer(entry.get("count"), "count", 1, LaserDrillTable.MAX_COUNT),
                    integer(entry.get("weight"), "weight", 1, LaserDrillTable.MAX_WEIGHT)));
        }
        return new LaserDrillTable(fileId, bodies, flag.getAsBoolean(), entries, ResourceAlgorithms.sha256Hex16(raw));
    }

    private static JsonObject object(JsonElement element, Set<String> allowed, String what) {
        if (!(element instanceof JsonObject object)) {
            throw new IllegalArgumentException("The " + what + " must be an object");
        }
        for (String key : object.keySet()) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException("Unknown field " + key + " in the " + what);
            }
        }
        for (String key : allowed) {
            if (!object.has(key)) {
                throw new IllegalArgumentException("Missing field " + key + " in the " + what);
            }
        }
        return object;
    }

    private static JsonArray array(JsonElement element, String name, int minimum, int maximum) {
        if (!(element instanceof JsonArray array) || array.size() < minimum || array.size() > maximum) {
            throw new IllegalArgumentException(name + " must be an array of " + minimum + ".." + maximum + " entries");
        }
        return array;
    }

    private static int integer(JsonElement element, String name, int minimum, int maximum) {
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
            throw new IllegalArgumentException(name + " must be a number");
        }
        BigDecimal value = primitive.getAsBigDecimal();
        if (value.stripTrailingZeros().scale() > 0
                || value.compareTo(BigDecimal.valueOf(minimum)) < 0 || value.compareTo(BigDecimal.valueOf(maximum)) > 0) {
            throw new IllegalArgumentException(name + " must be an integer in " + minimum + ".." + maximum);
        }
        return value.intValueExact();
    }

    private static ResourceLocation id(JsonElement element, String name) {
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isString()
                || primitive.getAsString().length() > MAX_ID_CHARS) {
            throw new IllegalArgumentException(name + " must be an ID of at most " + MAX_ID_CHARS + " characters");
        }
        ResourceLocation id = ResourceLocation.tryParse(primitive.getAsString());
        if (id == null) {
            throw new IllegalArgumentException(name + " is not a valid ID");
        }
        return id;
    }
}
