package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.BoundedSavedDataIo;
import io.github.sunthemoon.advancedrocketrycommunity.progression.ResearchAccount;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.AsteroidInstance;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveyJob;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteMissionRegistry;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Detached satellite registry payload validation and upgrade; no SavedData lifecycle or disk writes. */
public final class SatelliteRegistryPayload {
    static final String INSTANCES = "instances";
    static final String SAVE_EPOCH = "save_epoch";
    static final String GROUND_SURVEYS = "ground_surveys";

    private SatelliteRegistryPayload() {
    }

    /** Decodes current root 4, preserving the independent legacy-validator threshold. */
    public static SatelliteMissionRegistry decodeCurrent(CompoundTag payload) {
        return decode(payload, false);
    }

    private static SatelliteMissionRegistry decode(CompoundTag payload, boolean schema3) {
        if (SatelliteNbtSize.uncompressedBytes(payload) > SatelliteLimits.MAX_REGISTRY_NBT_BYTES) {
            throw new IllegalArgumentException("Satellite registry exceeds its fixed NBT bound");
        }
        CompoundTag clock = requireCompound(payload, "clock");
        if (!payload.contains(SAVE_EPOCH, Tag.TAG_LONG) || payload.getLong(SAVE_EPOCH) < 1L) {
            throw new IllegalArgumentException("Satellite registry save epoch is missing or invalid");
        }
        SatelliteMissionRegistry restored = SatelliteMissionRegistry.restore(
                requireNonNegativeLong(clock, "logical_game_time"),
                requireNonNegativeLong(clock, "last_observed_game_time"),
                payload.getLong(SAVE_EPOCH),
                CodecRecordSizer.INSTANCE
        );
        ListTag satellites = requireList(payload, "satellites");
        ListTag missions = requireList(payload, "missions");
        ListTag accounts = requireList(payload, "research_accounts");
        ListTag instances = requireList(payload, INSTANCES);
        if (schema3 && payload.contains(GROUND_SURVEYS)) {
            throw new IllegalArgumentException("Root 3 cannot carry ground_surveys");
        }
        ListTag jobs = schema3 ? new ListTag() : requireList(payload, GROUND_SURVEYS);
        if (satellites.size() > SatelliteLimits.MAX_SATELLITES
                || missions.size() > SatelliteLimits.MAX_MISSIONS
                || accounts.size() > SatelliteLimits.MAX_RESEARCH_ACCOUNTS
                || instances.size() > SatelliteLimits.MAX_INSTANCES || jobs.size() > SatelliteLimits.MAX_GROUND_SURVEYS) {
            throw new IllegalArgumentException("Satellite registry lists exceed fixed bounds");
        }
        for (Tag raw : satellites) {
            restored.restoreSatellite(SatelliteNbtCodec.decodeSatellite((CompoundTag) raw));
        }
        for (Tag raw : missions) {
            restored.restoreMission(SatelliteNbtCodec.decodeMission((CompoundTag) raw));
        }
        for (Tag raw : accounts) {
            restored.restoreAccount(SatelliteNbtCodec.decodeAccount((CompoundTag) raw));
        }
        for (Tag raw : instances) {
            CompoundTag instance = schema3 ? SatelliteNbtCodec.upgradeLegacyInstance((CompoundTag) raw) : (CompoundTag) raw;
            restored.restoreInstance(SatelliteNbtCodec.decodeInstance(instance));
        }
        for (Tag raw : jobs) { restored.groundSurveys().restore(GroundSurveyCodec.decode((CompoundTag) raw)); }
        restored.finishRestore();
        if (!schema3) {
            // D15 / W224: every loaded current root, repaired or not, must be canonically saveable.
            encodeCurrent(restored, new CompoundTag());
        }
        BoundedSavedDataIo.validatePayload(payload, ManagedSavedDataType.SATELLITE_MISSIONS);
        return restored;
    }

    /** Shared by current-root load validation and SavedData writes. */
    static CompoundTag encodeCurrent(SatelliteMissionRegistry registry, CompoundTag target) {
        encodeUnchecked(registry, target);
        SatelliteCanonicalBoundsException.checkRoot(target, SatelliteLimits.MAX_REGISTRY_NBT_BYTES);
        return target;
    }

    /** The current encoding before its root bound is checked; a caller that edits the root checks it after. */
    static CompoundTag encodeUnchecked(SatelliteMissionRegistry registry, CompoundTag target) {
        // ADR-050 section 2: the epoch advances only after a changed write succeeds.
        return encodeSorted(registry.logicalGameTime(), registry.lastObservedGameTime(), registry.epochToWrite(),
                registry.satellites(), registry.missions(), registry.accounts(), registry.instances(),
                registry.groundSurveys().all(), target);
    }

    /**
     * The one current-root encoder, shared by the live view and the off-thread capture (ADR-074 item 3): the same
     * insertion order and the same records in the same order give the same bytes. Every list is sorted by ID.
     */
    static CompoundTag encodeSorted(long logicalGameTime, long lastObservedGameTime, long epoch,
            List<SatelliteState> satellites, List<MissionState> missions, List<ResearchAccount> accounts,
            List<AsteroidInstance> instances, List<GroundSurveyJob> jobs, CompoundTag target) {
        SavedDataSchemaMigrator.stampCurrent(ManagedSavedDataType.SATELLITE_MISSIONS, target);
        CompoundTag clock = new CompoundTag();
        clock.putLong("logical_game_time", logicalGameTime);
        clock.putLong("last_observed_game_time", lastObservedGameTime);
        target.put("clock", clock);
        target.putLong(SAVE_EPOCH, epoch);
        ListTag satelliteList = new ListTag();
        satellites.forEach(state -> satelliteList.add(SatelliteNbtCodec.encodeSatellite(state)));
        target.put("satellites", satelliteList);
        ListTag missionList = new ListTag();
        missions.forEach(state -> missionList.add(SatelliteNbtCodec.encodeMission(state)));
        target.put("missions", missionList);
        ListTag accountList = new ListTag();
        accounts.forEach(account -> accountList.add(SatelliteNbtCodec.encodeAccount(account)));
        target.put("research_accounts", accountList);
        ListTag instanceList = new ListTag();
        instances.forEach(instance -> instanceList.add(SatelliteNbtCodec.encodeInstance(instance)));
        target.put(INSTANCES, instanceList);
        ListTag jobList = new ListTag();
        jobs.forEach(job -> jobList.add(GroundSurveyCodec.encode(job)));
        target.put(GROUND_SURVEYS, jobList);
        return target;
    }

    /**
     * Upgrades roots 1/2 through the historical root-3 validator, then roots 3 to 4 with tagged origins.
     * Account state and an existing save epoch are unchanged; no load-time repair is copied into the result.
     */
    public static CompoundTag upgrade(CompoundTag source, int rootSchema) {
        return upgradeWithSnapshot(source, rootSchema).payload();
    }

    public record BudgetSnapshot(int instanceCount, int largestInstance, int rootBytesBefore, int rootBytesAfter,
                                 long instanceReservationBefore, long instanceReservationAfter) { }
    public record Upgrade(CompoundTag payload, BudgetSnapshot snapshot) { }

    public static Upgrade upgradeWithSnapshot(CompoundTag source, int rootSchema) {
        if (rootSchema < 1 || rootSchema >= SatelliteLimits.REGISTRY_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Satellite registry root " + rootSchema + " is not upgradable");
        }
        if (rootSchema < 3 && (source.contains(INSTANCES) || source.contains(SAVE_EPOCH))) {
            throw new IllegalArgumentException("Satellite registry root " + rootSchema + " cannot carry v1.6 sections");
        }
        if (source.contains(GROUND_SURVEYS)) {
            throw new IllegalArgumentException("Legacy satellite root cannot carry ground_surveys");
        }
        CompoundTag result = source.copy();
        if (rootSchema < 3) { result = upgradeToSchema3(result); }
        // Fixed historical decoder: root-3 instances are schema 1, not the current instance constant.
        decode(result, true);
        ListTag migrated = new ListTag();
        long reservedBefore = 0;
        long reservedAfter = 0;
        int largest = 0;
        for (Tag raw : requireList(result, INSTANCES)) {
            CompoundTag upgraded = SatelliteNbtCodec.upgradeLegacyInstance((CompoundTag) raw);
            migrated.add(upgraded);
            largest = Math.max(largest, SatelliteNbtSize.uncompressedBytes(upgraded));
            int reservation = CodecRecordSizer.INSTANCE.instanceBytes(SatelliteNbtCodec.decodeInstance(upgraded));
            reservedAfter = Math.addExact(reservedAfter, reservation);
            // The fixed tagged envelope adds 25 bytes over the exact schema-1 source_mission field.
            reservedBefore = Math.addExact(reservedBefore, reservation - 25);
        }
        result.put(INSTANCES, migrated);
        result.put(GROUND_SURVEYS, new ListTag());
        SavedDataSchemaMigrator.stampCurrent(ManagedSavedDataType.SATELLITE_MISSIONS, result);
        decodeCurrent(result);
        BudgetSnapshot snapshot = new BudgetSnapshot(migrated.size(), largest,
                SatelliteNbtSize.uncompressedBytes(source), SatelliteNbtSize.uncompressedBytes(result),
                reservedBefore, reservedAfter);
        return new Upgrade(result, snapshot);
    }

    /** The historical 1/2 -> 3 transformation; it is validated as schema 3, not as current. */
    private static CompoundTag upgradeToSchema3(CompoundTag result) {
        ListTag satellites = requireList(result, "satellites");
        ListTag missions = requireList(result, "missions");
        requireList(result, "research_accounts");
        requireCompound(result, "clock");
        if (satellites.size() > SatelliteLimits.MAX_SATELLITES || missions.size() > SatelliteLimits.MAX_MISSIONS) {
            throw new IllegalArgumentException("Legacy satellite registry lists exceed fixed bounds");
        }
        ListTag upgradedSatellites = new ListTag();
        for (Tag raw : satellites) {
            upgradedSatellites.add(SatelliteNbtCodec.upgradeLegacySatellite(requireCompoundElement(raw)));
        }
        ListTag upgradedMissions = new ListTag();
        for (Tag raw : missions) {
            upgradedMissions.add(SatelliteNbtCodec.upgradeLegacyMission(requireCompoundElement(raw)));
        }
        result.put("satellites", upgradedSatellites);
        result.put("missions", upgradedMissions);
        result.put(INSTANCES, new ListTag());
        result.putLong(SAVE_EPOCH, 1L);
        result.putInt("schema_version", 3);
        result.putString("format_epoch", ManagedSavedDataType.SATELLITE_MISSIONS.formatEpoch(3));
        decode(result, true);
        return result;
    }

    private static CompoundTag requireCompoundElement(Tag raw) {
        if (!(raw instanceof CompoundTag compound)) {
            throw new IllegalArgumentException("Satellite registry list holds a non-compound element");
        }
        return compound;
    }

    private static long requireNonNegativeLong(CompoundTag source, String key) {
        if (!source.contains(key, Tag.TAG_LONG)) {
            throw new IllegalArgumentException("Missing satellite registry long " + key);
        }
        long value = source.getLong(key);
        if (value < 0L) {
            throw new IllegalArgumentException("Satellite registry long " + key + " cannot be negative");
        }
        return value;
    }

    private static CompoundTag requireCompound(CompoundTag source, String key) {
        if (!source.contains(key, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Missing satellite registry compound " + key);
        }
        return source.getCompound(key);
    }

    private static ListTag requireList(CompoundTag source, String key) {
        Tag raw = source.get(key);
        if (!(raw instanceof ListTag list)
                || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Missing or invalid satellite registry list " + key);
        }
        return list;
    }
}
