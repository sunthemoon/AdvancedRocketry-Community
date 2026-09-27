package io.github.sunthemoon.advancedrocketrycommunity.celestial.model;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Explicit legacy decode and strict schema-2 authoring; never changes a world registry. */
final class CelestialDefinitionCodec {
    private static final Set<String> BODY_FIELDS = Set.of("schema_version", "id", "parent", "level",
            "gravity_multiplier", "atmosphere", "orbit", "visual_profile", "capabilities",
            "solar_intensity", "radiation", "environment_effects", "discovery_required");
    private static final Set<String> CAPABILITY_FIELDS = Set.of("landable", "orbitable", "gas_giant");
    private static final Set<String> ATMOSPHERE_FIELDS = Set.of("pressure", "breathable", "temperature_kelvin", "profile");
    private static final Set<String> ORBIT_FIELDS = Set.of("distance", "period_ticks", "inclination_degrees");
    private static final Codec<Double> GRAVITY = BoundedCelestialCodecs.finiteDouble(
            0, CelestialBodyDefinition.MAX_GRAVITY_MULTIPLIER);
    private static final Codec<Double> SOLAR = BoundedCelestialCodecs.finiteDouble(
            0, CelestialBodyDefinition.MAX_SOLAR_INTENSITY);
    private static final Codec<Double> RADIATION = BoundedCelestialCodecs.finiteDouble(
            0, CelestialBodyDefinition.MAX_RADIATION);

    static final Codec<CelestialBodyDefinition> CODEC = new Codec<>() {
        @Override
        public <T> DataResult<Pair<CelestialBodyDefinition, T>> decode(DynamicOps<T> ops, T input) {
            try {
                return DataResult.success(Pair.of(read(ops, input), ops.empty()));
            } catch (IllegalArgumentException exception) {
                return DataResult.error(exception::getMessage);
            }
        }

        @Override
        public <T> DataResult<T> encode(CelestialBodyDefinition input, DynamicOps<T> ops, T prefix) {
            return fields(input, ops)
                    .add("schema_version", ops.createInt(CelestialBodyDefinition.SCHEMA_VERSION))
                    .add("capabilities", ops.mapBuilder()
                            .add("landable", ops.createBoolean(input.capabilities().landable()))
                            .add("orbitable", ops.createBoolean(input.capabilities().orbitable()))
                            .add("gas_giant", ops.createBoolean(input.capabilities().gasGiant()))
                            .build(ops.empty()))
                    .add("solar_intensity", ops.createDouble(input.solarIntensity()))
                    .add("radiation", ops.createDouble(input.radiation()))
                    .add("environment_effects", ops.createBoolean(input.environmentEffects()))
                    .add("discovery_required", ops.createBoolean(input.discoveryRequired()))
                    .build(prefix);
        }
    };

    private CelestialDefinitionCodec() {
    }

    static <T> DataResult<T> encodeLegacy(CelestialBodyDefinition value, DynamicOps<T> ops) {
        if (value.levelKey().isEmpty()
                || !value.capabilities().equals(CelestialCapabilities.legacy(value.levelKey().orElseThrow()))
                || value.solarIntensity() != 1.0D || value.radiation() != 0.0D || value.environmentEffects()
                || value.discoveryRequired()) {
            return DataResult.error(() -> "Legacy encoding would lose schema-2 celestial metadata");
        }
        return fields(value, ops).build(ops.empty());
    }

    private static <T> RecordBuilder<T> fields(CelestialBodyDefinition value, DynamicOps<T> ops) {
        RecordBuilder<T> builder = ops.mapBuilder()
                .add("id", ops.createString(value.id().toString()));
        value.parentId().ifPresent(parent -> builder.add("parent", ops.createString(parent.toString())));
        value.levelKey().ifPresent(level -> builder.add("level", ops.createString(level.location().toString())));
        return builder.add("gravity_multiplier", ops.createDouble(value.gravityMultiplier()))
                .add("atmosphere", AtmosphereDefinition.CODEC.encodeStart(ops, value.atmosphere()))
                .add("orbit", OrbitDefinition.CODEC.encodeStart(ops, value.orbit()))
                .add("visual_profile", ops.createString(value.visualProfile().toString()));
    }

    private static <T> CelestialBodyDefinition read(DynamicOps<T> ops, T input) {
        Fields<T> fields = new Fields<>(ops, input);
        long schema = fields.has("schema_version")
                ? fields.read("schema_version", BoundedCelestialCodecs.EXACT_LONG) : 1;
        if (schema != 1 && schema != CelestialBodyDefinition.SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported celestial definition schema");
        }
        boolean legacy = schema == 1;
        if (legacy) {
            if (fields.has("capabilities") || fields.has("solar_intensity") || fields.has("radiation")
                    || fields.has("environment_effects") || fields.has("discovery_required")) {
                throw new IllegalArgumentException("New celestial fields require schema_version 2");
            }
        } else {
            fields.allowOnly(BODY_FIELDS);
            new Fields<>(ops, fields.required("atmosphere")).allowOnly(ATMOSPHERE_FIELDS);
            new Fields<>(ops, fields.required("orbit")).allowOnly(ORBIT_FIELDS);
        }
        ResourceLocation id = fields.read("id", BoundedCelestialCodecs.RESOURCE_LOCATION);
        Optional<ResourceLocation> parent = fields.optional("parent", BoundedCelestialCodecs.RESOURCE_LOCATION);
        Optional<ResourceKey<Level>> level = legacy
                ? Optional.of(fields.read("level", BoundedCelestialCodecs.LEVEL_KEY))
                : fields.optional("level", BoundedCelestialCodecs.LEVEL_KEY);
        CelestialCapabilities capabilities;
        if (legacy) {
            capabilities = CelestialCapabilities.legacy(level.orElseThrow());
        } else {
            Fields<T> flags = new Fields<>(ops, fields.required("capabilities"));
            flags.allowOnly(CAPABILITY_FIELDS);
            capabilities = new CelestialCapabilities(
                    flags.read("landable", BoundedCelestialCodecs.BOOLEAN),
                    flags.read("orbitable", BoundedCelestialCodecs.BOOLEAN),
                    flags.read("gas_giant", BoundedCelestialCodecs.BOOLEAN));
        }
        return new CelestialBodyDefinition(id, parent, level,
                fields.read("gravity_multiplier", GRAVITY),
                fields.read("atmosphere", AtmosphereDefinition.CODEC),
                fields.read("orbit", OrbitDefinition.CODEC),
                fields.read("visual_profile", BoundedCelestialCodecs.RESOURCE_LOCATION),
                capabilities, legacy ? 1.0D : fields.read("solar_intensity", SOLAR),
                legacy ? 0.0D : fields.read("radiation", RADIATION),
                !legacy && fields.optional("environment_effects", BoundedCelestialCodecs.BOOLEAN).orElse(false),
                !legacy && fields.optional("discovery_required", BoundedCelestialCodecs.BOOLEAN).orElse(false));
    }

    /** Small strict field adapter; errors never echo an unbounded input object. */
    private record Fields<T>(DynamicOps<T> ops, MapLike<T> map) {
        Fields(DynamicOps<T> ops, T input) {
            this(ops, ops.getMap(input).result().orElseThrow(
                    () -> new IllegalArgumentException("Expected a celestial definition object")));
        }

        boolean has(String name) {
            // JsonOps.get treats explicit JSON null as missing; field presence must not.
            T key = ops.createString(name);
            return map.entries().anyMatch(entry -> key.equals(entry.getFirst()));
        }

        T required(String name) {
            T value = map.get(name);
            if (value == null) {
                throw new IllegalArgumentException("Missing celestial field " + name);
            }
            return value;
        }

        <A> A read(String name, Codec<A> codec) {
            return codec.parse(ops, required(name)).result().orElseThrow(
                    () -> new IllegalArgumentException("Invalid celestial field " + name));
        }

        <A> Optional<A> optional(String name, Codec<A> codec) {
            return has(name) ? Optional.of(read(name, codec)) : Optional.empty();
        }

        void allowOnly(Set<String> allowed) {
            var entries = map.entries().iterator();
            int count = 0;
            while (entries.hasNext()) {
                var entry = entries.next();
                String key = ops.getStringValue(entry.getFirst()).result().orElse("");
                if (++count > allowed.size() || !allowed.contains(key)) {
                    throw new IllegalArgumentException("Unknown celestial definition field");
                }
            }
        }
    }
}
