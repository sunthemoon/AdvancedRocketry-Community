package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRecoveryFence;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.ResourceEscrowSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
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
    private final java.util.function.Function<MinecraftServer, io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowLedger> ledgerReader;

    RocketTransferFences() { this(server -> {
        var data = ResourceEscrowSavedData.existing(server); return data == null ? null : data.ledger();
    }); }
    RocketTransferFences(java.util.function.Function<MinecraftServer, io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowLedger> ledgerReader) {
        this.ledgerReader = java.util.Objects.requireNonNull(ledgerReader);
    }

    Set<UUID> ids() { return Set.copyOf(reasons.keySet()); }
    String reason(UUID id) { return reasons.get(id); }

    void classify(MinecraftServer server, RocketTransferSavedData journal, Set<UUID> live, Set<UUID> settled) {
        var ledger = ledgerReader.apply(server);
        if (journal.acknowledgedCurrent()) {
            Set<UUID> present = journal.entries().stream().map(RocketTransferRecord::transferId).collect(java.util.stream.Collectors.toSet());
            reasons.keySet().retainAll(present); attempts.keySet().retainAll(present);
            approved.keySet().retainAll(present);
        }
        for (var record : journal.entries()) {
            String reason = null;
            if (RocketEscrowGate.logicalLocked(server, record.logicalRocketId())) { reason = "ESCROW_PENDING_OR_TOMBSTONE"; }
            else if (ledger != null && EscrowRecoveryFence.known(ledger, record.logicalRocketId(), record.transferId())
                    && !proofKey(server, record).equals(approved.get(record.transferId()))) { reason = "FLOOR_PROOF_REQUIRED"; }
            else if (loadedProtectedHint(server, record, approved.containsKey(record.transferId()))) { reason = "ENDPOINT_PROOF_REQUIRED"; }
            // An existing detailed conflict is retained during re-evaluation, not reset every tick.
            if (reason != null && !reasons.containsKey(record.transferId())) { fence(record, reason, live, settled); }
        }
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
            if (permit(server, record, live, settled)) { return record; }
        }
        return null;
    }

    boolean permit(MinecraftServer server, RocketTransferRecord record, Set<UUID> live, Set<UUID> settled) {
        if (!reasons.containsKey(record.transferId())) { return true; }
        long now = server.getTickCount();
        boolean permitted = reevaluate(record.transferId(), now, () -> loadedReason(server, record), live, settled);
        if (permitted) { approved.put(record.transferId(), proofKey(server, record)); }
        return permitted;
    }

    /** Supplier does only already-loaded reads. null is a complete permitted-action proof. */
    boolean reevaluate(UUID id, long now, java.util.function.Supplier<String> observation, Set<UUID> live, Set<UUID> settled) {
        if (tick != now) { tick = now; retries = 0; }
        if (retries >= RETRIES_PER_TICK || attempts.containsKey(id) && now - attempts.get(id) < RETRY_TICKS) { return false; }
        retries++; attempts.put(id, now);
        String reason = observation.get();
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
        for (var record : journal.entries()) {
            if (logical.equals(record.logicalRocketId()) && (protectedJoin || approved.containsKey(record.transferId()) || reasons.containsKey(record.transferId()))) {
                approved.remove(record.transferId());
                fence(record, "ENDPOINT_JOIN_PROOF_REQUIRED", live, settled);
            }
        }
        classify(server, journal, live, settled);
    }

    private String loadedReason(MinecraftServer server, RocketTransferRecord record) {
        if (RocketEscrowGate.logicalLocked(server, record.logicalRocketId())) { return "ESCROW_PENDING_OR_TOMBSTONE"; }
        var ledger = ledgerReader.apply(server);
        if (ledger != null && (ledger.records().stream().anyMatch(op -> record.logicalRocketId().equals(op.guard().rocket()))
                || EscrowRecoveryFence.tombstoned(ledger, record.logicalRocketId()))) { return "ESCROW_PENDING_OR_TOMBSTONE"; }
        for (var dimension : List.of(record.sourceSnapshot().sourceDimension(), record.destinationSnapshot().sourceDimension())) {
            var level = RocketTransferEntities.level(server, dimension);
            if (level == null) { continue; }
            for (var id : List.of(record.sourceEntityId(), record.destinationEntityId().orElse(record.sourceEntityId()))) {
                if (level.getEntity(id) instanceof RocketEntity rocket && !state(rocket).valid()) { return "INVALID_ENDPOINT"; }
            }
        }
        if (!RocketTransferEntities.recoveryEndsAlreadyLoaded(server, record)) { return "WAITING_UNLOADED"; }
        var sources = RocketTransferEntities.loadedMatches(server, record, false);
        var destinations = RocketTransferEntities.loadedMatches(server, record, true);
        if (sources.size() >= io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits.MAX_TRANSFER_ENTITY_MATCHES
                || destinations.size() >= io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits.MAX_TRANSFER_ENTITY_MATCHES) { return "MATCH_LIMIT"; }
        var action = RocketTransferRecoveryDecision.decide(record.phase(), new RocketTransferPresence(!sources.isEmpty(), !destinations.isEmpty()));
        return EscrowRecoveryFence.reason(ledger, record.logicalRocketId(), record.transferId(),
                action, sources.stream().map(RocketTransferFences::state).toList(), destinations.stream().map(RocketTransferFences::state).toList());
    }

    static EscrowRecoveryFence.Rocket state(RocketEntity rocket) {
        var data = rocket.level() instanceof net.minecraft.server.level.ServerLevel level ? ResourceEscrowSavedData.existing(level.getServer()) : null;
        return new EscrowRecoveryFence.Rocket(rocket.getUUID(), rocket.escrowSection().epoch(),
                !rocket.escrowSection().markers().isEmpty(), rocket.operational()
                && (rocket.escrowSection().ledger() == null || data != null && rocket.escrowSection().ledger().equals(data.ledger().id())));
    }

    /** Final action guard also covers duplicate candidates not named in the journal's UUID fields. */
    boolean actionAllowed(MinecraftServer server, RocketTransferRecord record,
                          io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryAction action,
                          List<RocketEntity> sources, List<RocketEntity> destinations, Set<UUID> live, Set<UUID> settled) {
        var data = ResourceEscrowSavedData.existing(server);
        String reason = sources.size() >= io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits.MAX_TRANSFER_ENTITY_MATCHES
                || destinations.size() >= io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits.MAX_TRANSFER_ENTITY_MATCHES ? "MATCH_LIMIT"
                : RocketEscrowGate.logicalLocked(server, record.logicalRocketId()) ? "ESCROW_PENDING_OR_TOMBSTONE"
                : EscrowRecoveryFence.reason(data == null ? null : data.ledger(), record.logicalRocketId(), record.transferId(), action,
                        sources.stream().map(RocketTransferFences::state).toList(), destinations.stream().map(RocketTransferFences::state).toList());
        if (reason != null) { fence(record, reason, live, settled); return false; }
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
