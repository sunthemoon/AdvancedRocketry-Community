package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Ledger-owned coordinator: one exists for each bound ledger SavedData, and only a test hook's ledger replacement ends
 * its attachment. Admission uses the runtime-owned authority through the PROPULSION delegate. Production admission
 * stays disabled: the loader's fair queue reaches {@link #prepareFill} through {@link EscrowLoaderAdmission} only behind
 * its gated release-test switch.
 */
public final class EscrowCoordinator implements AutoCloseable {
    /** W11B3-DESIGN section 2's enabling condition is not met; only Root's reviewed enable commit may change this. */
    static final boolean PRODUCTION_ADMISSION = false;
    private final Thread owner = Thread.currentThread();
    private final ResourceEscrowSavedData data;
    private final EscrowLedgerWriter writer;
    private final Map<UUID, EscrowOperation> queued = new LinkedHashMap<>();
    private final Map<UUID, Consumer<Boolean>> requests = new HashMap<>();
    private final Map<EscrowEndpoint.Key, EscrowPrefix.State> frontiers = new HashMap<>();
    private final Map<UUID, EscrowReason> transitions = new HashMap<>();
    private final EscrowReconciliationPruning.Candidates pruningCandidates = new EscrowReconciliationPruning.Candidates();
    private final EscrowAdmissionAuthority admission;
    private final EscrowAdmissionAuthority.Delegate propulsion;
    private final EscrowAdmissionAuthority.Store admissionStore = new EscrowAdmissionAuthority.Store() {
        @Override public EscrowAdmissionAuthority.Index index() {
            return new EscrowAdmissionAuthority.Index(true, unresolved().map(EscrowAdmissionAuthority.Pending::of).toList());
        }
        @Override public boolean holdsWork() { return !queued.isEmpty() || !writer.idle(); }
    };
    private final EscrowFlightWrites flightWrites = new EscrowFlightWrites();
    private final EscrowTransferTurns transferTurns = new EscrowTransferTurns();
    EscrowTransferTurns transferTurns() { owner(); return transferTurns; }
    private final EscrowReconciliationDriver reconciliation = new EscrowReconciliationDriver(this);
    private long issued;
    private long tick;
    private long lastStart = -10;
    private boolean closed;
    /** A ledger created by a first fill: its empty generation-1 baseline must be acknowledged before any L1 capture. */
    private boolean creationBaseline;
    private io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData floorJournal;

    /** Detached coordinator with its own authority, for unit tests and gated standalone fixtures; never a server's ledger. */
    EscrowCoordinator(ResourceEscrowSavedData data, EscrowLedgerWriter writer) {
        this(data, writer, new EscrowAdmissionAuthority().register(EscrowAdmissionAuthority.Consumer.PROPULSION));
    }
    /** Attaches this ledger's index to the given PROPULSION delegate; it never registers one. */
    EscrowCoordinator(ResourceEscrowSavedData data, EscrowLedgerWriter writer, EscrowAdmissionAuthority.Delegate propulsion) {
        EscrowTags.require(propulsion.consumer() == EscrowAdmissionAuthority.Consumer.PROPULSION, "Coordinator needs the PROPULSION delegate");
        this.data = data; this.writer = writer; issued = data.ledger().issuedThrough();
        this.propulsion = propulsion; admission = propulsion.authority();
        EscrowTags.require(attachAdmission(), "Admission delegate still holds another ledger's queued, captured or UNKNOWN work");
    }
    /** Retargets the PROPULSION delegate to this ledger; false, with nothing changed, while another ledger holds work. */
    boolean attachAdmission() { owner(); return propulsion.retarget(admissionStore); }
    public boolean productionAdmissionEnabled() { return PRODUCTION_ADMISSION; }
    /** Creation step 2's mode (W11B3-DESIGN section 2): set once, on the owner thread, for a ledger nothing has written. */
    void beginCreationBaseline() {
        owner(); EscrowTags.require(!creationBaseline && data.ledger().writerGeneration() == 0 && data.ledger().records().isEmpty()
                && writer.idle() && writer.lastGeneration() == 0 && queued.isEmpty(), "Creation baseline needs an unwritten empty ledger");
        creationBaseline = true;
    }
    /** True until the empty creation baseline's exact acknowledgement; L1 is never captured before it. */
    boolean creationBaselinePending() { owner(); return creationBaseline; }
    /**
     * Creation step 2's queue entry for an operation the authority has already reserved. A failed bind-time readback
     * counts as a definite baseline failure: the operation leaves the queue at once, and its reservation with it.
     */
    void enqueueCreated(EscrowOperation op, Consumer<Boolean> completion) {
        owner(); EscrowTags.require(creationBaseline && op.sequence() == nextSequence() && queued.isEmpty(), "Creation admission out of order");
        enqueue(op, completion);
        if (!writer.baselineAvailable()) { cancelUncaptured(); }
    }
    /** A definite failure before L1: only queued, never-captured work leaves, each with exactly one false callback. */
    private void cancelUncaptured() {
        for (UUID id : List.copyOf(queued.keySet())) {
            if (writer.containsCaptured(id)) { continue; }
            queued.remove(id); Consumer<Boolean> callback = requests.remove(id);
            if (callback != null && !closed) { callback.accept(false); }
        }
        data.setDirty(); admission.refresh();
    }
    /**
     * Every unresolved fill naming an endpoint, by sequence: durable records, queued admissions and captured writer
     * candidates, each once. Same-direction chaining projects a capture through exactly this prefix.
     */
    List<EscrowOperation> unresolvedChain(EscrowEndpoint.Key endpoint) {
        owner(); var seen = new java.util.HashSet<UUID>();
        return unresolved().filter(op -> op.kind() == EscrowOperation.Kind.FLUID_PROPULSION_FILL
                        && (op.source().key().equals(endpoint) || op.destination().key().equals(endpoint)) && seen.add(op.id()))
                .sorted(java.util.Comparator.comparingLong(EscrowOperation::sequence)).toList();
    }
    /**
     * Every unresolved operation naming the logical rocket is an ordinary loader fill into exactly this endpoint:
     * PREPARED, no reason and no settlement.
     */
    boolean ordinaryFillsOnly(UUID logical, EscrowEndpoint.Key destination) {
        owner();
        return unresolved().filter(op -> logical.equals(op.guard().rocket()) || logical.equals(op.rocket().logicalRocket()))
                .allMatch(op -> op.kind() == EscrowOperation.Kind.FLUID_PROPULSION_FILL && op.settlement().isEmpty()
                        && currentReason(op) == EscrowReason.NONE && op.phase() == EscrowOperation.Phase.PREPARED
                        && EscrowLoaderEndpoint.is(op.source()) && op.destination().key().equals(destination));
    }
    /** Every unresolved operation whose source or destination identity is this physical UUID, each once. */
    List<EscrowOperation> unresolvedNaming(UUID identity) {
        owner(); var seen = new java.util.HashSet<UUID>();
        return unresolved().filter(op -> (identity.equals(op.source().identity()) || identity.equals(op.destination().identity()))
                && seen.add(op.id())).toList();
    }
    boolean durable(UUID operation) { owner(); return data.ledger().records().stream().anyMatch(op -> op.id().equals(operation)); }
    boolean cargoPending(UUID logical) {
        owner(); return unresolved().anyMatch(op -> op.kind().cargo() && logical.equals(op.rocket().logicalRocket()));
    }
    boolean writerUnknown() { owner(); return writer.unknown(); }
    /** A failed baseline or three unresolved writes (EscrowLedgerWriter#unavailable); operational() covers only the first. */
    boolean writerUnavailable() { owner(); return writer.unavailable(); }
    boolean frozenIdentity(UUID identity) { owner(); return frontiers.keySet().stream().anyMatch(key -> identity.equals(key.identity())); }
    void reconcile(net.minecraft.server.MinecraftServer server, EscrowOperation operation,
                   Consumer<EscrowReconciliationScheduler.Result> completion) {
        owner(); reconciliation.attempt(server, operation, completion);
    }
    void registerReconciliationTestSource(net.minecraft.server.MinecraftServer server, EscrowEndpoint source) {
        owner(); reconciliation.registerTestSource(server, source);
    }
    boolean reconciliationCheckpointProved(UUID operation) { owner(); return reconciliation.checkpointProved(operation); }
    public boolean operational() { owner(); return !closed && writer.baselineAvailable(); }
    public EscrowLedger ledger() { return data.ledger(); }
    public long nextSequence() { owner(); return Math.addExact(Math.max(issued, data.ledger().issuedThrough()), 1); }
    public EscrowAdmissionAuthority admissionAuthority() { owner(); return admission; }
    /** Gated C0/C1 entry: observed native state, acknowledged component/guard, then the existing L1 writer. */
    void prepareNativeTest(EscrowOperation op, io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity rocket,
                           net.minecraft.world.level.block.entity.BlockEntity source, Consumer<Boolean> completion) {
        owner(); EscrowTestEndpoints.requireEnabled(rocket.getServer());
        EscrowTags.require(operational() && writer.idle() && queued.isEmpty(), "C0 waits for the acknowledged ledger");
        EscrowReconciliationPruning.source(rocket.getServer(), this, op.source());
        requireNativeSourceAvailable(EscrowTestEndpoints.state(source.getPersistentData(), data.ledger().id(), false));
        // Rocket restart pruning runs from the ordinary observation hook; C0 never binds a stale wrapper.
        EscrowReconciliationPruning.observe(rocket);
        var capture = EscrowNativeBinding.captureCurrentTest(op, rocket, source, data.ledger());
        var candidate = data.ledger().append(List.of(capture.operation()));
        EscrowReconciliationView.capture(rocket.getServer(), candidate, EscrowSettlementIntent.component(candidate.records(), op.id()));
        reconciliation.registerTestSource(rocket.getServer(), op.source());
        prepareTest(capture.operation(), capture.before(), completion);
    }
    /** The fixture's fixed C2 projection supports one source member, not a live source chain. */
    static void requireNativeSourceAvailable(EscrowPrefix.State source) {
        EscrowTags.require(source.markers().isEmpty(), "Fixture source still carries an earlier member");
    }
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
        EscrowTags.require(!EscrowAdapterIds.nativeRocket(op.rocket()) || op.nativeBinding().complete(), "Native L1 needs a complete envelope binding");
        EscrowSettlementCapacity.preflight(data.ledger().append(candidate), op.id());
        var reservation = admission.tryReserve(op.id(), op.owner(), op.guard().rocket(), EscrowAdmissionAuthority.Consumer.PROPULSION);
        EscrowTags.require(reservation == EscrowAdmissionAuthority.Result.RESERVED, "Shared admission refused: " + reservation);
        enqueue(op, completion);
    }
    /** prepareFill's typed outcome. Every member but QUEUED is a refusal that changed nothing. */
    enum Fill { QUEUED, UNAVAILABLE, INVALID, STALE, ENDPOINT_BUSY, ROCKET_LIMIT, ESCROW_FULL, SERVER_FULL, ROCKET_FULL, IDENTITY_CONFLICT }
    /** The generalized transfer result does not expand W11b3's closed fill result contract. */
    enum Transfer {
        QUEUED, UNAVAILABLE, INVALID, STALE, ENDPOINT_BUSY, ROCKET_LIMIT, ESCROW_FULL, SERVER_FULL, ROCKET_FULL,
        IDENTITY_CONFLICT, ROCKET_BUSY, OTHER_TRANSFER_PENDING, RECEIPT_BACKED;
        static Transfer of(Fill fill) { return valueOf(fill.name()); }
        Fill fill() {
            return switch (this) {
                case ROCKET_BUSY, OTHER_TRANSFER_PENDING, RECEIPT_BACKED -> Fill.ENDPOINT_BUSY;
                default -> Fill.valueOf(name());
            };
        }
    }
    /**
     * Production fill admission (W11B3-DESIGN sections 2 and 4), beside the gated {@link #prepareTest}: the pure
     * {@link #fillRefusal} check, then the shared {@code tryReserve}, then the queue entry that locks both endpoints,
     * as one owner-thread action. A refusal issues no sequence and leaves the queue, the writer and the authority
     * unchanged. The completion receives true after the exact L1 acknowledgement and false after a definite failure.
     * No device calls this yet; production admission stays disabled.
     */
    Fill prepareFill(EscrowNativeBinding.Admission capture, Consumer<Boolean> completion) {
        return prepareTransfer(capture, completion).fill();
    }
    /** C-P capture entry for C-E: all eligibility passes finish before issuing a sequence or reserving anything. */
    Transfer prepareCargo(EscrowCargoPreparedCapture prepared, long serverTick,
                          io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoWorkBudget budget,
                          java.util.function.BooleanSupplier unchanged,
                          Consumer<Boolean> completion) {
        owner();
        var result = prepared.result();
        if (result.status() != io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoSelection.Status.ELIGIBLE) {
            prepared.discard();
            return result.status() == io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoSelection.Status.CARGO_TOO_LARGE
                    ? Transfer.ESCROW_FULL : Transfer.INVALID;
        }
        // Changed native inputs are discarded and must repeat the full eligibility capture, not merely its digests.
        var outcome = new Transfer[] {Transfer.UNAVAILABLE};
        budget.perform(serverTick, 16L * 1_048_576, 16_384, false, () -> {
            try {
                if (!unchanged.getAsBoolean() || !prepared.unchanged()) { outcome[0] = Transfer.STALE; }
                else {
                    // Repeat sequence, root, O1 and combined reservation against the final acknowledged root.
                    outcome[0] = prepareTransfer(result.capture(), completion);
                }
            } finally {
                // Apply must reconstruct from BEFORE, even when final admission throws.
                prepared.discard();
            }
            return new io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketSnapshotPreparation.Work(16L * 1_048_576, 16_384);
        });
        return outcome[0];
    }
    /** Shared owner-thread entry. Cargo has no production caller until C-E; no publication permit is issued here. */
    Transfer prepareTransfer(EscrowNativeBinding.Admission capture, Consumer<Boolean> completion) {
        owner(); var op = capture.operation();
        if (closed || writer.unavailable()) { return Transfer.UNAVAILABLE; }
        if (EscrowTransferAdmission.invalid(op)) { return Transfer.INVALID; }
        if (op.sequence() != nextSequence() || requests.containsKey(op.id())
                || unresolved().anyMatch(existing -> existing.id().equals(op.id()))) { return Transfer.STALE; }
        if (frontiers.containsKey(op.source().key()) || frontiers.containsKey(op.destination().key())
                || flightWrites.locked(op.guard().rocket())
                || data.ledger().armedFlights().stream().anyMatch(arm -> arm.logical().equals(op.guard().rocket()))) {
            return Transfer.ENDPOINT_BUSY;
        }
        var seen = new java.util.HashSet<UUID>();
        var pending = unresolved().filter(member -> !durable(member.id()) && seen.add(member.id())).toList();
        var refusal = EscrowTransferAdmission.refusal(data.ledger(), pending, capture);
        if (refusal.isPresent()) {
            if (refusal.get() == Transfer.OTHER_TRANSFER_PENDING || refusal.get() == Transfer.ROCKET_BUSY) {
                transferTurns.blocked(op.device().identity(), op.guard().rocket());
            }
            return refusal.get();
        }
        if (!transferTurns.allows(op.device().identity(), op.guard().rocket())) {
            return op.kind().cargo() ? Transfer.OTHER_TRANSFER_PENDING : Transfer.ROCKET_BUSY;
        }
        var reservation = admission.tryReserve(op.id(), op.owner(), op.guard().rocket(), EscrowAdmissionAuthority.Consumer.PROPULSION);
        if (reservation != EscrowAdmissionAuthority.Result.RESERVED) { return Transfer.of(fill(reservation)); }
        // Nothing that can fail runs between the reservation and its queue entry: a reservation has no release call.
        enqueue(op, completion);
        transferTurns.admitted(op.device().identity(), op.guard().rocket());
        return Transfer.QUEUED;
    }
    /** Not a fresh FLUID_PROPULSION_FILL with a complete FUEL_VECTOR_V1 binding. */
    static boolean invalid(EscrowOperation op) {
        return op.kind() != EscrowOperation.Kind.FLUID_PROPULSION_FILL || op.phase() != EscrowOperation.Phase.PREPARED
                || op.reason() != EscrowReason.NONE || !op.settlement().isEmpty()
                || op.nativeBinding().kind() != EscrowSignatureKind.FUEL_VECTOR_V1 || !op.nativeBinding().complete();
    }
    /** The authority's refusal as a typed fill result; ALREADY_HELD is STALE because every index was checked first. */
    static Fill fill(EscrowAdmissionAuthority.Result reservation) {
        return switch (reservation) {
            case SERVER_FULL -> Fill.SERVER_FULL;
            case ROCKET_FULL -> Fill.ROCKET_FULL;
            case IDENTITY_CONFLICT -> Fill.IDENTITY_CONFLICT;
            case ALREADY_HELD -> Fill.STALE;
            case UNAVAILABLE -> Fill.UNAVAILABLE;
            case RESERVED -> Fill.QUEUED;
        };
    }
    /**
     * prepareFill's pure check against a given root and its queued admissions: the five-marker limit and settlement
     * fit of {@code requireAdmission}, then the candidate preflight, which keeps Root-R6's floor-retirement headroom
     * and charges every unsettled component, the candidate's included, at its ceiling (Root-R8). It adds no capacity
     * rule of its own and mutates nothing; the first-fill path runs it against a virtual empty root.
     */
    static java.util.Optional<Fill> fillRefusal(EscrowLedger root, java.util.Collection<EscrowOperation> admissions,
                                                EscrowNativeBinding.Admission capture) {
        var op = capture.operation();
        if (capture.before().ledger() != null && !capture.before().ledger().equals(root.id())) { return java.util.Optional.of(Fill.STALE); }
        var candidate = new ArrayList<>(admissions); candidate.add(op);
        EscrowLedger prospective;
        try { prospective = root.append(candidate); }
        catch (IllegalArgumentException invalid) { return java.util.Optional.of(Fill.STALE); }
        try { EscrowSettlementCapacity.requireAdmission(prospective, op, capture.before()); }
        catch (IllegalArgumentException limit) { return java.util.Optional.of(Fill.ROCKET_LIMIT); }
        try { EscrowSettlementCapacity.preflight(prospective, op.id()); }
        catch (IllegalArgumentException full) { return java.util.Optional.of(Fill.ESCROW_FULL); }
        return java.util.Optional.empty();
    }
    /** The shared queue core: the reserved operation joins the queue, which locks both endpoints until resolution. */
    private void enqueue(EscrowOperation op, Consumer<Boolean> completion) {
        queued.put(op.id(), op); requests.put(op.id(), completion); issued = op.sequence(); data.setDirty();
        admission.refresh();
    }
    public boolean commitPreparations() {
        owner(); if (closed || queued.isEmpty() || tick - lastStart < 10) { return false; }
        if (creationBaseline) {
            // Creation step 3: the empty generation-1 root, never a queued admission, is the first checked write.
            if (!writer.idle()) { return false; }
            if (writer.request(() -> snapshot(false, null), this::acknowledge)) { lastStart = tick; return true; }
            return false;
        }
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
        boolean accepted = writer.request(() -> writer.snapshot(frozen, Set.of()), result -> {
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
        // R17 item 4: an unavailable writer needs this checked generation to recover; queued flights cannot start first.
        if (flightWrites.pending() && transitions.isEmpty() && !writer.unavailable()) { return; }
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
        boolean accepted = writer.request(() -> writer.snapshot(frozen, Set.of()), result -> {
            acknowledge(result);
            if (result.outcome() == EscrowLedgerWriter.Outcome.UNKNOWN) { return; }
            // An ordinary C3 freeze may precede a later sticky repair/O1. Only exact O4,
            // never O1/O3 or a failed/unknown write, supersedes that obsolete frontier.
            if (release && result.outcome() == EscrowLedgerWriter.Outcome.COMMITTED) {
                keys.forEach(frontiers::remove); reconciliation.settled(keys);
            }
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
        if (floorJournal != null && floorJournal.clean()) {
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
        return writer.snapshot(candidate, included);
    }
    private static boolean candidateContains(EscrowLedger ledger, UUID id) {
        return ledger.records().stream().anyMatch(op -> op.id().equals(id));
    }
    private void acknowledge(EscrowLedgerWriter.Completion completion) {
        owner();
        if (completion.outcome() == EscrowLedgerWriter.Outcome.COMMITTED) {
            // Any exact acknowledgement establishes the file, the creation baseline included; L1 may follow.
            creationBaseline = false;
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
        } else if (creationBaseline && (completion.outcome() == EscrowLedgerWriter.Outcome.FAILED_BEFORE_REPLACE
                || completion.outcome() == EscrowLedgerWriter.Outcome.GUARD_REFUSED)) {
            // Creation step 4: a definite baseline failure before L1 removes the queued fills and their reservations.
            // The empty root stays in RAM; a later checked write may still publish it. UNKNOWN keeps everything.
            cancelUncaptured();
        } else if (completion.outcome() == EscrowLedgerWriter.Outcome.FAILED_BEFORE_REPLACE
                || completion.outcome() == EscrowLedgerWriter.Outcome.GUARD_REFUSED) {
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
    record RetirementAcknowledgement(UUID ledger, Set<UUID> members, long generation, String digest, boolean committed) {
        RetirementAcknowledgement { members = Set.copyOf(members); }
    }
    /** C7 consumes a native C3-C6 request, not caller-supplied booleans or individual endpoint proofs. */
    boolean retireNative(EscrowReconciliationCheckpoint request, Consumer<RetirementAcknowledgement> callback) {
        owner();
        if (closed || !writer.idle() || writer.unavailable() || !queued.isEmpty() || !transitions.isEmpty()
                || tick - lastStart < 10 || data.ledger() != request.root || !request.proved()
                || !ordinaryRetirementAllowed(request.members)) { return false; }
        var members = request.members;
        var proofs = Map.copyOf(request.proofs);
        var lease = request.journalLease;
        var states = request.ends.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey, entry -> entry.getValue().state()));
        EscrowTags.require(EscrowReconciliationValidation.retirementReady(request.root, members, states),
                "C7 waits for every later acknowledged member on the affected endpoints");
        requireCoveringProofs(request.root.records(), members, proofs, frontiers);
        java.util.function.BooleanSupplier valid = () -> lease.getAsBoolean()
                && proofs.values().stream().allMatch(EscrowBarrier.Proof::valid);
        // Idle writer capture is synchronous on the owner thread. No uncaptured successor can
        // substitute a newer root or a different retirement request beneath this acknowledgement.
        long generation = Math.addExact(writer.lastGeneration(), 1);
        boolean accepted = writer.request(() -> {
            // A racing lease/token loss is a normal refusal, never a sticky endpoint diagnosis.
            return snapshot(false, data.ledger() == request.root && ordinaryRetirementAllowed(members)
                    && valid.getAsBoolean() ? members : null, proofs);
        }, result -> {
            acknowledge(result);
            if (result.outcome() == EscrowLedgerWriter.Outcome.UNKNOWN) { return; }
            boolean committed = result.outcome() == EscrowLedgerWriter.Outcome.COMMITTED
                    && result.generation() == generation && result.snapshot().ledger().id().equals(request.root.id())
                    && result.snapshot().ledger().records().stream().noneMatch(op -> members.contains(op.id()));
            if (committed) {
                // As with the model-fixture L2 path, release only in this exact writer callback.
                // Remaining operations still hold their endpoints and require freshly frozen tokens.
                request.ends.keySet().forEach(frontiers::remove);
                proofs.values().forEach(EscrowBarrier.Proof::release);
            }
            if (!closed) { callback.accept(new RetirementAcknowledgement(request.root.id(), members,
                    result.generation(), result.digest(), committed)); }
        }, valid);
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
    void observePruningCandidate(RocketEscrowSection section, Runnable observation) {
        owner(); pruningCandidates.observe(data.ledger(), section, observation);
    }
    /** Scheduling hint only; every writer entry point still repeats its own checks. */
    boolean checkedWriteReady() {
        owner(); return !closed && writer.idle() && !writer.unavailable() && queued.isEmpty()
                && transitions.isEmpty() && tick - lastStart >= 10;
    }
    /** ARMED is consulted by the governed dispatcher, not treated as ordinary idle authority. */
    boolean governedAvailable(UUID logical) {
        return governedWaitReason(logical) == null;
    }
    /** R11 distinguishes ordinary queued work from a writer or manual-repair failure. */
    String governedWaitReason(UUID logical) {
        owner();
        if (!operational() || writer.unavailable()) { return "GOVERNED_LEDGER_UNAVAILABLE"; }
        var related = unresolved().filter(op -> logical.equals(op.guard().rocket())
                || logical.equals(op.source().logicalRocket()) || logical.equals(op.destination().logicalRocket())).toList();
        if (related.stream().anyMatch(op -> currentReason(op).manual())) { return "GOVERNED_LEDGER_UNAVAILABLE"; }
        return flightWrites.locked(logical) || !related.isEmpty()
                || frontiers.keySet().stream().anyMatch(key -> logical.equals(key.logicalRocket()))
                ? "GOVERNED_LEDGER_PENDING" : null;
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
        if (closed || !journal.clean()) { return; }
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
    @Override public void close() { owner(); if (!closed) { closed = true; transferTurns.close(); reconciliation.close(); flightWrites.close(); writer.close(); frontiers.clear(); } }
    /** Crash path, where ServerStoppingEvent never ran: no drain or callbacks, but the writer is joined. */
    public void abort() { owner(); if (!closed) { closed = true; transferTurns.close(); reconciliation.close(); flightWrites.close(); writer.abort(); frontiers.clear(); } }
    private void owner() { EscrowTags.require(Thread.currentThread() == owner, "Escrow coordinator requires its owner thread"); }
}
