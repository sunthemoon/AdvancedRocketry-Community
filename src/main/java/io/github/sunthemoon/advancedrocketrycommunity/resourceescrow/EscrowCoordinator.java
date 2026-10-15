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
    private final EscrowAdmissionAuthority admission = new EscrowAdmissionAuthority();
    private final EscrowFlightWrites flightWrites = new EscrowFlightWrites();
    private long issued;
    private long tick;
    private long lastStart = -10;
    private boolean closed;
    private io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData floorJournal;

    public EscrowCoordinator(ResourceEscrowSavedData data, EscrowLedgerWriter writer) {
        this.data = data; this.writer = writer; issued = data.ledger().issuedThrough();
        admission.registerIndex(EscrowAdmissionAuthority.Consumer.PROPULSION, () -> new EscrowAdmissionAuthority.Index(true,
                java.util.stream.Stream.of(data.ledger().records().stream(), queued.values().stream(), writer.capturedOperations().stream())
                        .flatMap(java.util.function.Function.identity()).map(EscrowAdmissionAuthority.Pending::of).toList()));
    }
    public boolean productionAdmissionEnabled() { return false; }
    public boolean operational() { owner(); return !closed && writer.baselineAvailable(); }
    public EscrowLedger ledger() { return data.ledger(); }
    public long nextSequence() { owner(); return Math.addExact(Math.max(issued, data.ledger().issuedThrough()), 1); }
    public EscrowAdmissionAuthority admissionAuthority() { owner(); return admission; }
    /** Caller must already be in an explicitly gated test fixture. No block/item/command invokes this. */
    void prepareTest(EscrowOperation op, Consumer<Boolean> completion) {
        prepareTest(op, null, completion);
    }
    void prepareTest(EscrowOperation op, RocketEscrowSection observed, Consumer<Boolean> completion) {
        owner(); EscrowTags.require(!closed && !writer.unavailable(), "Ledger unavailable");
        EscrowTags.require(op.phase() == EscrowOperation.Phase.PREPARED && op.reason() == EscrowReason.NONE, "Admission must be a fresh preparation");
        EscrowTags.require(op.sequence() == nextSequence() && !queued.containsKey(op.id())
                && !requests.containsKey(op.id()), "Duplicate or stale admission");
        EscrowTags.require(!frontiers.containsKey(op.source().key()) && !frontiers.containsKey(op.destination().key()), "Frozen endpoint frontier");
        EscrowTags.require(!flightWrites.locked(op.guard().rocket()) && data.ledger().armedFlights().stream()
                .noneMatch(arm -> arm.logical().equals(op.guard().rocket())), "Flight authority holds the endpoint");
        var candidate = new ArrayList<>(queued.values()); candidate.add(op);
        EscrowSettlementCapacity.requireAdmission(data.ledger().append(candidate), op, observed);
        EscrowTags.require(op.nativeBinding().kind() != EscrowSignatureKind.FUEL_VECTOR_V1 || op.nativeBinding().complete(), "Native L1 needs a complete envelope binding");
        EscrowSettlementCapacity.preflight(data.ledger().append(candidate), op.id());
        var reservation = admission.tryReserve(op.id(), op.owner(), op.guard().rocket(), EscrowAdmissionAuthority.Consumer.PROPULSION);
        EscrowTags.require(reservation == EscrowAdmissionAuthority.Result.RESERVED, "Shared admission refused: " + reservation);
        queued.put(op.id(), op); requests.put(op.id(), completion); issued = op.sequence(); data.setDirty();
        admission.refresh();
    }
    public boolean commitPreparations() {
        owner(); if (closed || queued.isEmpty() || tick - lastStart < 10) { return false; }
        if (writer.request(() -> snapshot(true, null), this::acknowledge)) { lastStart = tick; return true; }
        return false;
    }
    /** O1 only. This acknowledgement does not prove endpoints, remove journals or authorize unlock. */
    boolean beginSettlement(EscrowSettlementIntent intent, Consumer<Boolean> completion) {
        owner();
        if (closed || !writer.idle() || writer.unavailable() || !queued.isEmpty() || !transitions.isEmpty()
                || tick - lastStart < 10) { return false; }
        EscrowLedger candidate;
        if (intent.choice() == EscrowSettlementIntent.Choice.TRANSFER_FLOOR_RETIRE) {
            EscrowTags.require(intent.ledger().equals(data.ledger().id())
                    && data.ledger().floors().stream().anyMatch(intent.floor()::equals)
                    && data.ledger().records().stream().noneMatch(op -> op.guard().rocket().equals(intent.floor().getUUID("rocket_id"))),
                    "Floor changed since preview");
            candidate = EscrowSettlementCapacity.floorRetirement(data.ledger(), intent, nextSequence(), tick);
        } else {
            candidate = intent.freeze(data.ledger());
            EscrowSettlementCapacity.preflight(candidate);
        }
        candidate.records().stream().filter(op -> intent.ids().contains(op.id())).forEach(op -> EscrowSettlementTombstones.create(op, intent));
        var frozen = candidate.generation(Math.addExact(writer.lastGeneration(), 1));
        if (intent.choice() == EscrowSettlementIntent.Choice.TRANSFER_FLOOR_RETIRE) {
            var op = candidate.records().stream().filter(record -> intent.ids().contains(record.id())).findFirst().orElseThrow();
            var reservation = admission.tryReserve(op.id(), op.owner(), op.guard().rocket(), EscrowAdmissionAuthority.Consumer.PROPULSION);
            EscrowTags.require(reservation == EscrowAdmissionAuthority.Result.RESERVED, "Shared floor-intent admission refused: " + reservation);
        }
        boolean accepted = writer.request(() -> EscrowLedgerWriter.Snapshot.of(frozen, Set.of()), result -> {
            acknowledge(result);
            if (result.outcome() != EscrowLedgerWriter.Outcome.UNKNOWN && !closed) {
                completion.accept(result.outcome() == EscrowLedgerWriter.Outcome.COMMITTED
                        && intent.ids().stream().allMatch(id -> data.ledger().records().stream()
                        .anyMatch(op -> op.id().equals(id) && op.settlement().equals(intent.encode()))));
            }
        });
        if (accepted) { issued = Math.max(issued, candidate.issuedThrough()); lastStart = tick; admission.refresh(); }
        return accepted;
    }
    public void autosave() {
        owner(); if (closed || !data.isDirty() || tick - lastStart < 10) { return; }
        if (flightWrites.pending() && transitions.isEmpty()) { return; }
        // Only acknowledged records are included. Queued preparations are never implicit autosave admissions.
        if (writer.request(() -> snapshot(false, null), this::acknowledge)) { lastStart = tick; }
    }
    /** O3/O4 use the frozen decision, never the ordinary prefix-retirement path. */
    boolean settlementWrite(EscrowSettlementIntent intent, Map<EscrowEndpoint.Key, EscrowBarrier.Proof> proofs,
                            boolean release, java.util.function.BooleanSupplier journalLease, Consumer<Boolean> callback) {
        return settlementWriteExact(intent, proofs, release, journalLease, acknowledgement -> callback.accept(acknowledgement.committed()));
    }
    record SettlementAcknowledgement(UUID ledger, String digest, Set<UUID> members, long generation, boolean committed) { }
    boolean settlementWriteExact(EscrowSettlementIntent intent, Map<EscrowEndpoint.Key, EscrowBarrier.Proof> proofs,
                                 boolean release, java.util.function.BooleanSupplier journalLease,
                                 Consumer<SettlementAcknowledgement> callback) {
        owner();
        if (closed || !writer.idle() || writer.unavailable() || !queued.isEmpty() || !transitions.isEmpty()
                || tick - lastStart < 10) { return false; }
        var keys = intent.checkpointed().stream().map(observation -> observation.endpoint().key()).collect(java.util.stream.Collectors.toSet());
        var frozenProofs = Map.copyOf(proofs);
        EscrowTags.require(frozenProofs.keySet().equals(keys) && frozenProofs.values().stream().allMatch(EscrowBarrier.Proof::valid),
                "Settlement lacks jointly retained survivor/absence proofs");
        for (var observation : intent.checkpointed()) {
            var expected = observation.missing() ? new EscrowPrefix.State(intent.ledger(), 0,
                    EscrowSettlementTombstones.observationDigest(observation), List.of()) : observation.state();
            EscrowTags.require(expected.equals(frozenProofs.get(observation.endpoint().key()).frontier()), "Settlement proof does not cover the frozen decision");
        }
        EscrowTags.require(data.ledger().records().stream().filter(op -> intent.ids().contains(op.id())).count() == intent.ids().size()
                && data.ledger().records().stream().filter(op -> intent.ids().contains(op.id())).allMatch(op -> op.settlement().equals(intent.encode())),
                "Settlement decision is not acknowledged");
        EscrowLedger candidate = release ? EscrowSettlementTombstones.release(data.ledger(), intent, Math.addExact(writer.lastGeneration(), 1))
                : EscrowSettlementTombstones.install(data.ledger(), intent);
        EscrowSettlementCapacity.preflight(candidate);
        var frozen = candidate.generation(Math.addExact(writer.lastGeneration(), 1));
        boolean accepted = writer.request(() -> EscrowLedgerWriter.Snapshot.of(frozen, Set.of()), result -> {
            acknowledge(result);
            if (result.outcome() == EscrowLedgerWriter.Outcome.UNKNOWN) { return; }
            if (!closed) { callback.accept(new SettlementAcknowledgement(intent.ledger(), intent.digest(), intent.ids(),
                    result.generation(), result.outcome() == EscrowLedgerWriter.Outcome.COMMITTED)); }
        }, () -> frozenProofs.values().stream().allMatch(EscrowBarrier.Proof::valid) && journalLease.getAsBoolean());
        if (accepted) { lastStart = tick; } return accepted;
    }
    void persistTransitions() { owner(); if (!transitions.isEmpty()) { autosave(); } }
    private EscrowLedgerWriter.Snapshot snapshot(boolean admissions, Set<UUID> retire) {
        return snapshot(admissions, retire, Map.of());
    }
    private EscrowLedgerWriter.Snapshot snapshot(boolean admissions, Set<UUID> retire, Map<EscrowEndpoint.Key, EscrowBarrier.Proof> proofs) {
        EscrowLedger candidate = data.ledger();
        candidate = candidate.replace(
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
            EscrowSettlementCapacity.preflight(candidate);
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
        owner();
        // A queued admission still reaches L1 after the freeze, and its marker would be absent from this frontier.
        EscrowTags.require(!admitting(endpoint), "Queued admission touches the endpoint");
        var chain = EscrowPrefix.chains(data.ledger().records()).get(endpoint);
        EscrowTags.require(chain != null && EscrowPrefix.classify(data.ledger().id(), chain, frontier)
                == EscrowPrefix.Classification.AFTER, "Proof needs the complete live prefix");
        EscrowPrefix.State old = frontiers.putIfAbsent(endpoint, frontier);
        EscrowTags.require(old == null || old.equals(frontier), "Frozen frontier changed");
    }
    /** The callback runs at most once: true after exact L2 acknowledgement, false after a definite failure. */
    public boolean retire(Set<UUID> ids, Map<EscrowEndpoint.Key, EscrowBarrier.Proof> proofs,
                          Consumer<Boolean> callback) {
        owner(); EscrowTags.require(ids.size() <= EscrowLimits.OPERATIONS, "Retirement count exceeded");
        Set<UUID> retired = Set.copyOf(ids); // Successor capture must not retain a caller-owned mutable set.
        EscrowPrefix.requireRetirementPrefix(data.ledger().records(), retired);
        if (!ordinaryRetirementAllowed(retired)) { return false; }
        requireCoveringProofs(data.ledger().records(), retired, proofs, frontiers);
        if (closed || tick - lastStart < 10) { return false; }
        Set<EscrowEndpoint.Key> affected = data.ledger().records().stream().filter(op -> retired.contains(op.id()))
                .flatMap(op -> java.util.stream.Stream.of(op.source().key(), op.destination().key()))
                .collect(java.util.stream.Collectors.toSet());
        var frozenProofs = proofs.entrySet().stream().filter(entry -> affected.contains(entry.getKey()))
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        boolean accepted = writer.request(() -> {
            // Revalidate tokens at capture, not merely when the successor request is queued.
            // A superseded queued proof is a normal refusal, not an owner-thread exception.
            return snapshot(false, ordinaryRetirementAllowed(retired)
                    && frozenProofs.values().stream().allMatch(EscrowBarrier.Proof::valid) ? retired : null, frozenProofs);
        }, result -> {
            acknowledge(result);
            // UNKNOWN is not final: the writer retries this exact candidate. The callback reports only the resolution.
            if (result.outcome() == EscrowLedgerWriter.Outcome.UNKNOWN) { return; }
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
    /** Every endpoint of every retired operation needs a valid frozen proof whose markers contain that operation. */
    static void requireCoveringProofs(List<EscrowOperation> records, Set<UUID> retired,
                                      Map<EscrowEndpoint.Key, EscrowBarrier.Proof> proofs, Map<EscrowEndpoint.Key, EscrowPrefix.State> frontiers) {
        for (var chain : EscrowPrefix.chains(records).entrySet()) {
            EscrowBarrier.Proof proof = proofs.get(chain.getKey());
            for (EscrowPrefix.Link link : chain.getValue()) {
                if (!retired.contains(link.operation().id())) { continue; }
                EscrowTags.require(proof != null && proof.valid() && proof.frontier().equals(frontiers.get(chain.getKey()))
                        && proof.frontier().markers().contains(link.marker()), "Retirement lacks a valid frozen endpoint proof covering its operation");
            }
        }
    }
    /** Package-private until the F1/F6 server adapter supplies complete retained proofs and leases. */
    boolean flightWrite(UUID logical, java.util.function.BiFunction<EscrowLedger, Long, EscrowLedger> change,
                        java.util.function.BooleanSupplier proofAndJournalLease, Consumer<Boolean> callback) {
        owner(); if (closed || writer.unavailable() || !queued.isEmpty() || !transitions.isEmpty()) { return false; }
        return flightWrites.request(logical, change, proofAndJournalLease, callback);
    }
    public void tick(long now) {
        owner(); tick = now; writer.tick(now);
        if (!closed && queued.isEmpty() && transitions.isEmpty() && tick - lastStart >= 10
                && flightWrites.start(data::ledger, writer, this::acknowledge)) { lastStart = tick; }
    }
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
    boolean pruningBusy(EscrowEndpoint.Key endpoint) {
        owner(); return !operational() || EscrowSettlementPruning.busy(endpoint, data.ledger().records(), queued.values(),
                writer.capturedOperations(), frontiers.keySet());
    }
    public boolean admitting(EscrowEndpoint.Key endpoint) {
        owner(); return queued.values().stream().anyMatch(op -> op.source().key().equals(endpoint) || op.destination().key().equals(endpoint));
    }
    public boolean writerIdle() { return writer.idle(); }
    /** ARMED is consulted by the governed dispatcher, not treated as ordinary idle authority. */
    boolean governedAvailable(UUID logical) {
        owner();
        return operational() && !writer.unavailable() && !flightWrites.locked(logical)
                && frontiers.keySet().stream().noneMatch(key -> logical.equals(key.logicalRocket()))
                && unresolved().noneMatch(op -> logical.equals(op.guard().rocket())
                        || logical.equals(op.source().logicalRocket()) || logical.equals(op.destination().logicalRocket()));
    }
    EscrowReason currentReason(EscrowOperation op) { owner(); return transitions.getOrDefault(op.id(), op.reason()); }
    private boolean ordinaryRetirementAllowed(Set<UUID> ids) {
        owner();
        return data.ledger().records().stream().filter(op -> ids.contains(op.id())).noneMatch(op -> currentReason(op).manual()
                || op.phase() == EscrowOperation.Phase.SETTLING_ACCEPT || op.phase() == EscrowOperation.Phase.SETTLING_ABANDON);
    }
    public EscrowFloors.Usage floorUsage() { owner(); return EscrowFloors.usage(data.ledger(), queued.values()); }
    void collectFloors(io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData journal) {
        owner(); floorJournal = journal;
        if (closed || !journal.acknowledgedCurrent()) { return; }
        var candidate = EscrowFloors.collect(data.ledger(), queued.values(), id -> journal.find(id).isEmpty());
        if (candidate.floors().size() != data.ledger().floors().size()) { data.setDirty(); autosave(); }
    }
    public boolean logicalLocked(UUID logical) {
        owner();
        return !operational() || flightWrites.locked(logical) || data.ledger().armedFlights().stream().anyMatch(arm -> arm.logical().equals(logical))
                || frontiers.keySet().stream().anyMatch(key -> logical.equals(key.logicalRocket()))
                || unresolved()
                .anyMatch(op -> logical.equals(op.guard().rocket()));
    }
    public boolean locked(EscrowEndpoint.Key endpoint) {
        return !operational() || frontiers.containsKey(endpoint) || EscrowSettlementTombstones.endpointLocked(data.ledger(), endpoint) || unresolved()
                .anyMatch(op -> op.source().key().equals(endpoint) || op.destination().key().equals(endpoint));
    }
    private java.util.stream.Stream<EscrowOperation> unresolved() {
        return java.util.stream.Stream.of(data.ledger().records().stream(), queued.values().stream(), writer.capturedOperations().stream())
                .flatMap(java.util.function.Function.identity());
    }
    Set<UUID> unresolvedIds() {
        owner(); return unresolved().map(EscrowOperation::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    @Override public void close() { owner(); if (!closed) { closed = true; flightWrites.close(); writer.close(); frontiers.clear(); } }
    /** Crash path, where ServerStoppingEvent never ran: no drain or callbacks, but the writer is joined. */
    public void abort() { owner(); if (!closed) { closed = true; flightWrites.close(); writer.abort(); frontiers.clear(); } }
    private void owner() { EscrowTags.require(Thread.currentThread() == owner, "Escrow coordinator requires its owner thread"); }
}
