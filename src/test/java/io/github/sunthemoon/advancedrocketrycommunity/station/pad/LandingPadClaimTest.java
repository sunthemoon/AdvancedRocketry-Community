package io.github.sunthemoon.advancedrocketrycommunity.station.pad;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.PadClaimJournalFixture;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationPosition;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LandingPadClaimTest {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_TRANSFERS;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @Test void movedButUnacknowledgedRemovalRetainsClaimUntilOneAcknowledgedRelease() throws Exception {
        var journal = new RocketTransferSavedData();
        var record = PadClaimJournalFixture.record();
        var index = new LandingPadIndex();
        var pad = index.register(UUID.randomUUID(), record.ownerId(), UUID.randomUUID(), "Claim boundary",
                new StationPosition(0, 100, 0));
        assertTrue(index.claim(pad.stationId(), pad.reference(), pad.physicalId(), record.transferId()));
        var claimed = index.find(pad.stationId(), pad.reference()).orElseThrow();
        var before = index.save(new CompoundTag());
        var moved = new CountDownLatch(1); var proceed = new CountDownLatch(1);
        Path file = directory.resolve(TYPE.fileName());
        try {
            journal.put(record);
            PadClaimJournalFixture.write(journal, file, CheckedSavedDataPolicy.atomic(TYPE));
            journal.remove(record.transferId());
            PadClaimJournalFixture.request(journal, file, new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
                CheckedSavedDataFile.atomicMove(staged, target); moved.countDown();
                try {
                    if (!proceed.await(10, TimeUnit.SECONDS)) { throw new java.io.IOException("Fixture wait expired"); }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt(); throw new java.io.IOException(interrupted);
                }
            }));
            assertTrue(moved.await(10, TimeUnit.SECONDS), "Writer did not reach atomic move");
            assertTrue(journal.find(record.transferId()).isEmpty());
            assertFalse(journal.acknowledgedCurrent());
            UUID contender = UUID.randomUUID();
            for (int retry = 0; retry < 2; retry++) {
                assertFalse(LandingPadService.claim(journal, index, claimed, contender), "Pending removal reused pad");
                assertEquals(before, index.save(new CompoundTag()));
            }
            proceed.countDown(); PadClaimJournalFixture.drain(journal);
            assertTrue(journal.acknowledgedCurrent());
            assertTrue(LandingPadService.claim(journal, index, claimed, contender));
            assertEquals(contender, index.find(pad.stationId(), pad.reference()).orElseThrow().claim());
            assertFalse(index.release(pad.stationId(), pad.reference(), record.transferId()), "Old claim released twice");
            assertTrue(LandingPadService.claim(journal, index, claimed, contender));
            assertFalse(LandingPadService.claim(journal, index, claimed, UUID.randomUUID()));
            assertEquals(contender, index.find(pad.stationId(), pad.reference()).orElseThrow().claim());
        } finally { proceed.countDown(); journal.stop(); }
    }
}
