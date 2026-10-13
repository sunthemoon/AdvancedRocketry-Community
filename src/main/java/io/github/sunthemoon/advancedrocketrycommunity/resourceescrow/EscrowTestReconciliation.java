package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Test-only endpoint adapter for the production-neutral scheduler; no public admission path. */
final class EscrowTestReconciliation implements EscrowReconciliationScheduler.Driver, AutoCloseable {
    private final EscrowCoordinator coordinator;
    private final EscrowRuntime runtime;
    private final Map<EscrowEndpoint.Key, EscrowTestEndpoints.Fixture> fixtures = new HashMap<>();
    private final Map<EscrowEndpoint.Key, EscrowBarrier.Proof> proofs = new HashMap<>();
    private final Set<EscrowEndpoint.Key> busy = new HashSet<>();
    private final Predicate<EscrowEndpoint> veto;

    EscrowTestReconciliation(EscrowCoordinator coordinator, EscrowRuntime runtime,
                             List<EscrowTestEndpoints.Fixture> endpoints, Predicate<EscrowEndpoint> veto) {
        this.coordinator = coordinator; this.runtime = runtime; this.veto = veto;
        for (var fixture : endpoints) {
            EscrowTestEndpoints.requireEnabled(fixture.level().getServer());
            var reference = fixture.operation(1);
            fixtures.put(reference.source().key(), fixture); fixtures.put(reference.destination().key(), fixture);
        }
    }
    void unregister(EscrowEndpoint.Key key) { fixtures.remove(key); }
    @Override public void attempt(EscrowOperation op, List<EscrowOperation> records, Consumer<EscrowReconciliationScheduler.Result> completion) {
        var source = fixtures.get(op.source().key()); var destination = fixtures.get(op.destination().key());
        if (source == null || destination == null || !loaded(source, op.source()) || !loaded(destination, op.destination())) {
            completion.accept(new EscrowReconciliationScheduler.Result(EscrowReason.WAITING_UNLOADED, false)); return;
        }
        var chains = EscrowPrefix.chains(records);
        for (EscrowEndpoint endpoint : List.of(op.source(), op.destination())) {
            var fixture = fixtures.get(endpoint.key()); EscrowPrefix.State state;
            try { state = state(fixture, endpoint); }
            catch (RuntimeException invalid) { completion.accept(new EscrowReconciliationScheduler.Result(EscrowReason.INVALID_ENDPOINT, false)); return; }
            var chain = chains.get(endpoint.key());
            var classification = EscrowPrefix.classify(coordinator.ledger().id(), chain, state);
            if (classification == EscrowPrefix.Classification.OTHER) {
                completion.accept(new EscrowReconciliationScheduler.Result(EscrowReason.OTHER_STATE, false)); return;
            }
            if (state.markers().stream().noneMatch(marker -> marker.operation().equals(op.id()))) {
                int applied = state.markers().size();
                if (runtime.stopping() || coordinator.frozen(endpoint.key()) || !chain.get(applied).operation().id().equals(op.id())) {
                    completion.accept(EscrowReconciliationScheduler.Result.deferred()); return;
                }
                if (endpoint.kind() == EscrowEndpoint.Kind.ENTITY) { fixture.applyBank(op); } else { fixture.applySource(op); }
            }
        }
        for (EscrowEndpoint endpoint : List.of(op.source(), op.destination())) {
            if (EscrowPrefix.classify(coordinator.ledger().id(), chains.get(endpoint.key()), state(fixtures.get(endpoint.key()), endpoint))
                    != EscrowPrefix.Classification.AFTER) { completion.accept(EscrowReconciliationScheduler.Result.deferred()); return; }
        }
        prove(op, records, completion);
    }
    private void prove(EscrowOperation op, List<EscrowOperation> records, Consumer<EscrowReconciliationScheduler.Result> completion) {
        proofs.entrySet().removeIf(entry -> { if (entry.getValue().valid()) { return false; } entry.getValue().release(); return true; });
        for (EscrowEndpoint endpoint : List.of(op.source(), op.destination())) {
            if (proofs.containsKey(endpoint.key())) { continue; }
            if (busy.contains(endpoint.key()) || coordinator.admitting(endpoint.key())) {
                completion.accept(EscrowReconciliationScheduler.Result.deferred()); return;
            }
            var chains = EscrowPrefix.chains(records); var targets = new ArrayList<EscrowBarrier.Target>();
            for (var chain : chains.values()) {
                EscrowEndpoint candidate = chain.get(0).endpoint(); var fixture = fixtures.get(candidate.key());
                if (!EscrowBarrier.sameSlot(endpoint, candidate) || fixture == null || !loaded(fixture, candidate)
                        || coordinator.admitting(candidate.key())) { continue; }
                EscrowPrefix.State frontier;
                try { frontier = state(fixture, candidate); }
                catch (RuntimeException invalidOtherEndpoint) { continue; }
                if (EscrowPrefix.classify(coordinator.ledger().id(), chain, frontier) != EscrowPrefix.Classification.AFTER) { continue; }
                coordinator.freeze(candidate.key(), frontier);
                targets.add(new EscrowBarrier.Target(candidate, frontier, root -> !veto.test(candidate) && EscrowTestEndpoints.validator(candidate, frontier).test(root)));
            }
            var targetKeys = targets.stream().map(target -> target.endpoint().key()).toList();
            if (!targetKeys.contains(endpoint.key())) { completion.accept(EscrowReconciliationScheduler.Result.deferred()); return; }
            busy.addAll(targetKeys);
            boolean accepted = runtime.barrier().beginJoint(runtime.store(fixtures.get(endpoint.key()).level()), targets,
                    fixtures.get(endpoint.key()).level().getServer().getTickCount(), result -> {
                        busy.removeAll(targetKeys);
                        if (!result.confirmed()) { completion.accept(new EscrowReconciliationScheduler.Result(result.reason(), false)); return; }
                        result.proofs().forEach((key, proof) -> {
                            var old = proofs.put(key, proof); if (old != null) { old.release(); }
                        });
                        completion.accept(EscrowReconciliationScheduler.Result.deferred());
                    });
            if (!accepted) { busy.removeAll(targetKeys); completion.accept(EscrowReconciliationScheduler.Result.deferred()); }
            // A synchronous capture refusal already delivered the callback; never deliver it twice.
            return;
        }
        try { EscrowPrefix.requireRetirementPrefix(records, Set.of(op.id())); }
        catch (IllegalArgumentException dependency) { completion.accept(EscrowReconciliationScheduler.Result.deferred()); return; }
        boolean accepted = coordinator.retire(Set.of(op.id()), proofs, result -> {
            if (result) {
                var endpoints = new HashSet<>(List.of(fixtures.get(op.source().key()), fixtures.get(op.destination().key())));
                endpoints.forEach(fixture -> EscrowTestEndpoints.clearRetired(fixture, Set.of(op.id())));
            }
            completion.accept(result ? new EscrowReconciliationScheduler.Result(EscrowReason.NONE, true) : EscrowReconciliationScheduler.Result.deferred());
        });
        if (!accepted) { completion.accept(EscrowReconciliationScheduler.Result.deferred()); }
    }
    private static EscrowPrefix.State state(EscrowTestEndpoints.Fixture fixture, EscrowEndpoint endpoint) {
        return endpoint.kind() == EscrowEndpoint.Kind.ENTITY ? fixture.bankState() : fixture.sourceState();
    }
    private static boolean loaded(EscrowTestEndpoints.Fixture fixture, EscrowEndpoint endpoint) {
        if (endpoint.kind() == EscrowEndpoint.Kind.ENTITY) {
            return fixture.level().areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(endpoint.x(), endpoint.z()).toLong())
                    && !fixture.bank().isRemoved() && fixture.bank().chunkPosition().equals(new net.minecraft.world.level.ChunkPos(endpoint.x(), endpoint.z()));
        }
        var position = new net.minecraft.core.BlockPos(endpoint.x(), endpoint.y(), endpoint.z());
        return fixture.level().hasChunkAt(position) && fixture.level().getBlockEntity(position) == fixture.source();
    }
    @Override public void close() { proofs.values().forEach(EscrowBarrier.Proof::release); proofs.clear(); }
}
