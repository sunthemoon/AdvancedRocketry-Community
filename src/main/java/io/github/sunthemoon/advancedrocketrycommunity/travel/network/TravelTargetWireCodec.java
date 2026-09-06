package io.github.sunthemoon.advancedrocketrycommunity.travel.network;

import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Compact schema-versioned network representation of the closed target contract. */
public final class TravelTargetWireCodec {
    public static final int MAX_ENCODED_BYTES = Byte.BYTES
            + Byte.BYTES
            + 2
            + TravelTarget.MAX_RESOURCE_LOCATION_CHARS;

    private static final int BODY_SURFACE = 0;
    private static final int ORBIT = 1;
    private static final int STATION = 2;
    private static final int MISSION = 3;

    private TravelTargetWireCodec() {
    }

    public static void encode(FriendlyByteBuf buffer, TravelTarget target) {
        buffer.writeByte(TravelTarget.SCHEMA_VERSION);
        if (target instanceof TravelTarget.BodySurface surface) {
            buffer.writeByte(BODY_SURFACE);
            writeBody(buffer, surface.bodyId());
        } else if (target instanceof TravelTarget.Orbit orbit) {
            buffer.writeByte(ORBIT);
            writeBody(buffer, orbit.bodyId());
        } else if (target instanceof TravelTarget.Station station) {
            buffer.writeByte(STATION);
            buffer.writeUUID(station.instanceId());
        } else if (target instanceof TravelTarget.Mission mission) {
            buffer.writeByte(MISSION);
            buffer.writeUUID(mission.instanceId());
        } else {
            throw new IllegalArgumentException("Unsupported travel target implementation");
        }
    }

    public static TravelTarget decode(FriendlyByteBuf buffer) {
        int schema = buffer.readUnsignedByte();
        if (schema != TravelTarget.SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported travel target wire schema " + schema);
        }
        return switch (buffer.readUnsignedByte()) {
            case BODY_SURFACE -> new TravelTarget.BodySurface(readBody(buffer));
            case ORBIT -> new TravelTarget.Orbit(readBody(buffer));
            case STATION -> new TravelTarget.Station(buffer.readUUID());
            case MISSION -> new TravelTarget.Mission(buffer.readUUID());
            default -> throw new IllegalArgumentException("Unknown travel target wire type");
        };
    }

    private static void writeBody(FriendlyByteBuf buffer, ResourceLocation bodyId) {
        buffer.writeUtf(bodyId.toString(), TravelTarget.MAX_RESOURCE_LOCATION_CHARS);
    }

    private static ResourceLocation readBody(FriendlyByteBuf buffer) {
        String value = buffer.readUtf(TravelTarget.MAX_RESOURCE_LOCATION_CHARS);
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        if (parsed == null || !parsed.toString().equals(value)) {
            throw new IllegalArgumentException("Travel target wire body ID is invalid or non-canonical");
        }
        return parsed;
    }
}
