package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** Live F6 joint checkpoint and F7 writer adapter. No terminal entry permits forward flight. */
public final class EscrowGovernedTerminal implements AutoCloseable {
    public enum Result { HELD, WAITING, ACKNOWLEDGED }
    private final Map<UUID, Work> work = new HashMap<>();
    private boolean closed;

    public Result tick(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
                       RocketEntity survivor, EscrowFlightTables.Outcome outcome, BooleanSupplier boundary) {
        if (closed || !server.isSameThread()) { return Result.HELD; }
        work.entrySet().removeIf(entry -> {
            if (entry.getValue().queued || journal.find(entry.getKey()).isPresent()) { return false; }
            entry.getValue().close(); return true;
        });
        try { return checked(server, journal, record, survivor, outcome, boundary); }
        catch (IllegalArgumentException | IllegalStateException | ArithmeticException refused) {
            var state = work.get(record.transferId());
            if (state != null && !state.queued) { state.close(); work.remove(record.transferId(), state); }
            return Result.HELD;
        }
    }

    private Result checked(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
                           RocketEntity survivor, EscrowFlightTables.Outcome outcome, BooleanSupplier boundary) {
        var state = work.get(record.transferId());
        if (state != null && state.queued) { return Result.WAITING; }
        var data = ResourceEscrowSavedData.existing(server);
        if (data == null || survivor == null) { return Result.HELD; }
        var coordinator = data.bind(server);
        if (!coordinator.governedAvailable(record.logicalRocketId()) || !journal.actionAuthorityAvailable()
                || journal.find(record.transferId()).filter(record::equals).isEmpty()) { return Result.HELD; }
        var decision = EscrowGovernedDispatch.decide(data.ledger(), record);
        if (decision.route() == EscrowGovernedDispatch.Route.CLEANUP) { return Result.ACKNOWLEDGED; }
        if (decision.route() != EscrowGovernedDispatch.Route.ACTIVE || !boundary.getAsBoolean()
                || !EscrowGovernedTerminalPassengers.resolved(server, record, outcome, survivor)) { return Result.HELD; }
        if (state != null && (!state.record.equals(record) || state.source.rocket != survivor
                || state.outcome != outcome || !state.source.observe())) {
            state.close(); work.remove(record.transferId(), state); state = null;
        }
        if (state == null) {
            requireCapacity(work, record.transferId());
            state = new Work(record, outcome, new EscrowFlightSource(survivor, () -> boundary.getAsBoolean()
                    && EscrowGovernedTerminalPassengers.resolved(server, record, outcome, survivor)));
            work.put(record.transferId(), state);
        }
        if (state.pending || server.getTickCount() < state.nextAttempt) { return Result.WAITING; }
        if (state.proofs.values().stream().anyMatch(proof -> !proof.valid())) { state.retry(server.getTickCount()); return Result.WAITING; }
        var pending = state;
        if (state.proofs.size() < state.slots.size()) {
            var slot = state.slots.get(state.proofs.size());
            var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.tryParse(slot.level())));
            if (level == null) { return Result.HELD; }
            var runtime = EscrowRuntime.of(server); state.pending = true;
            boolean started = runtime.barrier().begin(runtime.store(level), slot,
                    new EscrowPrefix.State(data.ledger().id(), slot.beforeEpoch(), slot.beforeSignature(), List.of()),
                    EscrowGovernedTerminalDisk.validator(record, outcome, slot, state.source.envelope), server.getTickCount(), result -> {
                        pending.pending = false;
                        if (closed || work.get(record.transferId()) != pending || !pending.source.observe()
                                || !journal.actionAuthorityAvailable() || journal.find(record.transferId()).filter(record::equals).isEmpty()) {
                            if (result.proof() != null) { result.proof().release(); }
                            pending.release(); return;
                        }
                        if (result.confirmed()) { pending.proofs.put(slot, result.proof()); }
                        else { pending.retry(server.getTickCount()); }
                    });
            if (!started) { state.pending = false; }
            return Result.WAITING;
        }
        var proof = new EscrowGovernedHandoff.Proof(journal, record, outcome, state.source.envelope, state.proofs,
                () -> EscrowNativeEnvelope.rocket(survivor), pending.source::observe, pending.source.unchanged::get);
        state.handoff = proof;
        state.queued = EscrowGovernedHandoff.request(coordinator, journal, proof, committed -> {
            pending.queued = false;
            pending.release();
            if (committed) { work.remove(record.transferId(), pending); }
            else { pending.retry(server.getTickCount()); }
        });
        if (!state.queued) { state.retry(server.getTickCount()); }
        return Result.WAITING;
    }

    void observe(RocketEntity rocket) {
        for (var state : work.values()) {
            if (state.source.rocket == rocket) { state.source.observe(); }
            else if (rocket.assemblyTransactionId().filter(state.record.logicalRocketId()::equals).isPresent()) {
                state.source.unchanged.set(false);
            }
        }
    }

    void invalidated(RocketEntity rocket) {
        work.values().stream().filter(state -> rocket.assemblyTransactionId().filter(state.record.logicalRocketId()::equals).isPresent())
                .forEach(state -> state.source.unchanged.set(false));
    }

    void passengerLeft(UUID passenger) {
        work.values().stream().filter(state -> state.record.sourceFlightData().passengers().assignment(passenger).isPresent())
                .forEach(state -> state.source.unchanged.set(false));
    }

    static void requireCapacity(Map<UUID, ?> work, UUID transfer) {
        EscrowTags.require(work.containsKey(transfer) || work.size() < RocketFlightLimits.MAX_ACTIVE_TRANSFERS,
                "Terminal checkpoint work limit exceeded");
    }

    @Override public void close() { closed = true; work.values().forEach(Work::close); work.clear(); }

    private static final class Work implements AutoCloseable {
        final RocketTransferRecord record;
        final EscrowFlightTables.Outcome outcome;
        final EscrowFlightSource source;
        final List<EscrowEndpoint> slots;
        final Map<EscrowEndpoint, EscrowBarrier.Proof> proofs = new LinkedHashMap<>();
        EscrowGovernedHandoff.Proof handoff;
        boolean pending;
        boolean queued;
        int failures;
        long nextAttempt;
        Work(RocketTransferRecord record, EscrowFlightTables.Outcome outcome, EscrowFlightSource source) {
            this.record = record; this.outcome = outcome; this.source = source;
            slots = EscrowGovernedTerminalDisk.slots(record, outcome);
        }
        void release() {
            if (handoff != null) { handoff.close(); handoff = null; }
            proofs.values().forEach(EscrowBarrier.Proof::release); proofs.clear();
        }
        void retry(long tick) {
            release(); failures = Math.min(3, failures + 1);
            nextAttempt = tick + (failures == 1 ? 100 : failures == 2 ? 400 : 1_200);
        }
        @Override public void close() { source.unchanged.set(false); release(); }
    }
}
