package io.github.sunthemoon.advancedrocketrycommunity.satellite.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Schema-2 definition of a non-{@code data} satellite kind (ADR-049 §3), decoded strictly: unknown fields and
 * fields of another kind are rejected. Kind parameters are snapshotted into each satellite at launch.
 */
public record SatelliteKindDefinition(
        ResourceLocation id,
        SatelliteKind kind,
        ResourceLocation primaryComponent,
        int requiredLifetimeResearch,
        List<ResourceLocation> launchTargets,
        Parameters parameters
) {
    private static final Set<String> COMMON = Set.of(
            "schema_version", "id", "kind", "primary_component", "required_lifetime_research", "launch_targets");
    private static final Set<String> SURVEY = Set.of(
            "mission_duration_ticks", "instances_per_survey", "scan_energy", "scan_radius_blocks", "scan_cell_blocks");
    private static final Set<String> SOLAR = Set.of("output_multiplier_percent");

    public SatelliteKindDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(primaryComponent, "primaryComponent");
        Objects.requireNonNull(launchTargets, "launchTargets");
        Objects.requireNonNull(parameters, "parameters");
        launchTargets = List.copyOf(launchTargets);
        if (kind == SatelliteKind.DATA) {
            throw new IllegalArgumentException("Data definitions stay schema 1");
        }
        if (requiredLifetimeResearch < 0 || requiredLifetimeResearch > SatelliteLimits.MAX_REQUIRED_LIFETIME_RESEARCH) {
            throw new IllegalArgumentException("Required lifetime research is outside its bound");
        }
        if (launchTargets.isEmpty() || launchTargets.size() > SatelliteLimits.MAX_TARGETS_PER_DEFINITION
                || new HashSet<>(launchTargets).size() != launchTargets.size()) {
            throw new IllegalArgumentException("Launch targets must be non-empty, unique and bounded");
        }
        if (parameters.kind() != kind) {
            throw new IllegalArgumentException("Kind parameters do not match the kind");
        }
    }

    public static SatelliteKindDefinition decode(JsonElement json) {
        if (!(json instanceof JsonObject object)) {
            throw new IllegalArgumentException("Satellite definition must be an object");
        }
        if (integer(object, "schema_version") != SatelliteLimits.KIND_DEFINITION_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported satellite definition schema");
        }
        SatelliteKind kind = SatelliteKind.parse(string(object, "kind"))
                .orElseThrow(() -> new IllegalArgumentException("Unknown satellite kind"));
        Set<String> own = kind == SatelliteKind.SURVEY ? SURVEY : kind == SatelliteKind.SOLAR ? SOLAR : Set.of();
        for (String key : object.keySet()) {
            if (!COMMON.contains(key) && !own.contains(key)) {
                throw new IllegalArgumentException("Field " + key + " is not allowed for kind " + kind.id());
            }
        }
        JsonElement rawTargets = object.get("launch_targets");
        if (!(rawTargets instanceof JsonArray array) || array.size() > SatelliteLimits.MAX_TARGETS_PER_DEFINITION) {
            throw new IllegalArgumentException("launch_targets must be a bounded array");
        }
        List<ResourceLocation> targets = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            targets.add(parse(element, "launch_targets"));
        }
        Parameters parameters = switch (kind) {
            case SURVEY -> new Survey(
                    integer(object, "mission_duration_ticks"),
                    integer(object, "instances_per_survey"),
                    integer(object, "scan_energy"),
                    integer(object, "scan_radius_blocks"),
                    integer(object, "scan_cell_blocks")
            );
            case SOLAR -> new Solar(integer(object, "output_multiplier_percent"));
            default -> new Plain(kind);
        };
        return new SatelliteKindDefinition(
                parse(object.get("id"), "id"),
                kind,
                parse(object.get("primary_component"), "primary_component"),
                object.has("required_lifetime_research") ? integer(object, "required_lifetime_research") : 0,
                targets,
                parameters
        );
    }

    /** The parameters snapshotted into a new satellite of this kind (ADR-049 §7). */
    public SatelliteKindState initialState(long logicalTime) {
        if (parameters instanceof Survey survey) {
            return new SatelliteKindState.Survey(0L, logicalTime, survey.scanEnergy(), survey.scanRadius(), survey.scanCell());
        }
        if (parameters instanceof Solar solar) {
            return new SatelliteKindState.Solar(solar.outputMultiplierPercent(), java.util.Optional.empty());
        }
        return new SatelliteKindState.Plain(kind);
    }

    /** The scan energy a blueprint of this kind must be able to store (0 for other kinds). */
    public int scanEnergy() {
        return parameters instanceof Survey survey ? survey.scanEnergy() : 0;
    }

    public sealed interface Parameters {
        SatelliteKind kind();
    }

    public record Plain(SatelliteKind kind) implements Parameters {
        public Plain {
            if (kind == SatelliteKind.SURVEY || kind == SatelliteKind.SOLAR || kind == SatelliteKind.DATA) {
                throw new IllegalArgumentException("Kind " + kind.id() + " has its own parameters");
            }
        }
    }

    public record Survey(int missionDurationTicks, int instancesPerSurvey, int scanEnergy, int scanRadius, int scanCell)
            implements Parameters {
        public Survey {
            if (missionDurationTicks < SatelliteLimits.MIN_MISSION_DURATION_TICKS
                    || missionDurationTicks > SatelliteLimits.MAX_MISSION_DURATION_TICKS) {
                throw new IllegalArgumentException("Survey duration is outside its bound");
            }
            if (instancesPerSurvey < 1 || instancesPerSurvey > SatelliteLimits.MAX_INSTANCES_PER_SURVEY) {
                throw new IllegalArgumentException("Instances per survey is outside its bound");
            }
            new SatelliteKindState.Survey(0L, 0L, scanEnergy, scanRadius, scanCell);
        }

        @Override
        public SatelliteKind kind() {
            return SatelliteKind.SURVEY;
        }
    }

    public record Solar(int outputMultiplierPercent) implements Parameters {
        public Solar {
            if (outputMultiplierPercent < 1 || outputMultiplierPercent > SatelliteLimits.MAX_SOLAR_MULTIPLIER_PERCENT) {
                throw new IllegalArgumentException("Solar output multiplier is outside its bound");
            }
        }

        @Override
        public SatelliteKind kind() {
            return SatelliteKind.SOLAR;
        }
    }

    private static ResourceLocation parse(JsonElement element, String key) {
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isString()
                || primitive.getAsString().length() > 128) {
            throw new IllegalArgumentException("Field " + key + " must be an identifier");
        }
        ResourceLocation parsed = ResourceLocation.tryParse(primitive.getAsString());
        if (parsed == null) {
            throw new IllegalArgumentException("Field " + key + " must be an identifier");
        }
        return parsed;
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isString()) {
            throw new IllegalArgumentException("Field " + key + " must be a string");
        }
        return primitive.getAsString();
    }

    private static int integer(JsonObject object, String key) {
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
