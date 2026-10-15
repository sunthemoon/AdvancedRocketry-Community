package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** Bounded loaded-only O2 -> O3a -> O3b -> O4. Restart reacquires every endpoint proof. */
final class EscrowSettlementRecovery implements AutoCloseable {
    private static final class Work {
        final Map<EscrowEndpoint.Key, EscrowBarrier.Proof> proofs = new HashMap<>();
        final Set<EscrowEndpoint.Key> busy = new HashSet<>();
        boolean writing;
        long retryAt;
        String phase = "O2";
        String lastReason;
        void clear() { proofs.values().forEach(EscrowBarrier.Proof::release); proofs.clear(); }
    }
    private final Map<String, Work> work = new HashMap<>();
    private final EscrowSettlementPruning.Cursor pruning = new EscrowSettlementPruning.Cursor();
    private EscrowLedger decodedRoot;
    private List<EscrowSettlementIntent> decodedIntents = List.of();
    private int cursor;
    private boolean closed;
    void tick(MinecraftServer server, EscrowCoordinator coordinator, EscrowRuntime runtime) {
        if (closed || runtime.stopping()) { return; }
        // Released groups progress independently of unrelated permanently held O1 decisions.
        pruning.tick(server, coordinator);
        var intents = intents(coordinator.ledger());
        var live = intents.stream().map(EscrowSettlementIntent::digest).collect(java.util.stream.Collectors.toSet());
        work.entrySet().removeIf(entry -> { if (live.contains(entry.getKey())) { return false; } entry.getValue().clear(); return true; });
        if (intents.isEmpty()) { return; }
        var intent = intents.get(Math.floorMod(cursor++, intents.size()));
        var state = work.computeIfAbsent(intent.digest(), ignored -> new Work());
        long tick = server.getTickCount();
        if (state.writing || !state.busy.isEmpty() || tick < state.retryAt) { return; }
        state.proofs.entrySet().removeIf(entry -> { if (entry.getValue().valid()) { return false; } entry.getValue().release(); return true; });
        try {
            var references = intent.observations().stream().map(EscrowSettlementIntent.Observation::endpoint).toList();
            var journal = RocketTransferSavedData.get(server);
            if (!journal.retryChecked(server)) { return; }
            boolean installed = EscrowSettlementTombstones.installed(coordinator.ledger(), intent);
            requireJournal(coordinator.ledger(), intent, journal, installed);
            if (EscrowSettlementNormalization.publish(server, coordinator, intent, journal.acknowledgedLease(), runtime)) { return; }
            var current = EscrowSettlementObserver.observe(server, intent.ledger(), references);
            EscrowTags.require(current.equals(intent.checkpointed()), "Frozen settlement endpoint changed");
            for (var observation : intent.checkpointed()) {
                if (state.proofs.containsKey(observation.endpoint().key())) { continue; }
                var targets = new ArrayList<EscrowBarrier.Target>();
                for (var candidate : intent.checkpointed()) {
                    if (!EscrowBarrier.sameSlot(observation.endpoint(), candidate.endpoint())) { continue; }
                    // One physical save must prove all references in that slot together.
                    var frontier = candidate.missing() ? new EscrowPrefix.State(intent.ledger(), 0,
                            EscrowSettlementTombstones.observationDigest(candidate), List.of()) : candidate.state();
                    targets.add(new EscrowBarrier.Target(candidate.endpoint(), frontier, EscrowSettlementDisk.validator(candidate, intent.ledger())));
                }
                var keys = targets.stream().map(target -> target.endpoint().key()).toList(); state.busy.addAll(keys);
                var endpoint = observation.endpoint();
                var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.tryParse(endpoint.level())));
                boolean accepted = runtime.barrier().beginJoint(runtime.store(level), targets, tick, result -> {
                    state.busy.removeAll(keys);
                    if (closed) { result.proofs().values().forEach(EscrowBarrier.Proof::release); return; }
                    if (!result.confirmed()) { stalled(runtime, intent, state, result.reason().name(), server.getTickCount()); return; }
                    result.proofs().forEach((key, proof) -> {
                        runtime.checkpoint("O2_BEFORE_" + index(intent, key));
                        var old = state.proofs.put(key, proof); if (old != null) { old.release(); }
                        runtime.checkpoint("O2_AFTER_" + index(intent, key));
                    });
                });
                if (!accepted) { state.busy.removeAll(keys); }
                return;
            }
            if (!state.proofs.values().stream().allMatch(EscrowBarrier.Proof::valid)) { return; }
            if (!installed) {
                state.phase = "O3"; state.writing = true;
                runtime.checkpoint("O3_CAPTURE");
                if (!coordinator.settlementWrite(intent, state.proofs, false, journal.acknowledgedLease(), committed -> {
                    state.writing = false;
                    if (committed) { runtime.checkpoint("O3_AFTER"); }
                    else { stalled(runtime, intent, state, "O3_WRITE_UNACKNOWLEDGED", server.getTickCount()); }
                })) { state.writing = false; }
                return;
            }
            // Every named removal was checked against the frozen digest above, before any RAM mutation.
            boolean removed = false;
            for (var op : coordinator.ledger().records()) {
                if (!intent.ids().contains(op.id()) || op.guard().transfer() == null) { continue; }
                if (journal.find(op.guard().transfer()).isPresent()) { journal.remove(op.guard().transfer()); removed = true; }
            }
            if (removed) { state.phase = "O3J"; journal.flushChecked(server); return; }
            if (!journal.acknowledgedCurrent()) { return; }
            runtime.checkpoint("O3J_AFTER");
            state.phase = "O4"; state.writing = true;
            runtime.checkpoint("O4_CAPTURE");
            if (!coordinator.settlementWriteExact(intent, state.proofs, true, journal.acknowledgedLease(), acknowledgement -> {
                state.writing = false;
                if (acknowledgement.committed()) {
                    runtime.checkpoint("O4_AFTER"); state.clear();
                    pruning.enqueue(coordinator.ledger(), acknowledgement);
                    com.mojang.logging.LogUtils.getLogger().info("ARCE_ESCROW_O4 actor={} choice={} digest={} operations={} released=acknowledged",
                            intent.actor(), intent.choice(), intent.digest(), intent.ids());
                } else { stalled(runtime, intent, state, "O4_WRITE_UNACKNOWLEDGED", server.getTickCount()); }
            })) { state.writing = false; }
        } catch (IllegalArgumentException | java.util.NoSuchElementException refused) {
            stalled(runtime, intent, state, refused.getMessage(), tick);
        }
    }
    List<EscrowSettlementIntent> intents(EscrowLedger root) {
        if (decodedRoot != root) {
            decodedIntents = root.records().stream().filter(op -> !op.settlement().isEmpty())
                    .map(op -> EscrowSettlementIntent.decode(op.settlement())).distinct().toList();
            decodedRoot = root;
        }
        return decodedIntents;
    }
    private static int index(EscrowSettlementIntent intent, EscrowEndpoint.Key key) {
        for (int i = 0; i < intent.observations().size(); i++) { if (intent.observations().get(i).endpoint().key().equals(key)) { return i; } }
        throw new IllegalArgumentException("Unknown proof endpoint");
    }
    static void requireJournal(EscrowLedger root, EscrowSettlementIntent intent, RocketTransferSavedData journal, boolean installed) {
        EscrowTags.require(journal.acknowledgedCurrent(), "Unacknowledged journal");
        for (var op : root.records()) {
            if (!intent.ids().contains(op.id())) { continue; }
            var current = journal.acknowledgedGuard(op.guard().rocket()).orElseThrow();
            if (installed && current.transfer() == null) { continue; }
            EscrowTags.require(RocketEscrowAudit.journal(op.guard(), current) == EscrowReason.NONE, "Frozen matching journal changed");
        }
    }
    private static void stalled(EscrowRuntime runtime, EscrowSettlementIntent intent, Work state, String reason, long tick) {
        state.clear(); state.retryAt = tick + 200;
        runtime.checkpoint("O_VETO");
        if (!java.util.Objects.equals(state.lastReason, reason)) {
            state.lastReason = reason;
            com.mojang.logging.LogUtils.getLogger().info("ARCE_ESCROW_SETTLEMENT_HELD digest={} reason={} records=retained", intent.digest(), reason);
        }
    }
    String phase() { return work.values().stream().map(value -> value.phase).findFirst().orElse("O2"); }
    @Override public void close() { closed = true; work.values().forEach(Work::clear); work.clear(); decodedRoot = null; decodedIntents = List.of(); }
}
