package io.github.sunthemoon.advancedrocketrycommunity.station.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationEnvironmentProfile;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationGridCell;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationPosition;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationRegion;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationReservation;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Strict size-bounded codec for independently versioned station records. */
final class StationNbtCodec {
    private StationNbtCodec() {
    }

    static CompoundTag encodeState(StationState state) {
        Objects.requireNonNull(state, "state");
        CompoundTag target = base(
                state.stationId(),
                state.ownerId(),
                state.name(),
                state.cell(),
                state.orbitBody(),
                state.createdAtGameTime()
        );
        target.putInt("schema_version", state.schemaVersion());
        target.putIntArray("region", new int[]{
                state.region().minimumX(),
                state.region().minimumZ(),
                state.region().maximumX(),
                state.region().maximumZ()
        });
        target.putIntArray("landing_pad", position(state.landingPad()));
        CompoundTag environment = new CompoundTag();
        environment.putInt("gravity_milli", state.environment().gravityMilli());
        environment.putBoolean("vacuum", state.environment().vacuum());
        environment.putInt("solar_angle_milli_degrees", state.environment().solarAngleMilliDegrees());
        target.put("environment", environment);
        ListTag members = new ListTag();
        for (UUID memberId : state.sortedMembers()) {
            CompoundTag member = new CompoundTag();
            member.putUUID("id", memberId);
            members.add(member);
        }
        target.put("members", members);
        ListTag invitations = new ListTag();
        for (UUID playerId : state.sortedInvitations()) {
            CompoundTag invitation = new CompoundTag();
            invitation.putUUID("id", playerId);
            invitations.add(invitation);
        }
        target.put("invitations", invitations);
        requireRecordBound(target);
        return target;
    }

    static StationState decodeState(CompoundTag source) {
        requireRecordBound(source);
        int schema = requireInt(source, "schema_version");
        if (schema != StationLimits.STATE_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported station state schema " + schema);
        }
        StationGridCell cell = cell(source);
        int[] rawRegion = requireIntArray(source, "region", 4);
        StationRegion region = new StationRegion(
                rawRegion[0], rawRegion[1], rawRegion[2], rawRegion[3]
        );
        int[] rawLanding = requireIntArray(source, "landing_pad", 3);
        StationPosition landing = new StationPosition(rawLanding[0], rawLanding[1], rawLanding[2]);
        CompoundTag environment = requireCompound(source, "environment");
        StationEnvironmentProfile profile = new StationEnvironmentProfile(
                requireInt(environment, "gravity_milli"),
                requireBoolean(environment, "vacuum"),
                requireInt(environment, "solar_angle_milli_degrees")
        );
        ListTag memberTags = requireList(source, "members", Tag.TAG_COMPOUND);
        if (memberTags.size() > StationLimits.MAX_MEMBERS) {
            throw new IllegalArgumentException("Station member list exceeds the fixed bound");
        }
        ArrayList<UUID> members = new ArrayList<>(memberTags.size());
        for (Tag raw : memberTags) {
            members.add(requireUuid((CompoundTag) raw, "id"));
        }
        ListTag invitationTags = requireList(source, "invitations", Tag.TAG_COMPOUND);
        if (invitationTags.size() > StationLimits.MAX_INVITATIONS) {
            throw new IllegalArgumentException("Station invitation list exceeds the fixed bound");
        }
        ArrayList<UUID> invitations = new ArrayList<>(invitationTags.size());
        for (Tag raw : invitationTags) {
            invitations.add(requireUuid((CompoundTag) raw, "id"));
        }
        return new StationState(
                schema,
                requireUuid(source, "station_id"),
                requireUuid(source, "owner_id"),
                requireString(source, "name", StationLimits.MAX_NAME_LENGTH),
                cell,
                region,
                landing,
                requireLocation(source, "orbit_body"),
                requireNonNegativeLong(source, "created_at_game_time"),
                profile,
                members,
                invitations
        );
    }

    static CompoundTag encodeReservation(StationReservation reservation) {
        CompoundTag target = base(
                reservation.stationId(),
                reservation.ownerId(),
                reservation.name(),
                reservation.cell(),
                reservation.orbitBody(),
                reservation.createdAtGameTime()
        );
        target.putInt("schema_version", StationLimits.STATE_SCHEMA_VERSION);
        requireRecordBound(target);
        return target;
    }

    static StationReservation decodeReservation(CompoundTag source) {
        requireRecordBound(source);
        int schema = requireInt(source, "schema_version");
        if (schema != StationLimits.STATE_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported station reservation schema " + schema);
        }
        return new StationReservation(
                requireUuid(source, "station_id"),
                requireUuid(source, "owner_id"),
                requireString(source, "name", StationLimits.MAX_NAME_LENGTH),
                cell(source),
                requireLocation(source, "orbit_body"),
                requireNonNegativeLong(source, "created_at_game_time")
        );
    }

    private static CompoundTag base(
            UUID stationId,
            UUID ownerId,
            String name,
            StationGridCell cell,
            ResourceLocation orbitBody,
            long createdAt
    ) {
        CompoundTag target = new CompoundTag();
        target.putUUID("station_id", stationId);
        target.putUUID("owner_id", ownerId);
        target.putString("name", name);
        target.putInt("cell_x", cell.x());
        target.putInt("cell_z", cell.z());
        target.putString("orbit_body", orbitBody.toString());
        target.putLong("created_at_game_time", createdAt);
        return target;
    }

    private static StationGridCell cell(CompoundTag source) {
        return new StationGridCell(requireInt(source, "cell_x"), requireInt(source, "cell_z"));
    }

    private static int[] position(StationPosition position) {
        return new int[]{position.x(), position.y(), position.z()};
    }

    private static void requireRecordBound(CompoundTag source) {
        if (StationNbtSize.uncompressedBytes(source) > StationLimits.MAX_STATION_RECORD_NBT_BYTES) {
            throw new IllegalArgumentException("Station record exceeds the fixed NBT bound");
        }
    }

    private static UUID requireUuid(CompoundTag source, String key) {
        if (!source.hasUUID(key)) {
            throw new IllegalArgumentException("Missing station UUID " + key);
        }
        return source.getUUID(key);
    }

    private static int requireInt(CompoundTag source, String key) {
        if (!source.contains(key, Tag.TAG_INT)) {
            throw new IllegalArgumentException("Missing station integer " + key);
        }
        return source.getInt(key);
    }

    private static long requireNonNegativeLong(CompoundTag source, String key) {
        if (!source.contains(key, Tag.TAG_LONG)) {
            throw new IllegalArgumentException("Missing station long " + key);
        }
        long value = source.getLong(key);
        if (value < 0L) {
            throw new IllegalArgumentException("Station long " + key + " cannot be negative");
        }
        return value;
    }

    private static boolean requireBoolean(CompoundTag source, String key) {
        if (!source.contains(key, Tag.TAG_BYTE)) {
            throw new IllegalArgumentException("Missing station boolean " + key);
        }
        return source.getBoolean(key);
    }

    private static String requireString(CompoundTag source, String key, int maximumLength) {
        if (!source.contains(key, Tag.TAG_STRING)) {
            throw new IllegalArgumentException("Missing station string " + key);
        }
        String value = source.getString(key);
        if (value.isEmpty() || value.length() > maximumLength) {
            throw new IllegalArgumentException("Station string " + key + " is outside its bound");
        }
        return value;
    }

    private static ResourceLocation requireLocation(CompoundTag source, String key) {
        ResourceLocation location = ResourceLocation.tryParse(requireString(source, key, 128));
        if (location == null) {
            throw new IllegalArgumentException("Station identifier " + key + " is invalid");
        }
        return location;
    }

    private static int[] requireIntArray(CompoundTag source, String key, int length) {
        if (!source.contains(key, Tag.TAG_INT_ARRAY)) {
            throw new IllegalArgumentException("Missing station integer array " + key);
        }
        int[] value = source.getIntArray(key);
        if (value.length != length) {
            throw new IllegalArgumentException("Station integer array " + key + " has the wrong length");
        }
        return value;
    }

    private static CompoundTag requireCompound(CompoundTag source, String key) {
        if (!source.contains(key, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Missing station compound " + key);
        }
        return source.getCompound(key);
    }

    private static ListTag requireList(CompoundTag source, String key, byte elementType) {
        Tag raw = source.get(key);
        if (!(raw instanceof ListTag list)
                || (!list.isEmpty() && list.getElementType() != elementType)) {
            throw new IllegalArgumentException("Missing or invalid station list " + key);
        }
        return list;
    }
}
