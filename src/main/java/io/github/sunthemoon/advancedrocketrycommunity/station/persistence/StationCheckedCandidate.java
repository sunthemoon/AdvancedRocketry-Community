package io.github.sunthemoon.advancedrocketrycommunity.station.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventOutbox;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationReservation;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import java.util.List;
import java.util.SortedMap;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/**
 * The expected content of one checked station candidate and its validation (W44 sections 1.2, 1.3 and 10), extracted
 * from the registry adapter. A candidate must decode as a strict CURRENT root to exactly the expected stations,
 * reservations, balances, outbox and clock sample; anything else is refused before any replacement.
 */
record StationCheckedCandidate(List<StationState> stations, List<StationReservation> reservations,
                               SortedMap<UUID, Integer> balances, FirstEventOutbox firstWarp, long sample) {
    StationCheckedCandidate {
        stations = List.copyOf(stations);
        reservations = List.copyOf(reservations);
        balances = new java.util.TreeMap<>(balances);
    }

    /** A named update: the replacement station substituted and, for a relocation, its debited balance. */
    static StationCheckedCandidate named(List<StationState> live, List<StationReservation> reservations,
                                         SortedMap<UUID, Integer> balances, StationState replacement, Integer debited,
                                         FirstEventOutbox firstWarp, long sample) {
        UUID stationId = replacement.stationId();
        List<StationState> expected = live.stream()
                .map(state -> state.stationId().equals(stationId) ? replacement : state)
                .toList();
        SortedMap<UUID, Integer> expectedBalances = new java.util.TreeMap<>(balances);
        if (debited != null && debited > 0) {
            expectedBalances.put(stationId, debited);
        } else if (debited != null) {
            expectedBalances.remove(stationId);
        }
        return new StationCheckedCandidate(expected, reservations, expectedBalances, firstWarp, sample);
    }

    void require(CompoundTag candidate) {
        SavedDataSchemaMigrator.MigrationResult migration = SavedDataSchemaMigrator.migrate(
                ManagedSavedDataType.STATIONS, candidate.copy());
        if (migration.status() != SavedDataSchemaMigrator.MigrationStatus.CURRENT) {
            throw new IllegalStateException("Candidate station registry failed validation");
        }
        StationRegistryPayload.Decoded decoded = StationRegistryPayload.decodeCurrentRoot(migration.payload());
        if (!decoded.registry().stations().equals(stations)
                || !decoded.registry().reservations().equals(reservations)
                || !decoded.registry().warpEnergyBalances().equals(balances)
                || !decoded.firstWarp().equals(firstWarp)
                || decoded.logicalTick() != sample) {
            throw new IllegalStateException("Candidate station registry failed validation");
        }
    }
}
