package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** Bay-first classification and final-boundary dispatch, owned by one transfer service. */
final class RocketTransferBayDispatch {
    private final Set<UUID> live;
    private final Set<UUID> settled;
    private final Set<UUID> claimedTransfers = new HashSet<>();
    private final Map<UUID, RocketTransferBayRecoveryGate.Claim> claims = new HashMap<>();
    private final Map<UUID, UUID> readySources = new HashMap<>();
    private final Set<UUID> released = new HashSet<>();
    private final RocketTransferFences fences;
    private RocketTransferBayRecoveryGate gate = RocketTransferBayRecoveryGate.UNCLAIMED;
    private boolean installed;

    RocketTransferBayDispatch(Set<UUID> live, Set<UUID> settled, RocketTransferFences fences) {
        this.live = live; this.settled = settled; this.fences = fences;
    }
    void install(RocketTransferBayRecoveryGate gate) {
        if (installed) { throw new IllegalStateException("Bay recovery gate already installed for this service"); }
        this.gate = Objects.requireNonNull(gate); installed = true;
    }
    Set<UUID> ids() { return Set.copyOf(claimedTransfers); }
    Set<UUID> excluded() { var ids = new HashSet<>(fences.ids()); ids.addAll(claimedTransfers); return ids; }
    Set<UUID> moving() { var ids = new HashSet<>(live); ids.addAll(excluded()); return ids; }

    void prune(RocketTransferSavedData journal) {
        if (!journal.clean()) { return; }
        var present = journal.entries().stream().map(RocketTransferRecord::transferId)
                .collect(java.util.stream.Collectors.toSet());
        claimedTransfers.retainAll(present); claims.keySet().retainAll(present);
        readySources.keySet().retainAll(present); released.retainAll(present);
    }
    /** true means ordinary fence classification may run. Handoffs are applied only here. */
    boolean classify(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record) {
        var claim = Objects.requireNonNull(gate.claim(server, journal, record));
        UUID id = record.transferId();
        if (record.phase().destinationAuthoritative()) { readySources.remove(id); }
        switch (claim.kind()) {
            case HELD -> hold(record, claim);
            case RELEASED_FLIGHT -> {
                claimedTransfers.remove(id); claims.remove(id); fences.drop(id);
                settled.remove(id); live.add(id); released.add(id);
            }
            case READY_SOURCE -> {
                claimedTransfers.remove(id); claims.remove(id); fences.drop(id); released.remove(id);
                settled.remove(id); live.add(id); readySources.put(id, claim.sourceUuid());
            }
            case RELEASED_TO_ORDINARY -> {
                claimedTransfers.remove(id); claims.remove(id); fences.drop(id); released.remove(id);
                live.remove(id); settled.remove(id); readySources.remove(id);
                return true;
            }
            case UNCLAIMED -> {
                // A disappearing receipt cannot silently end a claim. The coordinator must report its handoff.
                return !claimedTransfers.contains(id) && !released.contains(id);
            }
        }
        return false;
    }
    private void hold(RocketTransferRecord record, RocketTransferBayRecoveryGate.Claim claim) {
        UUID id = record.transferId();
        claimedTransfers.add(id); claims.put(id, claim); fences.drop(id);
        live.remove(id); settled.remove(id); released.remove(id); readySources.remove(id);
    }

    /** null is refusal; an allowed PERMIT is distinguished so only escrow-only predicates run afterwards. */
    RocketTransferBayRecoveryGate.Boundary before(MinecraftServer server, RocketTransferSavedData journal,
            RocketTransferRecord record, RocketTransferBayRecoveryGate.Action action) {
        var boundary = Objects.requireNonNull(gate.beforeAction(server, journal, record, action));
        if (boundary.kind() == RocketTransferBayRecoveryGate.BoundaryKind.HELD) {
            hold(record, claims.getOrDefault(record.transferId(),
                    RocketTransferBayRecoveryGate.Claim.held(RocketTransferBayRecoveryGate.State.OWNED_WAIT, "ACTION_HELD")));
            return null;
        }
        // End notifications are not effective at a boundary, only at classification.
        if (claimedTransfers.contains(record.transferId())) { return null; }
        if (boundary.kind() == RocketTransferBayRecoveryGate.BoundaryKind.PERMIT) {
            if (!boundary.token().valid(server, journal, record, action)) {
                hold(record, RocketTransferBayRecoveryGate.Claim.held(RocketTransferBayRecoveryGate.State.REPAIR, "INVALID_ACTION_PERMIT"));
                return null;
            }
        } else if (released.contains(record.transferId())) { return null; }
        return boundary;
    }
    boolean allowed(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
            RocketTransferBayRecoveryGate.Action action) { return before(server, journal, record, action) != null; }
    void conflict(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record) {
        gate.escrowConflict(server, journal, record);
        hold(record, RocketTransferBayRecoveryGate.Claim.held(RocketTransferBayRecoveryGate.State.REPAIR, "ESCROW_AUTHORITY_CONFLICT"));
    }
    boolean endpointChanged(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record) {
        boolean previouslyClaimed = claimedTransfers.contains(record.transferId()) || released.contains(record.transferId());
        // claim() may report a one-shot handoff. Always apply it, never consume it merely as an observation.
        boolean classified = !classify(server, journal, record);
        if (previouslyClaimed || claimedTransfers.contains(record.transferId()) || released.contains(record.transferId())) {
            gate.endpointChanged(server, journal, record);
            classify(server, journal, record);
            return true;
        }
        return classified;
    }
    UUID source(UUID transfer) { return readySources.get(transfer); }
    String diagnostics(RocketTransferSavedData journal) {
        return " claimed=" + claimedTransfers.size() + journal.entries().stream()
                .filter(record -> claimedTransfers.contains(record.transferId())).limit(4).map(record -> {
                    var claim = claims.get(record.transferId());
                    return " [claimed_transfer=" + record.transferId() + " logical=" + record.logicalRocketId()
                            + " state=" + claim.state() + " reason=" + claim.reason()
                            + (claim.state() == RocketTransferBayRecoveryGate.State.REPAIR ? " repair=offline-whole-world" : "") + "]";
                }).collect(java.util.stream.Collectors.joining());
    }
    void clear() {
        claimedTransfers.clear(); claims.clear(); readySources.clear(); released.clear();
        gate = RocketTransferBayRecoveryGate.UNCLAIMED; installed = false;
    }
}
