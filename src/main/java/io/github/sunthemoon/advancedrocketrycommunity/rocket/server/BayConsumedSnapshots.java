package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoWorkBudget;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.SatelliteBayRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketSnapshotPreparation;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * W170 D4 and R3: the consumed snapshots S' and D' of one group, computed from T's before snapshots through the runtime's
 * ADR-065 4.1.1 budget (one reconstruction per tick). Each consumed bay root becomes {@code {owner, consumed_op}}; every
 * other block, payload and REJECTED bay stays byte-equal. Only a bay that still holds the receipt's exact package (by
 * its NC1 signature) can be consumed. Server thread only.
 */
final class BayConsumedSnapshots {
    record Result(RocketStructureSnapshot source, RocketStructureSnapshot destination) { }

    /** One computation: both preparations share the budget's reconstruction slot for this tick. */
    static final class Job {
        private final RocketSnapshotPreparation source, destination;
        @Nullable private Result result;

        private Job(RocketSnapshotPreparation source, RocketSnapshotPreparation destination) {
            this.source = source; this.destination = destination;
        }

        /** As much work as this tick's budget allows; true once both snapshots are complete. */
        boolean step(RocketCargoWorkBudget budget, long tick) {
            if (result != null) { return true; }
            if (!budget.claim(tick, this)) { return false; }
            for (RocketSnapshotPreparation preparation : List.of(source, destination)) {
                while (!preparation.done()) {
                    if (!budget.perform(tick, preparation.nextByteBound(), preparation.nextVisits(), false, preparation::step)) {
                        return false;
                    }
                }
            }
            result = new Result(source.result(), destination.result());
            return true;
        }

        Optional<Result> result() { return Optional.ofNullable(result); }
    }

    private BayConsumedSnapshots() { }

    /**
     * The job for one group, or empty when a consumed bay of either snapshot is not a valid bay root that holds exactly
     * the receipt's package (CONSUMPTION_MISMATCH). {@code consumed} maps each bay position to its receipt.
     */
    static Optional<Job> prepare(RocketStructureSnapshot source, RocketStructureSnapshot destination, Map<RocketPosition, BayReceipt> consumed) {
        var sourcePayloads = payloads(source, consumed);
        var destinationPayloads = payloads(destination, consumed);
        if (sourcePayloads == null || destinationPayloads == null) { return Optional.empty(); }
        return Optional.of(new Job(new RocketSnapshotPreparation(source, sourcePayloads),
                new RocketSnapshotPreparation(destination, destinationPayloads)));
    }

    /** Index to consumed payload for every consumed bay, or null on any mismatch; at most 128 block-entity visits. */
    @Nullable
    static Map<Integer, RocketBlockEntityPayload> payloads(RocketStructureSnapshot snapshot, Map<RocketPosition, BayReceipt> consumed) {
        var payloads = new HashMap<Integer, RocketBlockEntityPayload>();
        int visits = 0;
        for (int i = 0; i < snapshot.blocks().size() && payloads.size() < consumed.size(); i++) {
            var block = snapshot.blocks().get(i);
            var payload = block.blockEntityPayload().orElse(null);
            if (payload == null) { continue; }
            if (++visits > BayDeploymentChecks.VISITS) { return null; }
            BayReceipt receipt = consumed.get(block.position());
            if (receipt == null) { continue; }
            if (!SatelliteBayRocketAdapter.ID.equals(payload.adapterId())) { return null; }
            var decoded = SatelliteBayRoot.decode(payload.data());
            var contents = decoded.valid() ? decoded.contents().orElseThrow() : null;
            var pkg = contents == null ? null : contents.packageTag().orElse(null);
            if (pkg == null || !RocketNativeResources.signature(pkg).equals(receipt.packageSig())) { return null; }
            payloads.put(i, new RocketBlockEntityPayload(payload.adapterId(),
                    SatelliteBayRoot.encode(new SatelliteBayRoot.Contents(contents.owner(), null, receipt.op()))));
        }
        return payloads.size() == consumed.size() ? payloads : null;
    }
}
