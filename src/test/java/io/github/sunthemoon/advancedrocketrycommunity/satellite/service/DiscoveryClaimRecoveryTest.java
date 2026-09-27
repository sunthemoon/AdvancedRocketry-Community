package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionStatus;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationResult;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DiscoveryClaimRecoveryTest {
    @TempDir Path root;
    private final UUID owner = UUID.randomUUID(), mission = UUID.randomUUID(), satellite = UUID.randomUUID();
    private static final ResourceLocation TARGET = PlanetaryContent.MARS;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @ParameterizedTest @ValueSource(ints = {-1, 1, -2, 2, -3, 3})
    void diskCutsBeforeAndAfterEachBarrierRecoverWithoutLostDiscoveryOrRepeatedPayment(int cut) throws Exception {
        var data = ready(true); var progress = CelestialSavedData.create();
        data.flush(missionsFile()); progress.flush(celestialFile());
        data.claim(mission, owner, 201);
        var count = new AtomicInteger();
        Consumer<io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.AtomicSavedData> write = store -> {
            int barrier = count.incrementAndGet();
            if (cut == -barrier) { throw new Cut(); }
            store.flush(store instanceof SatelliteMissionSavedData ? missionsFile() : celestialFile());
            if (cut == barrier) { throw new Cut(); }
        };
        assertThrows(Cut.class, () -> recover(data, progress, true, write::accept, write::accept));
        var restored = SatelliteMissionSavedData.load(payload(missionsFile()));
        var restoredProgress = CelestialSavedData.load(payload(celestialFile()));
        var saved = restored.mission(mission).orElseThrow();
        if (restoredProgress.get(TARGET).isPresent()) { assertPaidOnce(restored); }
        if (saved.status() == MissionStatus.CLAIMED) { assertTrue(restoredProgress.get(TARGET).isPresent()); }
        restored.claim(mission, owner, 202);
        recover(restored, restoredProgress, true);
        assertComplete(restored, restoredProgress);
        recover(restored, restoredProgress, true);
        assertComplete(SatelliteMissionSavedData.load(payload(missionsFile())),
                CelestialSavedData.load(payload(celestialFile())));
    }

    @ParameterizedTest @ValueSource(ints = {1, 2, 3})
    void failedAcknowledgmentRemainsRetryableInTheSameProcess(int failed) {
        var data = ready(true); var progress = CelestialSavedData.create();
        data.claim(mission, owner, 201);
        var count = new AtomicInteger();
        Consumer<io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.AtomicSavedData> write = store -> {
            if (count.incrementAndGet() == failed) { throw new UncheckedIOException(new IOException("injected")); }
            store.flush(store instanceof SatelliteMissionSavedData ? missionsFile() : celestialFile());
        };
        assertThrows(UncheckedIOException.class, () -> recover(data, progress, true, write::accept, write::accept));
        assertPaidOnce(data);
        assertEquals(failed == 3, progress.get(TARGET).isPresent(), "Unacknowledged discovery leaked into navigation");
        data.claim(mission, owner, 202);
        recover(data, progress, true);
        assertComplete(data, progress);
    }

    @Test void dirtyExistingVisitIsAcknowledgedBeforeFinishingTheReceipt() throws Exception {
        var data = ready(true); var progress = CelestialSavedData.create();
        data.claim(mission, owner, 201); progress.recordVisit(TARGET, 37);
        var before = progress.save(new CompoundTag()); var order = new java.util.ArrayList<String>();
        recover(data, progress, true, value -> {
            order.add(value.mission(mission).orElseThrow().status().name());
            if (value.mission(mission).orElseThrow().status() == MissionStatus.CLAIMED) {
                try { assertEquals(before, payload(celestialFile())); }
                catch (IOException exception) { throw new UncheckedIOException(exception); }
            }
            value.flush(missionsFile());
        }, value -> { order.add("discovery"); value.flush(celestialFile()); });
        assertEquals(java.util.List.of("CLAIM_PENDING_DISCOVERY", "discovery", "CLAIMED"), order);
        assertEquals(before, progress.save(new CompoundTag()));
        assertFalse(progress.isDirty()); assertComplete(data, progress);
    }

    @Test void historicalClaimedReceiptRepairsAbsentProgressWithoutChangingItsSnapshot() {
        var data = ready(true); data.claim(mission, owner, 201); data.finishDiscovery(mission);
        var historical = data.save(new CompoundTag()); var progress = CelestialSavedData.create();
        assertEquals(SatelliteOperationCode.CATALOG_UNAVAILABLE, recover(data, progress, false).code());
        assertEquals(historical, data.save(new CompoundTag()));
        assertEquals(SatelliteOperationCode.ALREADY_CLAIMED, recover(data, progress, true).code());
        assertEquals(historical, data.save(new CompoundTag())); assertComplete(data, progress);
    }

    @Test void absentFullAndFutureProgressWaitWithoutDiskWritesOrHistoryEviction() {
        var data = ready(true); data.claim(mission, owner, 201);
        var progress = CelestialSavedData.create();
        var noMissionsWrite = (Consumer<SatelliteMissionSavedData>) value -> fail("Unavailable replay wrote missions");
        var noCelestialWrite = (Consumer<CelestialSavedData>) value -> fail("Unavailable replay wrote progress");
        assertEquals(SatelliteOperationCode.CATALOG_UNAVAILABLE,
                recover(data, progress, false, noMissionsWrite, noCelestialWrite).code());
        for (int i = 0; i < 128; i++) { progress.discover(ModIdentity.id("retained_" + i), 1); }
        var full = progress.save(new CompoundTag());
        assertEquals(SatelliteOperationCode.PENDING_DISCOVERY,
                recover(data, progress, true, noMissionsWrite, noCelestialWrite).code());
        assertEquals(full, progress.save(new CompoundTag()));
        CompoundTag future = full.copy(); future.putInt("schema_version", 999);
        var blocked = CelestialSavedData.load(future);
        assertEquals(SatelliteOperationCode.PENDING_DISCOVERY,
                recover(data, blocked, true, noMissionsWrite, noCelestialWrite).code());
        assertEquals(future, blocked.save(new CompoundTag())); assertPaidOnce(data);
        assertEquals(MissionStatus.CLAIM_PENDING_DISCOVERY, data.mission(mission).orElseThrow().status());
    }

    @Test void historicalRepairDoesNotReleaseOrRewriteANewerCurrentMission() {
        var data = ready(true); data.claim(mission, owner, 201); data.finishDiscovery(mission);
        UUID next = UUID.randomUUID();
        assertEquals(SatelliteOperationCode.SUCCESS, data.startMission(satellite, next, owner,
                PlanetaryContent.surveySatellite(), PlanetaryContent.VENUS, 202, true).code());
        var before = data.save(new CompoundTag()); var progress = CelestialSavedData.create();
        assertEquals(SatelliteOperationCode.ALREADY_CLAIMED, recover(data, progress, true).code());
        assertEquals(before, data.save(new CompoundTag()));
        assertEquals(next, data.satellite(satellite).orElseThrow().currentMissionId().orElseThrow());
        assertEquals(MissionStatus.ACTIVE, data.mission(next).orElseThrow().status());
        assertTrue(progress.get(TARGET).isPresent()); assertTrue(progress.get(PlanetaryContent.VENUS).isEmpty());
        assertPaidOnce(data);
    }

    @Test void unpaidCancelledAndNonDiscoveryRecordsCannotGrantProgress() {
        var progress = CelestialSavedData.create(); var data = ready(true);
        assertEquals(SatelliteOperationCode.NOT_READY, recover(data, progress, true).code());
        data.cancel(mission, owner, false, 201);
        assertEquals(SatelliteOperationCode.NOT_READY, recover(data, progress, true).code());
        data = ready(false); data.claim(mission, owner, 201);
        assertEquals(SatelliteOperationCode.NOT_READY, recover(data, progress, true).code());
        assertTrue(progress.entries().isEmpty()); assertEquals(120, data.account(owner).balance());
    }

    private SatelliteMissionSavedData ready(boolean discovery) {
        var data = SatelliteMissionSavedData.create(0);
        data.launch(satellite, mission, owner, PlanetaryContent.surveySatellite(), TARGET, 0, discovery);
        data.completeDue(200); return data;
    }

    private SatelliteOperationResult recover(SatelliteMissionSavedData data, CelestialSavedData progress, boolean available) {
        return recover(data, progress, available, value -> value.flush(missionsFile()), value -> value.flush(celestialFile()));
    }

    private SatelliteOperationResult recover(SatelliteMissionSavedData data, CelestialSavedData progress, boolean available,
            Consumer<SatelliteMissionSavedData> saveMissions, Consumer<CelestialSavedData> saveCelestial) {
        return DiscoveryClaimRecovery.recover(data, progress, mission, 203, id -> available, saveMissions, saveCelestial);
    }

    private void assertComplete(SatelliteMissionSavedData data, CelestialSavedData progress) {
        assertPaidOnce(data); assertTrue(progress.get(TARGET).isPresent());
        assertEquals(MissionStatus.CLAIMED, data.mission(mission).orElseThrow().status());
        assertTrue(data.satellite(satellite).orElseThrow().currentMissionId().isEmpty());
    }

    private void assertPaidOnce(SatelliteMissionSavedData data) {
        var account = data.account(owner);
        assertEquals(20, account.balance()); assertEquals(120, account.lifetimeEarned()); assertEquals(100, account.lifetimeSpent());
    }

    private Path missionsFile() { return root.resolve(ManagedSavedDataType.SATELLITE_MISSIONS.fileName()); }
    private Path celestialFile() { return root.resolve(ManagedSavedDataType.CELESTIAL.fileName()); }
    private static CompoundTag payload(Path file) throws IOException { return NbtIo.readCompressed(file.toFile()).getCompound("data"); }
    private static final class Cut extends Error { }
}
