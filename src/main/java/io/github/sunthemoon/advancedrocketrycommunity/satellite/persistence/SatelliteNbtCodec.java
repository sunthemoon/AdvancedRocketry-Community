package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.progression.ResearchAccount;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionStatus;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteStatus;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Strict bounded codec for independently versioned satellite runtime records. */
final class SatelliteNbtCodec {
    private SatelliteNbtCodec() {
    }

    static CompoundTag encodeSatellite(SatelliteState state) {
        CompoundTag target = new CompoundTag();
        target.putInt("schema_version", state.schemaVersion());
        target.putUUID("satellite_id", state.satelliteId());
        target.putString("definition_id", state.definitionId().toString());
        target.putUUID("owner_id", state.ownerId());
        target.putLong("launched_at", state.launchedAtLogicalTime());
        target.putString("status", state.status().name().toLowerCase(Locale.ROOT));
        state.currentMissionId().ifPresent(value -> target.putUUID("current_mission_id", value));
        requireBound(target);
        return target;
    }

    static SatelliteState decodeSatellite(CompoundTag source) {
        requireBound(source);
        return new SatelliteState(
                requireSchema(source, SatelliteLimits.SATELLITE_SCHEMA_VERSION, "satellite"),
                requireUuid(source, "satellite_id"),
                requireLocation(source, "definition_id"),
                requireUuid(source, "owner_id"),
                requireNonNegativeLong(source, "launched_at"),
                requireEnum(source, "status", SatelliteStatus.class),
                optionalUuid(source, "current_mission_id")
        );
    }

    static CompoundTag encodeMission(MissionState state) {
        CompoundTag target = new CompoundTag();
        target.putInt("schema_version", state.schemaVersion());
        target.putUUID("mission_id", state.missionId());
        target.putUUID("satellite_id", state.satelliteId());
        target.putUUID("owner_id", state.ownerId());
        target.putString("definition_id", state.definitionId().toString());
        target.putString("target_body_id", state.targetBodyId().toString());
        target.putLong("started_at", state.startedAtLogicalTime());
        target.putLong("completes_at", state.completesAtLogicalTime());
        target.putInt("research_yield", state.researchYield());
        target.putInt("discovery_cost", state.discoveryCost());
        target.putBoolean("discovery_required", state.discoveryRequired());
        target.putString("status", state.status().name().toLowerCase(Locale.ROOT));
        state.readyAtLogicalTime().ifPresent(value -> target.putLong("ready_at", value));
        state.resolvedAtLogicalTime().ifPresent(value -> target.putLong("resolved_at", value));
        requireBound(target);
        return target;
    }

    static MissionState decodeMission(CompoundTag source) {
        requireBound(source);
        return new MissionState(
                requireSchema(source, SatelliteLimits.MISSION_SCHEMA_VERSION, "mission"),
                requireUuid(source, "mission_id"),
                requireUuid(source, "satellite_id"),
                requireUuid(source, "owner_id"),
                requireLocation(source, "definition_id"),
                requireLocation(source, "target_body_id"),
                requireNonNegativeLong(source, "started_at"),
                requireNonNegativeLong(source, "completes_at"),
                requireInt(source, "research_yield"),
                requireInt(source, "discovery_cost"),
                requireBoolean(source, "discovery_required"),
                requireEnum(source, "status", MissionStatus.class),
                optionalLong(source, "ready_at"),
                optionalLong(source, "resolved_at")
        );
    }

    static CompoundTag encodeAccount(ResearchAccount account) {
        CompoundTag target = new CompoundTag();
        target.putInt("schema_version", account.schemaVersion());
        target.putUUID("owner_id", account.ownerId());
        target.putInt("balance", account.balance());
        target.putLong("lifetime_earned", account.lifetimeEarned());
        target.putLong("lifetime_spent", account.lifetimeSpent());
        requireBound(target);
        return target;
    }

    static ResearchAccount decodeAccount(CompoundTag source) {
        requireBound(source);
        return new ResearchAccount(
                requireSchema(source, SatelliteLimits.RESEARCH_ACCOUNT_SCHEMA_VERSION, "research account"),
                requireUuid(source, "owner_id"),
                requireInt(source, "balance"),
                requireNonNegativeLong(source, "lifetime_earned"),
                requireNonNegativeLong(source, "lifetime_spent")
        );
    }

    private static void requireBound(CompoundTag source) {
        if (SatelliteNbtSize.uncompressedBytes(source) > SatelliteLimits.MAX_RECORD_NBT_BYTES) {
            throw new IllegalArgumentException("Satellite runtime record exceeds its fixed NBT bound");
        }
    }

    private static int requireSchema(CompoundTag source, int expected, String recordName) {
        int value = requireInt(source, "schema_version");
        if (value != expected) {
            throw new IllegalArgumentException("Unsupported " + recordName + " schema " + value);
        }
        return value;
    }

    private static int requireInt(CompoundTag source, String key) {
        if (!source.contains(key, Tag.TAG_INT)) {
            throw new IllegalArgumentException("Missing satellite integer " + key);
        }
        return source.getInt(key);
    }

    private static long requireNonNegativeLong(CompoundTag source, String key) {
        if (!source.contains(key, Tag.TAG_LONG)) {
            throw new IllegalArgumentException("Missing satellite long " + key);
        }
        long value = source.getLong(key);
        if (value < 0L) {
            throw new IllegalArgumentException("Satellite long " + key + " cannot be negative");
        }
        return value;
    }

    private static boolean requireBoolean(CompoundTag source, String key) {
        if (!source.contains(key, Tag.TAG_BYTE)) {
            throw new IllegalArgumentException("Missing satellite boolean " + key);
        }
        return source.getBoolean(key);
    }

    private static UUID requireUuid(CompoundTag source, String key) {
        if (!source.hasUUID(key)) {
            throw new IllegalArgumentException("Missing satellite UUID " + key);
        }
        return source.getUUID(key);
    }

    private static Optional<UUID> optionalUuid(CompoundTag source, String key) {
        if (!source.contains(key)) {
            return Optional.empty();
        }
        return Optional.of(requireUuid(source, key));
    }

    private static OptionalLong optionalLong(CompoundTag source, String key) {
        if (!source.contains(key)) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(requireNonNegativeLong(source, key));
    }

    private static ResourceLocation requireLocation(CompoundTag source, String key) {
        if (!source.contains(key, Tag.TAG_STRING)) {
            throw new IllegalArgumentException("Missing satellite identifier " + key);
        }
        String raw = source.getString(key);
        if (raw.isEmpty() || raw.length() > 128) {
            throw new IllegalArgumentException("Satellite identifier " + key + " is outside its bound");
        }
        ResourceLocation parsed = ResourceLocation.tryParse(raw);
        if (parsed == null) {
            throw new IllegalArgumentException("Satellite identifier " + key + " is invalid");
        }
        return parsed;
    }

    private static <E extends Enum<E>> E requireEnum(
            CompoundTag source,
            String key,
            Class<E> type
    ) {
        if (!source.contains(key, Tag.TAG_STRING)) {
            throw new IllegalArgumentException("Missing satellite enum " + key);
        }
        String raw = source.getString(key);
        if (raw.isEmpty() || raw.length() > 64) {
            throw new IllegalArgumentException("Satellite enum " + key + " is outside its bound");
        }
        try {
            return Enum.valueOf(type, raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown satellite enum " + key + ": " + raw, exception);
        }
    }
}
