package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;

/** Ordinary native fills only. Model fixtures and settlement decisions have separate executors. */
final class EscrowReconciliationDriver {
    private final EscrowCoordinator coordinator;
    private EscrowLedger cached;
    private Map<EscrowEndpoint.Key, List<EscrowPrefix.Link>> chains = Map.of();
    private final Map<UUID, List<EscrowOperation>> components = new HashMap<>();
    private final Map<UUID, EscrowReconciliationView> views = new HashMap<>();
    private final Map<UUID, EscrowReconciliationCheckpoint> checkpoints = new HashMap<>();
    private final Set<UUID> changed = new HashSet<>();
    private final Set<EscrowEndpoint.Key> registeredSources = new HashSet<>();
    private long tick = Long.MIN_VALUE;
    private boolean captured, actionApplied;

    EscrowReconciliationDriver(EscrowCoordinator coordinator) { this.coordinator = coordinator; }

    void registerTestSource(MinecraftServer server, EscrowEndpoint source) {
        EscrowTestEndpoints.requireEnabled(server);
        registeredSources.removeIf(key -> !coordinator.admitting(key)
                && coordinator.ledger().records().stream().noneMatch(op -> op.source().key().equals(key)));
        EscrowTags.require(source.kind() == EscrowEndpoint.Kind.BLOCK_ENTITY && source.type().equals("minecraft:chest")
                && (registeredSources.contains(source.key()) || registeredSources.size() < EscrowLimits.OPERATIONS), "Invalid fixture adapter registration");
        registeredSources.add(source.key());
    }

    void attempt(MinecraftServer server, EscrowOperation op, Consumer<EscrowReconciliationScheduler.Result> completion) {
        if (!server.isSameThread() || EscrowRuntime.of(server).stopping() || !coordinator.operational() || !coordinator.writerIdle()) {
            completion.accept(EscrowReconciliationScheduler.Result.deferred()); return;
        }
        var root = coordinator.ledger();
        if (!root.records().contains(op) || coordinator.currentReason(op).manual() || !op.settlement().isEmpty()) {
            completion.accept(EscrowReconciliationScheduler.Result.deferred()); return;
        }
        // Legacy model-only fixtures do not identify the native resource adapter.
        if (op.nativeBinding().kind() != EscrowSignatureKind.FUEL_VECTOR_V1) {
            completion.accept(new EscrowReconciliationScheduler.Result(EscrowReason.RECONCILIATION_UNIMPLEMENTED, false)); return;
        }
        // Historical settlement/relocation harnesses own their endpoint actions. Only the explicitly
        // registered native fixture source enters this driver; registration itself grants no authority.
        if (!registeredSources.contains(op.source().key())) {
            completion.accept(new EscrowReconciliationScheduler.Result(EscrowReason.RECONCILIATION_UNIMPLEMENTED, false)); return;
        }
        if (cached != root) {
            cached = root; chains = EscrowPrefix.chains(root.records()); components.clear(); views.clear();
            checkpoints.values().forEach(EscrowReconciliationCheckpoint::close); checkpoints.clear();
        }
        if (tick != server.getTickCount()) { tick = server.getTickCount(); changed.clear(); captured = false; actionApplied = false; }
        var component = components.get(op.id());
        if (component == null) {
            component = EscrowSettlementIntent.component(root.records(), op.id());
            for (var member : component) { components.put(member.id(), component); }
        }
        if (component.stream().anyMatch(member -> !registeredSources.contains(member.source().key()))) {
            completion.accept(new EscrowReconciliationScheduler.Result(EscrowReason.RECONCILIATION_UNIMPLEMENTED, false)); return;
        }
        var key = component.get(0).id();
        if (actionApplied || changed.contains(key) || component.stream().anyMatch(member -> coordinator.currentReason(member).manual()
                || !member.settlement().isEmpty() || coordinator.admitting(member.source().key()) || coordinator.admitting(member.destination().key()))) {
            completion.accept(EscrowReconciliationScheduler.Result.deferred()); return;
        }
        try {
            var pending = checkpoints.get(op.id());
            if (pending != null && pending.journalLease.getAsBoolean()) {
                if (pending.busy) { completion.accept(EscrowReconciliationScheduler.Result.deferred()); return; }
                pending.discardInvalid();
                var next = pending.ends.values().stream().filter(end -> !pending.proofs.containsKey(end.endpoint().key())).findFirst();
                if (next.isPresent() ? !EscrowRuntime.of(server).barrier().available(next.orElseThrow().endpoint(), server.getTickCount())
                        : !coordinator.checkedWriteReady()) {
                    completion.accept(EscrowReconciliationScheduler.Result.deferred()); return;
                }
            }
            var view = views.get(key);
            if (view == null || !view.current(coordinator)) {
                // The scheduler keeps its eight cheap visits. At most one whole component is
                // captured per tick, and retained complete captures stay under the existing cap.
                if (captured) { completion.accept(EscrowReconciliationScheduler.Result.deferred()); return; }
                captured = true;
                for (var member : component) { EscrowReconciliationPruning.source(server, coordinator, member.source()); }
                view = EscrowReconciliationView.capture(server, root, component);
                views.remove(key);
                if (views.values().stream().mapToLong(value -> value.bytes).sum() + view.bytes > EscrowLimits.CAPTURE_BYTES) { views.clear(); }
                views.put(key, view);
            }
            for (var endpoint : List.of(op.source(), op.destination())) {
                var end = view.ends.get(endpoint.key()); var chain = chains.get(endpoint.key());
                int applied = end.state().markers().size();
                if (applied == chain.size() || !chain.get(applied).operation().equals(op)) { continue; }
                if (coordinator.frozen(endpoint.key())) { continue; }
                EscrowReconciliationAction.apply(coordinator, view, op, endpoint.kind() == EscrowEndpoint.Kind.ENTITY);
                actionApplied = true; changed.add(key); views.remove(key);
                completion.accept(EscrowReconciliationScheduler.Result.deferred()); return;
            }
            checkpoint(server, op, view, completion);
        } catch (EscrowReconciliationView.Refusal refused) {
            completion.accept(new EscrowReconciliationScheduler.Result(refused.reason, false));
        } catch (IllegalArgumentException | IllegalStateException | java.util.NoSuchElementException invalid) {
            completion.accept(new EscrowReconciliationScheduler.Result(EscrowReason.INVALID_ENDPOINT, false));
        }
    }

    private void checkpoint(MinecraftServer server, EscrowOperation op, EscrowReconciliationView view,
                            Consumer<EscrowReconciliationScheduler.Result> completion) {
        var ids = Set.of(op.id());
        try { EscrowPrefix.requireRetirementPrefix(view.root.records(), ids); }
        catch (IllegalArgumentException dependency) { completion.accept(EscrowReconciliationScheduler.Result.deferred()); return; }
        var states = view.ends.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().state()));
        if (!EscrowReconciliationValidation.retirementReady(view.root, ids, states)) {
            EscrowRuntime.of(server).checkpoint("C3_LATER_MEMBER_WAIT");
            completion.accept(EscrowReconciliationScheduler.Result.deferred()); return;
        }
        var work = checkpoints.get(op.id());
        if (work != null && !work.journalLease.getAsBoolean()) { work.close(); checkpoints.remove(op.id()); work = null; }
        if (work == null) {
            var references = List.of(op.source(), op.destination());
            if (checkpoints.values().stream().anyMatch(other -> references.stream().anyMatch(end -> other.ends.containsKey(end.key())))) {
                completion.accept(EscrowReconciliationScheduler.Result.deferred()); return;
            }
            var barrier = EscrowRuntime.of(server).barrier();
            if (references.stream().noneMatch(end -> barrier.available(end, server.getTickCount()))) {
                completion.accept(EscrowReconciliationScheduler.Result.deferred()); return;
            }
            var observations = EscrowSettlementObserver.observe(server, view.root.id(), references);
            work = new EscrowReconciliationCheckpoint(view.root, ids, observations, view.journalLease);
            // Validate the whole affected set before publishing any freeze. No owner-thread interleaving.
            EscrowTags.require(view.unchanged(coordinator), "C3 observation changed before freezing");
            for (var end : observations) { coordinator.freeze(end.endpoint().key(), end.state()); }
            checkpoints.put(op.id(), work); EscrowRuntime.of(server).checkpoint("C3_FROZEN");
        }
        work.discardInvalid();
        if (work.busy) { completion.accept(EscrowReconciliationScheduler.Result.deferred()); return; }
        // The cheap view above is never C4/C7 authority. Re-observe the complete
        // affected component immediately before either capture, including duplicate identities.
        EscrowTags.require(view.unchanged(coordinator), "Native observation changed before checkpoint capture");
        for (var end : work.ends.values()) {
            if (work.proofs.containsKey(end.endpoint().key())) { continue; }
            var frozen = work; var targets = work.targets(end.endpoint()); var runtime = EscrowRuntime.of(server);
            runtime.checkpoint(end.endpoint().kind() == EscrowEndpoint.Kind.ENTITY ? "C4_DESTINATION_SELECTED" : "C4_SOURCE_SELECTED");
            work.busy = true;
            boolean accepted = runtime.barrier().beginJoint(runtime.store(view.ends.get(end.endpoint().key()).level()), targets,
                    server.getTickCount(), result -> {
                        frozen.busy = false;
                        if (checkpoints.get(op.id()) != frozen) {
                            result.proofs().values().forEach(EscrowBarrier.Proof::release);
                            completion.accept(EscrowReconciliationScheduler.Result.deferred()); return;
                        }
                        frozen.accept(targets, result);
                        if (frozen.proved()) { runtime.checkpoint("C6_JOINT_READBACK"); }
                        // C6 is internal proof, never scheduler success or release authority.
                        completion.accept(new EscrowReconciliationScheduler.Result(result.reason(), false));
                    });
            if (!accepted) { work.busy = false; completion.accept(EscrowReconciliationScheduler.Result.deferred()); }
            return;
        }
        var frozen = work; var runtime = EscrowRuntime.of(server);
        work.busy = true;
        boolean accepted = coordinator.retireNative(work, ack -> {
            frozen.busy = false;
            if (checkpoints.get(op.id()) != frozen || !ack.members().equals(frozen.members)
                    || !ack.ledger().equals(frozen.root.id())) {
                frozen.close(); completion.accept(EscrowReconciliationScheduler.Result.deferred()); return;
            }
            if (ack.committed()) {
                runtime.checkpoint("C7_ACKNOWLEDGED");
                EscrowReconciliationPruning.acknowledged(server, coordinator, frozen, ack);
                frozen.close(); checkpoints.remove(op.id()); views.clear();
                runtime.checkpoint("C8_RELEASED");
                completion.accept(new EscrowReconciliationScheduler.Result(EscrowReason.NONE, true));
            } else {
                // A definite failed candidate repeats both native proofs, retaining the frozen frontiers.
                frozen.proofs.values().forEach(EscrowBarrier.Proof::release); frozen.proofs.clear();
                completion.accept(new EscrowReconciliationScheduler.Result(EscrowReason.LEDGER_WRITE_FAILED, false));
            }
        });
        if (!accepted) { work.busy = false; completion.accept(EscrowReconciliationScheduler.Result.deferred()); }
    }

    boolean checkpointProved(UUID operation) {
        var work = checkpoints.get(operation); return work != null && work.proved();
    }

    void settled(Set<EscrowEndpoint.Key> endpoints) {
        checkpoints.entrySet().removeIf(entry -> {
            if (entry.getValue().ends.keySet().stream().noneMatch(endpoints::contains)) { return false; }
            entry.getValue().close(); return true;
        });
    }

    void close() {
        checkpoints.values().forEach(EscrowReconciliationCheckpoint::close); checkpoints.clear();
        views.clear(); components.clear(); changed.clear(); registeredSources.clear(); cached = null; chains = Map.of();
    }
}
