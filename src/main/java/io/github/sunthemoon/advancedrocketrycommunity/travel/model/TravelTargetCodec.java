package io.github.sunthemoon.advancedrocketrycommunity.travel.model;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Exact-shape codec for the closed {@link TravelTarget} contract. */
final class TravelTargetCodec {
    private static final Set<String> BODY_KEYS = Set.of("schema_version", "type", "body_id");
    private static final Set<String> INSTANCE_KEYS = Set.of("schema_version", "type", "instance_id");
    private static final Codec<ResourceLocation> RESOURCE_LOCATION = ResourceLocation.CODEC.flatXmap(
            TravelTargetCodec::boundedLocation,
            TravelTargetCodec::boundedLocation
    );

    static final Codec<TravelTarget> CODEC = new Codec<>() {
        @Override
        public <T> DataResult<Pair<TravelTarget, T>> decode(DynamicOps<T> ops, T input) {
            return ops.getMap(input)
                    .flatMap(map -> decodeMap(ops, map))
                    .map(target -> Pair.of(target, ops.empty()));
        }

        @Override
        public <T> DataResult<T> encode(TravelTarget input, DynamicOps<T> ops, T prefix) {
            RecordBuilder<T> builder = ops.mapBuilder()
                    .add("schema_version", ops.createInt(TravelTarget.SCHEMA_VERSION))
                    .add("type", ops.createString(input.typeId().toString()));
            if (input instanceof TravelTarget.BodySurface bodySurface) {
                return builder.add("body_id", ops.createString(bodySurface.bodyId().toString())).build(prefix);
            }
            if (input instanceof TravelTarget.Orbit orbit) {
                return builder.add("body_id", ops.createString(orbit.bodyId().toString())).build(prefix);
            }
            if (input instanceof TravelTarget.Station station) {
                return builder.add("instance_id", ops.createString(station.instanceId().toString())).build(prefix);
            }
            if (input instanceof TravelTarget.Mission mission) {
                return builder.add("instance_id", ops.createString(mission.instanceId().toString())).build(prefix);
            }
            return DataResult.error(() -> "Unsupported travel target implementation: " + input.getClass().getName());
        }
    };

    private TravelTargetCodec() {
    }

    private static <T> DataResult<TravelTarget> decodeMap(DynamicOps<T> ops, MapLike<T> map) {
        DataResult<Integer> schemaResult = required(map, "schema_version")
                .flatMap(value -> Codec.INT.parse(ops, value));
        Optional<Integer> schema = schemaResult.result();
        if (schema.isEmpty()) {
            return copyError("Invalid travel target schema_version", schemaResult);
        }
        if (schema.get() != TravelTarget.SCHEMA_VERSION) {
            return DataResult.error(() -> "Unsupported travel target schema " + schema.get());
        }

        DataResult<ResourceLocation> typeResult = required(map, "type")
                .flatMap(value -> RESOURCE_LOCATION.parse(ops, value));
        Optional<ResourceLocation> type = typeResult.result();
        if (type.isEmpty()) {
            return copyError("Invalid travel target type", typeResult);
        }

        DataResult<Set<String>> keysResult = stringKeys(ops, map);
        Optional<Set<String>> keys = keysResult.result();
        if (keys.isEmpty()) {
            return copyError("Invalid travel target keys", keysResult);
        }

        if (TravelTarget.BODY_SURFACE_TYPE.equals(type.get())) {
            return requireExactKeys(keys.get(), BODY_KEYS)
                    .flatMap(ignored -> decodeBodyId(ops, map))
                    .flatMap(TravelTargetCodec::bodySurface);
        }
        if (TravelTarget.ORBIT_TYPE.equals(type.get())) {
            return requireExactKeys(keys.get(), BODY_KEYS)
                    .flatMap(ignored -> decodeBodyId(ops, map))
                    .flatMap(TravelTargetCodec::orbit);
        }
        if (TravelTarget.STATION_TYPE.equals(type.get())) {
            return requireExactKeys(keys.get(), INSTANCE_KEYS)
                    .flatMap(ignored -> decodeInstanceId(ops, map))
                    .flatMap(TravelTargetCodec::station);
        }
        if (TravelTarget.MISSION_TYPE.equals(type.get())) {
            return requireExactKeys(keys.get(), INSTANCE_KEYS)
                    .flatMap(ignored -> decodeInstanceId(ops, map))
                    .flatMap(TravelTargetCodec::mission);
        }
        return DataResult.error(() -> "Unknown travel target type " + type.get());
    }

    private static <T> DataResult<T> required(MapLike<T> map, String key) {
        T value = map.get(key);
        return value == null
                ? DataResult.error(() -> "Missing travel target field " + key)
                : DataResult.success(value);
    }

    private static <T> DataResult<Set<String>> stringKeys(DynamicOps<T> ops, MapLike<T> map) {
        Set<String> keys = new HashSet<>();
        for (Pair<T, T> entry : map.entries().toList()) {
            Optional<String> key = ops.getStringValue(entry.getFirst()).result();
            if (key.isEmpty()) {
                return DataResult.error(() -> "Travel target contains a non-string field name");
            }
            keys.add(key.get());
        }
        return DataResult.success(Set.copyOf(keys));
    }

    private static DataResult<Boolean> requireExactKeys(Set<String> actual, Set<String> expected) {
        if (!actual.equals(expected)) {
            return DataResult.error(() -> "Travel target fields must be exactly " + expected + "; found " + actual);
        }
        return DataResult.success(Boolean.TRUE);
    }

    private static <T> DataResult<ResourceLocation> decodeBodyId(DynamicOps<T> ops, MapLike<T> map) {
        return required(map, "body_id")
                .flatMap(value -> RESOURCE_LOCATION.parse(ops, value));
    }

    private static <T> DataResult<UUID> decodeInstanceId(DynamicOps<T> ops, MapLike<T> map) {
        return required(map, "instance_id")
                .flatMap(value -> Codec.STRING.parse(ops, value))
                .flatMap(TravelTargetCodec::canonicalUuid);
    }

    private static DataResult<UUID> canonicalUuid(String raw) {
        if (!raw.matches("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) {
            return DataResult.error(() -> "Travel target instance_id is not a canonical RFC 4122 UUID");
        }
        try {
            UUID parsed = UUID.fromString(raw);
            if (!parsed.toString().equals(raw)) {
                return DataResult.error(() -> "Travel target instance_id is not canonical");
            }
            return DataResult.success(parsed);
        } catch (IllegalArgumentException exception) {
            return DataResult.error(() -> "Travel target instance_id is invalid");
        }
    }

    private static DataResult<ResourceLocation> boundedLocation(ResourceLocation value) {
        if (value.toString().length() > TravelTarget.MAX_RESOURCE_LOCATION_CHARS) {
            return DataResult.error(() -> "Travel target resource location exceeds 128 characters");
        }
        return DataResult.success(value);
    }

    private static DataResult<TravelTarget> bodySurface(ResourceLocation bodyId) {
        return construct(() -> new TravelTarget.BodySurface(bodyId));
    }

    private static DataResult<TravelTarget> orbit(ResourceLocation bodyId) {
        return construct(() -> new TravelTarget.Orbit(bodyId));
    }

    private static DataResult<TravelTarget> station(UUID instanceId) {
        return construct(() -> new TravelTarget.Station(instanceId));
    }

    private static DataResult<TravelTarget> mission(UUID instanceId) {
        return construct(() -> new TravelTarget.Mission(instanceId));
    }

    private static DataResult<TravelTarget> construct(TargetFactory factory) {
        try {
            return DataResult.success(factory.create());
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private static <T> DataResult<T> copyError(String prefix, DataResult<?> source) {
        String detail = source.error().map(error -> error.message()).orElse("unknown error");
        return DataResult.error(() -> prefix + ": " + detail);
    }

    @FunctionalInterface
    private interface TargetFactory {
        TravelTarget create();
    }
}
