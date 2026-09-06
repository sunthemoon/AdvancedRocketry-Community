package io.github.sunthemoon.advancedrocketrycommunity.travel.route.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** One bounded, versioned data route before graph validation. */
public record RouteDefinition(
        int schemaVersion,
        ResourceLocation id,
        RouteAnchor from,
        RouteAnchor to,
        int distanceUnits,
        boolean bidirectional
) {
    private static final Codec<Encoded> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("schema_version").forGetter(Encoded::schemaVersion),
            RouteCodecs.RESOURCE_LOCATION.fieldOf("id").forGetter(Encoded::id),
            RouteAnchor.CODEC.fieldOf("from").forGetter(Encoded::from),
            RouteAnchor.CODEC.fieldOf("to").forGetter(Encoded::to),
            Codec.intRange(0, RouteLimits.MAX_DISTANCE_UNITS)
                    .fieldOf("distance_units").forGetter(Encoded::distanceUnits),
            Codec.BOOL.fieldOf("bidirectional").forGetter(Encoded::bidirectional)
    ).apply(instance, Encoded::new));

    public static final Codec<RouteDefinition> CODEC = ExactMapCodec.wrap(
            RAW_CODEC,
            Set.of("schema_version", "id", "from", "to", "distance_units", "bidirectional"),
            "RouteDefinition"
    ).flatXmap(RouteDefinition::decode, RouteDefinition::encode);

    public RouteDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (schemaVersion != RouteLimits.SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported route schema " + schemaVersion);
        }
        if (id.toString().length() > RouteLimits.MAX_RESOURCE_LOCATION_CHARS) {
            throw new IllegalArgumentException("Route identifier exceeds 128 characters");
        }
        if (distanceUnits < 0 || distanceUnits > RouteLimits.MAX_DISTANCE_UNITS) {
            throw new IllegalArgumentException("Route distance is outside its fixed bound");
        }
    }

    private static DataResult<RouteDefinition> decode(Encoded value) {
        try {
            return DataResult.success(new RouteDefinition(
                    value.schemaVersion,
                    value.id,
                    value.from,
                    value.to,
                    value.distanceUnits,
                    value.bidirectional
            ));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private static DataResult<Encoded> encode(RouteDefinition value) {
        return DataResult.success(new Encoded(
                value.schemaVersion,
                value.id,
                value.from,
                value.to,
                value.distanceUnits,
                value.bidirectional
        ));
    }

    private record Encoded(
            int schemaVersion,
            ResourceLocation id,
            RouteAnchor from,
            RouteAnchor to,
            int distanceUnits,
            boolean bidirectional
    ) {
    }
}
