package io.github.sunthemoon.advancedrocketrycommunity.station.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventKind;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventOutbox;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationOrbitalClock;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationOrbitalControls;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationRegistryModel;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Detached station payload validation; no SavedData lifecycle or disk writes. */
public final class StationRegistryPayload {
    static final String WARP_ENERGY = "warp_energy";
    static final String ORBITAL_CLOCK = "orbital_clock";
    /** W44 section 1.1: the first-warp outbox of root 5 with the epoch {@code v1.8.0-station-first-warp}. */
    static final String FIRST_WARP = ManagedSavedDataType.FIRST_STATION_WARP_KEY;

    private StationRegistryPayload() {
    }

    /**
     * Decodes a current (root 5) payload: record 3, reservation 1, the required warp energy list and the
     * required orbital clock, with every epoch at or before the clock.
     */
    public static StationRegistryModel decodeCurrent(CompoundTag source) {
        return decodeCurrentRoot(source).registry();
    }

    /**
     * The current payload, its saved orbital clock (ADR-065 section 5.2.1) and its first-warp outbox (W44 section 1.1),
     * from one validated root.
     */
    static Decoded decodeCurrentRoot(CompoundTag source) {
        Decoded withoutOutbox = decodeControlsRoot(source);
        return new Decoded(withoutOutbox.registry(), withoutOutbox.logicalTick(),
                FirstEventOutbox.decode(source.get(FIRST_WARP), FirstEventKind.FIRST_STATION_WARP));
    }

    /** Root 5 under the pre-W44 contract: records, reservations, balances and a valid clock, no outbox. */
    private static Decoded decodeControlsRoot(CompoundTag source) {
        StationRegistryModel registry = decode(source, StationLimits.STATE_SCHEMA_VERSION, true);
        long logicalTick = decodeClock(requireCompound(source, ORBITAL_CLOCK));
        for (StationState station : registry.stations()) {
            if (station.orbitalControls().epoch() > logicalTick) {
                throw new IllegalArgumentException("A station orbital epoch is ahead of the saved clock");
            }
        }
        return new Decoded(registry, logicalTick, FirstEventOutbox.empty());
    }

    /**
     * W44 section 1.4: the exact pre-release root 5 (epoch {@code v1.8.0-station-orbital-controls}, no outbox) is
     * validated completely under its old contract, including its clock, and gains the EMPTY outbox. Every other field
     * is copied; stamping the new epoch and the new-root budget check are the caller's.
     */
    public static CompoundTag upgradePreReleaseRoot(CompoundTag source) {
        if (source.contains(FIRST_WARP)) {
            throw new IllegalArgumentException("A pre-release station root cannot carry a first-warp outbox");
        }
        decodeControlsRoot(source);
        CompoundTag result = source.copy();
        result.put(FIRST_WARP, FirstEventOutbox.empty().encode(FirstEventKind.FIRST_STATION_WARP));
        return result;
    }

    /**
     * Upgrades an older root's content to root 5. Roots 1 to 3 first take the ADR-044 path to root 4
     * (record 1 becomes record 2 after strict geometry validation, and an empty warp energy list is added);
     * root 4 keeps its balances. Then every record becomes record 3 with the initial orbital controls
     * (distance 4, no rotation, epoch 0, the EAST phase reproducing the stored sun angle) and the root
     * gains {@code orbital_clock} at tick 0 (ADR-065 section 5.2.1). All other original tags are
     * preserved; stamping is the caller's.
     */
    public static CompoundTag upgrade(CompoundTag source, int rootSchema) {
        if (rootSchema < 1 || rootSchema >= StationLimits.CONTROLS_REGISTRY_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Station registry root " + rootSchema + " is not upgradable");
        }
        boolean warpRoot = rootSchema >= StationLimits.WARP_REGISTRY_SCHEMA_VERSION;
        if (!warpRoot && source.contains(WARP_ENERGY)) {
            throw new IllegalArgumentException("Station registry root " + rootSchema + " cannot carry warp energy");
        }
        if (source.contains(ORBITAL_CLOCK)) {
            throw new IllegalArgumentException("Station registry root " + rootSchema + " cannot carry an orbital clock");
        }
        if (source.contains(FIRST_WARP)) {
            throw new IllegalArgumentException("Station registry root " + rootSchema + " cannot carry a first-warp outbox");
        }
        int recordSchema = rootSchema < StationLimits.ORBITAL_REGISTRY_SCHEMA_VERSION
                ? 1 : StationLimits.ORBITAL_STATE_SCHEMA_VERSION;
        decode(source, recordSchema, warpRoot);
        CompoundTag result = source.copy();
        for (Tag raw : requireList(result, "stations")) {
            CompoundTag record = (CompoundTag) raw;
            StationState older = StationNbtCodec.decodeOlderState(record, recordSchema);
            record.putInt("schema_version", StationLimits.STATE_SCHEMA_VERSION);
            record.put(StationNbtCodec.ORBITAL_CONTROLS, StationNbtCodec.encodeControls(
                    StationOrbitalControls.initial(older.environment().solarAngleMilliDegrees())));
        }
        if (!warpRoot) {
            result.put(WARP_ENERGY, new ListTag());
        }
        result.put(ORBITAL_CLOCK, encodeClock(0L));
        // W44 section 1.4: no old root records a warp, so every migration initializes the outbox EMPTY.
        result.put(FIRST_WARP, FirstEventOutbox.empty().encode(FirstEventKind.FIRST_STATION_WARP));
        return result;
    }

    /**
     * Encodes one current root with {@code logicalTick} as its orbital clock and {@code outbox} as its first-warp
     * slot, optionally substituting one station by UUID and, for a relocation, that station's balance. Every encoded
     * epoch must be at or before that clock, and the root must fit the fixed bound.
     */
    static CompoundTag encodeRoot(CompoundTag target, java.util.List<StationState> live,
                                  java.util.List<io.github.sunthemoon.advancedrocketrycommunity.station.model.StationReservation> reservations,
                                  java.util.SortedMap<java.util.UUID, Integer> balances, StationState substitute,
                                  Integer substituteBalance, long logicalTick, FirstEventOutbox outbox) {
        io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator
                .stampCurrent(ManagedSavedDataType.STATIONS, target);
        ListTag stations = new ListTag();
        live.forEach(state -> {
            StationState encoded = substitute != null && substitute.stationId().equals(state.stationId())
                    ? substitute : state;
            if (encoded.orbitalControls().epoch() > logicalTick) {
                throw new IllegalStateException("A station orbital epoch is ahead of the encoded clock");
            }
            stations.add(StationNbtCodec.encodeState(encoded));
        });
        target.put("stations", stations);
        ListTag encodedReservations = new ListTag();
        reservations.forEach(reservation -> encodedReservations.add(StationNbtCodec.encodeReservation(reservation)));
        target.put("reservations", encodedReservations);
        java.util.SortedMap<java.util.UUID, Integer> encodedBalances = new java.util.TreeMap<>(balances);
        if (substitute != null && substituteBalance != null) {
            encodedBalances.remove(substitute.stationId());
            if (substituteBalance > 0) {
                encodedBalances.put(substitute.stationId(), substituteBalance);
            }
        }
        ListTag warpEnergy = new ListTag();
        encodedBalances.forEach((stationId, energy) -> warpEnergy.add(StationNbtCodec.encodeWarpEnergy(stationId, energy)));
        target.put(WARP_ENERGY, warpEnergy);
        target.put(ORBITAL_CLOCK, encodeClock(logicalTick));
        target.put(FIRST_WARP, outbox.encode(FirstEventKind.FIRST_STATION_WARP));
        if (StationNbtSize.uncompressedBytes(target) > StationLimits.MAX_REGISTRY_NBT_BYTES) {
            throw new IllegalStateException("Encoded station registry exceeds the fixed NBT bound");
        }
        return target;
    }

    /** Exactly {@code schema_version} 1 and a nonnegative {@code logical_tick}; at most 128 bytes. */
    static CompoundTag encodeClock(long logicalTick) {
        if (logicalTick < 0L) {
            throw new IllegalArgumentException("Station orbital clock cannot be negative");
        }
        CompoundTag clock = new CompoundTag();
        clock.putInt("schema_version", StationOrbitalClock.SCHEMA_VERSION);
        clock.putLong("logical_tick", logicalTick);
        return clock;
    }

    static long decodeClock(CompoundTag source) {
        if (StationNbtSize.uncompressedBytes(source) > StationOrbitalClock.MAX_NBT_BYTES
                || source.getAllKeys().size() != 2
                || !source.contains("schema_version", Tag.TAG_INT)
                || source.getInt("schema_version") != StationOrbitalClock.SCHEMA_VERSION
                || !source.contains("logical_tick", Tag.TAG_LONG)
                || source.getLong("logical_tick") < 0L) {
            throw new IllegalArgumentException("Station orbital clock is missing, malformed or from the future");
        }
        return source.getLong("logical_tick");
    }

    private static StationRegistryModel decode(CompoundTag source, int recordSchema, boolean warpRoot) {
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
            registry.restoreStation(recordSchema == StationLimits.STATE_SCHEMA_VERSION
                    ? StationNbtCodec.decodeState((CompoundTag) raw)
                    : StationNbtCodec.decodeOlderState((CompoundTag) raw, recordSchema));
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

    private static CompoundTag requireCompound(CompoundTag source, String key) {
        if (!source.contains(key, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Missing station registry compound " + key);
        }
        return source.getCompound(key);
    }

    /** One validated current root: the registry, the saved orbital clock and the first-warp outbox. */
    record Decoded(StationRegistryModel registry, long logicalTick, FirstEventOutbox firstWarp) {
        Decoded {
            Objects.requireNonNull(registry, "registry");
            Objects.requireNonNull(firstWarp, "firstWarp");
        }
    }
}
