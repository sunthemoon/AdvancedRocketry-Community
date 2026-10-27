package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionStatus;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ADR-074 item 6 and ADR-038: a celestial discovery is never written before a published commit covers its paid
 * receipt. Until then the replay answers PENDING_DISCOVERY and only requests a registry write.
 */
class DiscoveryReceiptOrderTest {
    @TempDir Path root;
    private final UUID owner = UUID.randomUUID(), mission = UUID.randomUUID(), satellite = UUID.randomUUID();
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void theCelestialWriteWaitsForTheReceiptsCommitAndTheFinishIsRequested() {
        var data = SatelliteMissionSavedData.create(0);
        data.launch(satellite, mission, owner, PlanetaryContent.surveySatellite(), PlanetaryContent.MARS, 0, true);
        data.completeDue(200);
        data.flush(missions());
        var progress = CelestialSavedData.create();
        assertEquals(SatelliteOperationCode.PENDING_DISCOVERY, data.claim(mission, owner, 201).code());
        var receipt = data.token();
        var requests = new AtomicInteger(); var celestialWrites = new AtomicInteger();
        for (int poll = 0; poll < 3; poll++) {
            var waiting = DiscoveryClaimRecovery.recoverAcknowledged(data, progress, mission, receipt, 202, id -> true,
                    requests::incrementAndGet, value -> { celestialWrites.incrementAndGet(); value.flush(celestial()); });
            assertEquals(SatelliteOperationCode.PENDING_DISCOVERY, waiting.code());
        }
        assertEquals(3, requests.get(), "Each retry requests the receipt's write");
        assertEquals(0, celestialWrites.get(), "The discovery ran ahead of its paid receipt");
        assertTrue(progress.get(PlanetaryContent.MARS).isEmpty());
        assertEquals(MissionStatus.CLAIM_PENDING_DISCOVERY, data.mission(mission).orElseThrow().status());

        data.claim(UUID.randomUUID(), owner, 203); // a later unrelated poll does not move the receipt's token
        data.flush(missions());
        assertTrue(data.covers(receipt));
        var finished = DiscoveryClaimRecovery.recoverAcknowledged(data, progress, mission, receipt, 204, id -> true,
                requests::incrementAndGet, value -> { celestialWrites.incrementAndGet(); value.flush(celestial()); });
        assertEquals(SatelliteOperationCode.SUCCESS, finished.code());
        assertEquals(1, celestialWrites.get());
        assertTrue(progress.get(PlanetaryContent.MARS).isPresent());
        assertEquals(MissionStatus.CLAIMED, data.mission(mission).orElseThrow().status());
        assertTrue(requests.get() >= 4, "The finished claim is requested");
        var reloaded = SatelliteMissionSavedData.load(io.github.sunthemoon.advancedrocketrycommunity.persistence.migration
                .CheckedSavedDataFile.readPayload(missions(), ManagedSavedDataType.SATELLITE_MISSIONS).orElseThrow());
        assertTrue(DiscoveryClaimRecovery.paidReceipt(reloaded.mission(mission).orElseThrow()),
                "The paid receipt was in the file before the discovery");
    }

    /** A replayed receipt whose mission this registry lacks (an installed fixture registry) asks for no write. */
    @Test void aMissingMissionIsAnsweredWithoutARequest() {
        var data = SatelliteMissionSavedData.create(0);
        var stranger = SatelliteMissionSavedData.create(0);
        stranger.launch(satellite, mission, owner, PlanetaryContent.surveySatellite(), PlanetaryContent.MARS, 0, true);
        var receipt = stranger.token();
        var requests = new AtomicInteger();
        var missing = DiscoveryClaimRecovery.recoverAcknowledged(data, CelestialSavedData.create(), mission, receipt, 1,
                id -> true, requests::incrementAndGet, value -> fail("A missing mission wrote a discovery"));
        assertEquals(SatelliteOperationCode.MISSION_NOT_FOUND, missing.code());
        assertEquals(0, requests.get(), "A missing mission requested a registry write");
        assertFalse(data.isDirty());
    }

    private Path missions() { return root.resolve(ManagedSavedDataType.SATELLITE_MISSIONS.fileName()); }
    private Path celestial() { return root.resolve(ManagedSavedDataType.CELESTIAL.fileName()); }
}
