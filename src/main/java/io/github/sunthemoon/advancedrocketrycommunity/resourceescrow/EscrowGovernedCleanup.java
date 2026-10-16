package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** F8 checked journal removal, followed separately by exact terminal-entry pruning. */
public final class EscrowGovernedCleanup {
    private EscrowGovernedCleanup() { }

    /** Only queues a journal mutation. Its checked acknowledgement is still required for every effect. */
    public static boolean remove(EscrowCoordinator coordinator, RocketTransferSavedData journal,
                                 RocketTransferRecord record, BooleanSupplier boundary) {
        if (!coordinator.governedAvailable(record.logicalRocketId()) || !journal.actionAuthorityAvailable()
                || journal.find(record.transferId()).filter(record::equals).isEmpty()
                || EscrowGovernedDispatch.decide(coordinator.ledger(), record).route() != EscrowGovernedDispatch.Route.CLEANUP
                || !boundary.getAsBoolean()) { return false; }
        journal.remove(record.transferId());
        return true;
    }

    /** An acknowledged terminal root plus checked absence, including after a plain restart. */
    public static boolean absent(EscrowCoordinator coordinator, RocketTransferSavedData journal, EscrowFlightTables.Armed arm) {
        return coordinator.governedAvailable(arm.logical()) && terminal(coordinator.ledger(), arm)
                && journal.actionAuthorityAvailable()
                && EscrowFlightJournal.noTransfer(journal, arm.logical(), arm.transfer(), arm.serial()).getAsBoolean();
    }

    /** Transient acknowledgements stay silent; contradictory checked cleanup needs an operator. */
    public static String blockedReason(EscrowCoordinator coordinator, RocketTransferSavedData journal, EscrowFlightTables.Armed arm) {
        if (!coordinator.governedAvailable(arm.logical()) || !journal.actionAuthorityAvailable()) { return null; }
        if (!terminal(coordinator.ledger(), arm)) { return "GOVERNED_CLEANUP_AUTHORITY_CONFLICT"; }
        return EscrowFlightJournal.noTransfer(journal, arm.logical(), arm.transfer(), arm.serial()).getAsBoolean()
                ? null : "GOVERNED_CLEANUP_JOURNAL_CONFLICT";
    }

    /** No callbacks, candidates, failed writes or UNKNOWN outcomes can waive the absence lease. */
    public static boolean prune(EscrowCoordinator coordinator, RocketTransferSavedData journal, EscrowFlightTables.Armed arm,
                                BooleanSupplier dependenciesReleased, Consumer<Boolean> completion) {
        if (!absent(coordinator, journal, arm) || !dependenciesReleased.getAsBoolean()) { return false; }
        var lease = EscrowFlightJournal.noTransfer(journal, arm.logical(), arm.transfer(), arm.serial());
        return coordinator.flightWrite(arm.logical(), (root, generation) -> {
            EscrowTags.require(terminal(root, arm) && journal.actionAuthorityAvailable() && lease.getAsBoolean()
                    && dependenciesReleased.getAsBoolean(), "F8 lost its exact terminal entry or acknowledged cleanup");
            return EscrowFlightAuthority.prune(root, arm);
        }, lease, committed -> completion.accept(committed && lease.getAsBoolean() && journal.actionAuthorityAvailable()
                && coordinator.ledger().armedFlights().stream().noneMatch(value -> value.equals(arm))));
    }

    static boolean terminal(EscrowLedger root, EscrowFlightTables.Armed arm) {
        if (!root.id().equals(arm.ledger()) || !root.armedFlights().contains(arm)
                || arm.outcome() == EscrowFlightTables.Outcome.ARMED || arm.serial() > root.issuedLaunchSerial()
                || arm.generation() > root.writerGeneration() || EscrowSettlementTombstones.logicalLocked(root, arm.logical())
                || root.floors().stream().anyMatch(floor -> floor.hasUUID("transfer_id") && arm.transfer().equals(floor.getUUID("transfer_id")))
                || root.tombstones().stream().flatMap(tag -> EscrowTags.list(tag, "guards", 64).stream())
                        .map(tag -> EscrowCodec.guard((net.minecraft.nbt.CompoundTag) tag)).anyMatch(guard -> arm.transfer().equals(guard.transfer()))
                || root.records().stream().anyMatch(op -> arm.logical().equals(op.guard().rocket())
                || arm.logical().equals(op.source().logicalRocket()) || arm.logical().equals(op.destination().logicalRocket()))) { return false; }
        var head = root.rocketAuthorities().stream().filter(value -> value.logical().equals(arm.logical())).findFirst().orElse(null);
        if (arm.anchor() == null) { return head == null && !EscrowSettlementTombstones.history(root, arm.logical()); }
        boolean destination = arm.outcome() == EscrowFlightTables.Outcome.HANDED_OFF;
        return head != null && head.anchor().equals(arm.anchor()) && head.releaseGeneration() == arm.releaseGeneration()
                && head.owner().equals(arm.owner()) && head.issued() == arm.serial() && head.completed() == arm.serial()
                && head.outcome() == arm.outcome() && head.physical().equals(destination ? arm.destination() : arm.source())
                && head.epoch() == (destination ? 0 : arm.epoch());
    }
}
