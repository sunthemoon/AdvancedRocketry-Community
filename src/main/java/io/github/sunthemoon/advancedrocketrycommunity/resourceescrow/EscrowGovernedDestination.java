package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Fixed destination publication/recovery. Publication never proves F5 removal or F6 handoff. */
public final class EscrowGovernedDestination implements AutoCloseable {
    public enum Result { HELD, WAITING, PUBLISHED, PRESENT, CREATE_FAILED, SPAWN_FAILED }
    @FunctionalInterface public interface Boundary {
        boolean allows(RocketTransferRecord record, RocketEntity source, RocketEntity destination);
    }
    private final Map<UUID, Work> work = new HashMap<>();
    private final EscrowTerminalRetries retries = new EscrowTerminalRetries();
    private final EscrowGovernedEnvelopeHolds envelopeHolds = new EscrowGovernedEnvelopeHolds();
    private boolean closed;

    public Result tick(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
                       RocketEntity source, Boundary boundary) {
        if (closed || !server.isSameThread() || source == null && !EscrowGovernedPhaseAction.sourceOptional(record)) { return Result.HELD; }
        work.entrySet().removeIf(entry -> {
            if (journal.find(entry.getKey()).filter(entry.getValue().record::equals).isPresent()) { return false; }
            entry.getValue().release(); return true;
        });
        if (journal.actionAuthorityAvailable()) {
            retries.retain(logical -> journal.findByLogicalRocket(logical).isPresent());
            envelopeHolds.retain(logical -> journal.findByLogicalRocket(logical).isPresent());
        }
        try { return checked(server, journal, record, source, boundary); }
        catch (IllegalArgumentException | IllegalStateException | ArithmeticException refused) {
            var state = work.remove(record.transferId()); if (state != null) { state.release(); }
            if (envelopeHolds.rejected(record.logicalRocketId()) && EscrowRuntime.of(server).governedFlights()
                    .classify(server, journal, record).route() == EscrowGovernedDispatch.Route.ACTIVE) {
                EscrowRuntime.of(server).executorHolds().hold(server, record, EscrowGovernedExecutorHolds.Reason.DESTINATION_ENVELOPE);
            }
            return Result.HELD;
        }
    }

    private Result checked(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
                           RocketEntity source, Boundary boundary) {
        var runtime = EscrowRuntime.of(server); var data = ResourceEscrowSavedData.existing(server);
        if (data == null) { return Result.HELD; }
        if (!retries.ready(record.logicalRocketId(), server.getTickCount())) { return Result.WAITING; }
        var waiting = work.get(record.transferId());
        if (waiting != null && waiting.arrival == null && waiting.failure == null
                && !waiting.ready(server.getTickCount(), () -> waiting.proofs.size() == waiting.slots.size()
                        || runtime.barrier().available(waiting.slots.get(waiting.proofs.size()), server.getTickCount()))) {
            return Result.WAITING;
        }
        var authority = runtime.governedFlights().authority(data::ledger,
                () -> data.bind(server).governedAvailable(record.logicalRocketId()), journal, record);
        if (authority == null || authority.decision().route() != EscrowGovernedDispatch.Route.ACTIVE) { return Result.HELD; }
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, record.destinationSnapshot().sourceDimension()));
        if (level == null) { return Result.HELD; }
        var entity = level.getEntity(record.nativeTransfer().orElseThrow().armed().destination());
        if (entity != null && !(entity instanceof RocketEntity)) {
            runtime.executorHolds().hold(server, record, EscrowGovernedExecutorHolds.Reason.NATIVE_ENDPOINT); return Result.HELD;
        }
        var destination = (RocketEntity) entity;
        if (EscrowGovernedFrame.transientMotion(source) || EscrowGovernedFrame.transientMotion(destination)) { return Result.WAITING; }
        BooleanSupplier finalBoundary = () -> boundary.allows(record, source, destination);
        if (!finalBoundary.getAsBoolean()) {
            if (!runtime.flightPhases().allowsNative(source, destination)) {
                runtime.executorHolds().hold(server, record, EscrowGovernedExecutorHolds.Reason.PHASE_ENVELOPE);
            }
            return Result.HELD;
        }
        var cached = work.get(record.transferId());
        // A definite failed publication is handed to the return controller, never retried as a fresh spawn.
        // Any object appearing after that failure remains an unresolved publication, not return authority.
        if (cached != null && cached.failure != null) {
            return destination == null && cached.sourceValid() ? cached.failure : Result.HELD;
        }
        if (cached != null && cached.authority.valid() && cached.sourceValid()) {
            if (destination != null && cached.arrival == destination && runtime.flightPhases().present(record, destination)) {
                runtime.executorHolds().healthy(record);
                return Result.PRESENT;
            }
            if (destination == null && (cached.pending || !cached.retry.ready(server.getTickCount()))) { return Result.WAITING; }
        }
        boolean phases = record.phase().ordinal() >= RocketTransferPhase.PASSENGERS_TRANSFERRED.ordinal();
        if (record.phase() == RocketTransferPhase.PREPARED) {
            long time = runtime.sourceFlights().publicationTime(authority, source, finalBoundary);
            if (!EscrowGovernedDestinationAction.sourceDue(record, EscrowGovernedEnvelope.capture(source), time)) { return Result.WAITING; }
            if (destination != null) { return Result.HELD; }
            try (var reservation = journal.canQueueEffect(record.logicalRocketId()).orElse(null)) {
                if (reservation == null) { return Result.HELD; }
                var next = record.destinationSpawned(record.nativeTransfer().orElseThrow().armed().destination());
                journal.put(next);
                runtime.checkpoint("F4_DESTINATION_REQUESTED");
                journal.afterAcknowledged(server, reservation, () -> {
                    if (source == null || !source.isAlive() || !source.isAddedToWorld()
                            || !((net.minecraft.server.level.ServerLevel) source.level()).areEntitiesLoaded(source.chunkPosition().toLong())) {
                        return false;
                    }
                    runtime.checkpoint("F4_DESTINATION_ACK");
                    // No captured permit or authority crosses this acknowledgement. Reacquire everything.
                    return tick(server, journal, next, source, boundary) != Result.HELD;
                }, () -> { });
                return Result.WAITING;
            }
        }
        // The acknowledged F4 may survive with only F1's E on disk. Do not require a second
        // source ascent or debit; validate the unchanged source at E or its permitted progression.
        var time = envelopeHolds.capture(record.logicalRocketId(), data.ledger(), source, destination, () -> {
            long observedTime = phases ? (source == null ? server.overworld().getGameTime() : source.level().getGameTime())
                    : runtime.sourceFlights().publicationTime(authority, source, finalBoundary);
            var observedSource = source == null ? null : EscrowGovernedEnvelope.capture(source);
            if (!(phases ? EscrowGovernedPhaseAction.sourceMatches(record, observedSource, observedTime)
                    : EscrowGovernedSourceRecovery.sourceMatches(record, observedSource, observedTime))) { return null; }
            if (destination != null) {
                var previous = work.remove(record.transferId()); if (previous != null) { previous.release(); }
                if (!(phases ? EscrowGovernedPhaseAction.destinationMatches(record, EscrowGovernedEnvelope.capture(destination))
                        : record.nativeTransfer().orElseThrow().destinationEnvelope(record).equals(EscrowGovernedEnvelope.capture(destination)))) { return null; }
            }
            return observedTime;
        });
        if (time == null) {
            runtime.executorHolds().hold(server, record, EscrowGovernedExecutorHolds.Reason.DESTINATION_ENVELOPE); return Result.HELD;
        }
        runtime.executorHolds().healthy(record);
        if (destination != null) {
            if (work.size() >= RocketFlightLimits.MAX_ACTIVE_TRANSFERS) { return Result.HELD; }
            var present = new Work(record, authority, source == null ? null : new EscrowFlightSource(source, finalBoundary),
                    retries.history(record.logicalRocketId()));
            runtime.flightPhases().capturePresence(destination, journal);
            present.arrival = destination; work.put(record.transferId(), present);
            return Result.PRESENT;
        }
        var state = work.get(record.transferId());
        if (state != null && (!state.authority.valid() || !state.sourceValid())) {
            state.release(); work.remove(record.transferId()); state = null;
        }
        if (state == null) {
            if (work.size() >= RocketFlightLimits.MAX_ACTIVE_TRANSFERS) { return Result.HELD; }
            state = new Work(record, authority, source == null ? null : new EscrowFlightSource(source, finalBoundary),
                    retries.history(record.logicalRocketId())); work.put(record.transferId(), state);
        }
        if (state.pending || !state.retry.ready(server.getTickCount())) { return Result.WAITING; }
        if (state.proofs.stream().anyMatch(proof -> !proof.valid())) { state.retry(server.getTickCount(), "SUPERSEDING_SAVE"); return Result.WAITING; }
        if (state.proofs.size() < state.slots.size()) {
            var pending = state; var slot = state.slots.get(state.proofs.size());
            ServerLevel slotLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.tryParse(slot.level())));
            if (slotLevel == null) { return Result.HELD; }
            state.pending = true;
            runtime.checkpoint("F4_ABSENCE_STORE_BEFORE");
            boolean started = runtime.barrier().begin(runtime.store(slotLevel), slot,
                    new EscrowPrefix.State(data.ledger().id(), slot.beforeEpoch(), slot.beforeSignature(), List.of()),
                    EscrowGovernedDestinationDisk.validator(record, slot, state.sourceEnvelope()), server.getTickCount(), result -> {
                        pending.pending = false;
                        if (closed || work.get(record.transferId()) != pending || !pending.authority.valid() || !pending.sourceValid()
                                || !finalBoundary.getAsBoolean()) {
                            if (result.proof() != null) { result.proof().release(); }
                            pending.release(); work.remove(record.transferId(), pending); return;
                        }
                        if (result.confirmed()) { pending.proofs.add(result.proof()); runtime.checkpoint("F4_ABSENCE_READBACK_AFTER"); }
                        else { pending.retry(server.getTickCount(), result.reason() == EscrowReason.NONE
                                ? "SUPERSEDING_SAVE" : result.reason().name()); }
                    });
            if (!started) { state.pending = false; }
            return Result.WAITING;
        }
        var ready = state;
        var permit = EscrowGovernedDestinationAction.issue(authority, state.sourceEnvelope(), time, level.getGameTime(),
                () -> source == null ? null : EscrowGovernedEnvelope.capture(source), finalBoundary, () -> ready.sourceValid() && ready.proved());
        var created = ModEntities.ROCKET.get().create(level);
        if (created == null) {
            if (!EscrowGovernedReturnAction.phase(record)) { state.retry(server.getTickCount(), "DESTINATION_CREATE_FAILED"); return Result.HELD; }
            state.release(); state.failure = Result.CREATE_FAILED; return state.failure;
        }
        created.initializeGovernedDestination(permit);
        EscrowTags.require(record.nativeTransfer().orElseThrow().destinationEnvelope(record).equals(EscrowGovernedEnvelope.capture(created)),
                "Initialized destination differs from its complete envelope");
        EscrowTags.require(authority.valid() && finalBoundary.getAsBoolean() && state.sourceValid() && state.proved(),
                "Destination changed before publication");
        runtime.checkpoint("F4_PUBLICATION_BEFORE");
        boolean added = level.addFreshEntity(created);
        state.release();
        // A spawn veto or an unexpected join never authorizes source deletion or a refund.
        if (!added) {
            if (!EscrowGovernedReturnAction.phase(record)) { work.remove(record.transferId()); return Result.HELD; }
            state.failure = Result.SPAWN_FAILED; return state.failure;
        }
        work.remove(record.transferId());
        runtime.checkpoint("F4_PUBLICATION_AFTER");
        if (!boundary.allows(record, source, created)) { return Result.HELD; }
        boolean exact = java.util.Objects.equals(state.sourceEnvelope(), source == null ? null : EscrowGovernedEnvelope.capture(source))
                && record.nativeTransfer().orElseThrow().destinationEnvelope(record).equals(EscrowGovernedEnvelope.capture(created));
        if (!exact) { runtime.executorHolds().hold(server, record, EscrowGovernedExecutorHolds.Reason.DESTINATION_ENVELOPE); }
        return exact ? Result.PUBLISHED : Result.HELD;
    }

    void observe(RocketEntity rocket) {
        work.values().stream().filter(value -> value.source != null && value.source.rocket == rocket).forEach(value -> value.source.observe());
    }

    @Override public void close() { closed = true; work.values().forEach(Work::release); work.clear(); retries.clear(); envelopeHolds.clear(); }

    static final class Work {
        final RocketTransferRecord record;
        final EscrowGovernedDispatch.Authority authority;
        final EscrowFlightSource source;
        RocketEntity arrival;
        final List<EscrowEndpoint> slots;
        final java.util.ArrayList<EscrowBarrier.Proof> proofs = new java.util.ArrayList<>();
        boolean pending;
        final EscrowSuccessorRetry retry;
        Result failure;
        Work(RocketTransferRecord record, EscrowGovernedDispatch.Authority authority, EscrowFlightSource source,
             EscrowSuccessorRetry retry) {
            this.record = record; this.authority = authority; this.source = source;
            this.retry = retry;
            var destination = EscrowGovernedDestinationDisk.endpoint(record, true);
            var sourceSlot = EscrowGovernedDestinationDisk.endpoint(record, false);
            slots = EscrowBarrier.sameSlot(sourceSlot, destination) ? List.of(sourceSlot) : List.of(sourceSlot, destination);
        }
        boolean sourceValid() { return source == null || source.observe(); }
        boolean ready(long tick, BooleanSupplier capacity) {
            return !pending && retry.ready(tick) && capacity.getAsBoolean();
        }
        net.minecraft.nbt.CompoundTag sourceEnvelope() { return source == null ? null : source.envelope; }
        boolean proved() { return proofs.size() == slots.size() && proofs.stream().allMatch(EscrowBarrier.Proof::valid); }
        void release() { proofs.forEach(EscrowBarrier.Proof::release); proofs.clear(); }
        void retry(long tick, String reason) {
            release();
            var arm = record.nativeTransfer().orElseThrow().armed();
            retry.failed(arm.ledger(), arm.logical(), arm.transfer(), arm.serial(), "F4_PROOF", reason, tick);
        }
    }
}
