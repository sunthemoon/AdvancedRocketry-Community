package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;

/**
 * O2 targets of one Anvil slot. A native rocket that a fluid cargo member names is proved from one bounded decode that
 * yields both projections: the fuel vector and invariant, compared with the frozen O1 choice, and the composite,
 * compared with the composite of that same choice. The decode runs on the IO worker; only the owner thread confirms.
 * Every other endpoint, and every absence, keeps the unchanged generic validator.
 */
final class EscrowSettlementProofs {
    private EscrowSettlementProofs() { }

    record Slot(List<EscrowBarrier.Target> targets, Map<EscrowEndpoint.Key, EscrowCargoReadback> readbacks) {
        /** Owner thread only; a slot without a typed decode has nothing more to confirm. */
        boolean confirmed() { return readbacks.values().stream().allMatch(EscrowCargoReadback::confirmed); }
        /** The held reason after a barrier result, as the ordinary C6 checkpoint reports it. */
        EscrowReason reason(EscrowBarrier.JointResult result) {
            for (var readback : readbacks.values()) {
                var reason = readback.reason();
                if (reason != EscrowReason.NONE) { return reason; }
            }
            return result.confirmed() && !confirmed() ? EscrowReason.BARRIER_UNSTABLE : result.reason();
        }
    }

    /** One physical save proves every frozen reference in the slot of {@code first} together. */
    static Slot slot(EscrowSettlementIntent intent, List<EscrowOperation> members, EscrowEndpoint first,
                     Function<EscrowEndpoint, RocketStructureSnapshot> live) {
        var targets = new ArrayList<EscrowBarrier.Target>(); var readbacks = new LinkedHashMap<EscrowEndpoint.Key, EscrowCargoReadback>();
        for (var candidate : intent.checkpointed()) {
            if (!EscrowBarrier.sameSlot(first, candidate.endpoint())) { continue; }
            var frontier = candidate.missing() ? new EscrowPrefix.State(intent.ledger(), 0,
                    EscrowSettlementTombstones.observationDigest(candidate), List.of()) : candidate.state();
            var cargo = candidate.missing() ? List.<EscrowOperation>of() : fluidCargo(members, candidate.endpoint());
            Predicate<CompoundTag> validator;
            if (cargo.isEmpty()) { validator = EscrowSettlementDisk.validator(candidate, intent.ledger()); }
            else {
                // The caller observed this live rocket equal to the frozen choice, so its snapshot is that choice's content.
                var witness = live.apply(candidate.endpoint());
                var readback = new EscrowCargoReadback(intent.ledger(), EscrowReconciliationObservation.of(candidate, witness.contentHash()),
                        witness, cargo);
                readbacks.put(candidate.endpoint().key(), readback); validator = readback;
            }
            targets.add(new EscrowBarrier.Target(candidate.endpoint(), frontier, validator));
        }
        return new Slot(List.copyOf(targets), Map.copyOf(readbacks));
    }

    /** The fluid cargo members whose rocket side is this endpoint. Item cargo and fills keep their own paths. */
    static List<EscrowOperation> fluidCargo(List<EscrowOperation> members, EscrowEndpoint rocket) {
        return members.stream().filter(op -> op.kind().cargo() && op.kind().fluid()
                && op.nativeBinding().kind() == EscrowSignatureKind.ROCKET_COMPOSITE_V1
                && op.rocket().key().equals(rocket.key())).toList();
    }

    /** R39 before any native settlement action: the first fluid cargo member whose saved Fluid is unavailable, or null. */
    static EscrowOperation unavailableFluid(List<EscrowOperation> members) {
        for (var op : members) {
            if (!op.kind().cargo() || !op.kind().fluid()) { continue; }
            try { op.nativeFluid(); }
            catch (IllegalArgumentException unavailable) { return op; }
        }
        return null;
    }
}
