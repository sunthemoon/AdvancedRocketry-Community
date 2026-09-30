package io.github.sunthemoon.advancedrocketrycommunity.station.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationRegistryModel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Detached station payload validation; no SavedData lifecycle or disk writes. */
public final class StationRegistryPayload {
    static final String WARP_ENERGY = "warp_energy";

    private StationRegistryPayload() {
    }

    /** Decodes a current (root 4) payload: record 2, reservation 1 and the required warp energy list. */
    public static StationRegistryModel decodeCurrent(CompoundTag source) {
        return decode(source, false, true);
    }

    /**
     * Upgrades an older root's content to root 4 (ADR-044 §6): roots 1 and 2 carry record 1, which
     * becomes record 2 after strict geometry validation; root 3 already carries record 2. Every root
     * gains an empty warp energy list. All other original tags are preserved; stamping is the caller's.
     */
    public static CompoundTag upgrade(CompoundTag source, int rootSchema) {
        if (rootSchema < 1 || rootSchema >= StationLimits.REGISTRY_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Station registry root " + rootSchema + " is not upgradable");
        }
        if (source.contains(WARP_ENERGY)) {
            throw new IllegalArgumentException("Station registry root " + rootSchema + " cannot carry warp energy");
        }
        boolean legacyRecords = rootSchema < StationLimits.ORBITAL_REGISTRY_SCHEMA_VERSION;
        decode(source, legacyRecords, false);
        CompoundTag result = source.copy();
        if (legacyRecords) {
            for (Tag raw : requireList(result, "stations")) {
                ((CompoundTag) raw).putInt("schema_version", StationLimits.STATE_SCHEMA_VERSION);
            }
        }
        result.put(WARP_ENERGY, new ListTag());
        return result;
    }

    private static StationRegistryModel decode(CompoundTag source, boolean legacy, boolean warpRoot) {
        if (StationNbtSize.uncompressedBytes(source) > StationLimits.MAX_REGISTRY_NBT_BYTES) {
            throw new IllegalArgumentException("Station registry exceeds the fixed NBT bound");
        }
        ListTag stations = requireList(source, "stations");
        ListTag reservations = requireList(source, "reservations");
        if (stations.size() > StationLimits.MAX_STATIONS
                || reservations.size() > StationLimits.MAX_RESERVATIONS) {
            throw new IllegalArgumentException("Station registry lists exceed fixed bounds");
        }
        StationRegistryModel registry = new StationRegistryModel();
        for (Tag raw : stations) {
            registry.restoreStation(legacy
                    ? StationNbtCodec.decodeLegacyState((CompoundTag) raw)
                    : StationNbtCodec.decodeState((CompoundTag) raw));
        }
        for (Tag raw : reservations) {
            registry.restoreReservation(StationNbtCodec.decodeReservation((CompoundTag) raw));
        }
        if (warpRoot) {
            ListTag balances = requireList(source, WARP_ENERGY);
            if (balances.size() > StationLimits.MAX_WARP_ENERGY_ENTRIES) {
                throw new IllegalArgumentException("Station warp energy list exceeds the fixed bound");
            }
            for (Tag raw : balances) {
                StationNbtCodec.WarpEnergyEntry entry = StationNbtCodec.decodeWarpEnergy((CompoundTag) raw);
                registry.restoreWarpEnergy(entry.stationId(), entry.energy());
            }
        } else if (source.contains(WARP_ENERGY)) {
            throw new IllegalArgumentException("Station registry root without warp energy carries one");
        }
        return registry;
    }

    private static ListTag requireList(CompoundTag source, String key) {
        Tag raw = source.get(key);
        if (!(raw instanceof ListTag list)
                || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Missing or invalid station registry list " + key);
        }
        return list;
    }
}
