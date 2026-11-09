package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteRowGameTestFiles.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveys;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** W333 RT. Clean observations, all four checked write entries and frozen-capture/readback boundaries. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SatelliteTransportRowGameTests {
    private SatelliteTransportRowGameTests() { }

    @GameTest(template = "empty", batch = "w333_registry_rows", timeoutTicks = 200)
    public static void cleanSchemaFourReloadIsAnObservation(GameTestHelper h) throws Exception {
        try (var f = new SatelliteRowGameTestFiles(h)) {
            f.seed();
            byte[] before = Files.readAllBytes(f.file);
            try (var session = new RegistryClose(f.reload())) {
                var data = session.data();
                long epoch = data.saveEpoch(), sequence = data.mutationSequence();
                var watch = data.watch(id(1));
                var unused = ATOMIC.withCommitter((a, b) -> { throw new AssertionError("Clean observation wrote the file"); });
                h.assertTrue(data.checkpoint(f.file, List.of(watch), unused, path -> {
                    throw new AssertionError("Clean observation forced the directory");
                }).proves(watch), "Clean decoded identity was not acknowledged");
                data.flush(f.file);
                h.assertTrue(data.saveEpoch() == epoch && data.mutationSequence() == sequence && !data.isDirty(),
                        "Clean observation advanced an epoch/sequence or became dirty");
                h.assertTrue(data.save(new CompoundTag()).getString("format_epoch").equals("v1.8.0-ground-surveys")
                        && data.save(new CompoundTag()).getInt("schema_version") == 4, "Literal schema epoch changed");
                h.assertTrue(Arrays.equals(before, Files.readAllBytes(f.file)), "Clean proof/flush changed literal file bytes");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "w333_registry_rows", timeoutTicks = 400)
    public static void allWriteEntriesPreserveTaggedAndBlockedRoots(GameTestHelper h) throws Exception {
        for (String entry : List.of("barrier", "coalesced", "autosave", "stop")) {
            for (String outcome : List.of("COMMITTED", "FAILED", "UNKNOWN")) {
                for (boolean blocked : List.of(false, true)) { transport(h, entry, outcome, blocked); }
            }
        }
        h.succeed();
    }

    private static void transport(GameTestHelper h, String entry, String outcome, boolean blocked) throws Exception {
        try (var f = new SatelliteRowGameTestFiles(h)) {
            f.seed();
            CompoundTag before = f.data.save(new CompoundTag());
            CompoundTag previousFile = before.copy();
            if (blocked) {
                before.putInt("schema_version", 999);
                before.putString("w333_opaque", "must survive every write entry");
                f.data.close();
                f.data = SatelliteMissionSavedData.load(before);
                h.assertTrue(!f.data.operational(), "Future-root fixture unexpectedly operational");
                previousFile = before.copy();
                previousFile.putString("w333_opaque", "previous blocked root");
                CompoundTag previous = previousFile;
                ATOMIC.replace(f.file, () -> previous);
                f.data.setDirty();
            } else {
                h.assertTrue(f.data.groundSurveys(g -> g.start(ground(1, 1001), 1001)).code() == GroundSurveys.Code.SUCCESS,
                        "Transport mutation was refused");
            }
            CompoundTag expected = f.data.save(new CompoundTag());
            var calls = new AtomicInteger();
            var cut = f.cut(outcome.equals("UNKNOWN"));
            var policy = outcome.equals("COMMITTED") ? ATOMIC : new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
                if (calls.getAndIncrement() == 0) { cut.committer().commit(staged, target); }
                else { CheckedSavedDataFile.atomicMove(staged, target); }
            }, cut.reader());
            f.data.writePolicy(policy);
            if (entry.equals("stop")) {
                f.data.latchShutdown();
                // A live GameTest server cannot set vanilla's stopped flag. Unload/close invokes the same bounded stop drain.
                f.data.close();
                h.assertTrue(f.data.drainReport().orElseThrow().success(), "Stop drain did not cover final RAM");
                h.assertTrue(outcome.equals("COMMITTED") || calls.get() > 0, "Stop drain did not reach its injected cut");
                CompoundTag disk = CheckedSavedDataFile.readPayload(f.file, TYPE).orElseThrow();
                assertSections(h, expected, disk, blocked, entry + "/" + outcome);
            } else {
                if (entry.equals("barrier")) {
                    var result = f.data.checkpoint(f.file, List.of(), policy, path -> { });
                    h.assertTrue(result.outcome().name().equals(outcome), "Barrier missed cut " + outcome);
                } else if (entry.equals("autosave")) {
                    f.data.save(f.file.toFile());
                    h.assertTrue(f.data.writerForTests().lastOutcome().orElseThrow().name().equals(outcome), "Autosave missed cut");
                } else {
                    boolean failed = false;
                    try { f.data.flush(f.file); } catch (IllegalStateException refusal) { failed = true; }
                    h.assertTrue(failed == !outcome.equals("COMMITTED"), "Coalesced flush returned the wrong outcome");
                }
                if (!outcome.equals("COMMITTED")) {
                    h.assertTrue(f.data.isDirty(), "Unacknowledged write cleared dirty state");
                    if (!blocked) { h.assertTrue(f.data.flushPending(), "Unacknowledged write lost flushPending"); }
                    f.data.writePolicy(ATOMIC);
                    f.data.flush(f.file);
                }
                assertSections(h, expected, CheckedSavedDataFile.readPayload(f.file, TYPE).orElseThrow(), blocked, entry + "/" + outcome);
                if (!blocked) { f.assertPayloadEqual(); }
            }
        }
    }

    private static void assertSections(GameTestHelper h, CompoundTag expected, CompoundTag disk, boolean blocked, String variant) {
        var compared = disk.copy();
        if (!blocked) { compared.putLong("save_epoch", expected.getLong("save_epoch")); }
        h.assertTrue(compared.equals(expected), "Transport lost tagged origins, surveys, resources, accounts or opaque root: " + variant);
    }

    @GameTest(template = "empty", batch = "w333_registry_rows", timeoutTicks = 200)
    public static void replaceThenThrowAdvancesOnceAndKeepsLaterPendingMutation(GameTestHelper h) throws Exception {
        try (var f = new SatelliteRowGameTestFiles(h)) {
            f.seed();
            var data = f.data;
            h.assertTrue(data.groundSurveys(g -> g.start(ground(1, 1001), 1001)).code() == GroundSurveys.Code.SUCCESS,
                    "Frozen-capture fixture mutation failed");
            long epoch = data.saveEpoch();
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var reads = new AtomicInteger();
            var policy = new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
                entered.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) { throw new AssertionError("W333 owner did not release IO"); }
                } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new java.io.IOException(interrupted); }
                CheckedSavedDataFile.atomicMove(staged, target);
                throw new SatelliteRegistryTestAwait.InjectedFailure();
            }, (path, type) -> { reads.incrementAndGet(); return CheckedSavedDataFile.readPayload(path, type); });
            try {
                h.assertTrue(data.requestWrite(f.file, policy), "Could not request paused write");
                // A detached writer still obeys cadence. Pump its owner poll instead of blocking the owner before it can start.
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (entered.getCount() != 0 && System.nanoTime() < deadline) { data.pollWriter(); entered.await(1, TimeUnit.MILLISECONDS); }
                h.assertTrue(entered.getCount() == 0, "Writer never reached committer");
                h.assertTrue(data.groundSurveys(g -> g.start(ground(2, 1002), 1002)).code() == GroundSurveys.Code.SUCCESS,
                        "Owner mutation during capture failed");
                var later = data.token();
                release.countDown();
                data.awaitForTests(h.getLevel().getServer());
                h.assertTrue(reads.get() == 1 && data.saveEpoch() == epoch + 1, "Readback did not commit exactly one epoch");
                h.assertTrue(data.isDirty() && data.flushPending() && !data.covers(later), "Frozen readback acknowledged a later mutation");
                h.assertTrue(CheckedSavedDataFile.readPayload(f.file, TYPE).orElseThrow().getList("ground_surveys", 10).size() == 2,
                        "First write was not the frozen capture");
                data.writePolicy(ATOMIC);
                data.flush(f.file);
                h.assertTrue(data.saveEpoch() == epoch + 2 && data.covers(later) && !data.flushPending(), "Successor lost pending mutation");
                f.assertPayloadEqual();
            } finally { release.countDown(); }
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "w333_registry_rows", timeoutTicks = 200)
    public static void obsoleteSessionProofCannotCoverCurrentWatch(GameTestHelper h) throws Exception {
        try (var f = new SatelliteRowGameTestFiles(h)) {
            f.seed();
            var oldWatch = f.data.watch(id(1));
            var oldToken = f.data.token();
            var oldAnswer = f.data.acknowledgement().orElseThrow();
            try (var session = new RegistryClose(f.reload())) {
                var fresh = session.data();
                h.assertTrue(!fresh.proves(oldWatch) && !fresh.covers(oldToken), "A new session accepted an obsolete callback/token");
                h.assertTrue(!oldAnswer.proves(fresh.watch(id(1))), "Obsolete completion proved a new session's watch");
                h.assertTrue(fresh.proves(fresh.watch(id(1))), "New disk observation did not prove its own watch");
            }
        }
        h.succeed();
    }
}
