package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinRoot.Hold;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketReleasePermit;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketReleasePlan;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** J1 accepts only an acknowledged S1 and derives its own passenger-only replacement, never a caller's payload. */
final class RocketLegacyRelease {
    record Mutation(RocketTransferRecord before, RocketTransferRecord after) { }
    private RocketLegacyRelease() { }

    static Mutation prepare(RocketTransferSavedData journal, RocketReleasePermit permit,
                            boolean remainingCrewReady, BooleanSupplier nativeProof) {
        if (permit == null || !permit.valid()) { return null; }
        var batch = permit.batch();
        if (!journal.actionable(batch.rocket()) || !journal.logicalLease(batch.rocket(), batch.transfer(), 0).getAsBoolean()
                || !nativeProof.getAsBoolean()) { return null; }
        var current = journal.findByLogicalRocket(batch.rocket()).orElse(null);
        if (current == null) { return journal.find(batch.transfer()).isEmpty() ? new Mutation(null, null) : null; }
        if (!current.transferId().equals(batch.transfer()) || current.nativeTransfer().isPresent()) { return null; }
        var version = RocketReleasePlan.version(current);
        if (!version.equals(batch.before()) && !version.equals(batch.scrubbed())) { return null; }
        var scrubbed = current.releasePassengers(Set.copyOf(batch.released()));
        if (!RocketReleasePlan.version(scrubbed).equals(batch.scrubbed())) { return null; }
        return new Mutation(current, batch.hold() == Hold.H1 && remainingCrewReady ? null : scrubbed);
    }
}
