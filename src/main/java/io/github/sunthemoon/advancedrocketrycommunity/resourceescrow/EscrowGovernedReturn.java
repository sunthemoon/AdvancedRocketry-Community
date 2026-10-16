package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** Live exact-source returns, ending at F6. No terminal ledger or journal mutation is authorized here. */
public final class EscrowGovernedReturn implements AutoCloseable {
    public enum Result { HELD, WAITING, CHECKPOINT }
    private final Map<UUID, Work> work = new HashMap<>();
    private boolean closed;

    public boolean request(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
                           RocketEntity source, BooleanSupplier boundary, Runnable checkpointed) {
        if (closed || !server.isSameThread() || source == null || work.containsKey(record.transferId())) { return false; }
        prune(journal);
        try {
            var authority = authority(server, journal, record);
            if (authority == null || !EscrowGovernedReturnAction.phase(record) || !boundary.getAsBoolean()
                    || !EscrowRuntime.of(server).flightPhases().allowsNative(source, null)) { return false; }
            var capture = new EscrowFlightSource(source, boundary);
            if (!EscrowGovernedPhaseAction.sourceMatches(record, capture.envelope, source.level().getGameTime())) { return false; }
            requireCapacity(work, record.transferId());
            work.put(record.transferId(), new Work(record, authority, capture, false, checkpointed));
            return true;
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException refused) { return false; }
    }

    /** A pending request or an already-returned complete progression must never enter forward dispatch. */
    public boolean handles(RocketTransferRecord record, RocketEntity source) {
        if (work.containsKey(record.transferId())) { return true; }
        try { return source != null && EscrowGovernedReturnAction.returned(record, EscrowNativeEnvelope.rocket(source)); }
        catch (IllegalArgumentException | IllegalStateException invalid) { return false; }
    }

    public Result tick(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
                       RocketEntity source, BooleanSupplier boundary) {
        if (closed || !server.isSameThread()) { return Result.HELD; }
        prune(journal);
        try { return checked(server, journal, record, source, boundary); }
        catch (IllegalArgumentException | IllegalStateException | ArithmeticException refused) {
            var state = work.get(record.transferId());
            if (state != null) { state.release(); }
            return Result.HELD;
        }
    }

    private Result checked(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
                           RocketEntity source, BooleanSupplier boundary) {
        if (source == null || !EscrowGovernedReturnAction.phase(record) || !boundary.getAsBoolean()) { return Result.HELD; }
        var authority = authority(server, journal, record);
        if (authority == null) { return Result.HELD; }
        var state = work.get(record.transferId());
        if (state == null) {
            var capture = new EscrowFlightSource(source, boundary);
            if (!EscrowGovernedReturnAction.returned(record, capture.envelope)) { return Result.HELD; }
            requireCapacity(work, record.transferId());
            state = new Work(record, authority, capture, true, () -> { }); work.put(record.transferId(), state);
        }
        if (!state.record.equals(record) || state.source.rocket != source || !state.source.observe()) { return Result.HELD; }
        if (state.pending) { return Result.WAITING; }
        if (!state.authority.valid()) { state.release(); state.authority = authority; }
        if (state.proofs.stream().anyMatch(proof -> !proof.valid())) { state.retry(server.getTickCount()); }
        if (server.getTickCount() < state.nextAttempt) { return Result.WAITING; }
        if (!state.proved()) {
            var pending = state; var slot = state.slots.get(state.proofs.size());
            var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.tryParse(slot.level())));
            if (level == null) { return Result.HELD; }
            var runtime = EscrowRuntime.of(server);
            state.pending = true;
            boolean started = runtime.barrier().begin(runtime.store(level), slot,
                    new EscrowPrefix.State(authority.ledger().id(), slot.beforeEpoch(), slot.beforeSignature(), List.of()),
                    state.returned ? EscrowGovernedTerminalDisk.validator(record, EscrowFlightTables.Outcome.CANCELLED, slot, state.source.envelope)
                            : EscrowGovernedDestinationDisk.validator(record, slot, state.source.envelope), server.getTickCount(), result -> {
                        pending.pending = false;
                        if (closed || work.get(record.transferId()) != pending || !pending.authority.valid()
                                || !pending.source.observe() || !boundary.getAsBoolean()) {
                            if (result.proof() != null) { result.proof().release(); }
                            pending.release(); return;
                        }
                        if (result.confirmed()) { pending.proofs.add(result.proof()); }
                        else { pending.retry(server.getTickCount()); }
                    });
            if (!started) { state.pending = false; }
            return Result.WAITING;
        }
        if (state.returned) {
            if (!EscrowGovernedTerminalPassengers.resolved(server, record, EscrowFlightTables.Outcome.CANCELLED, source)) {
                return Result.HELD;
            }
            if (!state.notified) { state.notified = true; state.checkpointed.run(); }
            return Result.CHECKPOINT;
        }
        var ready = state;
        var permit = EscrowGovernedReturnAction.issue(authority, state.source.envelope, source.level().getGameTime(),
                source.level().getGameTime(), () -> EscrowNativeEnvelope.rocket(source), boundary,
                () -> ready.source.observe() && ready.proved());
        source.returnGovernedSource(permit);
        state.release();
        EscrowTags.require(EscrowGovernedReturnAction.returned(record, EscrowNativeEnvelope.rocket(source)), "Return changed its complete projection");
        state.source = new EscrowFlightSource(source, boundary); state.returned = true;
        return Result.WAITING;
    }

    /** Read-only checkpoint status; every prerequisite is checked again, not remembered as a success flag. */
    public boolean checkpoint(MinecraftServer server, RocketTransferRecord record) {
        var state = work.get(record.transferId());
        return !closed && server.isSameThread() && state != null && state.record.equals(record) && state.returned
                && state.authority.valid() && state.source.observe() && state.proved()
                && EscrowGovernedTerminalPassengers.resolved(server, record, EscrowFlightTables.Outcome.CANCELLED, state.source.rocket);
    }

    private static EscrowGovernedDispatch.Authority authority(MinecraftServer server, RocketTransferSavedData journal,
                                                             RocketTransferRecord record) {
        var data = ResourceEscrowSavedData.existing(server);
        if (data == null) { return null; }
        var authority = EscrowRuntime.of(server).governedFlights().authority(data::ledger,
                () -> data.bind(server).governedAvailable(record.logicalRocketId()), journal, record);
        return authority != null && authority.decision().route() == EscrowGovernedDispatch.Route.ACTIVE ? authority : null;
    }

    static void requireCapacity(Map<UUID, ?> work, UUID transfer) {
        EscrowTags.require(work.containsKey(transfer) || work.size() < RocketFlightLimits.MAX_ACTIVE_TRANSFERS,
                "Return work limit exceeded");
    }

    private void prune(RocketTransferSavedData journal) {
        work.entrySet().removeIf(entry -> {
            if (journal.find(entry.getKey()).isPresent()) { return false; }
            entry.getValue().release(); return true;
        });
    }

    void observe(RocketEntity rocket) {
        work.values().stream().filter(state -> state.source.rocket == rocket).forEach(state -> state.source.observe());
    }

    @Override public void close() { closed = true; work.values().forEach(Work::release); work.clear(); }

    private static final class Work {
        final RocketTransferRecord record;
        EscrowGovernedDispatch.Authority authority;
        EscrowFlightSource source;
        final List<EscrowEndpoint> slots;
        final List<EscrowBarrier.Proof> proofs = new ArrayList<>();
        final Runnable checkpointed;
        boolean returned;
        boolean pending;
        boolean notified;
        int failures;
        long nextAttempt;
        Work(RocketTransferRecord record, EscrowGovernedDispatch.Authority authority, EscrowFlightSource source,
             boolean returned, Runnable checkpointed) {
            this.record = record; this.authority = authority; this.source = source;
            this.returned = returned; this.checkpointed = checkpointed;
            slots = EscrowGovernedTerminalDisk.slots(record, EscrowFlightTables.Outcome.CANCELLED);
        }
        boolean proved() { return proofs.size() == slots.size() && proofs.stream().allMatch(EscrowBarrier.Proof::valid); }
        void release() { proofs.forEach(EscrowBarrier.Proof::release); proofs.clear(); }
        void retry(long tick) {
            release(); failures = Math.min(3, failures + 1);
            nextAttempt = tick + (failures == 1 ? 100 : failures == 2 ? 400 : 1_200);
        }
    }
}
