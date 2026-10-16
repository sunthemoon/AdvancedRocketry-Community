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

/** F4 only. Publication retains ARMED and both entities; F5/F6 must independently prove their actions. */
public final class EscrowGovernedDestination implements AutoCloseable {
    public enum Result { HELD, WAITING, PUBLISHED, PRESENT }
    @FunctionalInterface public interface Boundary {
        boolean allows(RocketTransferRecord record, RocketEntity source, RocketEntity destination);
    }
    private final Map<UUID, Work> work = new HashMap<>();
    private boolean closed;

    public Result tick(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
                       RocketEntity source, Boundary boundary) {
        if (closed || !server.isSameThread() || source == null) { return Result.HELD; }
        work.entrySet().removeIf(entry -> {
            if (journal.find(entry.getKey()).filter(entry.getValue().record::equals).isPresent()) { return false; }
            entry.getValue().release(); return true;
        });
        try { return checked(server, journal, record, source, boundary); }
        catch (IllegalArgumentException | IllegalStateException | ArithmeticException refused) {
            var state = work.remove(record.transferId()); if (state != null) { state.release(); }
            return Result.HELD;
        }
    }

    private Result checked(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
                           RocketEntity source, Boundary boundary) {
        if (record.phase() != RocketTransferPhase.PREPARED && record.phase() != RocketTransferPhase.DESTINATION_SPAWNED) { return Result.HELD; }
        var runtime = EscrowRuntime.of(server); var data = ResourceEscrowSavedData.existing(server);
        if (data == null) { return Result.HELD; }
        var authority = runtime.governedFlights().authority(data::ledger,
                () -> data.bind(server).governedAvailable(record.logicalRocketId()), journal, record);
        if (authority == null || authority.decision().route() != EscrowGovernedDispatch.Route.ACTIVE) { return Result.HELD; }
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, record.destinationSnapshot().sourceDimension()));
        if (level == null) { return Result.HELD; }
        var entity = level.getEntity(record.nativeTransfer().orElseThrow().armed().destination());
        if (entity != null && !(entity instanceof RocketEntity)) { return Result.HELD; }
        var destination = (RocketEntity) entity;
        BooleanSupplier finalBoundary = () -> boundary.allows(record, source, destination);
        if (!finalBoundary.getAsBoolean()) { return Result.HELD; }
        var cached = work.get(record.transferId());
        if (cached != null && cached.authority.valid() && cached.source.observe()) {
            if (destination != null && cached.arrival != null && cached.arrival.rocket == destination && cached.arrival.observe()) {
                return Result.PRESENT;
            }
            if (destination == null && (cached.pending || server.getTickCount() < cached.nextAttempt)) { return Result.WAITING; }
        }
        long time = runtime.sourceFlights().publicationTime(authority, source, finalBoundary);
        if (record.phase() == RocketTransferPhase.PREPARED) {
            if (!EscrowGovernedDestinationAction.sourceDue(record, EscrowNativeEnvelope.rocket(source), time)) { return Result.WAITING; }
            if (destination != null || !journal.canQueueEffect()) { return Result.HELD; }
            var next = record.destinationSpawned(record.nativeTransfer().orElseThrow().armed().destination());
            journal.put(next);
            runtime.checkpoint("F4_DESTINATION_REQUESTED");
            journal.afterAcknowledged(server, record.logicalRocketId(), () -> {
                runtime.checkpoint("F4_DESTINATION_ACK");
                // No captured permit or authority crosses this acknowledgement. Reacquire everything.
                return tick(server, journal, next, source, boundary) != Result.HELD;
            }, () -> { });
            return Result.WAITING;
        }
        // The acknowledged F4 may survive with only F1's E on disk. Do not require a second
        // source ascent or debit; validate the unchanged source at E or its permitted progression.
        if (!EscrowGovernedSourceRecovery.sourceMatches(record, EscrowNativeEnvelope.rocket(source), time)) { return Result.HELD; }
        if (destination != null) {
            var previous = work.remove(record.transferId()); if (previous != null) { previous.release(); }
            if (!record.nativeTransfer().orElseThrow().destinationEnvelope(record).equals(EscrowNativeEnvelope.rocket(destination))) { return Result.HELD; }
            if (work.size() >= RocketFlightLimits.MAX_ACTIVE_TRANSFERS) { return Result.HELD; }
            var present = new Work(record, authority, new EscrowFlightSource(source, finalBoundary));
            present.arrival = new EscrowFlightSource(destination, finalBoundary); work.put(record.transferId(), present);
            return Result.PRESENT;
        }
        var state = work.get(record.transferId());
        if (state != null && (!state.authority.valid() || !state.source.observe())) {
            state.release(); work.remove(record.transferId()); state = null;
        }
        if (state == null) {
            if (work.size() >= RocketFlightLimits.MAX_ACTIVE_TRANSFERS) { return Result.HELD; }
            state = new Work(record, authority, new EscrowFlightSource(source, finalBoundary)); work.put(record.transferId(), state);
        }
        if (state.pending || server.getTickCount() < state.nextAttempt) { return Result.WAITING; }
        if (state.proofs.stream().anyMatch(proof -> !proof.valid())) { state.retry(server.getTickCount()); return Result.WAITING; }
        if (state.proofs.size() < state.slots.size()) {
            var pending = state; var slot = state.slots.get(state.proofs.size());
            ServerLevel slotLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.tryParse(slot.level())));
            if (slotLevel == null) { return Result.HELD; }
            state.pending = true;
            runtime.checkpoint("F4_ABSENCE_STORE_BEFORE");
            boolean started = runtime.barrier().begin(runtime.store(slotLevel), slot,
                    new EscrowPrefix.State(data.ledger().id(), slot.beforeEpoch(), slot.beforeSignature(), List.of()),
                    EscrowGovernedDestinationDisk.validator(record, slot, state.source.envelope), server.getTickCount(), result -> {
                        pending.pending = false;
                        if (closed || work.get(record.transferId()) != pending || !pending.authority.valid() || !pending.source.observe()) {
                            if (result.proof() != null) { result.proof().release(); }
                            pending.release(); work.remove(record.transferId(), pending); return;
                        }
                        if (result.confirmed()) { pending.proofs.add(result.proof()); runtime.checkpoint("F4_ABSENCE_READBACK_AFTER"); }
                        else { pending.retry(server.getTickCount()); }
                    });
            if (!started) { state.pending = false; }
            return Result.WAITING;
        }
        var ready = state;
        var permit = EscrowGovernedDestinationAction.issue(authority, state.source.envelope, time, level.getGameTime(),
                () -> EscrowNativeEnvelope.rocket(source), finalBoundary, () -> ready.source.observe() && ready.proved());
        var created = ModEntities.ROCKET.get().create(level);
        if (created == null) { state.retry(server.getTickCount()); return Result.HELD; }
        created.initializeGovernedDestination(permit);
        EscrowTags.require(record.nativeTransfer().orElseThrow().destinationEnvelope(record).equals(EscrowNativeEnvelope.rocket(created)),
                "Initialized destination differs from its complete envelope");
        EscrowTags.require(authority.valid() && finalBoundary.getAsBoolean() && state.source.observe() && state.proved(),
                "Destination changed before publication");
        runtime.checkpoint("F4_PUBLICATION_BEFORE");
        boolean added = level.addFreshEntity(created);
        state.release(); work.remove(record.transferId());
        // A spawn veto or an unexpected join never authorizes source deletion or a refund.
        if (!added) { return Result.HELD; }
        runtime.checkpoint("F4_PUBLICATION_AFTER");
        return boundary.allows(record, source, created)
                && state.source.envelope.equals(EscrowNativeEnvelope.rocket(source))
                && record.nativeTransfer().orElseThrow().destinationEnvelope(record).equals(EscrowNativeEnvelope.rocket(created))
                ? Result.PUBLISHED : Result.HELD;
    }

    void observe(RocketEntity rocket) {
        work.values().stream().filter(value -> value.source.rocket == rocket).forEach(value -> value.source.observe());
    }

    @Override public void close() { closed = true; work.values().forEach(Work::release); work.clear(); }

    private static final class Work {
        final RocketTransferRecord record;
        final EscrowGovernedDispatch.Authority authority;
        final EscrowFlightSource source;
        EscrowFlightSource arrival;
        final List<EscrowEndpoint> slots;
        final java.util.ArrayList<EscrowBarrier.Proof> proofs = new java.util.ArrayList<>();
        boolean pending;
        long nextAttempt;
        int failures;
        Work(RocketTransferRecord record, EscrowGovernedDispatch.Authority authority, EscrowFlightSource source) {
            this.record = record; this.authority = authority; this.source = source;
            var destination = EscrowGovernedDestinationDisk.endpoint(record, true);
            slots = EscrowBarrier.sameSlot(source.endpoint, destination) ? List.of(source.endpoint) : List.of(source.endpoint, destination);
        }
        boolean proved() { return proofs.size() == slots.size() && proofs.stream().allMatch(EscrowBarrier.Proof::valid); }
        void release() { proofs.forEach(EscrowBarrier.Proof::release); proofs.clear(); }
        void retry(long tick) {
            release(); failures = Math.min(3, failures + 1);
            nextAttempt = tick + (failures == 1 ? 100 : failures == 2 ? 400 : 1_200);
        }
    }
}
