package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRecoveryFence;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedDispatch;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.ResourceEscrowSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPresence;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryDecision;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** Server-service lifetime classification. Fenced retry never enters ticket-adding ordinary recovery. */
final class RocketTransferFences {
    static final int RETRIES_PER_TICK = 2;
    static final int RETRY_TICKS = 1_200;
    private final Map<UUID, String> reasons = new HashMap<>();
    private final Map<UUID, Long> attempts = new HashMap<>();
    private final Map<UUID, String> approved = new HashMap<>();
    private long tick = Long.MIN_VALUE;
    private int retries;
    private int cursor;
    private RocketTransferBayDispatch bays;
    private final java.util.function.Function<MinecraftServer, io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowLedger> ledgerReader;

    RocketTransferFences() { this(server -> {
        var data = ResourceEscrowSavedData.existing(server); return data == null ? null : data.ledger();
    }); }
    RocketTransferFences(java.util.function.Function<MinecraftServer, io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowLedger> ledgerReader) {
        this.ledgerReader = java.util.Objects.requireNonNull(ledgerReader);
    }

    Set<UUID> ids() { return Set.copyOf(reasons.keySet()); }
    String reason(UUID id) { return reasons.get(id); }
    void bays(RocketTransferBayDispatch dispatch) { bays = dispatch; }
    void drop(UUID id) { reasons.remove(id); attempts.remove(id); approved.remove(id); }

    void classify(MinecraftServer server, RocketTransferSavedData journal, Set<UUID> live, Set<UUID> settled) {
        if (journal.acknowledgedCurrent()) {
            Set<UUID> present = journal.entries().stream().map(RocketTransferRecord::transferId).collect(java.util.stream.Collectors.toSet());
            reasons.keySet().retainAll(present); attempts.keySet().retainAll(present);
            approved.keySet().retainAll(present);
        }
        for (var record : journal.entries()) {
            if (bays != null && !bays.classify(server, journal, record)) { continue; }
            if (record.nativeTransfer().isPresent()) {
                var route = server == null ? new EscrowGovernedDispatch.Decision(EscrowGovernedDispatch.Route.HOLD, "GOVERNED_ACTION_REQUIRED")
                        : EscrowRuntime.of(server).governedFlights().classify(server, journal, record);
                classifyGoverned(record, route, live, settled); continue;
            }
            var ledger = ledgerReader.apply(server);
            String reason = null;
            if (RocketEscrowGate.logicalLocked(server, record.logicalRocketId())) { reason = "ESCROW_PENDING_OR_TOMBSTONE"; }
            else if (ledger != null && EscrowRecoveryFence.known(ledger, record.logicalRocketId(), record.transferId())
                    && !proofKey(server, record).equals(approved.get(record.transferId()))) { reason = "FLOOR_PROOF_REQUIRED"; }
            else if (loadedProtectedHint(server, record, approved.containsKey(record.transferId()))) { reason = "ENDPOINT_PROOF_REQUIRED"; }
            // An existing detailed conflict is retained during re-evaluation, not reset every tick.
            if (reason != null && !reasons.containsKey(record.transferId())) { fence(record, reason, live, settled); }
        }
    }

    /** Classification is not permission: both implemented executors repeat their final action checks. */
    void classifyGoverned(RocketTransferRecord record, EscrowGovernedDispatch.Decision route, Set<UUID> live, Set<UUID> settled) {
        if (route.route() == EscrowGovernedDispatch.Route.ACTIVE || route.route() == EscrowGovernedDispatch.Route.CLEANUP) {
            drop(record.transferId()); settled.remove(record.transferId()); live.add(record.transferId()); return;
        }
        if (route.route() == EscrowGovernedDispatch.Route.HOLD
                && ("GOVERNED_JOURNAL_UNACKNOWLEDGED".equals(route.reason())
                        || "GOVERNED_LEDGER_PENDING".equals(route.reason()))) {
            // R10/R11: retain the exclusion while awaiting the exact acknowledgement, without
            // diagnosing offline repair or replacing a previously observed repair reason.
            approved.remove(record.transferId());
            reasons.putIfAbsent(record.transferId(), route.reason());
            live.remove(record.transferId()); settled.remove(record.transferId()); return;
        }
        // R11: CLEANUP is dispatched only to F8, never legacy recovery or a forward phase.
        // The cleanup executor reports a specific blocked reason when operator action is needed.
        fence(record, route.reason(), live, settled);
    }

    private static boolean loadedProtectedHint(MinecraftServer server, RocketTransferRecord record, boolean proved) {
        for (var dimension : List.of(record.sourceSnapshot().sourceDimension(), record.destinationSnapshot().sourceDimension())) {
            var level = RocketTransferEntities.level(server, dimension);
            if (level == null) { continue; }
            for (var id : List.of(record.sourceEntityId(), record.destinationEntityId().orElse(record.sourceEntityId()))) {
                if (level.getEntity(id) instanceof RocketEntity rocket && (!rocket.operational()
                        || !proved && rocket.escrowSection().epoch() != 0 || !rocket.escrowSection().markers().isEmpty())) { return true; }
            }
        }
        return false;
    }

    RocketTransferRecord reevaluateOne(MinecraftServer server, RocketTransferSavedData journal, Set<UUID> live, Set<UUID> settled) {
        var records = journal.entries().stream().filter(record -> reasons.containsKey(record.transferId())).toList();
        if (!records.isEmpty()) {
            var record = records.get(Math.floorMod(cursor++, records.size()));
            if (permit(server, journal, record, live, settled)) { return record; }
        }
        return null;
    }

    boolean permit(MinecraftServer server, RocketTransferRecord record, Set<UUID> live, Set<UUID> settled) {
        return permit(server, RocketTransferSavedData.get(server), record, live, settled);
    }

    boolean permit(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record, Set<UUID> live, Set<UUID> settled) {
        if (bays != null && !bays.allowed(server, journal, record,
                RocketTransferBayRecoveryGate.Action.RECOVER_AUTO)) { return false; }
        if (record.nativeTransfer().isPresent()) { return false; }
        if (!reasons.containsKey(record.transferId())) { return true; }
        long now = server.getTickCount();
        boolean permitted = reevaluate(record.transferId(), now, () -> loadedReason(server, journal, record), live, settled);
        if (permitted) { approved.put(record.transferId(), proofKey(server, record)); }
        return permitted;
    }

    /** Supplier does only already-loaded reads. null is a complete permitted-action proof. */
    boolean reevaluate(UUID id, long now, java.util.function.Supplier<String> observation, Set<UUID> live, Set<UUID> settled) {
        if (tick != now) { tick = now; retries = 0; }
        if (retries >= RETRIES_PER_TICK || attempts.containsKey(id) && now - attempts.get(id) < RETRY_TICKS) { return false; }
        retries++; attempts.put(id, now);
        String reason = observation.get();
        if (bays != null && bays.ids().contains(id)) { return false; }
        if (reason != null) {
            if (!reason.equals(reasons.put(id, reason))) { log(id, reason); }
            live.remove(id); settled.remove(id);
            return false;
        }
        reasons.remove(id);
        return true;
    }

    private String proofKey(MinecraftServer server, RocketTransferRecord record) {
        var ledger = ledgerReader.apply(server);
        return record.checksum() + (ledger == null ? "" : ledger.id().toString()
                + ledger.floors().stream().filter(floor -> record.logicalRocketId().equals(floor.getUUID("rocket_id")))
                .map(io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources::signature).sorted().toList());
    }

    void joined(MinecraftServer server, UUID logical, boolean protectedJoin, RocketTransferSavedData journal, Set<UUID> live, Set<UUID> settled) {
        if (server != null) { EscrowRuntime.of(server).governedFlights().invalidated(logical); }
        for (var record : journal.entries()) {
            if (logical.equals(record.logicalRocketId()) && bays != null && bays.endpointChanged(server, journal, record)) { continue; }
            // Record-3 joins invalidate their checked authority above; the governed executor rechecks the entire census.
            if (server != null && record.nativeTransfer().isPresent()) { continue; }
            if (logical.equals(record.logicalRocketId()) && (protectedJoin || approved.containsKey(record.transferId()) || reasons.containsKey(record.transferId()))) {
                approved.remove(record.transferId());
                fence(record, "ENDPOINT_JOIN_PROOF_REQUIRED", live, settled);
            }
        }
        classify(server, journal, live, settled);
    }

    private String loadedReason(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record) {
        var boundary = bays == null ? RocketTransferBayRecoveryGate.Boundary.unclaimed()
                : bays.before(server, journal, record, RocketTransferBayRecoveryGate.Action.ELECT_DUPLICATES);
        if (boundary == null) { return "BAY_HELD"; }
        if (record.nativeTransfer().isPresent()) { return "GOVERNED_ACTION_REQUIRED"; }
        if (boundary.kind() == RocketTransferBayRecoveryGate.BoundaryKind.PERMIT
                ? RocketEscrowGate.escrowLogicalLocked(server, record.logicalRocketId())
                : RocketEscrowGate.logicalLocked(server, record.logicalRocketId())) {
            if (boundary.kind() == RocketTransferBayRecoveryGate.BoundaryKind.PERMIT) {
                bays.conflict(server, journal, record); return "BAY_HELD";
            }
            return "ESCROW_PENDING_OR_TOMBSTONE";
        }
        var ledger = ledgerReader.apply(server);
        if (ledger != null && (ledger.records().stream().anyMatch(op -> record.logicalRocketId().equals(op.guard().rocket()))
                || EscrowRecoveryFence.tombstoned(ledger, record.logicalRocketId()))) {
            return bayReason(server, journal, record, boundary, "ESCROW_PENDING_OR_TOMBSTONE");
        }
        for (var dimension : List.of(record.sourceSnapshot().sourceDimension(), record.destinationSnapshot().sourceDimension())) {
            var level = RocketTransferEntities.level(server, dimension);
            if (level == null) { continue; }
            for (var id : List.of(record.sourceEntityId(), record.destinationEntityId().orElse(record.sourceEntityId()))) {
                if (level.getEntity(id) instanceof RocketEntity rocket && !state(rocket).valid()) {
                    return bayReason(server, journal, record, boundary, "INVALID_ENDPOINT");
                }
            }
        }
        if (!RocketTransferEntities.recoveryEndsAlreadyLoaded(server, record)) {
            return bayReason(server, journal, record, boundary, "WAITING_UNLOADED");
        }
        var sources = RocketTransferEntities.loadedMatches(server, record, false);
        var destinations = RocketTransferEntities.loadedMatches(server, record, true);
        if (sources.size() >= io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits.MAX_TRANSFER_ENTITY_MATCHES
                || destinations.size() >= io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits.MAX_TRANSFER_ENTITY_MATCHES) {
            return bayReason(server, journal, record, boundary, "MATCH_LIMIT");
        }
        var action = RocketTransferRecoveryDecision.decide(record.phase(), new RocketTransferPresence(!sources.isEmpty(), !destinations.isEmpty()));
        String reason = EscrowRecoveryFence.reason(ledger, record.logicalRocketId(), record.transferId(),
                action, sources.stream().map(RocketTransferFences::state).toList(), destinations.stream().map(RocketTransferFences::state).toList());
        return bayReason(server, journal, record, boundary, reason);
    }

    private String bayReason(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
            RocketTransferBayRecoveryGate.Boundary boundary, String reason) {
        if (reason != null && boundary.kind() == RocketTransferBayRecoveryGate.BoundaryKind.PERMIT) {
            bays.conflict(server, journal, record); return "BAY_HELD";
        }
        return reason;
    }

    static EscrowRecoveryFence.Rocket state(RocketEntity rocket) {
        var data = rocket.level() instanceof net.minecraft.server.level.ServerLevel level ? ResourceEscrowSavedData.existing(level.getServer()) : null;
        return new EscrowRecoveryFence.Rocket(rocket.getUUID(), rocket.escrowSection().epoch(),
                !rocket.escrowSection().markers().isEmpty(), rocket.operational()
                && (rocket.escrowSection().ledger() == null || data != null && rocket.escrowSection().ledger().equals(data.ledger().id())));
    }

    /** Final action guard also covers duplicate candidates not named in the journal's UUID fields. */
    boolean actionAllowed(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
                          io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryAction action,
                          List<RocketEntity> sources, List<RocketEntity> destinations, Set<UUID> live, Set<UUID> settled) {
        var boundary = bays == null ? RocketTransferBayRecoveryGate.Boundary.unclaimed()
                : bays.before(server, journal, record, RocketTransferBayRecoveryGate.Action.ELECT_DUPLICATES);
        if (boundary == null) { return false; }
        if (record.nativeTransfer().isPresent()) { return false; }
        var data = ResourceEscrowSavedData.existing(server);
        String reason = sources.size() >= io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits.MAX_TRANSFER_ENTITY_MATCHES
                || destinations.size() >= io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits.MAX_TRANSFER_ENTITY_MATCHES ? "MATCH_LIMIT"
                : (boundary.kind() == RocketTransferBayRecoveryGate.BoundaryKind.PERMIT
                        ? RocketEscrowGate.escrowLogicalLocked(server, record.logicalRocketId())
                        : RocketEscrowGate.logicalLocked(server, record.logicalRocketId())) ? "ESCROW_PENDING_OR_TOMBSTONE"
                : EscrowRecoveryFence.reason(data == null ? null : data.ledger(), record.logicalRocketId(), record.transferId(), action,
                        sources.stream().map(RocketTransferFences::state).toList(), destinations.stream().map(RocketTransferFences::state).toList());
        if (reason != null) {
            if (boundary.kind() == RocketTransferBayRecoveryGate.BoundaryKind.PERMIT) { bays.conflict(server, journal, record); }
            else { fence(record, reason, live, settled); }
            return false;
        }
        return true;
    }

    private void fence(RocketTransferRecord record, String reason, Set<UUID> live, Set<UUID> settled) {
        approved.remove(record.transferId());
        String previous = reasons.put(record.transferId(), reason);
        live.remove(record.transferId()); settled.remove(record.transferId());
        if (!reason.equals(previous)) { log(record.transferId(), reason); }
    }

    private static void log(UUID id, String reason) {
        AdvancedRocketryCommunity.LOGGER.warn("ARCE_TRANSFER_FENCED transfer={} reason={} repair=offline-whole-world", id, reason);
    }

    void clear() { reasons.clear(); attempts.clear(); approved.clear(); retries = 0; tick = Long.MIN_VALUE; cursor = 0; }
}
