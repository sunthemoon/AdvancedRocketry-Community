package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * W129aF B3a (design section 6.1: "other live roots, plus existing writer traffic"): a resident escrow ledger and a
 * resident bay receipt store on one admission authority, as {@code EscrowRuntime} holds them, each written by its
 * existing checked writer on its own IO thread. The calling thread is their owner and drives their unchanged package
 * APIs. A ledger mutation persists a retry reason (NONE and BARRIER_IO alternate on one operation) through the
 * coordinator's autosave request; a receipt mutation re-puts one REJECTED receipt with a new reason and tick. Both
 * publish on the owner tick that polls their writers, and every tenth write of each takes the readback path (the
 * committer replaces, then throws). Neither writer is modified.
 */
public final class EscrowWriterBenchmarkAccess implements AutoCloseable {
    /**
     * B3a's composition: half the ledger's 256-floor bound, then fluid fills up to the 64-operation bound or the first
     * admission refusal (53 on {@code 2426f00d}'s capacity rules); the store's retained bound, 32 owners of 128 receipts.
     */
    public static final int FLOORS = 128, OPERATIONS = EscrowLimits.OPERATIONS, RECEIPTS = BayReceiptRoot.RECEIPTS;
    public static final String LEDGER_THREAD = "ARCE-escrow-ledger", RECEIPT_THREAD = "ARCE-bay-receipts";
    static final int READBACK_EVERY = 10;
    private final EscrowAdmissionAuthority authority = new EscrowAdmissionAuthority();
    private final AtomicBoolean ledgerReadback = new AtomicBoolean(), receiptReadback = new AtomicBoolean();
    private final AtomicInteger ledgerReadbacks = new AtomicInteger(), receiptReadbacks = new AtomicInteger();
    private final int floors;
    private final String admissionStop;
    private final List<UUID> operations = new ArrayList<>();
    private final List<BayReceipt> current = new ArrayList<>();
    private final List<Double> ledgerTicks = new ArrayList<>(), receiptTicks = new ArrayList<>();
    private final Map<BayReceiptWriter.Outcome, Integer> receiptOutcomes = new EnumMap<>(BayReceiptWriter.Outcome.class);
    private final ResourceEscrowSavedData ledger;
    private final EscrowCoordinator coordinator;
    private final BayReceipts receipts;
    private long now, ledgerMutations, receiptMutations, ledgerSkips, receiptSkips, receiptRefusals;
    private long ledgerRequestTick, ledgerRequestGeneration, receiptRequestTick;
    private UUID ledgerOp;
    private EscrowReason ledgerReason;
    private int receiptsWaiting;

    private EscrowWriterBenchmarkAccess(Path directory, int floors, int operations, int receiptCount, long tick) {
        this.floors = floors; now = tick;
        // Floors come from ordinary admitted-fill retirement; every operation passes the admission capacity checks, and
        // the ledger stops growing at the first refusal, so the root is one that admission can reach.
        EscrowLedger root = floors == 0 ? EscrowTestFixtures.ledger() : EscrowCombinedReservationTest.retainedFloors(floors);
        String stop = "REQUESTED_COUNT";
        for (int i = 1; i <= operations; i++) {
            var op = EscrowTestFixtures.operation(floors + i, 50_000 + i, 0);
            var admitted = root.append(List.of(op));
            try {
                EscrowSettlementCapacity.requireAdmission(admitted, op, null);
                EscrowSettlementCapacity.preflight(admitted, op.id());
            } catch (EscrowSettlementCapacity.ReservationFull full) { stop = "REFUSED: " + full.getMessage(); break; }
            root = admitted; this.operations.add(op.id());
        }
        admissionStop = stop;
        ledger = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
        var ledgerWriter = new EscrowLedgerWriter(directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName()), 0,
                (staged, target) -> commit(staged, target, ledgerReadback, ledgerReadbacks, "ledger"));
        coordinator = new EscrowCoordinator(ledger, ledgerWriter, authority.register(EscrowAdmissionAuthority.Consumer.PROPULSION));
        Path receiptFile = directory.resolve(ManagedSavedDataType.BAY_RECEIPTS.fileName());
        receipts = new BayReceipts(authority.register(EscrowAdmissionAuthority.Consumer.BAY),
                (staged, target) -> commit(staged, target, receiptReadback, receiptReadbacks, "receipt"));
        receipts.start(null, CheckedSavedDataFile.Presence.ABSENT, receiptFile, tick);
        receipts.createForAdmission(receiptFile, tick);
        coordinator.tick(tick);
        ledger.setDirty(); coordinator.autosave(); // The baseline file, so every measured write replaces one.
        for (int n = 1; n <= receiptCount; n++) {
            var receipt = receipt(n, "R".repeat(BayReceipt.MAX_REASON), Long.MAX_VALUE);
            EscrowTags.require(receipts.store().put(receipt, outcome -> { }).queued(), "Benchmark receipt refused");
            current.add(receipt);
        }
    }

    /**
     * Both roots, written once and acknowledged before this returns: {@code floors} retained floors and
     * {@code operations} durable fluid fills (one rocket each) in the ledger, and {@code receipts} REJECTED receipts
     * with every field at its maximum encoding in the store. Returns the access; {@link #now()} is the next free tick.
     */
    public static EscrowWriterBenchmarkAccess create(Path directory, int floors, int operations, int receipts, long tick)
            throws InterruptedException {
        var access = new EscrowWriterBenchmarkAccess(directory, floors, operations, receipts, tick);
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(5);
        while (access.holdsWork()) {
            if (System.nanoTime() > deadline) { throw new IllegalStateException("Escrow and receipt baselines did not complete"); }
            Thread.sleep(5); access.tick(access.now + 1);
        }
        access.ledgerTicks.clear(); access.receiptTicks.clear(); access.receiptOutcomes.clear();
        access.ledgerReadbacks.set(0); access.receiptReadbacks.set(0);
        return access;
    }

    /** A receipt with every optional field present and every string at its maximum, as W27A section 2's 938 bytes. */
    static BayReceipt receipt(int n, String reason, long updatedTick) {
        return new BayReceipt(BayReceiptFixtures.id(n), n, BayReceiptFixtures.id(100_000 + n),
                new RocketPosition(-2_000_000 - n, -64, 2_000_000), BayReceiptFixtures.id(200_000 + n),
                BayReceiptFixtures.id(300_000 + (n - 1) / EscrowAdmissionAuthority.RETAINED_PER_OWNER),
                BayReceiptFixtures.id("advancedrocketrycommunity", 128), SatelliteKind.ASTEROID_MINER,
                BayReceiptFixtures.id("advancedrocketrycommunity", 128), BayReceiptFixtures.packageSignature(n),
                BayReceiptFixtures.id(400_000 + n), BayReceiptFixtures.hash(1), BayReceiptFixtures.hash(2),
                BayReceiptFixtures.hash(3), BayReceiptFixtures.hash(4), BayReceipt.Phase.REJECTED, reason, true, false,
                Long.MAX_VALUE, Long.MAX_VALUE, updatedTick, BayReceipt.MAX_ATTEMPTS);
    }

    /** IO thread: the unchanged atomic move; on the readback path it then throws, so the writer reads the file back. */
    private static void commit(Path staged, Path target, AtomicBoolean readback, AtomicInteger taken, String root) throws IOException {
        CheckedSavedDataFile.atomicMove(staged, target);
        if (readback.getAndSet(false)) { taken.incrementAndGet(); throw new IOException("W129aF B3a " + root + " readback path"); }
    }

    /** One owner tick: both writers publish here; a ledger request refused by its 10-tick cadence is repeated. */
    public void tick(long tick) {
        now = tick; coordinator.tick(tick); receipts.tick(tick);
        if (ledgerOp != null && ledger.ledger().writerGeneration() > ledgerRequestGeneration && !ledger.isDirty()
                && ledger.ledger().records().stream().anyMatch(op -> op.id().equals(ledgerOp) && op.reason() == ledgerReason)) {
            ledgerTicks.add((double) (tick - ledgerRequestTick)); ledgerOp = null;
        }
        if (ledger.isDirty() && coordinator.writerIdle()) { coordinator.autosave(); }
    }

    /** A ledger mutation and its write request; skipped (and counted) while the previous one is unpublished. */
    public void mutateLedger() {
        if (ledgerOp != null) { ledgerSkips++; return; }
        UUID id = operations.get((int) (ledgerMutations % operations.size()));
        EscrowOperation op = ledger.ledger().records().stream().filter(record -> record.id().equals(id)).findFirst().orElseThrow();
        EscrowReason next = op.reason() == EscrowReason.NONE ? EscrowReason.BARRIER_IO : EscrowReason.NONE;
        ledgerReadback.set(ledgerMutations % READBACK_EVERY == READBACK_EVERY - 1);
        ledgerOp = id; ledgerReason = next; ledgerRequestTick = now; ledgerRequestGeneration = ledger.ledger().writerGeneration();
        ledgerMutations++;
        coordinator.transition(id, next); coordinator.persistTransitions();
    }

    /** A receipt mutation through the store's put, which requests the write; skipped while the previous is unpublished. */
    public void mutateReceipt() {
        if (receiptsWaiting > 0) { receiptSkips++; return; }
        int index = (int) (receiptMutations % current.size());
        BayReceipt before = current.get(index);
        var next = before.withPhase(BayReceipt.Phase.REJECTED,
                (before.reason().startsWith("R") ? "S" : "R").repeat(BayReceipt.MAX_REASON), now);
        receiptReadback.set(receiptMutations % READBACK_EVERY == READBACK_EVERY - 1);
        receiptRequestTick = now;
        var admission = receipts.store().put(next, outcome -> {
            receiptsWaiting--; receiptOutcomes.merge(outcome, 1, Integer::sum);
            if (outcome == BayReceiptWriter.Outcome.COMMITTED) { receiptTicks.add((double) (now - receiptRequestTick)); }
        });
        if (!admission.queued()) { receiptRefusals++; return; }
        current.set(index, next); receiptMutations++; receiptsWaiting++;
    }

    public long now() { return now; }
    public boolean holdsWork() {
        return ledgerOp != null || ledger.isDirty() || !coordinator.writerIdle() || receiptsWaiting > 0 || receipts.store().holdsWork();
    }
    public boolean operational() { return coordinator.operational() && !receipts.store().unavailable(); }
    public List<Double> ledgerPublicationTicks() { return List.copyOf(ledgerTicks); }
    public List<Double> receiptPublicationTicks() { return List.copyOf(receiptTicks); }

    public Map<String, Object> composition() {
        var report = new LinkedHashMap<String, Object>();
        report.put("escrow_ledger", Map.of("operations", ledger.ledger().records().size(), "floors", floors,
                "kind", "FLUID_PROPULSION_FILL, one rocket each", "admission_stop", admissionStop,
                "root_bytes", RocketNbtSize.uncompressedBytes(EscrowCodec.encode(ledger.ledger())),
                "bound_bytes", EscrowLimits.ROOT_BYTES, "operation_bound", OPERATIONS, "pending_slots", authority.pendingCount()));
        report.put("bay_receipts", Map.of("receipts", receipts.store().receipts().size(), "owners",
                (current.size() + EscrowAdmissionAuthority.RETAINED_PER_OWNER - 1) / EscrowAdmissionAuthority.RETAINED_PER_OWNER,
                "phase", "REJECTED, every field at its maximum encoding",
                "root_bytes", RocketNbtSize.uncompressedBytes(BayReceiptCodec.encode(receipts.store().durable())),
                "bound_bytes", BayReceiptCodec.ROOT_BYTES, "receipt_bound", RECEIPTS, "retained_slots", authority.retainedCount()));
        return report;
    }

    /** Counts since {@link #create}; the publication latencies are returned separately. */
    public Map<String, Object> results() {
        var report = new LinkedHashMap<String, Object>();
        report.put("escrow_ledger", Map.of("mutations", ledgerMutations, "published", ledgerTicks.size(),
                "skipped_while_unpublished", ledgerSkips, "readback_paths_taken", ledgerReadbacks.get(),
                "writer_generation", ledger.ledger().writerGeneration()));
        var outcomes = new LinkedHashMap<String, Integer>();
        receiptOutcomes.forEach((outcome, count) -> outcomes.put(outcome.name(), count));
        report.put("bay_receipts", Map.of("mutations", receiptMutations, "published", receiptTicks.size(),
                "skipped_while_unpublished", receiptSkips, "refused", receiptRefusals,
                "readback_paths_taken", receiptReadbacks.get(), "outcomes", outcomes,
                "writer_generation", receipts.store().durable().writerGeneration()));
        return report;
    }

    /** Drains and joins both writers, as the server's stop does. */
    @Override public void close() { coordinator.close(); receipts.stop(); }
}
