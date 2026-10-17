package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;

/** Checked F7 writer. The live adapter owns F6 capture and invalidation, not this ledger transition. */
final class EscrowGovernedHandoff {
    private EscrowGovernedHandoff() { }

    /**
     * Retained F6 observations. The adapter must invalidate nativeAndPassengerLease synchronously
     * on departure/native change, and observe again before capture. It may not read Minecraft
     * objects from that lease: the ledger writer evaluates it off-thread immediately before replace.
     */
    static final class Proof implements AutoCloseable {
        private final Thread owner = Thread.currentThread();
        private final RocketTransferRecord record;
        private final EscrowFlightTables.Outcome outcome;
        private final CompoundTag survivor;
        private final Map<EscrowEndpoint, EscrowBarrier.Proof> slots;
        private final Supplier<CompoundTag> observeSurvivor;
        private final BooleanSupplier passengersResolved;
        private final BooleanSupplier nativeAndPassengerLease;
        private final BooleanSupplier journalLease;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private boolean claimed;

        Proof(RocketTransferSavedData journal, RocketTransferRecord record, EscrowFlightTables.Outcome outcome,
                CompoundTag survivor, Map<EscrowEndpoint, EscrowBarrier.Proof> slots,
                Supplier<CompoundTag> observeSurvivor, BooleanSupplier passengersResolved,
                BooleanSupplier nativeAndPassengerLease) {
            this.record = record; this.outcome = outcome; this.survivor = survivor.copy();
            this.slots = Map.copyOf(slots);
            this.observeSurvivor = java.util.Objects.requireNonNull(observeSurvivor);
            this.passengersResolved = java.util.Objects.requireNonNull(passengersResolved);
            this.nativeAndPassengerLease = java.util.Objects.requireNonNull(nativeAndPassengerLease);
            EscrowTags.require(EscrowGovernedTerminalDisk.survivor(record, outcome, this.survivor),
                    "F7 requires its complete terminal survivor");
            var expected = EscrowGovernedTerminalDisk.slots(record, outcome);
            EscrowTags.require(this.slots.size() == expected.size() && this.slots.keySet().containsAll(expected),
                    "F7 requires every exact recorded slot once");
            for (var endpoint : expected) {
                var proof = this.slots.get(endpoint);
                var frontier = new EscrowPrefix.State(record.nativeTransfer().orElseThrow().armed().ledger(),
                        endpoint.beforeEpoch(), endpoint.beforeSignature(), List.of());
                EscrowTags.require(proof.frontier().equals(frontier)
                        && proof.capture().chunk().x == endpoint.x() && proof.capture().chunk().z == endpoint.z(),
                        "F7 slot token has a different frontier or position");
            }
            EscrowTags.require(journal.actionAuthorityAvailable(record.logicalRocketId())
                    && journal.find(record.transferId()).filter(record::equals).isPresent(),
                    "F7 requires its exact checked journal record");
            // R17 item 3: L's exact record instance and guard; another rocket's journal change does not revoke it.
            journalLease = journal.logicalLease(record.logicalRocketId(), record.transferId(),
                    record.nativeTransfer().orElseThrow().armed().serial());
        }

        /** No entity, player, registry, coordinator or mutable journal read is allowed here. */
        boolean valid() {
            return active.get() && journalLease.getAsBoolean() && nativeAndPassengerLease.getAsBoolean()
                    && slots.values().stream().allMatch(EscrowBarrier.Proof::valid);
        }

        boolean observe() {
            EscrowTags.require(Thread.currentThread() == owner, "F7 observation requires its owner thread");
            if (!valid()) { return false; }
            try {
                if (passengersResolved.getAsBoolean() && EscrowGovernedEnvelope.same(survivor, observeSurvivor.get())) { return valid(); }
            } catch (IllegalArgumentException | IllegalStateException | ArithmeticException invalid) {
                // A malformed live observation refuses this write; it is not a server-tick exception.
            }
            active.set(false);
            return false;
        }

        @Override public void close() {
            EscrowTags.require(Thread.currentThread() == owner, "F7 proof release requires its owner thread");
            active.set(false); slots.values().forEach(EscrowBarrier.Proof::release);
        }
    }

    /**
     * Takes ownership only when queued. UNKNOWN retains the proof and exact candidate in the writer.
     * A true callback names an acknowledged terminal root and still-exact journal, never a candidate.
     * It does not itself change an entity, release tickets, remove T or prune its charged slot.
     */
    static boolean request(EscrowCoordinator coordinator, RocketTransferSavedData journal, Proof proof,
                           Consumer<Boolean> completion) {
        java.util.Objects.requireNonNull(completion);
        var record = proof.record;
        if (proof.claimed || !coordinator.governedAvailable(record.logicalRocketId()) || !proof.observe()
                || !journal.actionAuthorityAvailable(record.logicalRocketId())
                || EscrowGovernedDispatch.decide(coordinator.ledger(), record).route() != EscrowGovernedDispatch.Route.ACTIVE) {
            return false;
        }
        var arm = record.nativeTransfer().orElseThrow().armed();
        var wrapper = RocketEscrowSection.decode(proof.survivor.getCompound("wrapper"));
        var signature = EscrowGovernedArrivalState.flight(proof.survivor).fuel().resourceSignature();
        boolean destination = proof.outcome == EscrowFlightTables.Outcome.HANDED_OFF;
        var physical = destination ? arm.destination() : arm.source();
        var invariant = EscrowInvariant.rocket(physical, arm.logical(), arm.owner(), EscrowAdapterIds.ROCKET,
                destination ? record.destinationSnapshot() : record.sourceSnapshot());
        boolean queued = coordinator.flightWrite(arm.logical(), (root, generation) -> {
            EscrowTags.require(proof.observe() && journal.actionAuthorityAvailable(arm.logical())
                    && EscrowGovernedDispatch.decide(root, record).route() == EscrowGovernedDispatch.Route.ACTIVE,
                    "F7 lost its joint proof or checked active authority at capture");
            return EscrowFlightAuthority.finish(root, arm, proof.outcome, physical, wrapper.epoch(), signature, invariant);
        }, proof::valid, committed -> {
            proof.close();
            boolean exact = committed && journal.actionAuthorityAvailable(arm.logical()) && proof.journalLease.getAsBoolean()
                    && journal.find(record.transferId()).filter(record::equals).isPresent()
                    && EscrowGovernedDispatch.decide(coordinator.ledger(), record).route() == EscrowGovernedDispatch.Route.CLEANUP
                    && coordinator.ledger().armedFlights().stream().anyMatch(terminal -> terminal.transfer().equals(arm.transfer())
                            && terminal.serial() == arm.serial() && terminal.outcome() == proof.outcome);
            completion.accept(exact);
        });
        if (queued) { proof.claimed = true; }
        return queued;
    }
}
