package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * One bay receipt store on a real file, attached to the BAY delegate of an authority, with a switchable committer and
 * an awaited IO thread. The PROPULSION side is either a synthetic index ({@link #fluid}) or a real ledger delegate.
 */
final class BayReceiptHarness implements AutoCloseable {
    static final CheckedSavedDataFile.Committer AMBIGUOUS = (staged, target) -> {
        throw new java.io.IOException("Injected ambiguous receipt replacement");
    };
    final EscrowAdmissionAuthority authority;
    final EscrowAdmissionAuthority.Delegate bay;
    final List<EscrowAdmissionAuthority.Pending> fluid = new ArrayList<>();
    final AtomicReference<CheckedSavedDataFile.Committer> committer = new AtomicReference<>(CheckedSavedDataFile::atomicMove);
    final AwaitedLedgerIo io = new AwaitedLedgerIo();
    final Path file;
    final BayReceipts receipts;
    final List<BayReceiptWriter.Outcome> outcomes = new ArrayList<>();
    long tick = 1;

    /** A synthetic PROPULSION index and a NO_FILE bay, as at startup in a world without a receipt file. */
    static BayReceiptHarness empty(Path directory) throws Exception {
        var authority = new EscrowAdmissionAuthority(); var harness = new BayReceiptHarness(authority, directory);
        authority.registerIndex(EscrowAdmissionAuthority.Consumer.PROPULSION, () -> new EscrowAdmissionAuthority.Index(true, harness.fluid));
        harness.receipts.start(null, CheckedSavedDataFile.Presence.ABSENT, harness.file, 0);
        harness.receipts.createForAdmission(harness.file, 0);
        return harness;
    }
    /** Shares an authority whose PROPULSION delegate a real ledger coordinator fills. */
    BayReceiptHarness(EscrowAdmissionAuthority authority, Path directory) throws Exception {
        this.authority = authority;
        bay = authority.register(EscrowAdmissionAuthority.Consumer.BAY);
        file = Files.createDirectories(directory).resolve(ManagedSavedDataType.BAY_RECEIPTS.fileName());
        receipts = new BayReceipts(bay, (staged, target) -> committer.get().commit(staged, target), () -> io);
    }
    BayReceiptStore store() { return receipts.store(); }
    BayReceiptStore.Admission put(BayReceipt receipt) { return store().put(receipt, outcomes::add); }
    BayReceiptStore.Admission remove(BayReceipt receipt) { return store().remove(receipt.op(), outcomes::add); }
    /** Owner ticks, each after the submitted writes, until the store holds no work. */
    void settle() { until(() -> !store().holdsWork()); }
    void until(BooleanSupplier done) { tick = io.tickUntil(store()::tick, tick, done); }
    EscrowAdmissionAuthority.Result reserve(BayReceipt receipt) {
        return authority.checkReserve(receipt.op(), receipt.owner(), receipt.rocket(), EscrowAdmissionAuthority.Consumer.BAY);
    }
    @Override public void close() { if (store() != null) { committer.set(CheckedSavedDataFile::atomicMove); store().close(); } }
}
