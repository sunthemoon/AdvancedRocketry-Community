package io.github.sunthemoon.advancedrocketrycommunity.satellite.component;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * One data-driven satellite component (ADR-049 §2): an item, its role and the single stat that role adds.
 * Decoding is strict: unknown fields, missing required fields and fields of another role are rejected.
 */
public record SatelliteComponentDefinition(
        ResourceLocation item,
        SatelliteComponentRole role,
        Optional<SatelliteKind> kind,
        int value
) {
    private static final Map<SatelliteComponentRole, Integer> MAXIMA = Map.of(
            SatelliteComponentRole.PRIMARY, SatelliteLimits.MAX_RATING,
            SatelliteComponentRole.POWER, SatelliteLimits.MAX_POWER,
            SatelliteComponentRole.BATTERY, SatelliteLimits.MAX_BATTERY,
            SatelliteComponentRole.DATA_STORAGE, SatelliteLimits.MAX_DATA,
            SatelliteComponentRole.CARGO, SatelliteLimits.MAX_CARGO
    );
    private static final Set<String> STAT_FIELDS = Set.of(
            "power_generation", "battery_capacity", "data_capacity", "cargo_stacks", "primary_rating");

    public SatelliteComponentDefinition {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(kind, "kind");
        if ((role == SatelliteComponentRole.PRIMARY) != kind.isPresent()) {
            throw new IllegalArgumentException("Only a primary component names a kind");
        }
        if (kind.filter(SatelliteKind.DATA::equals).isPresent()) {
            throw new IllegalArgumentException("A primary component cannot select the data kind");
        }
        if (role == SatelliteComponentRole.CHASSIS ? value != 0 : value < 1 || value > MAXIMA.get(role)) {
            throw new IllegalArgumentException("Component value is outside the bound of its role");
        }
    }

    public int powerGeneration() {
        return role == SatelliteComponentRole.POWER ? value : 0;
    }

    public int batteryCapacity() {
        return role == SatelliteComponentRole.BATTERY ? value : 0;
    }

    public int dataCapacity() {
        return role == SatelliteComponentRole.DATA_STORAGE ? value : 0;
    }

    public int cargoStacks() {
        return role == SatelliteComponentRole.CARGO ? value : 0;
    }

    public int primaryRating() {
        return role == SatelliteComponentRole.PRIMARY ? value : 0;
    }

    /** Strictly decodes one schema-1 component file. */
    public static SatelliteComponentDefinition decode(JsonElement json) {
        if (!(json instanceof JsonObject object)) {
            throw new IllegalArgumentException("Component definition must be an object");
        }
        for (String key : object.keySet()) {
            if (!key.equals("schema_version") && !key.equals("item") && !key.equals("role")
                    && !key.equals("kind") && !STAT_FIELDS.contains(key)) {
                throw new IllegalArgumentException("Unknown component field " + key);
            }
        }
        if (integer(object, "schema_version") != SatelliteLimits.COMPONENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported component schema");
        }
        ResourceLocation item = location(object, "item");
        SatelliteComponentRole role = SatelliteComponentRole.parse(string(object, "role"))
                .orElseThrow(() -> new IllegalArgumentException("Unknown component role"));
        Optional<SatelliteKind> kind = object.has("kind")
                ? Optional.of(SatelliteKind.parse(string(object, "kind"))
                        .orElseThrow(() -> new IllegalArgumentException("Unknown component kind")))
                : Optional.empty();
        String required = role.statField().orElse(null);
        for (String field : STAT_FIELDS) {
            if (object.has(field) && !field.equals(required)) {
                throw new IllegalArgumentException("Field " + field + " does not belong to role " + role.id());
            }
        }
        int value = required == null ? 0 : integer(object, required);
        return new SatelliteComponentDefinition(item, role, kind, value);
    }

    static ResourceLocation location(JsonObject object, String key) {
        String raw = string(object, key);
        ResourceLocation parsed = raw.length() > 128 ? null : ResourceLocation.tryParse(raw);
        if (parsed == null) {
            throw new IllegalArgumentException("Field " + key + " is not a valid identifier");
        }
        return parsed;
    }

    static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isString()) {
            throw new IllegalArgumentException("Field " + key + " must be a string");
        }
        return primitive.getAsString();
    }

    static int integer(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
            throw new IllegalArgumentException("Field " + key + " must be an integer");
        }
        double raw = primitive.getAsDouble();
        if (raw != Math.rint(raw) || raw < Integer.MIN_VALUE || raw > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Field " + key + " must be an integer");
        }
        return (int) raw;
    }
}
