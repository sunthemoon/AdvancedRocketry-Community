package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Immutable UUID/seat assignment with an explicit v0.6 multiplayer bound. */
public final class RocketPassengerManifest {
    private final int seatCapacity;
    private final List<RocketPassengerSeat> assignments;

    private RocketPassengerManifest(int seatCapacity, List<RocketPassengerSeat> assignments) {
        if (seatCapacity < 0 || seatCapacity > RocketFlightLimits.MAX_PASSENGERS) {
            throw new IllegalArgumentException("Passenger capacity is outside the fixed limit");
        }
        Objects.requireNonNull(assignments, "assignments");
        if (assignments.size() > seatCapacity) {
            throw new IllegalArgumentException("Passenger assignments exceed seat capacity");
        }
        ArrayList<RocketPassengerSeat> sorted = new ArrayList<>(assignments);
        sorted.sort(Comparator.comparingInt(RocketPassengerSeat::seatIndex));
        Set<UUID> passengers = new HashSet<>();
        Set<Integer> seats = new HashSet<>();
        for (RocketPassengerSeat assignment : sorted) {
            Objects.requireNonNull(assignment, "passenger assignment");
            if (assignment.seatIndex() >= seatCapacity) {
                throw new IllegalArgumentException("Passenger assignment uses a missing seat");
            }
            if (!passengers.add(assignment.passengerId()) || !seats.add(assignment.seatIndex())) {
                throw new IllegalArgumentException("Passenger manifest contains a duplicate UUID or seat");
            }
        }
        this.seatCapacity = seatCapacity;
        this.assignments = List.copyOf(sorted);
    }

    public static RocketPassengerManifest empty(int declaredSeats) {
        return new RocketPassengerManifest(effectiveCapacity(declaredSeats), List.of());
    }

    public static RocketPassengerManifest restore(
            int seatCapacity,
            List<RocketPassengerSeat> assignments
    ) {
        return new RocketPassengerManifest(seatCapacity, assignments);
    }

    public int seatCapacity() {
        return seatCapacity;
    }

    public List<RocketPassengerSeat> assignments() {
        return assignments;
    }

    public Optional<RocketPassengerSeat> assignment(UUID passengerId) {
        Objects.requireNonNull(passengerId, "passengerId");
        return assignments.stream()
                .filter(assignment -> assignment.passengerId().equals(passengerId))
                .findFirst();
    }

    public Optional<RocketPassengerManifest> assign(UUID passengerId) {
        Objects.requireNonNull(passengerId, "passengerId");
        if (assignment(passengerId).isPresent()) {
            return Optional.of(this);
        }
        boolean[] occupied = new boolean[seatCapacity];
        assignments.forEach(assignment -> occupied[assignment.seatIndex()] = true);
        for (int seat = 0; seat < occupied.length; seat++) {
            if (!occupied[seat]) {
                ArrayList<RocketPassengerSeat> updated = new ArrayList<>(assignments);
                updated.add(new RocketPassengerSeat(passengerId, seat));
                return Optional.of(new RocketPassengerManifest(seatCapacity, updated));
            }
        }
        return Optional.empty();
    }

    public RocketPassengerManifest remove(UUID passengerId) {
        Objects.requireNonNull(passengerId, "passengerId");
        List<RocketPassengerSeat> updated = assignments.stream()
                .filter(assignment -> !assignment.passengerId().equals(passengerId))
                .toList();
        return updated.size() == assignments.size()
                ? this
                : new RocketPassengerManifest(seatCapacity, updated);
    }

    public boolean full() {
        return assignments.size() >= seatCapacity;
    }

    private static int effectiveCapacity(int declaredSeats) {
        if (declaredSeats < 0) {
            throw new IllegalArgumentException("Declared rocket seats cannot be negative");
        }
        return Math.min(declaredSeats, RocketFlightLimits.MAX_PASSENGERS);
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof RocketPassengerManifest other
                && seatCapacity == other.seatCapacity
                && assignments.equals(other.assignments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(seatCapacity, assignments);
    }
}
