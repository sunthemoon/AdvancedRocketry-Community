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
    private long issued;
    private long tick;
    private long lastStart = -10;
    private boolean closed;

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
        EscrowCodec.encode(data.ledger().append(candidate)); // Preflight every count, chain and byte bound before mutation.
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
    private EscrowLedgerWriter.Snapshot snapshot(boolean admissions, Set<UUID> retire) {
        EscrowLedger candidate = data.ledger();
        if (retire != null) { candidate = candidate.retire(retire); }
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
            for (UUID id : completion.snapshot().admissions()) {
                queued.remove(id); Consumer<Boolean> callback = requests.remove(id);
                if (callback != null && !closed) { callback.accept(true); }
            }
            data.setDirty(!queued.isEmpty() || !writer.idle());
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
        owner(); EscrowPrefix.requireRetirementPrefix(data.ledger().records(), ids);
        for (EscrowOperation op : data.ledger().records()) {
            if (!ids.contains(op.id())) { continue; }
            for (EscrowEndpoint endpoint : List.of(op.source(), op.destination())) {
                EscrowBarrier.Proof proof = proofs.get(endpoint.key());
                EscrowTags.require(proof != null && proof.valid()
                        && proof.frontier().equals(frontiers.get(endpoint.key())), "Retirement lacks a valid frozen endpoint proof");
            }
        }
        if (closed || tick - lastStart < 10) { return false; }
        var frozenProofs = Map.copyOf(proofs);
        boolean accepted = writer.request(() -> {
            // Revalidate tokens at capture, not merely when the successor request is queued.
            // A superseded queued proof is a normal refusal, not an owner-thread exception.
            return snapshot(false, frozenProofs.values().stream().allMatch(EscrowBarrier.Proof::valid) ? ids : null);
        }, result -> {
            acknowledge(result);
            if (result.outcome() == EscrowLedgerWriter.Outcome.COMMITTED
                    && result.snapshot().ledger().records().stream().noneMatch(op -> ids.contains(op.id()))) {
                var remaining = EscrowPrefix.chains(data.ledger().records()).keySet();
                frontiers.keySet().removeIf(key -> !remaining.contains(key));
                frozenProofs.forEach((key, proof) -> { if (!remaining.contains(key)) { proof.release(); } });
                if (!closed) { callback.accept(true); }
            } else if (!closed) { callback.accept(false); }
        }, () -> frozenProofs.values().stream().allMatch(EscrowBarrier.Proof::valid));
        if (accepted) { lastStart = tick; }
        return accepted;
    }
    public void tick(long now) { owner(); tick = now; writer.tick(now); }
    public boolean writerIdle() { return writer.idle(); }
    public boolean locked(EscrowEndpoint.Key endpoint) {
        return frontiers.containsKey(endpoint) || java.util.stream.Stream.concat(data.ledger().records().stream(), queued.values().stream())
                .anyMatch(op -> op.source().key().equals(endpoint) || op.destination().key().equals(endpoint));
    }
    @Override public void close() { owner(); if (!closed) { closed = true; writer.close(); frontiers.clear(); } }
    private void owner() { EscrowTags.require(Thread.currentThread() == owner, "Escrow coordinator requires its owner thread"); }
}
