package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** F1 orphan recovery. This branch cannot return a source after F2 or publish any destination. */
final class EscrowPreF2Cancellation implements AutoCloseable {
    static final class Work {
        EscrowFlightSource source;
        EscrowBarrier.Proof proof;
        boolean pending;
        final EscrowSuccessorRetry retry = new EscrowSuccessorRetry();
        void release() { if (proof != null) { proof.release(); proof = null; } source = null; }
        void failed(EscrowFlightTables.Armed arm, String phase, String reason, long tick) {
            release(); retry.failed(arm.ledger(), arm.logical(), arm.transfer(), arm.serial(), phase, reason, tick);
        }
    }
    private final Map<UUID, Work> work = new HashMap<>();
    private int cursor;
    private boolean closed;

    static boolean eligible(EscrowLedger root, EscrowFlightTables.Armed arm, String sourceEnvelope, BooleanSupplier noTransfer) {
        return arm.outcome() == EscrowFlightTables.Outcome.ARMED && root.id().equals(arm.ledger())
                && root.armedFlights().contains(arm) && arm.journal().transfer() == null
                && arm.sourceEnvelope().equals(sourceEnvelope) && noTransfer.getAsBoolean()
                && root.records().stream().noneMatch(op -> op.guard().rocket().equals(arm.logical())
                        || arm.logical().equals(op.source().logicalRocket()) || arm.logical().equals(op.destination().logicalRocket()));
    }

    void observe(RocketEntity rocket) {
        var state = work.get(rocket.assemblyTransactionId().orElse(null));
        if (state != null && state.source != null) { state.source.observe(); }
    }

    void invalidated(RocketEntity rocket) {
        var state = work.get(rocket.assemblyTransactionId().orElse(null));
        if (state != null && state.source != null) { state.source.unchanged.set(false); }
    }

    void tick(MinecraftServer server, EscrowCoordinator coordinator, EscrowRuntime runtime) {
        if (closed || !coordinator.operational()) { return; }
        var arms = coordinator.ledger().armedFlights();
        work.entrySet().removeIf(entry -> {
            if (arms.stream().anyMatch(arm -> arm.logical().equals(entry.getKey()))) { return false; }
            entry.getValue().release(); return true;
        });
        if (arms.isEmpty()) { return; }
        var arm = arms.get(Math.floorMod(cursor++, arms.size()));
        var observation = runtime.timings();
        if (arm.outcome() == EscrowFlightTables.Outcome.ARMED) { EscrowConsumerTimingProbe.mark(server, "cancel.attempt"); }
        var state = work.computeIfAbsent(arm.logical(), ignored -> new Work());
        if (state.pending || !state.retry.ready(server.getTickCount())) { observation.sample("cancel.wait.PENDING_OR_RETRY", 0); return; }
        if (state.source != null) { state.source.observe(); }
        var journal = RocketTransferSavedData.get(server);
        // R17 item 3: wait for L's checked state, not for a quiet journal.
        if (!journal.retryChecked(server) && !journal.actionAuthorityAvailable(arm.logical())) { observation.sample("cancel.wait.JOURNAL", 0); return; }
        var noTransfer = EscrowFlightJournal.noTransfer(journal, arm.logical(), arm.transfer(), arm.serial());
        if (!noTransfer.getAsBoolean() || arm.outcome() != EscrowFlightTables.Outcome.ARMED) { observation.sample("cancel.wait.NOT_ARMED_OR_NO_TRANSFER", 0); state.release(); return; }
        if (!coordinator.governedAvailable(arm.logical())) { observation.sample("cancel.wait.COORDINATOR", 0); return; }
        try {
            RocketEntity rocket = source(server, arm);
            // Already-loaded UUID polling is cheap and silent; absence is not authority to reconstruct.
            if (rocket == null) { observation.sample("cancel.wait.SOURCE_UNLOADED", 0); state.release(); return; }
            if (state.proof == null && !runtime.barrier().available(rocket.level().dimension().location().toString(),
                    true, server.getTickCount())) { observation.sample("cancel.wait.BARRIER_BUDGET", 0); return; }
            if (state.source == null || !state.source.observe()) { state.release(); state.source = new EscrowFlightSource(rocket); }
            var frozen = state.source;
            if (!eligible(coordinator.ledger(), arm, frozen.digest, noTransfer)) {
                observation.sample("cancel.stall.SOURCE_OR_AUTHORITY_CHANGED", 0);
                state.failed(arm, "F1_CANCEL_PROOF", "SOURCE_OR_AUTHORITY_CHANGED", server.getTickCount()); return;
            }
            if (state.proof != null && !state.proof.valid()) {
                observation.sample("cancel.stall.SUPERSEDING_SAVE", 0);
                state.failed(arm, "F1_CANCEL_PROOF", "SUPERSEDING_SAVE", server.getTickCount()); return;
            }
            if (state.proof == null) {
                state.pending = true;
                long barrierBegan = observation == EscrowTimingSink.NONE ? 0 : System.nanoTime();
                boolean started = runtime.barrier().begin(runtime.store((ServerLevel) rocket.level()), frozen.endpoint, frozen.frontier,
                        frozen.validator(), server.getTickCount(), result -> {
                            if (observation != EscrowTimingSink.NONE) { observation.sample("cancel.barrier", System.nanoTime() - barrierBegan); }
                            state.pending = false;
                            if (closed || !result.confirmed() || !frozen.observe() || !noTransfer.getAsBoolean()) {
                                String reason = result.confirmed() ? "SOURCE_OR_JOURNAL_CHANGED"
                                        : result.reason() == EscrowReason.NONE ? "SUPERSEDING_SAVE" : result.reason().name();
                                observation.sample("cancel.stall." + reason, 0);
                                if (result.proof() != null) { result.proof().release(); }
                                state.release();
                                if (!closed) { state.failed(arm, "F1_CANCEL_PROOF", reason, server.getTickCount()); }
                                return;
                            }
                            state.proof = result.proof();
                        });
                if (!started) { observation.sample("cancel.wait.BARRIER_BEGIN", 0); state.pending = false; }
                return;
            }
            var proof = state.proof;
            state.pending = coordinator.flightWrite(arm.logical(), (root, generation) -> {
                EscrowTags.require(eligible(root, arm, frozen.digest, noTransfer) && frozen.observe(), "Pre-F2 cancellation lost its source");
                return EscrowFlightAuthority.finish(root, arm, EscrowFlightTables.Outcome.CANCELLED, arm.source(), arm.epoch(),
                        frozen.signature, frozen.invariant);
            }, () -> proof.valid() && frozen.unchanged.get() && noTransfer.getAsBoolean(), committed -> {
                state.pending = false; state.release();
                if (committed) { EscrowConsumerTimingProbe.mark(server, "cancel.complete"); runtime.checkpoint("F1_CANCELLED"); }
                else if (!closed) { observation.sample("cancel.stall.LEDGER_WRITE_FAILED", 0); state.failed(arm, "F1_CANCEL_WRITE", "LEDGER_WRITE_FAILED", server.getTickCount()); }
            });
            if (!state.pending) { observation.sample("cancel.wait.LEDGER_QUEUE_OR_SYNC_COMPLETION", 0); }
            // A full checked-writer queue is an ordinary silent wait, not a failed write.
        } catch (IllegalArgumentException | java.util.NoSuchElementException refused) {
            observation.sample("cancel.stall.SOURCE_OR_AUTHORITY_CHANGED", 0);
            state.failed(arm, "F1_CANCEL_PROOF", "SOURCE_OR_AUTHORITY_CHANGED", server.getTickCount());
        }
    }

    private static RocketEntity source(MinecraftServer server, EscrowFlightTables.Armed arm) {
        RocketEntity source = null; int levels = 0;
        for (var level : server.getAllLevels()) {
            EscrowTags.require(++levels <= 64, "Source Level observation bound exceeded");
            var entity = level.getEntity(arm.source());
            if (entity == null) { continue; }
            EscrowTags.require(source == null && entity instanceof RocketEntity, "Ambiguous source identity");
            source = (RocketEntity) entity;
        }
        if (source == null) { return null; }
        EscrowTags.require(source.operational() && source.ownerId().filter(arm.owner()::equals).isPresent()
                && source.assemblyTransactionId().filter(arm.logical()::equals).isPresent()
                && source.escrowSection().ledger() != null && source.escrowSection().ledger().equals(arm.ledger())
                && source.escrowSection().epoch() == arm.epoch(), "Missing or changed source; no reconstruction");
        return source;
    }

    @Override public void close() { closed = true; work.values().forEach(Work::release); work.clear(); }
}
