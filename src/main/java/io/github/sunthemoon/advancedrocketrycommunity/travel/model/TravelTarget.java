package io.github.sunthemoon.advancedrocketrycommunity.travel.model;

import com.mojang.serialization.Codec;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** A bounded, stable destination identity resolved by authoritative server services. */
public sealed interface TravelTarget permits TravelTarget.BodySurface, TravelTarget.Orbit,
        TravelTarget.Station, TravelTarget.Mission {
    int SCHEMA_VERSION = 1;
    int MAX_RESOURCE_LOCATION_CHARS = 128;

    ResourceLocation BODY_SURFACE_TYPE = ModIdentity.id("body_surface");
    ResourceLocation ORBIT_TYPE = ModIdentity.id("orbit");
    ResourceLocation STATION_TYPE = ModIdentity.id("station");
    ResourceLocation MISSION_TYPE = ModIdentity.id("mission");

    Codec<TravelTarget> CODEC = TravelTargetCodec.CODEC;

    default int schemaVersion() {
        return SCHEMA_VERSION;
    }

    ResourceLocation typeId();

    record BodySurface(ResourceLocation bodyId) implements TravelTarget {
        public BodySurface {
            bodyId = requireBodyId(bodyId);
        }

        @Override
        public ResourceLocation typeId() {
            return BODY_SURFACE_TYPE;
        }
    }

    record Orbit(ResourceLocation bodyId) implements TravelTarget {
        public Orbit {
            bodyId = requireBodyId(bodyId);
        }

        @Override
        public ResourceLocation typeId() {
            return ORBIT_TYPE;
        }
    }

    record Station(UUID instanceId) implements TravelTarget {
        public Station {
            instanceId = requireInstanceId(instanceId);
        }

        @Override
        public ResourceLocation typeId() {
            return STATION_TYPE;
        }
    }

    record Mission(UUID instanceId) implements TravelTarget {
        public Mission {
            instanceId = requireInstanceId(instanceId);
        }

        @Override
        public ResourceLocation typeId() {
            return MISSION_TYPE;
        }
    }

    private static ResourceLocation requireBodyId(ResourceLocation bodyId) {
        Objects.requireNonNull(bodyId, "bodyId");
        if (bodyId.toString().length() > MAX_RESOURCE_LOCATION_CHARS) {
            throw new IllegalArgumentException("Travel target body identifier exceeds 128 characters");
        }
        return bodyId;
    }

    private static UUID requireInstanceId(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        if (instanceId.variant() != 2 || instanceId.version() < 1 || instanceId.version() > 5) {
            throw new IllegalArgumentException("Travel target instance identifier is not an RFC 4122 UUID");
        }
        return instanceId;
    }
}
