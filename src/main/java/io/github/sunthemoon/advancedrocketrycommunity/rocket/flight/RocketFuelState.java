package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable bounded rocket fuel and durable exactly-once debit history. */
public final class RocketFuelState {
    private final long capacity;
    private final long amount;
    private final List<UUID> committedDebits;

    private RocketFuelState(long capacity, long amount, List<UUID> committedDebits) {
        if (capacity < 0L || capacity > RocketFlightLimits.MAX_FUEL_CAPACITY) {
            throw new IllegalArgumentException("Fuel capacity is outside the v0.6 bound");
        }
        if (amount < 0L || amount > capacity) {
            throw new IllegalArgumentException("Fuel amount must be within capacity");
        }
        Objects.requireNonNull(committedDebits, "committedDebits");
        if (committedDebits.size() > RocketFlightLimits.MAX_COMMITTED_FUEL_DEBITS) {
            throw new IllegalArgumentException("Fuel debit history exceeds the fixed bound");
        }
        ArrayList<UUID> copied = new ArrayList<>(committedDebits.size());
        Set<UUID> unique = new HashSet<>();
        for (UUID transactionId : committedDebits) {
            UUID checked = Objects.requireNonNull(transactionId, "committed debit transaction");
            if (!unique.add(checked)) {
                throw new IllegalArgumentException("Fuel debit history contains a duplicate transaction");
            }
            copied.add(checked);
        }
        this.capacity = capacity;
        this.amount = amount;
        this.committedDebits = List.copyOf(copied);
    }

    public static RocketFuelState empty(long capacity) {
        return new RocketFuelState(capacity, 0L, List.of());
    }

    public static RocketFuelState restore(
            long capacity,
            long amount,
            List<UUID> committedDebits
    ) {
        return new RocketFuelState(capacity, amount, committedDebits);
    }

    public long capacity() {
        return capacity;
    }

    public long amount() {
        return amount;
    }

    public long remainingCapacity() {
        return capacity - amount;
    }

    public List<UUID> committedDebits() {
        return committedDebits;
    }

    public boolean wasDebited(UUID transactionId) {
        return committedDebits.contains(Objects.requireNonNull(transactionId, "transactionId"));
    }

    public RocketFuelMutation fill(long requestedUnits) {
        if (requestedUnits <= 0L) {
            return unchanged(RocketFuelCode.INVALID_AMOUNT);
        }
        if (capacity == 0L) {
            return unchanged(RocketFuelCode.NO_CAPACITY);
        }
        long remaining = remainingCapacity();
        if (remaining == 0L) {
            return unchanged(RocketFuelCode.TANK_FULL);
        }
        long added = Math.min(requestedUnits, remaining);
        return new RocketFuelMutation(
                RocketFuelCode.SUCCESS,
                new RocketFuelState(capacity, amount + added, committedDebits),
                added
        );
    }

    public RocketFuelMutation debit(UUID transactionId, long requestedUnits) {
        Objects.requireNonNull(transactionId, "transactionId");
        if (committedDebits.contains(transactionId)) {
            return unchanged(RocketFuelCode.REQUEST_REPLAYED);
        }
        if (requestedUnits <= 0L || requestedUnits > RocketFlightLimits.MAX_TRAVEL_FUEL) {
            return unchanged(RocketFuelCode.INVALID_AMOUNT);
        }
        if (requestedUnits > amount) {
            return unchanged(RocketFuelCode.INSUFFICIENT_FUEL);
        }
        if (committedDebits.size() >= RocketFlightLimits.MAX_COMMITTED_FUEL_DEBITS) {
            return unchanged(RocketFuelCode.DEBIT_LEDGER_FULL);
        }
        ArrayList<UUID> updatedDebits = new ArrayList<>(committedDebits);
        updatedDebits.add(transactionId);
        return new RocketFuelMutation(
                RocketFuelCode.SUCCESS,
                new RocketFuelState(capacity, amount - requestedUnits, updatedDebits),
                requestedUnits
        );
    }

    private RocketFuelMutation unchanged(RocketFuelCode code) {
        return new RocketFuelMutation(code, this, 0L);
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof RocketFuelState other
                && capacity == other.capacity
                && amount == other.amount
                && committedDebits.equals(other.committedDebits);
    }

    @Override
    public int hashCode() {
        return Objects.hash(capacity, amount, committedDebits);
    }

    @Override
    public String toString() {
        return "RocketFuelState[capacity=" + capacity
                + ", amount=" + amount
                + ", committedDebits=" + committedDebits.size() + "]";
    }
}
