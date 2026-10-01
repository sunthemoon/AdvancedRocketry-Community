package io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.resource.ResourceAlgorithms;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/**
 * ADR-057 sections 1 and 2: strict schema-1 decoding of {@code singularities} (at most 4 KiB per file, 16 files) and
 * {@code black_hole_fuels} (at most 8 KiB per file, 16 tables). Unknown or missing fields, wrong types,
 * non-integral or out-of-range numbers, duplicate items and unknown items are rejected.
 */
public final class BlackHoleCodec {
    public static final String SINGULARITY_DIRECTORY = "singularities";
    public static final String FUEL_DIRECTORY = "black_hole_fuels";
    public static final int MAX_SINGULARITY_BYTES = 4 * 1024;
    public static final int MAX_FUEL_BYTES = 8 * 1024;
    public static final int MAX_FILES = 16;
    public static final int MAX_ID_CHARS = 128;

    private static final Set<String> SINGULARITY_KEYS = Set.of("schema_version", "body", "output_fe_per_tick",
            "fuel_table");
    private static final Set<String> FUEL_KEYS = Set.of("schema_version", "default_burn_ticks", "entries");
    private static final Set<String> ENTRY_KEYS = Set.of("item", "burn_ticks");

    private BlackHoleCodec() {
    }

    public static SingularityProfile decodeSingularity(ResourceLocation fileId, byte[] raw) {
        JsonObject object = object(parse(raw, MAX_SINGULARITY_BYTES), SINGULARITY_KEYS, "singularity");
        schema(object);
        return new SingularityProfile(fileId, id(object.get("body"), "body"),
                integer(object.get("output_fe_per_tick"), "output_fe_per_tick", 1, SingularityProfile.MAX_OUTPUT),
                id(object.get("fuel_table"), "fuel_table"), ResourceAlgorithms.sha256Hex16(raw));
    }

    public static BlackHoleFuelTable decodeFuelTable(ResourceLocation fileId, byte[] raw,
                                                     Predicate<ResourceLocation> itemExists) {
        JsonObject object = object(parse(raw, MAX_FUEL_BYTES), FUEL_KEYS, "fuel table");
        schema(object);
        int defaultBurn = integer(object.get("default_burn_ticks"), "default_burn_ticks", 1,
                BlackHoleFuelTable.MAX_BURN_TICKS);
        if (!(object.get("entries") instanceof JsonArray entries) || entries.size() > BlackHoleFuelTable.MAX_ENTRIES) {
            throw new IllegalArgumentException("entries must be an array of at most " + BlackHoleFuelTable.MAX_ENTRIES);
        }
        Map<ResourceLocation, Integer> burns = new LinkedHashMap<>();
        for (JsonElement element : entries) {
            JsonObject entry = object(element, ENTRY_KEYS, "fuel entry");
            ResourceLocation item = id(entry.get("item"), "item");
            if (!itemExists.test(item)) {
                throw new IllegalArgumentException("Item " + item + " does not exist or is air");
            }
            if (burns.put(item, integer(entry.get("burn_ticks"), "burn_ticks", 1, BlackHoleFuelTable.MAX_BURN_TICKS))
                    != null) {
                throw new IllegalArgumentException("Item " + item + " appears twice in a fuel table");
            }
        }
        return new BlackHoleFuelTable(fileId, defaultBurn, burns, ResourceAlgorithms.sha256Hex16(raw));
    }

    private static JsonElement parse(byte[] raw, int maximum) {
        Objects.requireNonNull(raw, "raw");
        if (raw.length > maximum) {
            throw new IllegalArgumentException("File exceeds " + maximum + " bytes");
        }
        return JsonParser.parseString(new String(raw, StandardCharsets.UTF_8));
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

    private static void schema(JsonObject object) {
        if (integer(object.get("schema_version"), "schema_version", 1, Integer.MAX_VALUE) != 1) {
            throw new IllegalArgumentException("Unsupported black-hole data schema");
        }
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
