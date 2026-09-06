package io.github.sunthemoon.advancedrocketrycommunity.travel.route.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** A stable data-defined graph endpoint; runtime instance UUIDs never enter route data. */
public record RouteAnchor(Kind kind, ResourceLocation bodyId) implements Comparable<RouteAnchor> {
    private static final Codec<Encoded> ENCODED_CODEC = ExactMapCodec.wrap(
            RecordCodecBuilder.create(instance -> instance.group(
                    RouteCodecs.RESOURCE_LOCATION.fieldOf("type").forGetter(Encoded::type),
                    RouteCodecs.RESOURCE_LOCATION.fieldOf("body_id").forGetter(Encoded::bodyId)
            ).apply(instance, Encoded::new)),
            Set.of("type", "body_id"),
            "RouteAnchor"
    );

    public static final Codec<RouteAnchor> CODEC = ENCODED_CODEC.flatXmap(
            RouteAnchor::decode,
            anchor -> DataResult.success(new Encoded(anchor.kind.typeId(), anchor.bodyId))
    );

    public RouteAnchor {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(bodyId, "bodyId");
        if (bodyId.toString().length() > RouteLimits.MAX_RESOURCE_LOCATION_CHARS) {
            throw new IllegalArgumentException("Route anchor body identifier exceeds 128 characters");
        }
    }

    public static RouteAnchor bodySurface(ResourceLocation bodyId) {
        return new RouteAnchor(Kind.BODY_SURFACE, bodyId);
    }

    public static RouteAnchor orbit(ResourceLocation bodyId) {
        return new RouteAnchor(Kind.ORBIT, bodyId);
    }

    public ResourceLocation typeId() {
        return kind.typeId();
    }

    @Override
    public int compareTo(RouteAnchor other) {
        int typeOrder = typeId().compareTo(other.typeId());
        return typeOrder != 0 ? typeOrder : bodyId.compareTo(other.bodyId);
    }

    private static DataResult<RouteAnchor> decode(Encoded encoded) {
        Kind kind = Kind.fromTypeId(encoded.type);
        if (kind == null) {
            return DataResult.error(() -> "Unknown route anchor type " + encoded.type);
        }
        try {
            return DataResult.success(new RouteAnchor(kind, encoded.bodyId));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    public enum Kind {
        BODY_SURFACE(TravelTarget.BODY_SURFACE_TYPE),
        ORBIT(TravelTarget.ORBIT_TYPE);

        private final ResourceLocation typeId;

        Kind(ResourceLocation typeId) {
            this.typeId = typeId;
        }

        public ResourceLocation typeId() {
            return typeId;
        }

        private static Kind fromTypeId(ResourceLocation typeId) {
            for (Kind value : values()) {
                if (value.typeId.equals(typeId)) {
                    return value;
                }
            }
            return null;
        }
    }

    private record Encoded(ResourceLocation type, ResourceLocation bodyId) {
    }
}
