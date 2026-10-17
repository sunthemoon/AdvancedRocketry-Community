package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalScheduleFixtures;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalTestAccess;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationPosition;
import io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadIndex;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** W86 A0: execute the same discard callback that prepareLaunch passes to governed preparation. */
class RocketGovernedLaunchDiscardTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @Test void unrelatedDirtyRecordDoesNotRetainTheUnarmedPad() {
        check(false, true, false);
    }

    @Test void acknowledgedTransferRetainsThePadEvenWhenResourcesAllowRelease() {
        check(true, true, false);
    }

    @Test void resourceRefusalRetainsThePadDespiteAcknowledgedAbsence() {
        check(false, false, false);
    }

    @Test void outstandingReservationRetainsThePadDespiteAcknowledgedAbsence() {
        check(false, true, true);
    }

    private void check(boolean transferExists, boolean resourcesAllowed, boolean reserve) {
        var journal = new RocketTransferSavedData();
        var record = RocketJournalScheduleFixtures.record(1);
        var other = RocketJournalScheduleFixtures.record(2);
        var pads = new LandingPadIndex();
        var station = new UUID(913, 1);
        var pad = pads.register(station, record.ownerId(), new UUID(914, 1), "Launch",
                new StationPosition(0, 80, 0));
        assertTrue(pads.claim(station, pad.reference(), pad.physicalId(), record.transferId()));
        var releaseCalls = new AtomicInteger();
        var resourceChecks = new AtomicInteger();
        var discards = new AtomicInteger();
        Runnable callback = RocketTransferService.governedLaunchDiscard(journal, record.logicalRocketId(),
                record.transferId(), () -> { resourceChecks.incrementAndGet(); return resourcesAllowed; },
                () -> {
                    releaseCalls.incrementAndGet();
                    assertTrue(pads.release(station, pad.reference(), record.transferId()));
                }, discards::incrementAndGet);
        try {
            if (transferExists) { journal.put(record); }
            journal.setDirty(); // A fresh detached empty root otherwise has no save request to acknowledge.
            journal.save(directory.resolve(RocketTransferSavedData.DATA_NAME + ".dat").toFile());
            RocketJournalTestAccess.awaitWriter(journal);
            assertTrue(journal.acknowledgedCurrent());
            assertTrue(journal.actionable(record.logicalRocketId()));
            try (var reservation = reserve ? journal.reserve(record.logicalRocketId()).orElseThrow() : null) {
                journal.put(other);
                assertFalse(journal.acknowledgedCurrent());
                assertFalse(journal.clean());
                assertFalse(journal.actionable(other.logicalRocketId()));
                assertEquals(!reserve, journal.actionable(record.logicalRocketId()));
                long sequence = journal.mutationSequence();

                callback.run();

                boolean released = !transferExists && resourcesAllowed && !reserve;
                assertEquals(released ? 1 : 0, releaseCalls.get());
                assertEquals(released ? null : record.transferId(),
                        pads.find(station, pad.reference()).orElseThrow().claim());
                assertEquals(!transferExists && !reserve ? 1 : 0, resourceChecks.get());
                assertEquals(1, discards.get(), "The request completion must be discarded even when the pad stays held");
                assertEquals(sequence, journal.mutationSequence());
                assertEquals(transferExists, journal.find(record.transferId()).isPresent());
                assertSame(other, journal.find(other.transferId()).orElseThrow());
            }
        } finally { journal.abort(); }
    }
}
