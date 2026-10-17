package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Observes native fields before entity ticks can normalize them. Physical riders have F5's separate checks. */
final class EscrowGovernedPhaseTracking {
    private final Map<UUID, Capture> captures = new HashMap<>();

    boolean allows(RocketEntity rocket) {
        if (rocket == null) { return true; }
        var captured = captures.get(rocket.getUUID());
        if (captured == null) { return true; }
        if (captured.rocket != rocket && captured.rocket.isRemoved()) { captures.remove(rocket.getUUID()); return true; }
        return captured.observe(rocket);
    }

    void capture(RocketEntity rocket, RocketTransferSavedData journal) {
        captures.entrySet().removeIf(entry -> entry.getValue().rocket.isRemoved()
                || journal.findByLogicalRocket(entry.getValue().rocket.assemblyTransactionId().orElseThrow()).isEmpty());
        if (rocket == null || captures.containsKey(rocket.getUUID())) { return; }
        var arm = journal.findByLogicalRocket(rocket.assemblyTransactionId().orElseThrow()).orElseThrow()
                .nativeTransfer().orElseThrow().armed();
        reserve(captures, rocket.getUUID(), () -> new Capture(rocket, arm));
    }

    /** The caller has checked F8 terminal authority and acknowledged journal absence. */
    void completed(EscrowFlightTables.Armed terminal) {
        for (var physical : java.util.List.of(terminal.source(), terminal.destination())) {
            var captured = captures.get(physical);
            if (captured != null && completedCapture(captured.arm, terminal)) { captures.remove(physical, captured); }
        }
    }

    static boolean completedCapture(EscrowFlightTables.Armed captured, EscrowFlightTables.Armed terminal) {
        return terminal.outcome() != EscrowFlightTables.Outcome.ARMED && captured.outcome() == EscrowFlightTables.Outcome.ARMED
                && captured.ledger().equals(terminal.ledger()) && captured.logical().equals(terminal.logical())
                && captured.transfer().equals(terminal.transfer()) && captured.serial() == terminal.serial()
                && captured.source().equals(terminal.source()) && captured.destination().equals(terminal.destination())
                && captured.owner().equals(terminal.owner()) && captured.epoch() == terminal.epoch()
                && captured.generation() == terminal.generation() && captured.binding().equals(terminal.binding())
                && captured.launch().equals(terminal.launch()) && captured.sourceEnvelope().equals(terminal.sourceEnvelope())
                && captured.destinationEnvelope().equals(terminal.destinationEnvelope()) && captured.journal().equals(terminal.journal())
                && java.util.Objects.equals(captured.anchor(), terminal.anchor()) && captured.releaseGeneration() == terminal.releaseGeneration();
    }

    static <T> void reserve(Map<UUID, T> captures, UUID id, java.util.function.Supplier<T> capture) {
        if (captures.containsKey(id)) { return; }
        EscrowTags.require(captures.size() < 2 * RocketFlightLimits.MAX_ACTIVE_TRANSFERS, "F5 native capture capacity exceeded");
        captures.put(id, capture.get());
    }

    void observe(RocketEntity rocket) { var captured = captures.get(rocket.getUUID()); if (captured != null) { captured.observe(rocket); } }
    void follow(RocketEntity rocket, Object flight, net.minecraft.world.phys.Vec3 position) {
        var captured = captures.get(rocket.getUUID());
        if (captured == null) { return; }
        EscrowTags.require(captured.observe(rocket), "Native observation changed before authorized arrival");
        captured.flight = flight; captured.frame.put("pos", EscrowGovernedArrival.position(position));
    }
    void clear() { captures.clear(); }

    private static CompoundTag frame(RocketEntity rocket) {
        return EscrowGovernedEnvelope.frame(rocket);
    }
    private static final class Capture {
        final RocketEntity rocket;
        final EscrowFlightTables.Armed arm;
        Object flight;
        final Object snapshot;
        final RocketEscrowSection wrapper;
        final CompoundTag frame;
        boolean changed;
        Capture(RocketEntity rocket, EscrowFlightTables.Armed arm) {
            this.rocket = rocket; this.arm = arm; flight = rocket.flightData().orElseThrow(); snapshot = rocket.snapshot().orElseThrow();
            wrapper = rocket.escrowSection(); frame = frame(rocket);
        }
        boolean observe(RocketEntity candidate) {
            if (changed) { return false; }
            try {
                if (candidate != rocket || !rocket.operational() || rocket.flightData().orElse(null) != flight
                        || rocket.snapshot().orElse(null) != snapshot || !rocket.escrowSection().equals(wrapper)
                        || !frame.equals(frame(rocket))) { changed = true; }
            } catch (IllegalArgumentException | IllegalStateException invalid) { changed = true; }
            return !changed;
        }
    }
}
