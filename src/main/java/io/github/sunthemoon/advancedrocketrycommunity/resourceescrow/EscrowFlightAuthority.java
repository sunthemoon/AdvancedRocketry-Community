package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Pure authority transitions. A candidate is not authority until its exact writer acknowledgement. */
final class EscrowFlightAuthority {
    private EscrowFlightAuthority() { }

    /** Native observations and journal provenance are validated by the server adapter before capture. */
    record Launch(UUID logical, UUID owner, UUID source, RocketEscrowSection wrapper,
                  String signature, String invariant, UUID transfer, UUID destination,
                  String binding, String launch, String sourceEnvelope, String destinationEnvelope,
                  EscrowGuard journal, Set<UUID> committedDebits) {
        Launch {
            Objects.requireNonNull(logical); Objects.requireNonNull(owner); Objects.requireNonNull(source);
            Objects.requireNonNull(wrapper); Objects.requireNonNull(transfer); Objects.requireNonNull(destination);
            Objects.requireNonNull(journal); committedDebits = Set.copyOf(committedDebits);
            EscrowTags.require(committedDebits.size() <= io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits.MAX_COMMITTED_FUEL_DEBITS,
                    "Launch debit history exceeds its native bound");
            for (var digest : List.of(signature, invariant, binding, launch, sourceEnvelope, destinationEnvelope)) {
                EscrowTags.digest(digest);
            }
            EscrowTags.require(!source.equals(destination) && logical.equals(journal.rocket()), "Invalid launch identity");
        }
    }

    /** Preflight runs before checkpointing as well as again at exact generation capture. */
    static EscrowLedger arm(EscrowLedger root, Launch launch, long generation) {
        EscrowTags.require(generation > root.writerGeneration(), "ARMED generation did not advance");
        EscrowTags.require(root.id().equals(launch.wrapper().ledger()) && launch.wrapper().markers().isEmpty(),
                "Launch requires a bound, pruned wrapper");
        EscrowTags.require(root.armedFlights().size() < 64
                && root.armedFlights().stream().noneMatch(value -> value.logical().equals(launch.logical())),
                "Flight authority capacity is held");
        EscrowTags.require(root.records().stream().noneMatch(op -> op.guard().rocket().equals(launch.logical())
                || launch.logical().equals(op.source().logicalRocket()) || launch.logical().equals(op.destination().logicalRocket()))
                && !EscrowSettlementTombstones.logicalLocked(root, launch.logical()), "Logical rocket is held");
        requireFreshTransfer(root, launch.transfer(), launch.committedDebits());
        // This implementation takes F8 cleanup before the next F1. It does not yet implement
        // the optional COMMITTED-predecessor replacement, which needs a separately charged slot.
        EscrowTags.require(launch.journal().transfer() == null, "Launch waits for checked predecessor cleanup");
        var head = head(root, launch.logical());
        if (head != null) {
            EscrowTags.require(head.owner().equals(launch.owner()) && head.physical().equals(launch.source())
                    && head.invariant().equals(launch.invariant()) && launch.wrapper().epoch() >= head.epoch()
                    && (launch.wrapper().epoch() != head.epoch() || head.signature().equals(launch.signature())),
                    "READY source frontier changed");
        } else {
            EscrowTags.require(!EscrowSettlementTombstones.history(root, launch.logical()), "Receipt-backed launch lost its READY head");
        }
        long serial = Math.addExact(root.issuedLaunchSerial(), 1);
        var value = new EscrowFlightTables.Armed(root.id(), launch.logical(), launch.owner(), launch.source(), launch.wrapper().epoch(),
                launch.transfer(), serial, generation, launch.binding(), launch.launch(), launch.sourceEnvelope(), launch.destinationEnvelope(),
                launch.destination(), launch.journal(), head == null ? null : head.anchor(), head == null ? 0 : head.releaseGeneration(),
                EscrowFlightTables.Outcome.ARMED);
        var arms = new ArrayList<>(root.armedFlights()); arms.add(value);
        var heads = root.rocketAuthorities().stream().map(old -> old.logical().equals(launch.logical())
                ? new EscrowFlightTables.Head(old.logical(), old.owner(), old.anchor(), old.releaseGeneration(), old.physical(), old.epoch(),
                old.kind(), old.signature(), old.invariant(), serial, old.completed(), old.outcome()) : old).toList();
        return bounded(root, serial, heads, arms);
    }

    private static void requireFreshTransfer(EscrowLedger root, UUID transfer, Set<UUID> committedDebits) {
        EscrowTags.require(!committedDebits.contains(transfer)
                && root.armedFlights().stream().noneMatch(value -> value.transfer().equals(transfer))
                && root.records().stream().noneMatch(value -> transfer.equals(value.guard().transfer()))
                && root.floors().stream().noneMatch(value -> value.hasUUID("transfer_id") && transfer.equals(value.getUUID("transfer_id")))
                && root.tombstones().stream().flatMap(value -> EscrowTags.list(value, "guards", 64).stream())
                .map(value -> EscrowCodec.guard((net.minecraft.nbt.CompoundTag) value)).noneMatch(value -> transfer.equals(value.transfer())),
                "Transfer intent is protected history");
        // Existing journal records are checked by the adapter, never inferred absent from this root.
    }

    /** Terminal write; F6 joint proofs, or F1's exact source plus checked no-T, remain writer prerequisites. */
    static EscrowLedger finish(EscrowLedger root, EscrowFlightTables.Armed expected, EscrowFlightTables.Outcome outcome,
                              UUID physical, long epoch, String signature, String invariant) {
        EscrowTags.require(outcome == EscrowFlightTables.Outcome.HANDED_OFF || outcome == EscrowFlightTables.Outcome.CANCELLED,
                "Flight outcome is not terminal");
        requireExact(root, expected);
        EscrowTags.require(expected.outcome() == EscrowFlightTables.Outcome.ARMED, "Terminal flight cannot resume");
        EscrowTags.require(outcome == EscrowFlightTables.Outcome.HANDED_OFF
                ? expected.destination().equals(physical) && epoch == 0
                : expected.source().equals(physical) && epoch == expected.epoch(), "Terminal frontier changed");
        EscrowTags.digest(signature); EscrowTags.digest(invariant);
        var next = new EscrowFlightTables.Armed(expected.ledger(), expected.logical(), expected.owner(), expected.source(), expected.epoch(),
                expected.transfer(), expected.serial(), expected.generation(), expected.binding(), expected.launch(), expected.sourceEnvelope(),
                expected.destinationEnvelope(), expected.destination(), expected.journal(), expected.anchor(), expected.releaseGeneration(), outcome);
        var arms = root.armedFlights().stream().map(old -> old.equals(expected) ? next : old).toList();
        var heads = root.rocketAuthorities().stream().map(old -> old.logical().equals(expected.logical())
                ? new EscrowFlightTables.Head(old.logical(), old.owner(), old.anchor(), old.releaseGeneration(), physical, epoch,
                EscrowSignatureKind.FUEL_VECTOR_V1, signature, invariant, expected.serial(), expected.serial(), outcome) : old).toList();
        return bounded(root, root.issuedLaunchSerial(), heads, arms);
    }

    /** F8 only; the caller must retain and recheck the checked no-T journal lease through replacement. */
    static EscrowLedger prune(EscrowLedger root, EscrowFlightTables.Armed expected) {
        requireExact(root, expected);
        EscrowTags.require(expected.outcome() != EscrowFlightTables.Outcome.ARMED, "Active flight cannot be pruned");
        return bounded(root, root.issuedLaunchSerial(), root.rocketAuthorities(),
                root.armedFlights().stream().filter(value -> !value.equals(expected)).toList());
    }

    private static void requireExact(EscrowLedger root, EscrowFlightTables.Armed expected) {
        EscrowTags.require(root.id().equals(expected.ledger()) && root.armedFlights().contains(expected), "Obsolete flight authority");
        var head = head(root, expected.logical());
        EscrowTags.require(expected.anchor() == null ? head == null : head != null && head.owner().equals(expected.owner())
                && head.anchor().equals(expected.anchor()) && head.releaseGeneration() == expected.releaseGeneration(), "Superseded READY anchor");
    }

    private static EscrowFlightTables.Head head(EscrowLedger root, UUID logical) {
        return root.rocketAuthorities().stream().filter(value -> value.logical().equals(logical)).findFirst().orElse(null);
    }

    private static EscrowLedger bounded(EscrowLedger root, long issued, List<EscrowFlightTables.Head> heads, List<EscrowFlightTables.Armed> arms) {
        var next = new EscrowLedger(root.id(), root.issuedThrough(), root.writerGeneration(), root.records(), root.floors(), root.tombstones(), issued, heads, arms);
        EscrowCodec.encode(next); // Include the root, not just the per-entry ceiling, in every reservation.
        return next;
    }
}
