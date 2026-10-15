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
    private static final class Work {
        EscrowFlightSource source;
        EscrowBarrier.Proof proof;
        boolean pending;
        long retryAt;
        void release() { if (proof != null) { proof.release(); proof = null; } source = null; }
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
        var state = work.computeIfAbsent(arm.logical(), ignored -> new Work());
        if (state.source != null) { state.source.observe(); }
        if (state.pending || server.getTickCount() < state.retryAt) { return; }
        var journal = RocketTransferSavedData.get(server);
        if (!journal.retryChecked(server)) { return; }
        var noTransfer = EscrowFlightJournal.noTransfer(journal, arm.logical(), arm.transfer(), arm.serial());
        if (!noTransfer.getAsBoolean() || arm.outcome() != EscrowFlightTables.Outcome.ARMED) { state.release(); return; }
        try {
            RocketEntity rocket = source(server, arm);
            if (state.source == null || !state.source.observe()) { state.release(); state.source = new EscrowFlightSource(rocket); }
            var frozen = state.source;
            if (!eligible(coordinator.ledger(), arm, frozen.digest, noTransfer)) {
                state.release(); state.retryAt = server.getTickCount() + 200; return;
            }
            if (state.proof != null && !state.proof.valid()) { state.release(); return; }
            if (state.proof == null) {
                state.pending = true;
                boolean started = runtime.barrier().begin(runtime.store((ServerLevel) rocket.level()), frozen.endpoint, frozen.frontier,
                        frozen.validator(), server.getTickCount(), result -> {
                            state.pending = false;
                            if (closed || !result.confirmed() || !frozen.observe() || !noTransfer.getAsBoolean()) {
                                if (result.proof() != null) { result.proof().release(); }
                                state.release(); state.retryAt = server.getTickCount() + 100; return;
                            }
                            state.proof = result.proof();
                        });
                if (!started) { state.pending = false; }
                return;
            }
            var proof = state.proof;
            state.pending = coordinator.flightWrite(arm.logical(), (root, generation) -> {
                EscrowTags.require(eligible(root, arm, frozen.digest, noTransfer) && frozen.observe(), "Pre-F2 cancellation lost its source");
                return EscrowFlightAuthority.finish(root, arm, EscrowFlightTables.Outcome.CANCELLED, arm.source(), arm.epoch(),
                        frozen.signature, frozen.invariant);
            }, () -> proof.valid() && frozen.unchanged.get() && noTransfer.getAsBoolean(), committed -> {
                state.pending = false; state.release();
                if (committed) { runtime.checkpoint("F1_CANCELLED"); }
                else { state.retryAt = server.getTickCount() + 100; }
            });
        } catch (IllegalArgumentException | java.util.NoSuchElementException refused) {
            state.release(); state.retryAt = server.getTickCount() + 200;
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
        EscrowTags.require(source != null && source.operational() && source.ownerId().filter(arm.owner()::equals).isPresent()
                && source.assemblyTransactionId().filter(arm.logical()::equals).isPresent()
                && source.escrowSection().ledger() != null && source.escrowSection().ledger().equals(arm.ledger())
                && source.escrowSection().epoch() == arm.epoch(), "Missing or changed source; no reconstruction");
        return source;
    }

    @Override public void close() { closed = true; work.values().forEach(Work::release); work.clear(); }
}
