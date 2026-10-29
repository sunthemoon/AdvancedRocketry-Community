package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayDeploymentSession;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptWriter;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayGroup;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayMember;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayStepQueue;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** W196: release preparation and one-action permits. The parent claim, receipts and pending slots never retire here. */
final class BayReleasedFlight {
    private final MinecraftServer server;
    private final BayDeploymentSession session;
    private final Consumer<UUID> offer;
    private final BaySourceResume sources;
    private final BayConsumptionSteps consumption;
    private final BayReleasedResume resume;
    private final Consumer<BayReceipt> releasing;
    // Entries are subordinate to the existing bounded claim book, never an independent admission authority.
    private final Map<UUID, State> states = new HashMap<>();
    private State effectState;
    private RocketTransferRecord effectRecord;
    private long preparationTick = Long.MIN_VALUE;

    private static final class State {
        BayReleasedAuthority authority;
        BayClaim consumption;
        List<BayReceipt> releasing;
        RocketTransferRecord proposed;
        String oldDigest, nextDigest;
        long observation, proposedObservation, sourceSave, destinationSave;
        boolean delegated, writing, closed;
    }

    BayReleasedFlight(MinecraftServer server, BayDeploymentSession session, Consumer<UUID> offer,
                      BaySourceResume sources, BayConsumptionSteps consumption, Consumer<BayReceipt> releasing) {
        this.server = server; this.session = session; this.offer = offer;
        this.sources = sources; this.consumption = consumption; resume = new BayReleasedResume(server);
        this.releasing = releasing;
    }

    /** Null means this is still an ordinary deployment claim. Release work uses the parent's 32-step queue. */
    BayStepQueue.Result step(BayClaim claim) {
        var receipts = session.receipts().stream().filter(r -> r.rocket().equals(claim.logical()) && r.pending()).toList();
        boolean alreadyReleased = !receipts.isEmpty() && receipts.stream().allMatch(BayReceipt::released);
        boolean blocked = claim.reason().equals("REGISTRY_BLOCKED");
        if (!states.containsKey(claim.logical()) && !alreadyReleased
                && (!blocked && receipts.stream().noneMatch(r -> r.phase() == BayReceipt.Phase.REPAIR)
                    || claim.members().stream().anyMatch(m -> m.awaiting() || m.state() == BayMember.State.PROVED))) { return null; }
        var state = states.computeIfAbsent(claim.logical(), ignored -> new State());
        if (state.closed || state.writing) { return BayStepQueue.Result.IDLE; }
        var journal = RocketTransferSavedData.get(server);
        var record = journal.find(claim.transfer()).orElse(null);
        if (record == null) { return held(claim, state, "TRANSFER_AUTHORITY_CONFLICT"); }
        if (session.unavailable() || !journal.actionable(claim.logical())) { return BayStepQueue.Result.NEXT_TICK; }
        var guard = journal.logicalGuard(claim.logical()).orElse(null);
        if (guard == null || !claim.transfer().equals(guard.transfer()) || guard.recordDigest() == null
                || receipts.stream().anyMatch(r -> !session.acknowledged(r.op()))) { return BayStepQueue.Result.NEXT_TICK; }
        if (state.delegated && state.authority != null && state.authority.matches(record) && physical(record, false)) {
            return BayStepQueue.Result.NEXT_TICK;
        }
        state.delegated = false;
        if (preparationTick == server.getTickCount()) { return BayStepQueue.Result.NEXT_TICK; }
        preparationTick = server.getTickCount(); // the existing one-group-per-tick reconstruction limit
        if (!alreadyReleased && exactSource(record) == null) { return BayStepQueue.Result.IDLE; }
        var registered = receipts.stream().filter(r -> r.phase() == BayReceipt.Phase.REGISTERED).toList();
        if (!registered.isEmpty() && !alreadyReleased) {
            var result = consume(claim, state, record, registered);
            if (result != null) { return result; }
            record = journal.find(claim.transfer()).orElseThrow();
        }
        var authority = BayReleasedAuthority.capture(record, receipts, blocked).orElse(null);
        if (authority == null) { return held(claim, state, "TRANSFER_AUTHORITY_CONFLICT"); }
        String wait = resume.resume(record, alreadyReleased, receipts);
        if (wait != null) {
            if (wait.equals("END_LOADING") || wait.equals("END_UNAVAILABLE") || wait.equals("SOURCE_LOADING")) {
                return BayStepQueue.Result.NEXT_TICK;
            }
            return held(claim, state, wait);
        }
        state.authority = authority;
        if (!alreadyReleased) {
            state.releasing = receipts.stream().map(BayReceipt::withReleased).toList();
            releasing.accept(state.releasing.get(0));
            var admission = session.releaseGroup(state.releasing, outcome -> {
                if (outcome == BayReceiptWriter.Outcome.UNKNOWN) { return; }
                state.writing = false;
                offer.accept(claim.logical());
            });
            state.writing = admission.queued();
            return state.writing ? BayStepQueue.Result.IDLE : BayStepQueue.Result.NEXT_TICK;
        }
        if (state.releasing != null && !receipts.equals(state.releasing)) {
            return held(claim, state, "TRANSFER_AUTHORITY_CONFLICT");
        }
        state.delegated = true;
        return BayStepQueue.Result.NEXT_TICK;
    }

    private BayStepQueue.Result consume(BayClaim claim, State state, RocketTransferRecord record, List<BayReceipt> registered) {
        if (state.consumption == null) {
            var members = registered.stream().map(BayMember::restartRegistered).toList();
            var group = BayGroup.resumed(claim.logical(), claim.transfer(), members).orElse(null);
            if (group == null) { return held(claim, state, "CONSUMPTION_MISMATCH"); }
            state.consumption = BayClaim.restart(claim.logical(), claim.transfer(), members);
            state.consumption.freeze(group);
        }
        var work = state.consumption;
        if (work.hold() == BayClaim.Hold.REPAIR) { return held(claim, state, work.reason()); }
        if (work.stage() == BayClaim.Stage.RESUME) {
            sources.renew(work);
            return sources.resume(work);
        }
        if (work.stage() == BayClaim.Stage.CONSUME) { return consumption.consume(work); }
        return work.stage() == BayClaim.Stage.PROVE ? null : held(claim, state, "CONSUMPTION_MISMATCH");
    }

    boolean delegated(RocketTransferRecord record) {
        var state = states.get(record.logicalRocketId());
        return state != null && !state.closed && state.delegated && state.authority != null && state.authority.matches(record);
    }
    boolean active(UUID logical) { var state = states.get(logical); return state != null && state.delegated && !state.closed; }

    RocketTransferBayRecoveryGate.Boundary before(RocketTransferSavedData journal, RocketTransferRecord record,
                                                 RocketTransferBayRecoveryGate.Action action) {
        var state = states.get(record.logicalRocketId());
        if (state == null || !valid(state, journal, record, action)) { return RocketTransferBayRecoveryGate.Boundary.held(); }
        long sourceObservation = observation(exactSource(journal.find(record.transferId()).orElseThrow()));
        long destinationObservation = observation(exactDestination(record));
        long generation = state.observation;
        var used = new boolean[] {false};
        return RocketTransferBayRecoveryGate.Boundary.permit((s, j, r, a) -> {
            if (used[0]) { return false; }
            used[0] = true;
            return s == server && j == journal && r == record && a == action
                    && generation == state.observation
                    && sourceObservation == observation(exactSource(journal.find(record.transferId()).orElseThrow()))
                    && destinationObservation == observation(exactDestination(record)) && valid(state, journal, record, action);
        });
    }

    private boolean valid(State state, RocketTransferSavedData journal, RocketTransferRecord record,
                          RocketTransferBayRecoveryGate.Action action) {
        if (!server.isSameThread() || state.closed || !state.delegated || session.closed() || session.unavailable()
                || state.authority == null || !state.authority.matches(record)
                || action != RocketTransferBayRecoveryGate.Action.TICK_SOURCE
                    && action != RocketTransferBayRecoveryGate.Action.SPAWN_DESTINATION
                || RocketReleaseAccess.fenced(server, record.logicalRocketId())
                || RocketEscrowGate.escrowLogicalLocked(server, record.logicalRocketId())) { return false; }
        for (var receipt : state.authority.receipts()) {
            if (!receipt.released() || !session.acknowledged(receipt.op())
                    || session.current(receipt.op()).filter(receipt::equals).isEmpty()) { return false; }
        }
        var current = journal.find(record.transferId()).orElse(null);
        var guard = journal.logicalGuard(record.logicalRocketId()).orElse(null);
        if (current == null || guard == null || !record.transferId().equals(guard.transfer()) || guard.recordDigest() == null) { return false; }
        if (action == RocketTransferBayRecoveryGate.Action.SPAWN_DESTINATION && record.phase() != RocketTransferPhase.PREPARED
                && !(record.phase() == RocketTransferPhase.DESTINATION_SPAWNED && (current != record || state.proposed == record))) { return false; }
        if (current != record) {
            if (!journal.actionable(record.logicalRocketId()) || !state.authority.successor(current, record, action)
                    || !physical(current, false)) { return false; }
            state.proposed = record; state.oldDigest = guard.recordDigest(); state.nextDigest = null;
            state.proposedObservation = state.observation;
            state.sourceSave = observation(exactSource(current)); state.destinationSave = observation(exactDestination(current));
            return true;
        }
        if (state.proposed == record) { state.nextDigest = guard.recordDigest(); }
        return physical(record, state.proposed == record);
    }

    /** Called immediately before the journal's acknowledged effect, never while a write is pending. */
    void effect(RocketTransferSavedData journal, RocketTransferRecord record,
                RocketTransferBayRecoveryGate.Action action, Runnable effect) {
        var state = states.get(record.logicalRocketId());
        if (state == null || effectState != null || state.proposed != record || state.oldDigest == null
                || state.proposedObservation != state.observation
                || state.sourceSave != observation(exactSource(record))
                || state.destinationSave != observation(exactDestination(record))
                || !valid(state, journal, record, action) || state.nextDigest == null) { return; }
        try { effectState = state; effectRecord = record; effect.run(); }
        finally { effectState = null; effectRecord = null; state.proposed = null; }
    }

    boolean mountAllowed(Entity passenger, RocketEntity destination) {
        return resume.mountAllowed(passenger, destination) || effectState != null && effectRecord != null && server.isSameThread() && passenger instanceof ServerPlayer player
                && server.getPlayerList().getPlayer(player.getUUID()) == player && player.isAlive()
                && effectRecord.phase() == RocketTransferPhase.PASSENGERS_TRANSFERRED
                && effectRecord.destinationFlightData().passengers().assignment(player.getUUID()).isPresent()
                && exactDestination(effectRecord) == destination && BayReleasedResume.destinationCurrent(destination, effectRecord);
    }

    /** The released login path routes only the retained assignment; no ordinary election or source settlement runs. */
    boolean reconnect(RocketTransferSavedData journal, RocketTransferRecord record, ServerPlayer player) {
        var state = states.get(record.logicalRocketId());
        if (state == null || !valid(state, journal, record, RocketTransferBayRecoveryGate.Action.TICK_SOURCE)
                || journal.find(record.transferId()).orElse(null) != record || !journal.actionable(record.logicalRocketId())
                || record.sourceFlightData().passengers().assignment(player.getUUID()).isEmpty()) { return false; }
        var target = record.phase().destinationAuthoritative() ? exactDestination(record) : exactSource(record);
        return target != null && resume.move(player, target, record);
    }

    private boolean physical(RocketTransferRecord record, boolean effectPending) {
        var source = exactSource(record);
        if (source != null && (!RocketTransferEntities.loadedEntity(source)
                || !RocketEscrowGate.bayNeutral(source, record.transferId()) || !BayConsumptionSteps.inTransit(source, record)
                || source.ownerId().filter(record.ownerId()::equals).isEmpty()
                || source.assemblyTransactionId().filter(record.logicalRocketId()::equals).isEmpty()
                || source.snapshot().filter(s -> s.snapshotId().equals(record.sourceSnapshot().snapshotId())
                        && s.contentHash().equals(record.sourceSnapshot().contentHash())).isEmpty())) { return false; }
        if (record.phase() == RocketTransferPhase.PREPARED) { return source != null; }
        var destination = exactDestination(record);
        if (destination == null) { return effectPending && record.phase() == RocketTransferPhase.DESTINATION_SPAWNED && source != null; }
        return RocketTransferEntities.loadedEntity(destination) && BayReleasedResume.destinationCurrent(destination, record)
                && (record.phase().ordinal() < RocketTransferPhase.SOURCE_REMOVED.ordinal() || source == null || effectPending);
    }

    private RocketEntity exactSource(RocketTransferRecord record) {
        return BayReleasedResume.exact(RocketTransferEntities.level(server, record.sourceSnapshot().sourceDimension()), record.sourceEntityId());
    }
    private RocketEntity exactDestination(RocketTransferRecord record) {
        return record.destinationEntityId().map(id -> BayReleasedResume.exact(
                RocketTransferEntities.level(server, record.destinationSnapshot().sourceDimension()), id)).orElse(null);
    }
    private static long observation(RocketEntity rocket) { return rocket == null ? -1 : rocket.governedObservation(); }

    private static BayStepQueue.Result held(BayClaim claim, State state, String reason) {
        state.delegated = false; state.closed = true; claim.repair(reason); return BayStepQueue.Result.IDLE;
    }
    void conflict(UUID logical) { var state = states.get(logical); if (state != null) { state.closed = true; state.delegated = false; } }
    void changed(UUID logical) { var state = states.get(logical); if (state != null) { state.observation++; } }
    void endpointChanged(UUID logical) {
        var state = states.get(logical);
        if (state != null) { state.observation++; state.delegated = false; offer.accept(logical); }
    }
    void seen(RocketEntity rocket) {
        var names = new java.util.HashSet<UUID>();
        rocket.assemblyTransactionId().ifPresent(names::add); rocket.preservedAssemblyTransactionId().ifPresent(names::add);
        rocket.flightData().ifPresent(flight -> names.add(flight.logicalRocketId()));
        for (var logical : names) {
            var state = states.get(logical);
            if (state == null || state.authority == null || rocket.isRemoved()) { continue; }
            var record = RocketTransferSavedData.get(server).find(state.authority.base().transferId()).orElse(null);
            if (record == null || !BayCopyCensus.within(server, record, rocket)) { conflict(logical); }
            else if (!record.sourceEntityId().equals(rocket.getUUID())
                    && record.destinationEntityId().filter(rocket.getUUID()::equals).isEmpty()) { endpointChanged(logical); }
        }
    }
    void forget(UUID logical) { states.remove(logical); }
    void clear() { states.clear(); effectState = null; effectRecord = null; }
}
