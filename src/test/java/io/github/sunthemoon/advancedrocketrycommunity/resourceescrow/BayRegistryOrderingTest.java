package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteManager;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Driver S2 ordering cuts only. These detached registry fixtures confer no rocket/source/coordinator authority. */
final class BayRegistryOrderingTest {
    @TempDir Path temporary;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void b1ReceiptBeforeAndAfterReplaceWithNoOwnerCallbackSurvivesTwoReloads() throws Exception {
        for (boolean replace : List.of(false, true)) {
            Path dir = temporary.resolve("B1-" + replace);
            var receipt = receipt(1, id(100), id(200), BayReceipt.Phase.INTENT);
            try (var h = BayReceiptHarness.empty(dir)) {
                if (!replace) { h.committer.set(BayReceiptHarness.AMBIGUOUS); }
                assertTrue(h.put(receipt).queued()); h.io.awaitWrites();
                assertTrue(h.outcomes.isEmpty(), "Cut must precede the owner-thread callback");
                h.store().abort();
            }
            for (int restart = 0; restart < 2; restart++) {
                try (var h = loaded(dir)) {
                    assertEquals(replace, h.receipts.blocksDecommission(receipt.satellite()));
                    assertEquals(replace, h.receipts.blocksInsertion(receipt.satellite()));
                    assertEquals(replace, h.store() != null && h.store().find(receipt.satellite()).isPresent());
                }
            }
        }
    }

    @Test void b2b3AndB13OnlyARegistryFileProvesRegistrationAndOnlyDurableRegisteredReleasesRemoval() throws Exception {
        for (boolean registryReplaced : List.of(false, true)) {
            for (boolean registeredReplaced : List.of(false, true)) {
                if (!registryReplaced && registeredReplaced) { continue; } // The driver cannot promote an unproved registration.
                Path dir = temporary.resolve("B2-B3-" + registryReplaced + "-" + registeredReplaced);
                Files.createDirectories(dir);
                Path registryFile = dir.resolve(ManagedSavedDataType.SATELLITE_MISSIONS.fileName());
                var data = SatelliteMissionSavedData.create(0); data.flush(registryFile);
                var intent = receipt(1, id(100), id(200), BayReceipt.Phase.INTENT);
                try (var h = BayReceiptHarness.empty(dir)) {
                    assertTrue(h.put(intent).queued()); h.settle();
                    assertTrue(data.launch(intent.satellite(), SatelliteManager.initialMissionId(intent.satellite()), intent.owner(),
                            definition(), ModIdentity.id("earth"), 0, false).success());
                    var watch = data.watch(intent.satellite());
                    assertFalse(data.acknowledgement().orElseThrow().proves(watch));
                    assertTrue(h.receipts.blocksDecommission(intent.satellite()));
                    if (registryReplaced) {
                        data.flush(registryFile);
                        assertTrue(data.acknowledgement().orElseThrow().proves(watch));
                        if (registeredReplaced) {
                            var registered = receipt(1, id(100), id(200), BayReceipt.Phase.REGISTERED);
                            assertTrue(h.put(registered).queued());
                            assertTrue(h.receipts.blocksDecommission(intent.satellite()));
                            h.store().tick(h.tick + 10); h.io.awaitWrites();
                        }
                    }
                    h.store().abort();
                }
                for (int restart = 0; restart < 2; restart++) {
                    var registry = SatelliteMissionSavedData.load(CheckedSavedDataFile.readPayload(registryFile,
                            ManagedSavedDataType.SATELLITE_MISSIONS).orElseThrow());
                    assertEquals(registryReplaced, registry.satellite(intent.satellite()).isPresent());
                    if (registryReplaced) {
                        assertTrue(registry.acknowledgement().orElseThrow().proves(registry.watch(intent.satellite())));
                        assertEquals(1, registry.satellites().size()); assertEquals(1, registry.missions().size());
                    }
                    try (var h = loaded(dir)) {
                        assertTrue(h.receipts.blocksInsertion(intent.satellite()));
                        assertEquals(!registeredReplaced, h.receipts.blocksDecommission(intent.satellite()));
                        assertEquals(registeredReplaced ? BayReceipt.Phase.REGISTERED : BayReceipt.Phase.INTENT,
                                h.store().find(intent.satellite()).orElseThrow().phase());
                    }
                }
            }
        }
    }

    @Test void b9RejectionAndB12UnknownKeepTheReceiptIdentityAndNoRegistryInsertion() throws Exception {
        Path registryFile = temporary.resolve(ManagedSavedDataType.SATELLITE_MISSIONS.fileName());
        var registry = SatelliteMissionSavedData.create(0); registry.flush(registryFile);
        byte[] before = Files.readAllBytes(registryFile);
        try (var h = BayReceiptHarness.empty(temporary)) {
            var intent = receipt(1, id(100), id(200), BayReceipt.Phase.INTENT);
            assertTrue(h.put(intent).queued()); h.settle();
            var rejected = intent.withPhase(BayReceipt.Phase.REJECTED, "TARGET_NOT_ALLOWED", 20);
            h.committer.set(BayReceiptHarness.AMBIGUOUS);
            assertTrue(h.put(rejected).queued()); h.until(h.store().writer()::unknown);
            assertTrue(h.receipts.blocksInsertion(intent.satellite()));
            assertTrue(h.receipts.blocksDecommission(intent.satellite()));
            assertEquals(1, h.authority.pendingCount());
            h.committer.set(CheckedSavedDataFile::atomicMove); h.settle();
            assertFalse(h.receipts.blocksInsertion(intent.satellite()));
            assertFalse(h.receipts.blocksDecommission(intent.satellite()));
            assertEquals(0, h.authority.pendingCount()); assertEquals(1, h.authority.retainedCount());
            assertEquals(intent.op(), h.store().find(intent.satellite()).orElseThrow().op());
            assertTrue(h.put(intent).queued()); h.settle();
            assertEquals(intent.op(), h.store().find(intent.satellite()).orElseThrow().op());
            assertEquals(1, h.authority.pendingCount());
        }
        assertArrayEquals(before, Files.readAllBytes(registryFile));
    }

    private BayReceiptHarness loaded(Path directory) throws Exception {
        var h = new BayReceiptHarness(new EscrowAdmissionAuthority(), directory);
        var payload = CheckedSavedDataFile.readPayload(h.file, ManagedSavedDataType.BAY_RECEIPTS);
        h.receipts.start(payload.map(BayReceiptSavedData::load).orElse(null),
                payload.isPresent() ? CheckedSavedDataFile.Presence.PRESENT : CheckedSavedDataFile.Presence.ABSENT, h.file, 0);
        return h;
    }
    private static SatelliteDefinition definition() {
        return new SatelliteDefinition(1, ModIdentity.id("w27c1_data"), 200, 120, 100, List.of(ModIdentity.id("earth")));
    }
}
