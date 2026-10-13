package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Server-lifetime coordinator. This slice deliberately has no production admission entry point. */
public final class EscrowCoordinator implements AutoCloseable {
    private final Thread owner = Thread.currentThread();
    private final ResourceEscrowSavedData data;
    private final EscrowLedgerWriter writer;
    private final Map<UUID, EscrowOperation> queued = new LinkedHashMap<>();
    private final Map<UUID, Consumer<Boolean>> requests = new HashMap<>();
    private final Map<EscrowEndpoint.Key, EscrowPrefix.State> frontiers = new HashMap<>();
    private final Map<UUID, EscrowReason> transitions = new HashMap<>();
    private long issued;
    private long tick;
    private long lastStart = -10;
    private boolean closed;
    private io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData floorJournal;

    public EscrowCoordinator(ResourceEscrowSavedData data, EscrowLedgerWriter writer) {
        this.data = data; this.writer = writer; issued = data.ledger().issuedThrough();
    }
    public boolean productionAdmissionEnabled() { return false; }
    public EscrowLedger ledger() { return data.ledger(); }
    public long nextSequence() { owner(); return Math.addExact(issued, 1); }
    /** Caller must already be in an explicitly gated test fixture. No block/item/command invokes this. */
    void prepareTest(EscrowOperation op, Consumer<Boolean> completion) {
        owner(); EscrowTags.require(!closed && !writer.unavailable(), "Ledger unavailable");
        EscrowTags.require(op.phase() == EscrowOperation.Phase.PREPARED && op.reason() == EscrowReason.NONE, "Admission must be a fresh preparation");
        EscrowTags.require(op.sequence() == nextSequence() && !queued.containsKey(op.id())
                && !requests.containsKey(op.id()), "Duplicate or stale admission");
        EscrowTags.require(!frontiers.containsKey(op.source().key()) && !frontiers.containsKey(op.destination().key()), "Frozen endpoint frontier");
        var candidate = new ArrayList<>(queued.values()); candidate.add(op);
        EscrowFloors.preflight(data.ledger().append(candidate)); // Reserve the L2 floor before any queued L1 state changes.
        queued.put(op.id(), op); requests.put(op.id(), completion); issued = op.sequence(); data.setDirty();
    }
    public boolean commitPreparations() {
        owner(); if (closed || queued.isEmpty() || tick - lastStart < 10) { return false; }
        if (writer.request(() -> snapshot(true, null), this::acknowledge)) { lastStart = tick; return true; }
        return false;
    }
    public void autosave() {
        owner(); if (closed || !data.isDirty() || tick - lastStart < 10) { return; }
        // Only acknowledged records are included. Queued preparations are never implicit autosave admissions.
        if (writer.request(() -> snapshot(false, null), this::acknowledge)) { lastStart = tick; }
    }
    void persistTransitions() { owner(); if (!transitions.isEmpty()) { autosave(); } }
    private EscrowLedgerWriter.Snapshot snapshot(boolean admissions, Set<UUID> retire) {
        return snapshot(admissions, retire, Map.of());
    }
    private EscrowLedgerWriter.Snapshot snapshot(boolean admissions, Set<UUID> retire, Map<EscrowEndpoint.Key, EscrowBarrier.Proof> proofs) {
        EscrowLedger candidate = data.ledger();
        candidate = new EscrowLedger(candidate.id(), candidate.issuedThrough(), candidate.writerGeneration(),
                candidate.records().stream().map(op -> transitions.containsKey(op.id())
                        ? op.withReason(transitions.get(op.id())) : op).toList(), candidate.floors(), candidate.tombstones());
        if (retire != null) { candidate = EscrowFloors.retire(candidate, retire, proofs.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().frontier()))); }
        if (floorJournal != null && floorJournal.acknowledgedCurrent()) {
            // This capture runs on the owner thread. It never collects from a RAM-only journal removal.
            candidate = EscrowFloors.collect(candidate, java.util.stream.Stream.concat(data.ledger().records().stream(), queued.values().stream()).toList(),
                    id -> floorJournal.find(id).isEmpty());
        }
        if (admissions) {
            List<EscrowOperation> additions = queued.values().stream()
                    .filter(op -> candidateContains(data.ledger(), op.id()) == false).toList();
            candidate = candidate.append(additions);
        }
        candidate = candidate.generation(Math.addExact(writer.lastGeneration(), 1));
        Set<UUID> included = candidate.records().stream().map(EscrowOperation::id)
                .filter(requests::containsKey).collect(java.util.stream.Collectors.toSet());
        return EscrowLedgerWriter.Snapshot.of(candidate, included);
    }
    private static boolean candidateContains(EscrowLedger ledger, UUID id) {
        return ledger.records().stream().anyMatch(op -> op.id().equals(id));
    }
    private void acknowledge(EscrowLedgerWriter.Completion completion) {
        owner();
        if (completion.outcome() == EscrowLedgerWriter.Outcome.COMMITTED) {
            if (completion.generation() > data.ledger().writerGeneration()) {
                data.acknowledged(completion.snapshot().ledger(), !queued.isEmpty());
            }
            transitions.entrySet().removeIf(entry -> data.ledger().records().stream()
                    .noneMatch(op -> op.id().equals(entry.getKey()) && op.reason() != entry.getValue()));
            for (UUID id : completion.snapshot().admissions()) {
                queued.remove(id); Consumer<Boolean> callback = requests.remove(id);
                if (callback != null && !closed) { callback.accept(true); }
            }
            data.setDirty(!queued.isEmpty() || !transitions.isEmpty() || !writer.idle());
        } else if (completion.outcome() == EscrowLedgerWriter.Outcome.FAILED_BEFORE_REPLACE) {
            for (UUID id : completion.snapshot().admissions()) {
                // A captured successor is still a potential durable L1; do not cancel its admission.
                if (!writer.containsCaptured(id)) {
                    queued.remove(id); Consumer<Boolean> callback = requests.remove(id);
                    if (callback != null && !closed) { callback.accept(false); }
                }
            }
            data.setDirty();
        } else { data.setDirty(); }
    }
    public void freeze(EscrowEndpoint.Key endpoint, EscrowPrefix.State frontier) {
        owner(); var chain = EscrowPrefix.chains(data.ledger().records()).get(endpoint);
        EscrowTags.require(chain != null && EscrowPrefix.classify(data.ledger().id(), chain, frontier)
                == EscrowPrefix.Classification.AFTER, "Proof needs the complete live prefix");
        EscrowPrefix.State old = frontiers.putIfAbsent(endpoint, frontier);
        EscrowTags.require(old == null || old.equals(frontier), "Frozen frontier changed");
    }
    public boolean retire(Set<UUID> ids, Map<EscrowEndpoint.Key, EscrowBarrier.Proof> proofs,
                          Consumer<Boolean> callback) {
        owner(); EscrowTags.require(ids.size() <= EscrowLimits.OPERATIONS, "Retirement count exceeded");
        Set<UUID> retired = Set.copyOf(ids); // Successor capture must not retain a caller-owned mutable set.
        EscrowPrefix.requireRetirementPrefix(data.ledger().records(), retired);
        for (EscrowOperation op : data.ledger().records()) {
            if (!retired.contains(op.id())) { continue; }
            for (EscrowEndpoint endpoint : List.of(op.source(), op.destination())) {
                EscrowBarrier.Proof proof = proofs.get(endpoint.key());
                EscrowTags.require(proof != null && proof.valid()
                        && proof.frontier().equals(frontiers.get(endpoint.key())), "Retirement lacks a valid frozen endpoint proof");
            }
        }
        if (closed || tick - lastStart < 10) { return false; }
        Set<EscrowEndpoint.Key> affected = data.ledger().records().stream().filter(op -> retired.contains(op.id()))
                .flatMap(op -> java.util.stream.Stream.of(op.source().key(), op.destination().key()))
                .collect(java.util.stream.Collectors.toSet());
        var frozenProofs = proofs.entrySet().stream().filter(entry -> affected.contains(entry.getKey()))
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        boolean accepted = writer.request(() -> {
            // Revalidate tokens at capture, not merely when the successor request is queued.
            // A superseded queued proof is a normal refusal, not an owner-thread exception.
            return snapshot(false, frozenProofs.values().stream().allMatch(EscrowBarrier.Proof::valid) ? retired : null, frozenProofs);
        }, result -> {
            acknowledge(result);
            if (result.outcome() == EscrowLedgerWriter.Outcome.COMMITTED
                    && result.snapshot().ledger().records().stream().noneMatch(op -> retired.contains(op.id()))) {
                // L2 changes the marker prefix, even when the endpoint still has later records.
                // Release its old token; the adapter prunes only retired IDs and must capture again.
                affected.forEach(frontiers::remove);
                frozenProofs.values().forEach(EscrowBarrier.Proof::release);
                if (!closed) { callback.accept(true); }
            } else if (!closed) { callback.accept(false); }
        }, () -> frozenProofs.values().stream().allMatch(EscrowBarrier.Proof::valid));
        if (accepted) { lastStart = tick; }
        return accepted;
    }
    public void tick(long now) { owner(); tick = now; writer.tick(now); }
    public void transition(UUID id, EscrowReason reason) {
        owner();
        EscrowOperation op = data.ledger().records().stream().filter(record -> record.id().equals(id)).findFirst().orElse(null);
        if (op == null || closed) { return; }
        EscrowTags.require(reason == EscrowReason.NONE || reason.manual() || reason.retryableProofFailure(), "Transient reason cannot be persisted");
        op.withReason(reason); // Enforce the reason-specific transition table before queueing.
        EscrowReason previous = transitions.getOrDefault(id, op.reason());
        EscrowTags.require(!previous.manual() || previous == reason, "Pending manual repair cannot clear automatically");
        if (previous != reason) { transitions.put(id, reason); data.setDirty(); }
    }
    public boolean frozen(EscrowEndpoint.Key endpoint) { owner(); return frontiers.containsKey(endpoint); }
    public boolean writerIdle() { return writer.idle(); }
    EscrowReason currentReason(EscrowOperation op) { owner(); return transitions.getOrDefault(op.id(), op.reason()); }
    public EscrowFloors.Usage floorUsage() { owner(); return EscrowFloors.usage(data.ledger(), queued.values()); }
    void collectFloors(io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData journal) {
        owner(); floorJournal = journal;
        if (closed || !journal.acknowledgedCurrent()) { return; }
        var candidate = EscrowFloors.collect(data.ledger(), queued.values(), id -> journal.find(id).isEmpty());
        if (candidate.floors().size() != data.ledger().floors().size()) { data.setDirty(); autosave(); }
    }
    public boolean logicalLocked(UUID logical) {
        owner();
        return frontiers.keySet().stream().anyMatch(key -> logical.equals(key.logicalRocket()))
                || java.util.stream.Stream.concat(data.ledger().records().stream(), queued.values().stream())
                .anyMatch(op -> logical.equals(op.guard().rocket()));
    }
    public boolean locked(EscrowEndpoint.Key endpoint) {
        return frontiers.containsKey(endpoint) || java.util.stream.Stream.concat(data.ledger().records().stream(), queued.values().stream())
                .anyMatch(op -> op.source().key().equals(endpoint) || op.destination().key().equals(endpoint));
    }
    @Override public void close() { owner(); if (!closed) { closed = true; writer.close(); frontiers.clear(); } }
    private void owner() { EscrowTags.require(Thread.currentThread() == owner, "Escrow coordinator requires its owner thread"); }
}
