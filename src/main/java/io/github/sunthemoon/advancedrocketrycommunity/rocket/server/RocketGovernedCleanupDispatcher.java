package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFlightTables;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedCleanup;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedDispatch;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.ResourceEscrowSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadIndex;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** Bounded F8 dispatch, including terminal entries whose journal removal preceded a restart. */
final class RocketGovernedCleanupDispatcher {
    private final Map<UUID, Work> work = new HashMap<>();
    private final Map<UUID, String> warnings = new HashMap<>();
    private int cursor;

    void tick(MinecraftServer server, RocketTransferSavedData journal, RocketTransferBayDispatch bays) {
        var data = ResourceEscrowSavedData.existing(server);
        if (data == null || !server.isSameThread() || !journal.actionAuthorityAvailable()) { return; }
        var coordinator = data.bind(server);
        if (!coordinator.operational()) { return; }
        var entries = data.ledger().armedFlights().stream().filter(arm -> arm.outcome() != EscrowFlightTables.Outcome.ARMED).toList();
        var ids = entries.stream().map(EscrowFlightTables.Armed::transfer).collect(java.util.stream.Collectors.toSet());
        work.keySet().retainAll(ids); warnings.keySet().retainAll(ids);
        if (entries.isEmpty()) { cursor = 0; return; }
        int start = Math.floorMod(cursor, entries.size()); int visits = Math.min(8, entries.size());
        for (int index = 0; index < visits && journal.actionAuthorityAvailable(); index++) {
            var arm = entries.get((start + index) % entries.size());
            var state = work.computeIfAbsent(arm.transfer(), ignored -> new Work(arm));
            if (!state.arm.equals(arm)) { blocked(arm.transfer(), "GOVERNED_CLEANUP_AUTHORITY_CONFLICT"); continue; }
            if (state.queued || server.getTickCount() < state.retryAt) { continue; }
            var record = journal.find(arm.transfer()).orElse(null);
            if (record != null) {
                var decision = EscrowRuntime.of(server).governedFlights().classify(server, journal, record);
                if (decision.route() != EscrowGovernedDispatch.Route.CLEANUP) { continue; }
                if (!EscrowGovernedCleanup.remove(coordinator, journal, record, () -> unclaimed(server, journal, record, bays))) { continue; }
                state.record = record; journal.flushChecked(server); continue;
            }
            if (!EscrowGovernedCleanup.absent(coordinator, journal, arm)) {
                var reason = EscrowGovernedCleanup.blockedReason(coordinator, journal, arm);
                if (reason != null) { blocked(arm.transfer(), reason); }
                continue;
            }
            if (state.record != null && !unclaimed(server, journal, state.record, bays)) { continue; }
            // On restart there are no old runtime tickets. A retained record supplies both exact
            // ticket coordinates in the live path; the saved pad index can release by exact T.
            var pads = server.overworld().getDataStorage().get(LandingPadIndex::load, LandingPadIndex.DATA_NAME);
            if (pads != null && !pads.releaseTransfer(arm.transfer())) {
                blocked(arm.transfer(), "GOVERNED_CLEANUP_PAD_UNAVAILABLE"); continue;
            }
            if (state.record != null) { RocketTransferEntities.releaseFlightTickets(server, state.record); }
            warnings.remove(arm.transfer());
            state.queued = EscrowGovernedCleanup.prune(coordinator, journal, arm,
                    () -> (pads == null || pads.operational())
                            && (state.record == null || unclaimed(server, journal, state.record, bays)), committed -> {
                        state.queued = false;
                        if (committed) { work.remove(arm.transfer(), state); }
                        else {
                            state.failures = Math.min(3, state.failures + 1);
                            state.retryAt = server.getTickCount() + (state.failures == 1 ? 100 : state.failures == 2 ? 400 : 1_200);
                        }
                    });
        }
        cursor = (start + visits) % entries.size();
    }

    private static boolean unclaimed(MinecraftServer server, RocketTransferSavedData journal,
                                     RocketTransferRecord record, RocketTransferBayDispatch bays) {
        var boundary = bays.before(server, journal, record, RocketTransferBayRecoveryGate.Action.REMOVE_JOURNAL);
        return boundary != null && boundary.kind() == RocketTransferBayRecoveryGate.BoundaryKind.UNCLAIMED;
    }

    void blocked(UUID transfer, String reason) {
        if (!reason.equals(warnings.put(transfer, reason))) {
            AdvancedRocketryCommunity.LOGGER.warn("ARCE_TRANSFER_FENCED transfer={} reason={} repair=offline-whole-world", transfer, reason);
        }
    }

    void clear() { work.clear(); warnings.clear(); cursor = 0; }

    private static final class Work {
        final EscrowFlightTables.Armed arm;
        RocketTransferRecord record;
        boolean queued;
        int failures;
        long retryAt;
        Work(EscrowFlightTables.Armed arm) { this.arm = arm; }
    }
}
