package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayAdmissionRowGameTestFiles.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.LongStream;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** W333 F3: composed admission rows through both real store delegates on the same authority. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BayAdmissionRowGameTests {
    private BayAdmissionRowGameTests() { }
    @FunctionalInterface private interface Case { void run(Path directory) throws Exception; }
    private static void isolated(GameTestHelper h, Case test) throws Exception {
        Path directory = Files.createTempDirectory("arce-w333-admission-");
        try { test.run(directory); }
        finally {
            try (var files = Files.walk(directory)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) { Files.deleteIfExists(path); }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "w333_admission_rows", timeoutTicks = 200)
    public static void mixedConsumersShareGlobalAndRocketLimits(GameTestHelper h) throws Exception {
        isolated(h, directory -> {
            try (var f = new BayAdmissionRowGameTestFiles(h, directory.resolve("fluid-major"), ledger(63), null)) {
                f.put(receipt(1, BayReceipt.Phase.INTENT));
                full(h, f);
                f.settle(); full(h, f);
            }
            try (var f = new BayAdmissionRowGameTestFiles(h, directory.resolve("bay-major"), ledger(0), null)) {
                for (int n = 1; n <= 63; n++) { f.put(receipt(n, BayReceipt.Phase.INTENT)); }
                f.coordinator.prepareTest(operation(1, 3000, 0), success -> { });
                h.assertTrue(f.authority.pendingCount() == 64, "Reverse consumers did not share 64 slots");
                h.assertTrue(f.store().put(receipt(64, BayReceipt.Phase.INTENT), outcome -> { }).reservation()
                        == EscrowAdmissionAuthority.Result.SERVER_FULL, "Reverse bay admission bypassed full pool");
                h.assertTrue(f.fluid(operation(2, 3001, 0)) == EscrowAdmissionAuthority.Result.SERVER_FULL, "Reverse fluid bypassed full pool");
            }
            var seven = LongStream.rangeClosed(1, 7).mapToObj(n -> operation(n, 3000, n - 1)).toArray(EscrowOperation[]::new);
            try (var f = new BayAdmissionRowGameTestFiles(h, directory.resolve("rocket"), ledger(seven), null)) {
                f.put(receipt(1, id(9000), id(3000), BayReceipt.Phase.INTENT));
                h.assertTrue(f.authority.pendingFor(id(3000)) == 8, "7 escrow + 1 bay did not share rocket count");
                h.assertTrue(f.store().put(receipt(2, id(9000), id(3000), BayReceipt.Phase.INTENT), outcome -> { }).reservation()
                        == EscrowAdmissionAuthority.Result.ROCKET_FULL, "Ninth bay was admitted");
                h.assertTrue(f.fluid(operation(8, 3000, 7)) == EscrowAdmissionAuthority.Result.ROCKET_FULL, "Ninth fluid was admitted");
            }
        });
    }

    private static void full(GameTestHelper h, BayAdmissionRowGameTestFiles f) {
        h.assertTrue(f.authority.pendingCount() == 64, "Shared union did not stay at 64");
        h.assertTrue(f.store().put(receipt(100, BayReceipt.Phase.INTENT), outcome -> { }).reservation()
                == EscrowAdmissionAuthority.Result.SERVER_FULL, "Another bay bypassed full pool");
        h.assertTrue(f.fluid(operation(100, 9000, 0)) == EscrowAdmissionAuthority.Result.SERVER_FULL, "A loader bypassed full pool");
    }

    @GameTest(template = "empty", batch = "w333_admission_rows", timeoutTicks = 200)
    public static void writerCopiesChargeOnePendingSlot(GameTestHelper h) throws Exception {
        isolated(h, directory -> {
            try (var f = new BayAdmissionRowGameTestFiles(h, directory, ledger(63), null)) {
                var x = receipt(1, BayReceipt.Phase.INTENT); f.put(x); f.settle();
                f.committer.set((a, b) -> { throw new java.io.IOException("W333 ambiguous replacement"); });
                f.put(x.withPhase(BayReceipt.Phase.REPAIR, "BAY_CONFLICT", 3));
                f.until(() -> f.store().writer().unknown());
                f.put(receipt(2, BayReceipt.Phase.REJECTED));
                h.assertTrue(f.store().writer().hasSuccessor(), "Fixture did not queue a successor");
                h.assertTrue(f.authority.checkReserve(x.op(), x.owner(), x.rocket(), BAY) == EscrowAdmissionAuthority.Result.ALREADY_HELD,
                        "Durable/captured/UNKNOWN duplicate was counted twice");
                full(h, f);
                f.committer.set(CheckedSavedDataFile::atomicMove); f.settle();
                f.put(x.withPhase(BayReceipt.Phase.REPAIR, "BAY_CONFLICT", 4).withReleased()); f.settle();
                full(h, f);
            }
        });
    }

    @GameTest(template = "empty", batch = "w333_admission_rows", timeoutTicks = 200)
    public static void repairAndReleasedReceiptsStayFullAfterReload(GameTestHelper h) throws Exception {
        isolated(h, directory -> {
            var repair = operation(1, 7, 0).withReason(EscrowReason.OTHER_STATE);
            var rows = new ArrayList<BayReceipt>();
            for (int n = 1; n <= 63; n++) {
                var row = receipt(n, BayReceipt.Phase.REPAIR);
                rows.add(n % 2 == 0 ? row.withReleased() : row);
            }
            try (var f = new BayAdmissionRowGameTestFiles(h, directory, ledger(repair), rows)) { full(h, f); }
            try (var f = new BayAdmissionRowGameTestFiles(h, directory, ledger(0), null)) {
                h.assertTrue(f.bay.state() == EscrowAdmissionAuthority.Delegate.State.LOADED
                        && f.propulsion.state() == EscrowAdmissionAuthority.Delegate.State.LOADED, "Startup did not index both files");
                h.assertTrue(f.store().durable().receipts().equals(rows), "Reload lost phase or released flags");
                full(h, f);
            }
        });
    }

    @GameTest(template = "empty", batch = "w333_admission_rows", timeoutTicks = 200)
    public static void rejectedRearmWaitsForExactCapacityRelease(GameTestHelper h) throws Exception {
        isolated(h, directory -> {
            try (var f = new BayAdmissionRowGameTestFiles(h, directory, ledger(63), null)) {
                var x = receipt(1, BayReceipt.Phase.INTENT); f.put(x); f.settle();
                var rejected = x.withPhase(BayReceipt.Phase.REJECTED, "LAUNCH_REFUSED", 3);
                f.put(rejected); h.assertTrue(f.authority.pendingCount() == 64, "Queued rejection released capacity early");
                f.settle(); h.assertTrue(f.authority.pendingCount() == 63, "Acknowledged rejection kept its pending slot");
                var y = receipt(2, BayReceipt.Phase.INTENT); f.put(y); f.settle();
                var rearm = rejected.withKey(id(7000), new RocketPosition(0, 2, 0)).withPhase(BayReceipt.Phase.INTENT, "NONE", 4);
                var before = f.store().receipts();
                h.assertTrue(f.store().put(rearm, outcome -> { }).reservation() == EscrowAdmissionAuthority.Result.SERVER_FULL,
                        "Full pool accepted REJECTED rearm");
                h.assertTrue(before.equals(f.store().receipts()) && f.store().current(x.op()).orElseThrow().equals(rejected),
                        "Refused rearm changed op, key, satellite or package signature");
                f.put(y.withPhase(BayReceipt.Phase.REJECTED, "INTERRUPTED", 5));
                h.assertTrue(f.authority.checkReserve(rearm.op(), rearm.owner(), rearm.rocket(), BAY) == EscrowAdmissionAuthority.Result.SERVER_FULL,
                        "Rearm used an unacknowledged release");
                f.settle(); f.put(rearm); f.settle();
                h.assertTrue(f.store().current(x.op()).orElseThrow().equals(rearm) && rearm.packageSig().equals(x.packageSig())
                        && f.authority.pendingCount() == 64, "Same-op rearm did not preserve the package/capacity");
            }
        });
    }

    @GameTest(template = "empty", batch = "w333_admission_rows", timeoutTicks = 200)
    public static void retainedLimitsAreIndependentAndStartupIndexesBothStores(GameTestHelper h) throws Exception {
        isolated(h, directory -> {
            for (int count : List.of(128, 4096)) {
                var rows = new ArrayList<BayReceipt>();
                for (int n = 1; n <= count; n++) {
                    rows.add(receipt(n, count == 128 ? id(9000) : id(9000 + (n - 1) / 128), id(500 + n), BayReceipt.Phase.REJECTED));
                }
                try (var f = new BayAdmissionRowGameTestFiles(h, directory.resolve("retained-" + count), ledger(0), rows)) {
                    h.assertTrue(f.authority.pendingCount() == 0 && f.authority.retainedCount() == count, "Retention was confused with pending");
                    var refused = f.store().put(receipt(5000, id(9000), id(8000), BayReceipt.Phase.INTENT), outcome -> { });
                    h.assertTrue(refused.retention() == (count == 128 ? EscrowAdmissionAuthority.Retention.OWNER_FULL
                            : EscrowAdmissionAuthority.Retention.SERVER_FULL), "Wrong independent retained limit: " + refused);
                    h.assertTrue(f.store().receipts().equals(rows) && f.authority.pendingCount() == 0, "Retention refusal changed state");
                    if (count == 128) { f.put(receipt(5001, id(9999), id(8001), BayReceipt.Phase.INTENT)); }
                }
            }
            var mixed = new ArrayList<BayReceipt>();
            for (int n = 1; n <= 8; n++) { mixed.add(receipt(n, n <= 5 ? BayReceipt.Phase.INTENT : BayReceipt.Phase.REJECTED)); }
            try (var f = new BayAdmissionRowGameTestFiles(h, directory.resolve("mixed"), ledger(10), mixed)) {
                h.assertTrue(f.authority.pendingCount() == 15 && f.authority.retainedCount() == 8
                        && f.receipts.blocksInsertion(mixed.get(0).satellite()) && !f.receipts.blocksInsertion(mixed.get(7).satellite()),
                        "Mixed-store startup was not indexed before any owner tick");
            }
        });
    }

    @GameTest(template = "empty", batch = "w333_admission_rows", timeoutTicks = 200)
    public static void failedAndUnknownRetirementKeepReservations(GameTestHelper h) throws Exception {
        isolated(h, directory -> {
            for (boolean unknown : List.of(false, true)) {
                try (var f = new BayAdmissionRowGameTestFiles(h, directory.resolve("cut-" + unknown), ledger(63), null)) {
                    var x = receipt(1, BayReceipt.Phase.INTENT); f.put(x); f.settle();
                    Path obstacle = f.bayFile.resolveSibling(f.bayFile.getFileName() + ".arce-pending");
                    try {
                    var outcomes = new ArrayList<BayReceiptWriter.Outcome>();
                    if (unknown) { f.committer.set((a, b) -> { throw new java.io.IOException("W333 retirement UNKNOWN"); }); }
                    else { Files.createDirectory(obstacle); }
                    h.assertTrue(f.store().removeGroup(List.of(x.op()), () -> true, outcomes::add).queued(), "Retirement not queued");
                    if (unknown) { f.until(() -> f.store().writer().unknown()); }
                    else { f.until(() -> !outcomes.isEmpty()); }
                    h.assertTrue(unknown ? f.store().writer().unknown() : outcomes.get(0) == BayReceiptWriter.Outcome.FAILED_BEFORE_REPLACE,
                            "Retirement missed required failure cut");
                    full(h, f);
                    if (!unknown) { Files.delete(obstacle); }
                    f.committer.set(CheckedSavedDataFile::atomicMove); f.settle();
                    if (f.store().current(x.op()).isPresent()) {
                        h.assertTrue(f.store().removeGroup(List.of(x.op()), () -> true, outcome -> { }).queued(), "Retirement retry refused");
                        f.settle();
                    }
                    h.assertTrue(f.authority.pendingCount() == 63 && f.authority.checkReserve(id(100), id(9000), id(9000), BAY)
                            == EscrowAdmissionAuthority.Result.RESERVED && f.fluid(operation(100, 9000, 0)) == EscrowAdmissionAuthority.Result.RESERVED,
                            "Exact retirement did not release capacity for both consumers");
                    } finally {
                        f.committer.set(CheckedSavedDataFile::atomicMove);
                        Files.deleteIfExists(obstacle);
                    }
                }
            }
        });
    }
}
