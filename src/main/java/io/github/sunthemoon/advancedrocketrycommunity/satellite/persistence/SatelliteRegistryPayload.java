package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteMissionRegistry;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Detached satellite registry payload validation and upgrade; no SavedData lifecycle or disk writes. */
public final class SatelliteRegistryPayload {
    static final String INSTANCES = "instances";
    static final String SAVE_EPOCH = "save_epoch";

    private SatelliteRegistryPayload() {
    }

    /** Decodes a root-3 payload into a restored registry (ADR-050 §10). */
    public static SatelliteMissionRegistry decodeCurrent(CompoundTag payload) {
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
                payload.getLong(SAVE_EPOCH)
        );
        ListTag satellites = requireList(payload, "satellites");
        ListTag missions = requireList(payload, "missions");
        ListTag accounts = requireList(payload, "research_accounts");
        ListTag instances = requireList(payload, INSTANCES);
        if (satellites.size() > SatelliteLimits.MAX_SATELLITES
                || missions.size() > SatelliteLimits.MAX_MISSIONS
                || accounts.size() > SatelliteLimits.MAX_RESEARCH_ACCOUNTS
                || instances.size() > SatelliteLimits.MAX_INSTANCES) {
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
            restored.restoreInstance(SatelliteNbtCodec.decodeInstance((CompoundTag) raw));
        }
        restored.finishRestore();
        return restored;
    }

    /**
     * Upgrades a root-1 or root-2 payload to root 3: satellite and mission records 1 → 2 (copying extra tags),
     * an empty instance list and save epoch 1. Accounts are unchanged. Stamping is the caller's.
     */
    public static CompoundTag upgrade(CompoundTag source, int rootSchema) {
        if (rootSchema < 1 || rootSchema >= SatelliteLimits.REGISTRY_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Satellite registry root " + rootSchema + " is not upgradable");
        }
        if (source.contains(INSTANCES) || source.contains(SAVE_EPOCH)) {
            throw new IllegalArgumentException("Satellite registry root " + rootSchema + " cannot carry v1.6 sections");
        }
        CompoundTag result = source.copy();
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
        decodeCurrent(result);
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
