package io.github.sunthemoon.advancedrocketrycommunity.satellite.resource;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/**
 * ADR-052 sections 1–2: strict schema-1 decoding of {@code asteroid_types} and {@code gas_harvest} files. Unknown
 * fields, wrong types, non-integral or out-of-range numbers and unknown items are rejected; a table's version is
 * the SHA-256 of its raw bytes.
 */
public final class ResourceTableCodec {
    public static final String ASTEROID_DIRECTORY = "asteroid_types";
    public static final String GAS_DIRECTORY = "gas_harvest";
    public static final int MAX_ASTEROID_FILE_BYTES = 32 * 1024;
    public static final int MAX_GAS_FILE_BYTES = 8 * 1024;
    public static final int MAX_ASTEROID_TYPES = 64;
    public static final int MAX_GAS_TABLES = 32;
    public static final int MAX_ID_CHARS = 128;

    private static final Set<String> ASTEROID_KEYS = Set.of("schema_version", "id", "weight", "systems", "mass",
            "mass_variability_pct", "richness_pct", "richness_variability_pct", "base_item", "ores",
            "time_multiplier_pct");
    private static final Set<String> ORE_KEYS = Set.of("item", "weight");
    private static final Set<String> GAS_KEYS = Set.of("schema_version", "body", "products");
    private static final Set<String> PRODUCT_KEYS = Set.of("item", "amount_per_1000_ticks");

    private ResourceTableCodec() {
    }

    public static AsteroidType decodeAsteroidType(ResourceLocation fileId, byte[] raw, Predicate<ResourceLocation> itemExists) {
        JsonObject object = object(parse(raw, MAX_ASTEROID_FILE_BYTES), ASTEROID_KEYS, "asteroid type");
        schema(object);
        ResourceLocation id = id(object.get("id"), "id");
        if (!id.equals(fileId)) {
            throw new IllegalArgumentException("Asteroid type id " + id + " does not match its file " + fileId);
        }
        List<ResourceLocation> systems = new ArrayList<>();
        for (JsonElement system : array(object.get("systems"), "systems", AsteroidType.MAX_SYSTEMS)) {
            systems.add(id(system, "system"));
        }
        List<AsteroidType.Ore> ores = new ArrayList<>();
        for (JsonElement raw1 : array(object.get("ores"), "ores", AsteroidType.MAX_ORES)) {
            JsonObject ore = object(raw1, ORE_KEYS, "ore");
            ores.add(new AsteroidType.Ore(item(ore.get("item"), itemExists), integer(ore.get("weight"), "ore weight", 1, 1_000)));
        }
        return new AsteroidType(
                id,
                integer(object.get("weight"), "weight", 1, 1_000),
                systems,
                integer(object.get("mass"), "mass", 1, AsteroidType.MAX_MASS),
                integer(object.get("mass_variability_pct"), "mass_variability_pct", 0, 100),
                integer(object.get("richness_pct"), "richness_pct", 0, 100),
                integer(object.get("richness_variability_pct"), "richness_variability_pct", 0, 100),
                item(object.get("base_item"), itemExists),
                ores,
                integer(object.get("time_multiplier_pct"), "time_multiplier_pct", 10, 1_000),
                ResourceAlgorithms.sha256Hex16(raw)
        );
    }

    public static GasTable decodeGasTable(ResourceLocation fileId, byte[] raw, Predicate<ResourceLocation> itemExists) {
        JsonObject object = object(parse(raw, MAX_GAS_FILE_BYTES), GAS_KEYS, "gas table");
        schema(object);
        List<GasTable.Product> products = new ArrayList<>();
        for (JsonElement element : array(object.get("products"), "products", GasTable.MAX_PRODUCTS)) {
            JsonObject product = object(element, PRODUCT_KEYS, "gas product");
            products.add(new GasTable.Product(item(product.get("item"), itemExists),
                    integer(product.get("amount_per_1000_ticks"), "amount_per_1000_ticks", 1, 64)));
        }
        return new GasTable(fileId, id(object.get("body"), "body"), products, ResourceAlgorithms.sha256Hex16(raw));
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
            throw new IllegalArgumentException("Unsupported resource table schema");
        }
    }

    private static JsonArray array(JsonElement element, String name, int maximum) {
        if (!(element instanceof JsonArray array) || array.size() > maximum) {
            throw new IllegalArgumentException(name + " must be an array of at most " + maximum + " entries");
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

    private static ResourceLocation item(JsonElement element, Predicate<ResourceLocation> itemExists) {
        ResourceLocation item = id(element, "item");
        if (!itemExists.test(item)) {
            throw new IllegalArgumentException("Item " + item + " does not exist or is air");
        }
        return item;
    }
}
