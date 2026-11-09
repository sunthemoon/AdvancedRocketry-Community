package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.stream.LongStream;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;

/** W333: two real checked stores and one authority, isolated from the running world's admission pool. */
final class BayAdmissionRowGameTestFiles implements AutoCloseable {
    static final EscrowAdmissionAuthority.Consumer BAY = EscrowAdmissionAuthority.Consumer.BAY;
    static final EscrowAdmissionAuthority.Consumer FLUID = EscrowAdmissionAuthority.Consumer.PROPULSION;
    final GameTestHelper helper;
    final Path directory, bayFile, ledgerFile;
    final EscrowAdmissionAuthority authority = new EscrowAdmissionAuthority();
    final EscrowAdmissionAuthority.Delegate propulsion = authority.register(FLUID);
    final EscrowAdmissionAuthority.Delegate bay = authority.register(BAY);
    final AtomicReference<CheckedSavedDataFile.Committer> committer = new AtomicReference<>(CheckedSavedDataFile::atomicMove);
    final Io io = new Io();
    final BayReceipts receipts;
    final EscrowLedgerWriter writer;
    final EscrowCoordinator coordinator;
    long tick = 1;

    BayAdmissionRowGameTestFiles(GameTestHelper helper, Path directory, EscrowLedger ledger, List<BayReceipt> historical) throws Exception {
        this.helper = helper;
        EscrowTestEndpoints.requireEnabled(helper.getLevel().getServer());
        this.directory = Files.createDirectories(directory);
        bayFile = directory.resolve(ManagedSavedDataType.BAY_RECEIPTS.fileName());
        ledgerFile = directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
        if (!Files.exists(ledgerFile)) { CheckedSavedDataFile.replace(ledgerFile, ManagedSavedDataType.RESOURCE_ESCROW, () -> EscrowCodec.encode(ledger)); }
        var loaded = ResourceEscrowSavedData.load(CheckedSavedDataFile.readPayload(ledgerFile, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow());
        writer = new EscrowLedgerWriter(ledgerFile, loaded.ledger().writerGeneration());
        writer.baseline(loaded.ledger());
        coordinator = new EscrowCoordinator(loaded, writer, propulsion);
        if (historical != null) {
            var root = new BayReceiptRoot(id(999), historical.stream().mapToLong(BayReceipt::seq).max().orElse(0), 1, historical);
            CheckedSavedDataFile.replace(bayFile, ManagedSavedDataType.BAY_RECEIPTS, () -> BayReceiptCodec.encode(root));
        }
        receipts = new BayReceipts(bay, (staged, target) -> committer.get().commit(staged, target), () -> io);
        var payload = CheckedSavedDataFile.readPayload(bayFile, ManagedSavedDataType.BAY_RECEIPTS);
        receipts.start(payload.map(BayReceiptSavedData::load).orElse(null), CheckedSavedDataFile.presence(bayFile), bayFile, 0);
        if (payload.isEmpty()) { receipts.createForAdmission(bayFile, 0); }
    }

    static UUID id(long n) { return new UUID(33303, n); }
    static String hash(long n) { return RocketNativeResources.hash("w333-admission-" + n); }
    static BayReceipt receipt(long n, BayReceipt.Phase phase) { return receipt(n, id(9000 + (n - 1) / 16), id(500 + n / 8), phase); }
    static BayReceipt receipt(long n, UUID owner, UUID rocket, BayReceipt.Phase phase) {
        return new BayReceipt(id(n), n, rocket, new RocketPosition((int) n, 1, 0), id(200000 + n), owner,
                "advancedrocketrycommunity:row_data", SatelliteKind.DATA, "advancedrocketrycommunity:earth", hash(n), id(400000 + n),
                hash(1), null, hash(3), null, phase, phase == BayReceipt.Phase.REJECTED ? "INTERRUPTED" : "NONE",
                false, false, 0, 1, 1, 0);
    }

    static EscrowOperation operation(long n, long rocket, long epoch) {
        CompoundTag payload = new CompoundTag(); payload.putString("FluidName", "minecraft:water"); payload.putInt("Amount", 1000);
        return new EscrowOperation(new UUID(33304, n), n, id(8000 + (n - 1) / 16), 1, EscrowOperation.Phase.PREPARED,
                EscrowReason.NONE, payload, RocketNativeResources.signature(payload),
                new EscrowEndpoint(EscrowEndpoint.Kind.BLOCK_ENTITY, "minecraft:overworld", id(1000 + n), null, "minecraft:chest",
                        (int) n, 64, 0, EscrowEndpoint.Unit.TANK, 0, hash(n), hash(n + 100)),
                new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, "minecraft:overworld", id(2000 + rocket), id(rocket), "minecraft:armor_stand",
                        0, 0, 0, EscrowEndpoint.Unit.PRIMARY, epoch, hash(epoch), hash(epoch + 1)), new EscrowGuard(id(rocket), null, 1, hash(400)));
    }
    static EscrowLedger ledger(EscrowOperation... operations) {
        return new EscrowLedger(id(888), Arrays.stream(operations).mapToLong(EscrowOperation::sequence).max().orElse(0), 0,
                List.of(operations), List.of(), List.of());
    }
    static EscrowLedger ledger(int count) {
        return ledger(LongStream.rangeClosed(1, count).mapToObj(n -> operation(n, 1000 + n, 0)).toArray(EscrowOperation[]::new));
    }

    BayReceiptStore store() { return receipts.store(); }
    void put(BayReceipt next) { helper.assertTrue(store().put(next, outcome -> { }).queued(), "Receipt put refused: " + next.seq()); }
    EscrowAdmissionAuthority.Result fluid(EscrowOperation next) {
        return authority.checkReserve(next.id(), next.owner(), next.guard().rocket(), FLUID);
    }
    void settle() { until(() -> !store().holdsWork()); }
    void until(BooleanSupplier done) throws AssertionError {
        for (long end = tick + 2000; !done.getAsBoolean() && tick < end; tick++) { io.await(); store().tick(tick); }
        helper.assertTrue(done.getAsBoolean(), "Receipt fixture exceeded 2000 synthetic owner ticks");
    }
    @Override public void close() {
        committer.set(CheckedSavedDataFile::atomicMove);
        if (store() != null) { store().close(); }
        coordinator.close(); writer.close(); io.shutdown();
    }

    /** Existing IO injection interface, with bounded awaits before each synthetic owner tick. */
    private static final class Io extends AbstractExecutorService {
        final ExecutorService thread = Executors.newSingleThreadExecutor(r -> { var t = new Thread(r, "ARCE-w333-bay-io"); t.setDaemon(true); return t; });
        final Queue<Future<?>> work = new ConcurrentLinkedQueue<>();
        void await() {
            for (Future<?> task; (task = work.poll()) != null;) {
                try { task.get(10, TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
                catch (ExecutionException | TimeoutException failure) { throw new AssertionError(failure); }
            }
        }
        @Override public void execute(Runnable task) { work.add(thread.submit(task)); }
        @Override public void shutdown() { thread.shutdown(); }
        @Override public List<Runnable> shutdownNow() { return thread.shutdownNow(); }
        @Override public boolean isShutdown() { return thread.isShutdown(); }
        @Override public boolean isTerminated() { return thread.isTerminated(); }
        @Override public boolean awaitTermination(long n, TimeUnit unit) throws InterruptedException { return thread.awaitTermination(n, unit); }
    }
}
