package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketOperationLedger;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Checked journal/callback restart coverage, not a native-server restart claim. */
class RocketMonitorAcceptanceTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @Test void revocationBeforeSubmissionRefusesWithoutPreparedOrDebit() throws Exception {
        var fixture = GovernedTransferFixture.create();
        var journal = new RocketTransferSavedData();
        try {
            acknowledge(journal);
            var before = fixture.source();
            assertFalse(RocketTransferService.governedAdmission(journal, fixture.record(), () -> false));
            assertTrue(journal.entries().isEmpty());
            assertFalse(MonitorFlightTestSupport.authorized(fixture, journal));
            assertEquals(before, fixture.source());
        } finally { journal.stop(); }
    }

    @Test void revocationAfterSubmissionBeforeAcknowledgementReportsLaunched() throws Exception {
        var fixture = GovernedTransferFixture.create(); var record = fixture.record();
        var access = new AtomicBoolean(true); var checks = new AtomicInteger();
        var journal = new RocketTransferSavedData();
        var requests = new RocketOperationLedger(); requests.begin(record.transferId());
        var monitor = new RocketMonitorFlights(null, requests, null);
        try {
            acknowledge(journal);
            assertTrue(RocketTransferService.governedAdmission(journal, record, access::get));
            journal.put(record);
            assertFalse(journal.logicalAcknowledged(record.logicalRocketId()));
            assertTrue(journal.afterAcknowledged(UnstartedServer.owned(), record.logicalRocketId(), () -> {
                if (!RocketTransferService.governedAdmission(journal, record, () -> { checks.incrementAndGet(); return access.get(); })) {
                    return false;
                }
                countdown(fixture, journal);
                requests.finish(record.transferId(), true); return true;
            }, () -> requests.finish(record.transferId(), false)));
            access.set(false);
            acknowledge(journal);
            assertEquals(io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode.SUCCESS,
                    monitor.completion(record.transferId()));
            assertEquals(0, checks.get(), "Accepted launch re-ran the monitor predicate");
        } finally { journal.stop(); }
    }

    @Test void acceptedRecordAfterRestartDoesNotNeedMonitorAuthority() throws Exception {
        var fixture = GovernedTransferFixture.create(); var journal = new RocketTransferSavedData();
        try { journal.put(fixture.record()); acknowledge(journal); } finally { journal.stop(); }
        var loaded = RocketTransferSavedData.load(CheckedSavedDataFile.readPayload(file(), ManagedSavedDataType.ROCKET_TRANSFERS).orElseThrow());
        try {
            acknowledge(loaded);
            var record = loaded.find(fixture.record().transferId()).orElseThrow();
            assertTrue(RocketTransferService.governedAdmission(loaded, record, () -> false));
            countdown(fixture, loaded);
        } finally { loaded.stop(); }
    }

    private static void countdown(GovernedTransferFixture.Fixture fixture, RocketTransferSavedData journal) {
        var after = MonitorFlightTestSupport.countdown(fixture, journal);
        assertEquals(RocketFlightState.COUNTDOWN, after.state());
        assertEquals(fixture.record().sourceFlightData().fuel(), after.fuel());
    }
    private Path file() { return directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName()); }
    private void acknowledge(RocketTransferSavedData journal) throws Exception {
        journal.setDirty(); journal.save(file().toFile());
        for (int tick = 0; tick < 1_000 && !journal.acknowledgedCurrent(); tick++) { journal.pollWriter(tick); Thread.sleep(2); }
        assertTrue(journal.acknowledgedCurrent());
    }
}
