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
    private final EscrowTerminalRetries retries = new EscrowTerminalRetries();
    private boolean closed;

    public Result tick(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
                       RocketEntity survivor, EscrowFlightTables.Outcome outcome, BooleanSupplier boundary) {
        if (closed || !server.isSameThread()) { return Result.HELD; }
        work.entrySet().removeIf(entry -> {
            if (!journal.actionAuthorityAvailable() || entry.getValue().queued || journal.find(entry.getKey()).isPresent()) { return false; }
            entry.getValue().close(); return true;
        });
        if (journal.actionAuthorityAvailable()) {
            retries.retain(logical -> journal.findByLogicalRocket(logical).isPresent()
                    || work.values().stream().anyMatch(state -> state.record.logicalRocketId().equals(logical)));
        }
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
        // Retain the owner of an in-flight callback, including an invalidated F6 capture.
        // Its completion releases the exact watch and records the failure before recapture.
        if (state != null && (state.queued || state.pending)) { return Result.WAITING; }
        // A recapture or a different proposed T is not permission to bypass this logical flight's backoff.
        if (!retries.ready(record.logicalRocketId(), server.getTickCount())) { return Result.WAITING; }
        if (state != null && state.proofs.size() < state.slots.size()
                && !EscrowRuntime.of(server).barrier().available(state.slots.get(state.proofs.size()), server.getTickCount())) {
            return Result.WAITING;
        }
        var data = ResourceEscrowSavedData.existing(server);
        if (data == null || survivor == null) { return Result.HELD; }
        var coordinator = data.bind(server);
        if (!coordinator.governedAvailable(record.logicalRocketId()) || !journal.actionAuthorityAvailable()
                || journal.find(record.transferId()).filter(record::equals).isEmpty()) { return Result.HELD; }
        var decision = EscrowGovernedDispatch.decide(data.ledger(), record);
        if (decision.route() == EscrowGovernedDispatch.Route.CLEANUP) {
            if (state != null) { state.close(); work.remove(record.transferId(), state); }
            retries.completed(record.logicalRocketId());
            return Result.ACKNOWLEDGED;
        }
        if (decision.route() != EscrowGovernedDispatch.Route.ACTIVE
                || !EscrowGovernedTerminalPassengers.present(server, record, outcome, survivor) || !boundary.getAsBoolean()
                || !EscrowGovernedTerminalPassengers.resolved(server, record, outcome, survivor)) { return Result.HELD; }
        if (state != null && (!state.record.equals(record) || state.source.rocket != survivor
                || state.outcome != outcome || !state.source.observe())) {
            state.close(); work.remove(record.transferId(), state); state = null;
        }
        if (state == null) {
            requireCapacity(work, record.transferId());
            retries.history(record.logicalRocketId());
            state = new Work(record, outcome, new EscrowFlightSource(survivor,
                    () -> EscrowGovernedTerminalPassengers.resolved(server, record, outcome, survivor) && boundary.getAsBoolean()));
            work.put(record.transferId(), state);
        }
        if (state.proofs.values().stream().anyMatch(proof -> !proof.valid())) {
            retry(state, "F6_PROOF", "SUPERSEDING_SAVE", server.getTickCount()); return Result.WAITING;
        }
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
                        if (closed || work.get(record.transferId()) != pending) {
                            if (result.proof() != null) { result.proof().release(); }
                            pending.release(); return;
                        }
                        if (!pending.source.observe() || !journal.actionAuthorityAvailable()
                                || journal.find(record.transferId()).filter(record::equals).isEmpty()) {
                            if (result.proof() != null) { result.proof().release(); }
                            retry(pending, "F6_PROOF", "SOURCE_OR_JOURNAL_CHANGED", server.getTickCount()); return;
                        }
                        if (result.confirmed()) { pending.proofs.put(slot, result.proof()); }
                        else {
                            if (result.proof() != null) { result.proof().release(); }
                            retry(pending, "F6_PROOF", result.reason() == EscrowReason.NONE
                                    ? "SUPERSEDING_SAVE" : result.reason().name(), server.getTickCount());
                        }
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
            if (closed || work.get(record.transferId()) != pending) { return; }
            if (committed) {
                work.remove(record.transferId(), pending); retries.completed(record.logicalRocketId());
                EscrowRuntime.of(server).checkpoint("F7_ACK");
            } else { retry(pending, "F7_WRITE", "LEDGER_WRITE_FAILED", server.getTickCount()); }
        });
        if (!state.queued) { retry(state, "F7_WRITE", "LEDGER_QUEUE_WAIT", server.getTickCount()); }
        return Result.WAITING;
    }

    private void retry(Work state, String phase, String reason, long tick) {
        state.release(); retries.failed(state.record, phase, reason, tick);
    }

    void observe(RocketEntity rocket) {
        for (var state : work.values()) {
            if (state.source.rocket == rocket) {
                if (!state.source.observe() && !state.queued) { state.release(); }
            }
            else if (rocket.assemblyTransactionId().filter(state.record.logicalRocketId()::equals).isPresent()) {
                invalidate(state);
            }
        }
    }

    void invalidated(RocketEntity rocket) {
        work.values().stream().filter(state -> rocket.assemblyTransactionId().filter(state.record.logicalRocketId()::equals).isPresent())
                .forEach(EscrowGovernedTerminal::invalidate);
    }

    void passengerLeft(UUID passenger) {
        work.values().stream().filter(state -> state.record.sourceFlightData().passengers().assignment(passenger).isPresent())
                .forEach(EscrowGovernedTerminal::invalidate);
    }

    private static void invalidate(Work state) {
        state.source.unchanged.set(false);
        if (!state.queued) { state.release(); }
    }

    /** Read-only F6 status; a cached success never replaces a fresh joint prerequisite. */
    public boolean checkpoint(MinecraftServer server, RocketTransferRecord record) {
        if (closed || !server.isSameThread()) { return false; }
        var state = work.get(record.transferId());
        var journal = RocketTransferSavedData.get(server);
        return state != null && state.record == record && state.source.observe()
                && journal.actionAuthorityAvailable() && journal.find(record.transferId()).filter(record::equals).isPresent()
                && state.proofs.size() == state.slots.size() && state.proofs.values().stream().allMatch(EscrowBarrier.Proof::valid);
    }

    static void requireCapacity(Map<UUID, ?> work, UUID transfer) {
        EscrowTags.require(work.containsKey(transfer) || work.size() < RocketFlightLimits.MAX_ACTIVE_TRANSFERS,
                "Terminal checkpoint work limit exceeded");
    }

    @Override public void close() { closed = true; work.values().forEach(Work::close); work.clear(); retries.clear(); }

    private static final class Work implements AutoCloseable {
        final RocketTransferRecord record;
        final EscrowFlightTables.Outcome outcome;
        final EscrowFlightSource source;
        final List<EscrowEndpoint> slots;
        final Map<EscrowEndpoint, EscrowBarrier.Proof> proofs = new LinkedHashMap<>();
        EscrowGovernedHandoff.Proof handoff;
        boolean pending;
        boolean queued;
        Work(RocketTransferRecord record, EscrowFlightTables.Outcome outcome, EscrowFlightSource source) {
            this.record = record; this.outcome = outcome; this.source = source;
            slots = EscrowGovernedTerminalDisk.slots(record, outcome);
        }
        void release() {
            if (handoff != null) { handoff.close(); handoff = null; }
            proofs.values().forEach(EscrowBarrier.Proof::release); proofs.clear();
        }
        @Override public void close() { source.unchanged.set(false); release(); }
    }
}
