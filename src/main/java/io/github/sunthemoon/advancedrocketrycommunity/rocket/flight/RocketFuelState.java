package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketFuelBank;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsionFuel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;

/** Immutable bounded rocket fuel and durable exactly-once debit history. */
public final class RocketFuelState {
    private final long capacity;
    private final long amount;
    private final List<UUID> committedDebits;
    private final RocketPropulsionFuel propulsion;

    private RocketFuelState(long capacity, long amount, List<UUID> committedDebits) {
        this(capacity, amount, committedDebits, null);
    }

    private RocketFuelState(long capacity, long amount, List<UUID> committedDebits, RocketPropulsionFuel propulsion) {
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
        this.propulsion = propulsion;
        if (propulsion != null && (capacity != propulsion.descriptor().primaryCapacity()
                || amount != propulsion.primary().amount())) { throw new IllegalArgumentException("Native fuel projection mismatch"); }
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

    public static RocketFuelState empty(RocketPropulsion descriptor) {
        return restorePropulsion(RocketPropulsionFuel.empty(descriptor), List.of());
    }
    public static RocketFuelState restorePropulsion(RocketPropulsionFuel propulsion, List<UUID> debits) {
        return new RocketFuelState(propulsion.descriptor().primaryCapacity(), propulsion.primary().amount(), debits, propulsion);
    }
    public java.util.Optional<RocketPropulsionFuel> propulsion() { return java.util.Optional.ofNullable(propulsion); }
    public long totalAmount() { return propulsion == null ? amount : propulsion.totalAmount(); }
    public boolean gravityAffectsFuels() { return propulsion == null || propulsion.descriptor().settings().gravityAffectsFuels(); }
    public boolean requiresFuel() { return propulsion == null || propulsion.descriptor().settings().rocketsRequireFuel(); }
    /** Readiness only; the planner still checks the complete debit and every flight authority. */
    public boolean availableForLaunch() { return amount > 0 || !requiresFuel(); }
    public String summary() { return propulsion == null ? "ABSTRACT: " + amount : propulsion.summary(); }
    public String resourceSignature() {
        if (propulsion == null) { throw new IllegalStateException("Legacy fuel has no native vector"); }
        return propulsion.resourceSignature();
    }
    public RocketFuelState withFluidBank(boolean oxidizer, ResourceLocation id, CompoundTag tag, long amount) {
        if (propulsion == null || propulsion.descriptor().mode() == RocketPropulsion.Mode.ABSTRACT
                || oxidizer && propulsion.descriptor().mode() != RocketPropulsion.Mode.BIPROPELLANT
                || !oxidizer && propulsion.primary().branch() == RocketFuelBank.Branch.ABSTRACT) {
            throw new IllegalArgumentException("This bank cannot hold Fluid");
        }
        var bank = RocketFuelBank.fluid(id, tag, amount);
        return restorePropulsion(propulsion.updated(oxidizer ? propulsion.primary() : bank,
                oxidizer ? bank : propulsion.oxidizer()), committedDebits);
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
        if (propulsion != null && (!propulsion.descriptor().acceptsAbstract()
                || propulsion.primary().branch() == RocketFuelBank.Branch.FLUID)) {
            return unchanged(RocketFuelCode.INVALID_AMOUNT);
        }
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
                propulsion == null ? new RocketFuelState(capacity, amount + added, committedDebits)
                        : restorePropulsion(propulsion.updated(RocketFuelBank.abstractUnits(amount + added),
                                propulsion.oxidizer()), committedDebits),
                added
        );
    }

    public RocketFuelMutation debit(UUID transactionId, long requestedUnits) {
        if (propulsion != null && (propulsion.primary().branch() == RocketFuelBank.Branch.FLUID
                || !propulsion.descriptor().acceptsAbstract())) { return unchanged(RocketFuelCode.INVALID_AMOUNT); }
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
                propulsion == null ? new RocketFuelState(capacity, amount - requestedUnits, updatedDebits)
                        : restorePropulsion(propulsion.updated(RocketFuelBank.abstractUnits(amount - requestedUnits),
                                propulsion.oxidizer()), updatedDebits),
                requestedUnits
        );
    }

    public RocketFuelMutation debit(RocketFlightPlan plan) {
        var vector = plan.propulsionDebit();
        if (vector == null) { return debit(plan.requestId(), plan.requiredFuel()); }
        if (wasDebited(plan.requestId())) { return unchanged(RocketFuelCode.REQUEST_REPLAYED); }
        if (propulsion == null || vector.revision() != propulsion.revision()
                || !vector.resourceSignature().equals(resourceSignature())) { return unchanged(RocketFuelCode.INVALID_AMOUNT); }
        if (committedDebits.size() >= RocketFlightLimits.MAX_COMMITTED_FUEL_DEBITS) {
            return unchanged(RocketFuelCode.DEBIT_LEDGER_FULL);
        }
        if (vector.primary() > amount || vector.oxidizer() > propulsion.oxidizer().amount()) {
            return unchanged(RocketFuelCode.INSUFFICIENT_FUEL);
        }
        var debits = new ArrayList<>(committedDebits); debits.add(plan.requestId());
        var updated = propulsion.updated(propulsion.primary().withAmount(amount - vector.primary()),
                propulsion.oxidizer().withAmount(propulsion.oxidizer().amount() - vector.oxidizer()));
        return new RocketFuelMutation(RocketFuelCode.SUCCESS, restorePropulsion(updated, debits), vector.primary());
    }

    private RocketFuelMutation unchanged(RocketFuelCode code) {
        return new RocketFuelMutation(code, this, 0L);
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof RocketFuelState other
                && capacity == other.capacity
                && amount == other.amount
                && committedDebits.equals(other.committedDebits) && Objects.equals(propulsion, other.propulsion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(capacity, amount, committedDebits, propulsion);
    }

    @Override
    public String toString() {
        return "RocketFuelState[capacity=" + capacity
                + ", amount=" + amount
                + ", committedDebits=" + committedDebits.size() + "]";
    }
}
