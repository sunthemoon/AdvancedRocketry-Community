package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.SatelliteBayRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** W196's immutable release authority. It never registers, consumes, writes or accepts a hash outside its group. */
final class BayReleasedAuthority {
    private final RocketTransferRecord base;
    private final List<BayReceipt> receipts;

    private BayReleasedAuthority(RocketTransferRecord base, List<BayReceipt> receipts) {
        this.base = base; this.receipts = List.copyOf(receipts);
    }

    /** Only a coherent unresolved group; every REGISTERED member has already been consumed at both ends. */
    static Optional<BayReleasedAuthority> capture(RocketTransferRecord record, List<BayReceipt> receipts,
                                                 boolean registryBlocked) {
        if (record == null || record.nativeTransfer().isPresent() || receipts.isEmpty()
                || receipts.size() > BayClaim.BAYS_PER_ROCKET) { return Optional.empty(); }
        boolean released = receipts.stream().allMatch(BayReceipt::released);
        if (!released && (record.phase() != RocketTransferPhase.PREPARED
                || receipts.stream().anyMatch(BayReceipt::released)
                || !registryBlocked && receipts.stream().noneMatch(r -> r.phase() == BayReceipt.Phase.REPAIR))) {
            return Optional.empty();
        }
        var positions = new HashSet<RocketPosition>();
        var operations = new HashSet<UUID>();
        String beforeSource = receipts.get(0).srcBefore(), beforeDestination = receipts.get(0).dstBefore();
        String afterSource = null, afterDestination = null;
        for (BayReceipt receipt : receipts) {
            if (!receipt.pending() || !receipt.rocket().equals(record.logicalRocketId())
                    || !receipt.transfer().equals(record.transferId()) || !receipt.owner().equals(record.ownerId())
                    || !positions.add(receipt.bay()) || !operations.add(receipt.op())
                    || !receipt.srcBefore().equals(beforeSource) || !receipt.dstBefore().equals(beforeDestination)) {
                return Optional.empty();
            }
            if (receipt.phase() == BayReceipt.Phase.REGISTERED) {
                if (afterSource != null && (!afterSource.equals(receipt.srcAfter()) || !afterDestination.equals(receipt.dstAfter()))) {
                    return Optional.empty();
                }
                afterSource = receipt.srcAfter(); afterDestination = receipt.dstAfter();
            }
        }
        if (!record.sourceSnapshot().contentHash().equals(afterSource == null ? beforeSource : afterSource)
                || !record.destinationSnapshot().contentHash().equals(afterDestination == null ? beforeDestination : afterDestination)
                || !payloads(record.sourceSnapshot(), receipts) || !payloads(record.destinationSnapshot(), receipts)) {
            return Optional.empty();
        }
        return Optional.of(new BayReleasedAuthority(record, receipts));
    }

    /** The existing 128-payload bound, no world lookup. Unresolved packages and REGISTERED markers must match exactly. */
    static boolean payloads(RocketStructureSnapshot snapshot, List<BayReceipt> receipts) {
        return payloads(snapshot, receipts, true);
    }

    static boolean payloads(RocketStructureSnapshot snapshot, List<BayReceipt> receipts, boolean consumed) {
        Map<RocketPosition, BayReceipt> remaining = new HashMap<>();
        for (var receipt : receipts) { if (remaining.put(receipt.bay(), receipt) != null) { return false; } }
        int visits = 0;
        for (var block : snapshot.blocks()) {
            var payload = block.blockEntityPayload().orElse(null);
            if (payload == null) { continue; }
            if (++visits > BayDeploymentChecks.VISITS) { return false; }
            var receipt = remaining.remove(block.position());
            if (receipt == null) { continue; }
            if (!SatelliteBayRocketAdapter.ID.equals(payload.adapterId())) { return false; }
            var decoded = SatelliteBayRoot.decode(payload.data());
            var contents = decoded.valid() ? decoded.contents().orElseThrow() : null;
            if (contents == null || !contents.owner().equals(receipt.owner())) { return false; }
            if (consumed && receipt.phase() == BayReceipt.Phase.REGISTERED) {
                if (contents.packageTag().isPresent() || contents.consumed().filter(receipt.op()::equals).isEmpty()) { return false; }
            } else if (contents.consumed().isPresent() || contents.packageTag()
                    .filter(pkg -> RocketNativeResources.signature(pkg).equals(receipt.packageSig())).isEmpty()) {
                return false;
            }
        }
        return remaining.isEmpty();
    }

    List<BayReceipt> receipts() { return receipts; }
    RocketTransferRecord base() { return base; }

    /** The checksum binds all immutable authority; explicitly retain end IDs and coordinates as defence in depth. */
    boolean matches(RocketTransferRecord record) {
        return record != null && record.nativeTransfer().isEmpty() && record.schemaVersion() == base.schemaVersion()
                && record.transferId().equals(base.transferId()) && record.logicalRocketId().equals(base.logicalRocketId())
                && record.ownerId().equals(base.ownerId()) && record.sourceEntityId().equals(base.sourceEntityId())
                && record.checksum().equals(base.checksum())
                && sameSnapshot(record.sourceSnapshot(), base.sourceSnapshot())
                && sameSnapshot(record.destinationSnapshot(), base.destinationSnapshot());
    }

    private static boolean sameSnapshot(RocketStructureSnapshot a, RocketStructureSnapshot b) {
        return a.snapshotId().equals(b.snapshotId()) && a.contentHash().equals(b.contentHash())
                && a.sourceDimension().equals(b.sourceDimension()) && a.sourceOrigin().equals(b.sourceOrigin());
    }

    /** No skipped/reversed phase, destination rebind, source settlement, passenger rewrite or snapshot replacement. */
    boolean successor(RocketTransferRecord old, RocketTransferRecord next, RocketTransferBayRecoveryGate.Action action) {
        if (!matches(old) || !matches(next) || next.phase().ordinal() != old.phase().ordinal() + 1) { return false; }
        if (old.phase() == RocketTransferPhase.PREPARED) {
            return action == RocketTransferBayRecoveryGate.Action.SPAWN_DESTINATION && old.destinationEntityId().isEmpty()
                    && next.destinationEntityId().isPresent() && !next.destinationEntityId().get().equals(old.sourceEntityId());
        }
        return action == RocketTransferBayRecoveryGate.Action.TICK_SOURCE
                && old.destinationEntityId().equals(next.destinationEntityId());
    }
}
