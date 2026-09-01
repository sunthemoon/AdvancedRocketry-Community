package io.github.sunthemoon.advancedrocketrycommunity.station.model;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Synchronized pure registry with deterministic allocation and indexed lookup. */
public final class StationRegistryModel {
    private final Map<UUID, StationState> stations = new LinkedHashMap<>();
    private final Map<UUID, StationReservation> reservations = new LinkedHashMap<>();
    private final Map<StationGridCell, UUID> occupiedCells = new LinkedHashMap<>();

    public synchronized StationReservation reserve(
            UUID stationId,
            UUID ownerId,
            String name,
            ResourceLocation orbitBody,
            long createdAtGameTime
    ) {
        Objects.requireNonNull(stationId, "stationId");
        if (stations.containsKey(stationId) || reservations.containsKey(stationId)) {
            throw new IllegalArgumentException("Station UUID is already allocated");
        }
        if (stations.size() >= StationLimits.MAX_STATIONS) {
            throw new IllegalStateException("Station registry is full");
        }
        if (reservations.size() >= StationLimits.MAX_RESERVATIONS) {
            throw new IllegalStateException("Station reservation limit is reached");
        }
        StationGridCell cell = nextAvailableCell();
        StationReservation reservation = new StationReservation(
                stationId,
                ownerId,
                name,
                cell,
                orbitBody,
                createdAtGameTime
        );
        reservations.put(stationId, reservation);
        occupiedCells.put(cell, stationId);
        return reservation;
    }

    public synchronized StationState commit(UUID stationId) {
        StationReservation reservation = reservations.remove(Objects.requireNonNull(stationId, "stationId"));
        if (reservation == null) {
            throw new IllegalArgumentException("Station reservation is missing");
        }
        StationState state = StationState.fromReservation(reservation);
        if (stations.put(stationId, state) != null) {
            reservations.put(stationId, reservation);
            throw new IllegalStateException("Station UUID changed authority during commit");
        }
        return state;
    }

    public synchronized boolean release(UUID stationId) {
        StationReservation removed = reservations.remove(Objects.requireNonNull(stationId, "stationId"));
        if (removed == null) {
            return false;
        }
        occupiedCells.remove(removed.cell(), stationId);
        return true;
    }

    public synchronized Optional<StationState> delete(UUID stationId) {
        StationState removed = stations.remove(Objects.requireNonNull(stationId, "stationId"));
        if (removed != null) {
            occupiedCells.remove(removed.cell(), stationId);
        }
        return Optional.ofNullable(removed);
    }

    public synchronized StationState addMember(UUID stationId, UUID memberId) {
        return update(requireStation(stationId).withMember(memberId));
    }

    public synchronized StationState removeMember(UUID stationId, UUID memberId) {
        return update(requireStation(stationId).withoutMember(memberId));
    }

    public synchronized StationState invite(UUID stationId, UUID playerId) {
        return update(requireStation(stationId).invite(playerId));
    }

    public synchronized StationState acceptInvitation(UUID stationId, UUID playerId) {
        return update(requireStation(stationId).acceptInvitation(playerId));
    }

    public synchronized StationState declineInvitation(UUID stationId, UUID playerId) {
        return update(requireStation(stationId).declineInvitation(playerId));
    }

    public synchronized StationState transferOwnership(UUID stationId, UUID ownerId) {
        return update(requireStation(stationId).transferOwnership(ownerId));
    }

    public synchronized Optional<StationState> find(UUID stationId) {
        return Optional.ofNullable(stations.get(Objects.requireNonNull(stationId, "stationId")));
    }

    public synchronized Optional<StationState> findAt(int x, int z) {
        long rawCellX = Math.floorDiv((long) x + StationLimits.GRID_SPACING / 2L,
                StationLimits.GRID_SPACING);
        long rawCellZ = Math.floorDiv((long) z + StationLimits.GRID_SPACING / 2L,
                StationLimits.GRID_SPACING);
        if (Math.abs(rawCellX) > StationLimits.MAX_CELL_COORDINATE
                || Math.abs(rawCellZ) > StationLimits.MAX_CELL_COORDINATE) {
            return Optional.empty();
        }
        int cellX = Math.toIntExact(rawCellX);
        int cellZ = Math.toIntExact(rawCellZ);
        UUID stationId = occupiedCells.get(new StationGridCell(cellX, cellZ));
        StationState state = stationId == null ? null : stations.get(stationId);
        return state != null && state.region().contains(x, z)
                ? Optional.of(state)
                : Optional.empty();
    }

    public synchronized List<StationState> stations() {
        return stations.values().stream()
                .sorted(Comparator.comparing(StationState::stationId))
                .toList();
    }

    public synchronized List<StationReservation> reservations() {
        return reservations.values().stream()
                .sorted(Comparator.comparing(StationReservation::stationId))
                .toList();
    }

    public synchronized long ownedBy(UUID ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        return stations.values().stream().filter(state -> state.ownerId().equals(ownerId)).count()
                + reservations.values().stream().filter(state -> state.ownerId().equals(ownerId)).count();
    }

    public synchronized void restoreStation(StationState state) {
        Objects.requireNonNull(state, "state");
        restoreIdentity(state.stationId(), state.cell());
        stations.put(state.stationId(), state);
    }

    public synchronized void restoreReservation(StationReservation reservation) {
        Objects.requireNonNull(reservation, "reservation");
        if (reservations.size() >= StationLimits.MAX_RESERVATIONS) {
            throw new IllegalArgumentException("Station reservation list exceeds the fixed bound");
        }
        restoreIdentity(reservation.stationId(), reservation.cell());
        reservations.put(reservation.stationId(), reservation);
    }

    private StationState requireStation(UUID stationId) {
        StationState state = stations.get(Objects.requireNonNull(stationId, "stationId"));
        if (state == null) {
            throw new IllegalArgumentException("Station is missing");
        }
        return state;
    }

    private StationState update(StationState state) {
        stations.put(state.stationId(), state);
        return state;
    }

    private void restoreIdentity(UUID stationId, StationGridCell cell) {
        if (stations.size() >= StationLimits.MAX_STATIONS) {
            throw new IllegalArgumentException("Station registry exceeds the fixed bound");
        }
        if (stations.containsKey(stationId) || reservations.containsKey(stationId)) {
            throw new IllegalArgumentException("Station registry contains a duplicate UUID");
        }
        UUID previous = occupiedCells.putIfAbsent(cell, stationId);
        if (previous != null) {
            throw new IllegalArgumentException("Station registry contains a duplicate cell");
        }
    }

    private StationGridCell nextAvailableCell() {
        int attempts = StationLimits.MAX_STATIONS + StationLimits.MAX_RESERVATIONS;
        for (int index = 0; index < attempts; index++) {
            StationGridCell candidate = spiralCell(index);
            if (!occupiedCells.containsKey(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("No station cell is available inside the fixed bound");
    }

    /** Maps a zero-based allocation index onto an outward square spiral. */
    public static StationGridCell spiralCell(int index) {
        if (index < 0 || index >= StationLimits.MAX_STATIONS + StationLimits.MAX_RESERVATIONS) {
            throw new IllegalArgumentException("Station allocation index is outside the fixed bound");
        }
        long n = (long) index + 1L;
        long layer = (long) Math.ceil((Math.sqrt(n) - 1.0D) / 2.0D);
        long side = 2L * layer + 1L;
        long maximum = side * side;
        long leg = side - 1L;
        long x;
        long z;
        if (n >= maximum - leg) {
            x = layer - (maximum - n);
            z = -layer;
        } else {
            maximum -= leg;
            if (n >= maximum - leg) {
                x = -layer;
                z = -layer + (maximum - n);
            } else {
                maximum -= leg;
                if (n >= maximum - leg) {
                    x = -layer + (maximum - n);
                    z = layer;
                } else {
                    x = layer;
                    z = layer - (maximum - n - leg);
                }
            }
        }
        return new StationGridCell(Math.toIntExact(x), Math.toIntExact(z));
    }
}
