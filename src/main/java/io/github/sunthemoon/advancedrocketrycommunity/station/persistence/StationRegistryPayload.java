package io.github.sunthemoon.advancedrocketrycommunity.station.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationRegistryModel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Detached station payload validation; no SavedData lifecycle or disk writes. */
public final class StationRegistryPayload {
    private StationRegistryPayload() {
    }

    public static StationRegistryModel decodeCurrent(CompoundTag source) {
        return decode(source, false);
    }

    /** Validate old geometry before changing versions; preserve all original tags. */
    public static CompoundTag upgradeLegacyRecords(CompoundTag source) {
        decode(source, true);
        CompoundTag result = source.copy();
        for (Tag raw : requireList(result, "stations")) {
            ((CompoundTag) raw).putInt("schema_version", StationLimits.STATE_SCHEMA_VERSION);
        }
        return result;
    }

    private static StationRegistryModel decode(CompoundTag source, boolean legacy) {
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
