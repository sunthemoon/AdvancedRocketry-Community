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
    static final class Work {
        final Map<EscrowEndpoint.Key, EscrowBarrier.Proof> proofs = new HashMap<>();
        final Set<EscrowEndpoint.Key> busy = new HashSet<>();
        boolean writing;
        long retryAt;
        String phase = "O2";
        String lastReason;
        void clear() { proofs.values().forEach(EscrowBarrier.Proof::release); proofs.clear(); }
        EscrowOperatorRetry.Outcome accelerate(long now) {
            if (writing || !busy.isEmpty()) { return EscrowOperatorRetry.Outcome.PENDING; }
            if (!retryable(lastReason)) { return EscrowOperatorRetry.Outcome.REFUSED; }
            if (retryAt <= now) { return EscrowOperatorRetry.Outcome.PENDING; }
            retryAt = now;
            return EscrowOperatorRetry.Outcome.ACCEPTED;
        }
    }
    private final Map<String, Work> work = new HashMap<>();
    private final EscrowSettlementPruning.Cursor pruning = new EscrowSettlementPruning.Cursor();
    private EscrowLedger decodedRoot;
    private List<EscrowSettlementIntent> decodedIntents = List.of();
    private final java.util.function.Function<net.minecraft.nbt.CompoundTag, EscrowSettlementIntent> decode;
    private int cursor;
    private boolean closed;
    EscrowSettlementRecovery() { this(EscrowSettlementIntent::decode); }
    EscrowSettlementRecovery(java.util.function.Function<net.minecraft.nbt.CompoundTag, EscrowSettlementIntent> decode) {
        this.decode = decode;
    }
    List<EscrowSettlementPruning.Group> pruningGroups(EscrowLedger root) { return pruning.released(root); }
    record View(String phase, String reason, int provedSlots, int waitingSlots, long nextTick, boolean active) { }
    View view(EscrowSettlementIntent intent) {
        var state = work.get(intent.digest());
        var slots = new HashMap<String, Boolean>();
        for (var observation : intent.checkpointed()) {
            var endpoint = observation.endpoint();
            boolean entity = endpoint.kind() == EscrowEndpoint.Kind.ENTITY;
            String slot = endpoint.level() + "/" + entity + "/" + (entity ? endpoint.x() : endpoint.x() >> 4)
                    + "/" + (entity ? endpoint.z() : endpoint.z() >> 4);
            var proof = state == null ? null : state.proofs.get(endpoint.key());
            slots.merge(slot, proof != null && proof.valid(), (a, b) -> a && b);
        }
        int proved = (int) slots.values().stream().filter(Boolean::booleanValue).count();
        return new View(state == null ? "O2_QUEUED" : state.phase,
                state == null || state.lastReason == null ? "NONE" : state.lastReason,
                proved, slots.size() - proved, state == null ? -1 : state.retryAt,
                state != null && (state.writing || !state.busy.isEmpty()));
    }
    EscrowOperatorRetry.Outcome accelerate(EscrowSettlementIntent intent, long now) {
        var state = work.get(intent.digest());
        if (closed) { return EscrowOperatorRetry.Outcome.REFUSED; }
        return state == null ? EscrowOperatorRetry.Outcome.PENDING : state.accelerate(now);
    }
    static boolean retryable(String reason) {
        if (reason == null) { return false; }
        return switch (reason) {
            case "BARRIER_IO", "BARRIER_FORCE", "BARRIER_UNSTABLE", "SUPERSEDING_SAVE",
                    "O3_WRITE_UNACKNOWLEDGED", "O4_WRITE_UNACKNOWLEDGED", "Unacknowledged journal",
                    "WAITING_UNLOADED: endpoint slot must be FULL, entity-loaded and ticking", "Endpoint Level is unavailable" -> true;
            default -> false;
        };
    }
    void tick(MinecraftServer server, EscrowCoordinator coordinator, EscrowRuntime runtime) {
        if (closed || runtime.stopping()) { return; }
        // Released groups progress independently of unrelated permanently held O1 decisions.
        long pruningStart = System.nanoTime();
        pruning.tick(server, coordinator);
        runtime.timings().sample("settlement_pruning_main", System.nanoTime() - pruningStart);
        if (!coordinator.operational() || !coordinator.writerIdle()) { return; }
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
            var members = coordinator.ledger().records().stream().filter(op -> intent.ids().contains(op.id())).toList();
            // R17 item 3: O3/O4 wait for each member rocket's checked state, not for a quiet journal.
            if (!journal.retryChecked(server) && !members.stream().allMatch(op -> journal.actionAuthorityAvailable(op.guard().rocket()))) {
                return;
            }
            boolean installed = EscrowSettlementTombstones.installed(coordinator.ledger(), intent);
            requireJournal(coordinator.ledger(), intent, journal, installed);
            var next = intent.checkpointed().stream().filter(end -> !state.proofs.containsKey(end.endpoint().key())).findFirst();
            if (next.isPresent() ? !runtime.barrier().available(next.orElseThrow().endpoint(), tick)
                    : !coordinator.checkedWriteReady()) { return; }
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
                if (!coordinator.settlementWrite(intent, state.proofs, false, EscrowFlightJournal.members(journal, members), committed -> {
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
            if (!members.stream().allMatch(op -> journal.actionAuthorityAvailable(op.guard().rocket()))) { return; }
            runtime.checkpoint("O3J_AFTER");
            state.phase = "O4"; state.writing = true;
            runtime.checkpoint("O4_CAPTURE");
            if (!coordinator.settlementWriteExact(intent, state.proofs, true, EscrowFlightJournal.members(journal, members), acknowledgement -> {
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
            // Every member stores the same frozen component intent. Decode/hash it once,
            // not once for each member, and never again while this acknowledged root is current.
            var members = new HashSet<java.util.UUID>();
            var result = new ArrayList<EscrowSettlementIntent>();
            for (var op : root.records()) {
                if (members.contains(op.id()) || op.phase() != EscrowOperation.Phase.SETTLING_ACCEPT
                        && op.phase() != EscrowOperation.Phase.SETTLING_ABANDON) { continue; }
                var encoded = op.settlement();
                // Earlier development settling phases without an intent remain locked.
                if (encoded.isEmpty()) { continue; }
                var intent = decode.apply(encoded);
                result.add(intent); members.addAll(intent.ids());
            }
            decodedIntents = List.copyOf(result);
            decodedRoot = root;
        }
        return decodedIntents;
    }
    private static int index(EscrowSettlementIntent intent, EscrowEndpoint.Key key) {
        for (int i = 0; i < intent.observations().size(); i++) { if (intent.observations().get(i).endpoint().key().equals(key)) { return i; } }
        throw new IllegalArgumentException("Unknown proof endpoint");
    }
    static void requireJournal(EscrowLedger root, EscrowSettlementIntent intent, RocketTransferSavedData journal, boolean installed) {
        for (var op : root.records()) {
            if (!intent.ids().contains(op.id())) { continue; }
            // R17 item 3: each member rocket's own checked guard; another rocket's pending change does not hold it.
            var current = journal.logicalGuard(op.guard().rocket()).orElse(null);
            EscrowTags.require(current != null, "Unacknowledged journal");
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
