package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalSchedulingGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationPosition;
import io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadIndex;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real two-slot fail-back admission with independent active and successor checked writes. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketFailBackHeldWriterGameTests {
    private RocketFailBackHeldWriterGameTests() { }

    @GameTest(template = "rocket_test", batch = "journal_fail_back_admission", timeoutTicks = 300)
    public static void oneCommittedDependencyAdmitsFailBackWhileTheSuccessorStaysHeld(GameTestHelper helper) {
        new Scenario(helper, false).start();
    }

    @GameTest(template = "rocket_test", batch = "journal_fail_back_admission", timeoutTicks = 300)
    public static void abortOfQueuedFailBackDiscardsOnceAndRetainsCheckedRestartAuthority(GameTestHelper helper) {
        new Scenario(helper, true).start();
    }

    @GameTest(template = "rocket_test", batch = "journal_fail_back_admission", timeoutTicks = 550)
    public static void unknownRemovalRetainsItsContinuationUntilRetryAndCheckedReload(GameTestHelper helper) {
        new Scenario(helper, false, true).start();
    }

    private static final class Scenario {
        final GameTestHelper helper;
        final boolean abort;
        final boolean unknown;
        final RocketTransferSavedData journal = new RocketTransferSavedData(2);
        final RocketTransferService service = new RocketTransferService();
        final GameTestTickPacer pacer;
        final UUID station = UUID.randomUUID(), owner = UUID.randomUUID();
        final AtomicInteger first = new AtomicInteger(), second = new AtomicInteger();
        final AtomicInteger accepted = new AtomicInteger(), discarded = new AtomicInteger();
        RocketJournalSchedulingGameTestSupport.HeldWrite active, successor;
        RocketJournalSchedulingGameTestSupport.HeldWrite third;
        RocketJournalSchedulingGameTestSupport.FailedWrite fault;
        io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadEntry pad;
        RocketEntity source;
        RocketTransferRecord record;
        UUID firstTransfer;
        Path directory, file;
        int step, waits;
        boolean closed;

        Scenario(GameTestHelper helper, boolean abort) {
            this(helper, abort, false);
        }
        Scenario(GameTestHelper helper, boolean abort, boolean unknown) {
            this.helper = helper; this.abort = abort; this.unknown = unknown;
            pacer = new GameTestTickPacer(helper.getLevel().getServer());
        }

        void start() { helper.onEachTick(this::tick); }

        void tick() {
            if (closed) { return; }
            try {
                helper.assertTrue(++waits < (unknown ? 500 : 250), "Held fail-back fixture exceeded its bounded wait");
                var server = helper.getLevel().getServer();
                switch (step) {
                    case 0 -> setup();
                    case 1 -> {
                        if (!active.entered()) { break; }
                        assertRefused(); active.close(); step = 2;
                    }
                    case 2 -> {
                        journal.pollWriter(server.getTickCount());
                        if (!successor.entered()) { break; }
                        helper.assertTrue(first.get() == 1 && second.get() == 0 && journal.outstandingEffectCount() == 1,
                                "Releasing one write did not free exactly one continuation slot");
                        helper.assertTrue(journal.actionable(record.logicalRocketId()) && !journal.clean(),
                                "The unaffected rocket did not retain per-record authority");
                        if (unknown) {
                            // Fill only the writer's spare slot. L's removal must coalesce rather than capture with the default policy.
                            journal.put(journal.find(firstTransfer).orElseThrow());
                            third = RocketJournalSchedulingGameTestSupport.hold(helper, journal, file);
                        }
                        helper.assertTrue(service.failBackToSource(server, journal, record, source,
                                RocketTransferReturnReason.COUNTDOWN_CANCELLED,
                                new RocketRequestCompletion(accepted::incrementAndGet, discarded::incrementAndGet)),
                                "Fail-back was refused after one slot became free");
                        helper.assertTrue(journal.find(record.transferId()).isEmpty()
                                && source.flightData().orElseThrow().state() == RocketFlightState.FUELED,
                                "Admitted fail-back did not restore the source and remove RAM authority");
                        assertRetained(); step = 3;
                    }
                    case 3 -> {
                        journal.pollWriter(server.getTickCount()); assertRetained(); successor.close();
                        if (abort) {
                            // Abort before the owner polls the held successor: the removal snapshot never starts.
                            journal.abort(); journal.abort();
                            helper.assertTrue(discarded.get() == 1 && accepted.get() == 0,
                                    "Queued fail-back was not discarded exactly once");
                            assertReload(true); finish();
                        } else { step = unknown ? 5 : 4; }
                    }
                    case 5 -> {
                        journal.pollWriter(server.getTickCount());
                        if (!third.entered()) { break; }
                        assertRetained();
                        fault = RocketJournalSchedulingGameTestSupport.fail(helper, journal, file, true, true);
                        third.close(); fault.releaseFailure(); step = 6;
                    }
                    case 6 -> {
                        journal.pollWriter(server.getTickCount()); assertRetained();
                        if (journal.checkedWriteResult().outcome() != RocketTransferSavedData.WriteOutcome.UNKNOWN) { break; }
                        helper.assertTrue(journal.outstandingEffectCount() == 1 && !journal.actionable(record.logicalRocketId()),
                                "UNKNOWN removal lost its continuation or granted absence authority");
                        // The file has moved but no continuation may use that fact before writer acknowledgement.
                        assertReload(false); fault.recover(); step = 4;
                    }
                    case 4 -> {
                        journal.pollWriter(server.getTickCount());
                        if (!journal.clean()) { break; }
                        helper.assertTrue(first.get() == 1 && second.get() == 1 && accepted.get() == 1 && discarded.get() == 0,
                                "Acknowledgement did not complete each continuation exactly once");
                        helper.assertTrue(!RocketFailBackAdmissionGameTests.live(service).contains(record.transferId())
                                && LandingPadIndex.get(server).find(station, pad.reference()).orElseThrow().claim() == null,
                                "Acknowledged fail-back retained its live ID or named-pad claim");
                        journal.pollWriter(server.getTickCount()); journal.pollWriter(server.getTickCount() + 1L);
                        helper.assertTrue(accepted.get() == 1, "Fail-back completion repeated");
                        assertReload(false); finish();
                    }
                    default -> throw new IllegalStateException("Unknown held fail-back fixture step");
                }
                if (!closed) { pacer.pace(); }
            } catch (IOException failure) { close(); throw new java.io.UncheckedIOException(failure); }
            catch (RuntimeException | Error failure) { close(); throw failure; }
        }

        void setup() throws IOException {
            var level = helper.getLevel(); var origin = helper.absolutePos(new BlockPos(3, 2, 3));
            if (!level.isPositionEntityTicking(origin)) { return; }
            directory = Files.createTempDirectory("arce-n2-held-");
            file = directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
            var index = LandingPadIndex.get(level.getServer());
            pad = index.register(station, owner, UUID.randomUUID(), "N2 held", new StationPosition(0, 80, 0));
            source = ModEntities.ROCKET.get().create(level); helper.assertTrue(source != null, "No source entity");
            record = RocketFailBackAdmissionGameTests.record(helper, source.getUUID(), owner, station, pad.reference(), origin, 0);
            source.initializeTransferred(record.sourceSnapshot(), record.logicalRocketId(), owner, record.sourceFlightData());
            RocketTransferEntities.positionAtAltitude(source, record.sourceSnapshot().sourceOrigin());
            helper.assertTrue(level.addFreshEntity(source), "Source was not added");
            var a = RocketFailBackAdmissionGameTests.record(helper, UUID.randomUUID(), owner, station, null, origin, 64);
            firstTransfer = a.transferId();
            var b = RocketFailBackAdmissionGameTests.record(helper, UUID.randomUUID(), owner, station, null, origin, 128);
            journal.put(record); journal.put(a); journal.put(b);
            RocketJournalGameTestSupport.confirmDetached(helper, journal, file);
            helper.assertTrue(index.claim(station, pad.reference(), pad.physicalId(), record.transferId()), "Pad claim failed");
            RocketFailBackAdmissionGameTests.live(service).add(record.transferId());
            journal.put(a.destinationSpawned(UUID.randomUUID()));
            helper.assertTrue(RocketJournalSchedulingGameTestSupport.queue(helper, journal, a.logicalRocketId(), first::incrementAndGet),
                    "First dependency was refused");
            active = RocketJournalSchedulingGameTestSupport.hold(helper, journal, file);
            journal.put(b.destinationSpawned(UUID.randomUUID()));
            helper.assertTrue(RocketJournalSchedulingGameTestSupport.queue(helper, journal, b.logicalRocketId(), second::incrementAndGet),
                    "Second dependency was refused");
            successor = RocketJournalSchedulingGameTestSupport.hold(helper, journal, file);
            step = 1;
        }

        void assertRefused() {
            var flight = source.flightData().orElseThrow(); var position = source.position();
            var payload = journal.save(new CompoundTag());
            helper.assertTrue(!service.failBackToSource(helper.getLevel().getServer(), journal, record, source,
                    RocketTransferReturnReason.COUNTDOWN_CANCELLED,
                    new RocketRequestCompletion(accepted::incrementAndGet, discarded::incrementAndGet)),
                    "Full held queue admitted fail-back");
            helper.assertTrue(source.flightData().orElseThrow() == flight && source.position().equals(position)
                    && journal.find(record.transferId()).orElseThrow() == record && payload.equals(journal.save(new CompoundTag())),
                    "Refused fail-back changed source or journal bytes");
            helper.assertTrue(first.get() == 0 && second.get() == 0, "Held write ran a continuation");
            assertRetained();
        }

        void assertRetained() {
            helper.assertTrue(accepted.get() == 0 && discarded.get() == 0,
                    "A dependent completion ran before the removal acknowledgement");
            helper.assertTrue(RocketFailBackAdmissionGameTests.live(service).contains(record.transferId())
                    && record.transferId().equals(LandingPadIndex.get(helper.getLevel().getServer())
                            .find(station, pad.reference()).orElseThrow().claim()),
                    "Unacknowledged removal released the live ID or named pad");
        }

        void assertReload(boolean retained) {
            var payload = CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.ROCKET_TRANSFERS).orElseThrow();
            var loaded = RocketTransferSavedData.load(payload);
            try {
                helper.assertTrue(loaded.operational() && loaded.find(record.transferId()).isPresent() == retained,
                        "Checked-file reload lost the expected removal boundary");
                if (retained) {
                    var retainedRecord = loaded.find(record.transferId()).orElseThrow();
                    helper.assertTrue(retainedRecord.checksum().equals(record.checksum())
                                    && retainedRecord.phase() == record.phase()
                                    && retainedRecord.destinationEntityId().equals(record.destinationEntityId()),
                            "Aborted removal changed durable source authority");
                }
                helper.assertTrue(source.isAlive() && source.flightData().orElseThrow().fuel().amount() == 1_000
                        && source.flightData().orElseThrow().state() == RocketFlightState.FUELED,
                        "Fail-back lost source resources");
            } finally { loaded.abort(); }
        }

        void finish() { close(); helper.succeed(); }
        void close() {
            if (closed) { return; } closed = true;
            if (active != null) { active.close(); } if (successor != null) { successor.close(); }
            if (third != null) { third.close(); } if (fault != null) { fault.close(); }
            journal.abort(); service.clear(); if (source != null) { source.discard(); }
            if (pad != null) { LandingPadIndex.get(helper.getLevel().getServer()).remove(station, pad.reference(), pad.physicalId()); }
            if (directory != null) {
                try (var paths = Files.walk(directory)) {
                    for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) { Files.delete(path); }
                } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }
        }
    }
}
