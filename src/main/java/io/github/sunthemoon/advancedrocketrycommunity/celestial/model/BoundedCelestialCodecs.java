package io.github.sunthemoon.advancedrocketrycommunity.celestial.model;

import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Shared bounded primitives for data-pack and network-facing celestial data. */
public final class BoundedCelestialCodecs {
    public static final int MAX_RESOURCE_LOCATION_CHARS = 128;

    /** Neither fractional numbers nor numeric strings may become an integer. */
    public static final Codec<Long> EXACT_LONG = numeric(new Codec<>() {
        @Override
        public <T> DataResult<Pair<Long, T>> decode(DynamicOps<T> ops, T input) {
            return ops.getNumberValue(input).flatMap(number -> {
                String raw = number.toString();
                if (raw.length() > 20 || !raw.matches("-?(0|[1-9][0-9]*)")) {
                    return DataResult.error(() -> "Expected an integer without fractions or exponents");
                }
                try {
                    return DataResult.success(Pair.of(Long.parseLong(raw), ops.empty()));
                } catch (NumberFormatException exception) {
                    return DataResult.error(() -> "Integer is outside the signed long range");
                }
            });
        }

        @Override
        public <T> DataResult<T> encode(Long input, DynamicOps<T> ops, T prefix) {
            return Codec.LONG.encode(input, ops, prefix);
        }
    });

    public static final Codec<Boolean> BOOLEAN = new Codec<>() {
        @Override
        public <T> DataResult<Pair<Boolean, T>> decode(DynamicOps<T> ops, T input) {
            return Codec.BOOL.decode(ops, input).flatMap(pair ->
                    ops.createBoolean(pair.getFirst()).equals(input)
                            ? DataResult.success(pair)
                            : DataResult.error(() -> "Expected a boolean"));
        }

        @Override
        public <T> DataResult<T> encode(Boolean input, DynamicOps<T> ops, T prefix) {
            return Codec.BOOL.encode(input, ops, prefix);
        }
    };

    public static final Codec<ResourceLocation> RESOURCE_LOCATION = ResourceLocation.CODEC.flatXmap(
            BoundedCelestialCodecs::validateResourceLocation,
            BoundedCelestialCodecs::validateResourceLocation
    );

    public static final Codec<ResourceKey<Level>> LEVEL_KEY = RESOURCE_LOCATION.xmap(
            id -> ResourceKey.create(Registries.DIMENSION, id),
            ResourceKey::location
    );

    private BoundedCelestialCodecs() {
    }

    public static void requireId(ResourceLocation value, String name) {
        Objects.requireNonNull(value, name);
        if (value.toString().length() > MAX_RESOURCE_LOCATION_CHARS) {
            throw new IllegalArgumentException(name + " exceeds 128 characters");
        }
    }

    public static void requireRange(double value, double minimum, double maximum, String name) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " is outside its finite range");
        }
    }

    public static Codec<Double> finiteDouble(double minimum, double maximum) {
        return numeric(Codec.DOUBLE.flatXmap(
                value -> finite(value, minimum, maximum), value -> finite(value, minimum, maximum)));
    }

    private static DataResult<Double> finite(double value, double minimum, double maximum) {
        return Double.isFinite(value) && value >= minimum && value <= maximum
                ? DataResult.success(value) : DataResult.error(() -> "Number is outside its finite range");
    }

    private static <A> Codec<A> numeric(Codec<A> delegate) {
        return new Codec<>() {
            @Override
            public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
                if (input instanceof JsonElement json
                        && (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isNumber())) {
                    return DataResult.error(() -> "Expected a JSON number");
                }
                return delegate.decode(ops, input);
            }

            @Override
            public <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix) {
                return delegate.encode(input, ops, prefix);
            }
        };
    }

    /** Constructor invariants also reject malformed serialized input as a DataResult. */
    public static <A> Codec<A> guarded(Codec<A> delegate) {
        return new Codec<>() {
            @Override
            public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
                try {
                    return delegate.decode(ops, input);
                } catch (IllegalArgumentException exception) {
                    return DataResult.error(exception::getMessage);
                }
            }

            @Override
            public <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix) {
                return delegate.encode(input, ops, prefix);
            }
        };
    }

    public static <T> Codec<T> validated(
            Codec<T> codec,
            Function<T, DataResult<T>> validator
    ) {
        return codec.flatXmap(validator, validator);
    }

    private static DataResult<ResourceLocation> validateResourceLocation(ResourceLocation value) {
        if (value.toString().length() > MAX_RESOURCE_LOCATION_CHARS) {
            return DataResult.error(() -> "Resource location exceeds 128 characters: " + value);
        }
        return DataResult.success(value);
    }
}
