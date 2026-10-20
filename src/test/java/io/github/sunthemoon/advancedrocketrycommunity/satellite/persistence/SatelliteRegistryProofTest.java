package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.RegistryLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteBlueprint;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKindState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteStats;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteManager;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SatelliteRegistryProofTest {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.SATELLITE_MISSIONS;
    private static final CheckedSavedDataPolicy NORMAL = CheckedSavedDataPolicy.atomic(TYPE);
    @TempDir Path temporary;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void cleanSchemaFourReloadIsAnObservationWithoutWritingOrAdvancing() throws Exception {
        var data = SatelliteMissionSavedData.create(0);
        UUID satellite = launch(data);
        data.flush(file());
        byte[] bytes = Files.readAllBytes(file());
        var loaded = reload();
        long epoch = loaded.saveEpoch();
        var watch = loaded.watch(satellite);
        var unused = new CheckedSavedDataPolicy(TYPE, (a, b) -> fail("Clean disk observation must not write"));
        assertTrue(checkpoint(loaded, unused, watch).proves(watch));
        assertEquals(epoch, loaded.saveEpoch());
        assertEquals("v1.8.0-ground-surveys", loaded.save(new CompoundTag()).getString("format_epoch"));
        assertArrayEquals(bytes, Files.readAllBytes(file()));
        assertFalse(loaded.acknowledgement().orElseThrow().proves(data.watch(satellite)), "Old session watch is not reusable");
    }

    @Test void replaceThenThrowReadsBackByEqualityInTheSameCallAndAdvancesOnlyOnce() {
        var data = SatelliteMissionSavedData.create(0);
        UUID satellite = launch(data);
        var watch = data.watch(satellite);
        long epoch = data.saveEpoch();
        var reads = new AtomicInteger();
        var policy = new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
            CheckedSavedDataFile.atomicMove(staged, target);
            throw new IOException("after replace");
        }, (path, type) -> { reads.incrementAndGet(); return CheckedSavedDataFile.readPayload(path, type); });
        var completion = checkpoint(data, policy, watch);
        assertEquals(SatelliteRegistryProof.Outcome.COMMITTED, completion.outcome());
        assertTrue(completion.proves(watch));
        assertEquals(1, reads.get());
        assertEquals(epoch + 1, data.saveEpoch());
        assertFalse(data.isDirty());
        assertFalse(data.flushPending());
        data.flush(file());
        assertEquals(epoch + 1, data.saveEpoch());
    }

    @Test void failureBeforeReplaceLeavesBytesEpochSequenceAndPendingThenCapturesFresh() throws Exception {
        var data = SatelliteMissionSavedData.create(0);
        UUID first = launch(data);
        data.flush(file());
        byte[] before = Files.readAllBytes(file());
        UUID second = launch(data);
        var watch = data.watch(second);
        long sequence = data.mutationSequence(), epoch = data.saveEpoch();
        var rejected = new CheckedSavedDataPolicy(TYPE, (a, b) -> { throw new IOException("before replace"); });
        assertEquals(SatelliteRegistryProof.Outcome.FAILED, checkpoint(data, rejected, watch).outcome());
        assertArrayEquals(before, Files.readAllBytes(file()));
        assertEquals(epoch, data.saveEpoch());
        assertEquals(sequence, data.mutationSequence(), "A write attempt is not a state mutation");
        assertTrue(data.isDirty()); assertTrue(data.flushPending());
        data.cancel(SatelliteManager.initialMissionId(first), data.satellite(first).orElseThrow().ownerId(), false, 1);
        var result = checkpoint(data, NORMAL, watch);
        assertTrue(result.proves(watch));
        assertEquals(epoch + 1, data.saveEpoch());
        assertEquals(data.missions(), reload().missions());
    }

    @Test void repeatedUnknownNeverRewritesCandidateOrReusesEpochAndAllowsMutations() {
        var data = SatelliteMissionSavedData.create(0);
        UUID first = launch(data);
        var watch = data.watch(first);
        long epoch = data.saveEpoch();
        var carries = new java.util.ArrayList<CompoundTag>();
        var unknown = new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
            carries.add(CheckedSavedDataFile.readPayload(staged, TYPE).orElseThrow());
            CheckedSavedDataFile.atomicMove(staged, target);
            throw new IOException("unresolved replace");
        }, (path, type) -> { throw new IllegalStateException("unreadable"); });
        assertEquals(SatelliteRegistryProof.Outcome.UNKNOWN, checkpoint(data, unknown, watch).outcome());
        assertTrue(data.acknowledgement().isEmpty());
        UUID second = launch(data);
        assertEquals(SatelliteRegistryProof.Outcome.UNKNOWN, checkpoint(data, unknown, watch).outcome());
        assertEquals(epoch, data.saveEpoch());
        assertTrue(data.isDirty()); assertTrue(data.flushPending());
        assertEquals(epoch + 1, carries.get(0).getLong("save_epoch"));
        assertEquals(epoch + 2, carries.get(1).getLong("save_epoch"));
        assertNotEquals(carries.get(0), carries.get(1));
        assertTrue(checkpoint(data, NORMAL, watch).proves(watch));
        assertEquals(epoch + 3, data.saveEpoch());
        assertTrue(reload().satellite(second).isPresent());
        assertEquals(data.save(new CompoundTag()), CheckedSavedDataFile.readPayload(file(), TYPE).orElseThrow());
    }

    @Test void mutationInsideCommitCannotAdvanceEpochOrClearDirtyOrPending() {
        var data = SatelliteMissionSavedData.create(0);
        UUID first = launch(data);
        var watch = data.watch(first);
        long epoch = data.saveEpoch();
        UUID[] later = {null};
        var policy = new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
            later[0] = launch(data);
            CheckedSavedDataFile.atomicMove(staged, target);
        });
        assertTrue(checkpoint(data, policy, watch).proves(watch));
        assertEquals(epoch, data.saveEpoch());
        assertTrue(data.isDirty()); assertTrue(data.flushPending());
        assertFalse(data.acknowledgement().orElseThrow().proves(data.watch(later[0])));
        assertTrue(reload().satellite(later[0]).isEmpty());
        data.flush(file());
        assertEquals(epoch + 2, data.saveEpoch());
        assertTrue(reload().satellite(later[0]).isPresent());
    }

    @Test void failedCheckpointThenEveryOtherEntryCanProveDescendantAfterMissionProgress() {
        for (String entry : List.of("barrier", "coalesced", "autosave", "stop")) {
            for (String progress : List.of("cancel", "claim", "prune", "replace")) {
                var data = SatelliteMissionSavedData.create(0);
                UUID satellite = launch(data);
                var watch = data.watch(satellite);
                var rejected = new CheckedSavedDataPolicy(TYPE, (a, b) -> { throw new IOException("before replace"); });
                assertEquals(SatelliteRegistryProof.Outcome.FAILED, checkpoint(data, rejected, watch).outcome());
                UUID owner = data.satellite(satellite).orElseThrow().ownerId();
                UUID initial = SatelliteManager.initialMissionId(satellite);
                if (progress.equals("claim") || progress.equals("prune")) {
                    data.completeDue(200);
                    assertTrue(data.claim(initial, owner, 200).success());
                } else {
                    assertTrue(data.cancel(initial, owner, false, 1).success());
                }
                if (progress.equals("replace") || progress.equals("prune")) {
                    UUID later = UUID.randomUUID();
                    assertTrue(data.startMission(satellite, later, owner, definition(), ModIdentity.id("earth"), 300, false).success());
                    if (progress.equals("prune")) { assertTrue(data.claim(later, owner, 500).success()); }
                }
                if (progress.equals("prune")) {
                    data.applyLimits(new RegistryLimits(1024, 64, 1, 3072, 4096, 256, 2048, 16, 10, 2));
                    data.completeDue(10000);
                    assertTrue(data.mission(initial).isEmpty());
                }
                writeEntry(data, entry);
                assertTrue(data.acknowledgement().orElseThrow().proves(watch), entry + "/" + progress);
                assertEquals(1, data.satellites().size());
                var loaded = reload();
                assertEquals(data.missions(), loaded.missions());
                assertTrue(loaded.acknowledgement().orElseThrow().proves(loaded.watch(satellite)));
                assertEquals(SatelliteOperationCode.IDEMPOTENT, loaded.launch(satellite, initial, owner, definition(),
                        ModIdentity.id("earth"), 10000, false).code());
                assertEquals(data.missions(), loaded.missions(), "Replay must not reconstruct an initial mission");
            }
        }
    }

    @Test void ordinaryEntriesResolveThrowsAndSequenceClockOnlyChangesWithoutSettingPending() {
        for (String entry : List.of("barrier", "coalesced", "autosave", "stop")) {
            var data = SatelliteMissionSavedData.create(0);
            UUID satellite = launch(data);
            long sequence = data.mutationSequence();
            data.writePolicy(new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
                CheckedSavedDataFile.atomicMove(staged, target); throw new IOException("after replace");
            }));
            writeEntry(data, entry);
            assertEquals(sequence, data.mutationSequence());
            assertFalse(data.isDirty()); assertFalse(data.flushPending());
            long epoch = data.saveEpoch();
            data.completeDue(1);
            assertTrue(data.mutationSequence() > sequence);
            assertTrue(data.isDirty()); assertFalse(data.flushPending());
            writeEntry(data, entry);
            assertEquals(epoch, data.saveEpoch());
            assertTrue(data.acknowledgement().orElseThrow().proves(data.watch(satellite)));
        }
    }

    @Test void everyOrdinaryEntryKeepsFailedOrUnknownWritesPendingAndRetriesFresh() {
        for (String entry : List.of("barrier", "coalesced", "autosave", "stop")) {
            for (boolean unknown : List.of(false, true)) {
                var data = SatelliteMissionSavedData.create(0);
                launch(data); data.flush(file());
                long epoch = data.saveEpoch();
                UUID satellite = launch(data);
                var watch = data.watch(satellite);
                data.writePolicy(new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
                    if (unknown) { CheckedSavedDataFile.atomicMove(staged, target); }
                    throw new IOException("entry cut");
                }, (path, type) -> {
                    if (unknown) { throw new IllegalStateException("readback unavailable"); }
                    return CheckedSavedDataFile.readPayload(path, type);
                }));
                if (entry.equals("autosave") || entry.equals("stop")) { writeEntry(data, entry); }
                else { assertThrows(IllegalStateException.class, () -> writeEntry(data, entry)); }
                assertEquals(epoch, data.saveEpoch());
                assertTrue(data.isDirty()); assertTrue(data.flushPending());
                if (unknown) { assertTrue(data.acknowledgement().isEmpty()); }
                UUID later = launch(data);
                data.writePolicy(NORMAL); writeEntry(data, entry);
                assertEquals(epoch + (unknown ? 2 : 1), data.saveEpoch());
                assertTrue(data.acknowledgement().orElseThrow().proves(watch));
                assertTrue(reload().satellite(later).isPresent());
                assertFalse(data.isDirty()); assertFalse(data.flushPending());
            }
        }
    }

    @Test void wrongIdentityAndRawBlockedRootsNeverAcquireProof() {
        var data = SatelliteMissionSavedData.create(0);
        UUID satellite = launch(data);
        var watch = data.watch(satellite);
        UUID owner = data.satellite(satellite).orElseThrow().ownerId();
        data.cancel(SatelliteManager.initialMissionId(satellite), owner, false, 1);
        // Data history can prevent decommission; a foreign watch must still never match another session.
        var other = SatelliteMissionSavedData.create(0);
        other.launch(satellite, UUID.randomUUID(), UUID.randomUUID(), definition(), ModIdentity.id("earth"), 0, false);
        other.flush(file());
        assertFalse(other.acknowledgement().orElseThrow().proves(watch));
        CompoundTag raw = other.save(new CompoundTag()); raw.putInt("schema_version", 999);
        var blocked = SatelliteMissionSavedData.load(raw);
        blocked.setDirty();
        assertEquals(SatelliteRegistryProof.Outcome.COMMITTED, checkpoint(blocked, NORMAL).outcome());
        assertTrue(blocked.acknowledgement().isEmpty());
        assertEquals(raw, CheckedSavedDataFile.readPayload(file(), TYPE).orElseThrow());
    }

    @Test void restoreMutationIsSequencedAndNeedsAFreshAcknowledgement() {
        var source = SatelliteMissionSavedData.create(0);
        UUID satellite = launch(source);
        CompoundTag root = source.save(new CompoundTag());
        root.put("research_accounts", new net.minecraft.nbt.ListTag());
        var restored = SatelliteMissionSavedData.load(root);
        assertTrue(restored.operational()); assertTrue(restored.isDirty()); assertTrue(restored.flushPending());
        assertTrue(restored.mutationSequence() > 0); assertTrue(restored.acknowledgement().isEmpty());
        var watch = restored.watch(satellite);
        assertTrue(checkpoint(restored, NORMAL, watch).proves(watch));
        assertFalse(restored.isDirty()); assertFalse(restored.flushPending());
        assertFalse(reload().isDirty(), "The repaired account must be in the committed capture");
    }

    @Test void refusedAndIdempotentOperationsAndClockRollbackAreSequencedWithoutPendingFlush() {
        for (String operation : List.of("launch", "idle", "start", "claim", "cancel", "scan", "scheduler")) {
            var data = SatelliteMissionSavedData.create(0);
            UUID satellite = launch(data), owner = data.satellite(satellite).orElseThrow().ownerId();
            var idle = SatelliteState.launchIdle(
                    UUID.randomUUID(), ModIdentity.id("gas_harvester"), owner, 0, ModIdentity.id("earth"),
                    new SatelliteBlueprint(List.of(ModIdentity.id("primary")), false, new SatelliteStats(4, 10720, 1000, 1, 10)),
                    new SatelliteKindState.Plain(SatelliteKind.GAS_HARVESTER));
            assertTrue(data.launchIdle(t -> idle, 0).success());
            data.flush(file()); long sequence = data.mutationSequence(), epoch = data.saveEpoch();
            switch (operation) {
                case "launch" -> assertFalse(data.launch(satellite, SatelliteManager.initialMissionId(satellite), owner,
                        definition(), ModIdentity.id("earth"), 20, false).changed());
                case "idle" -> assertFalse(data.launchIdle(t -> idle, 20).changed());
                case "start" -> assertFalse(data.startMission(UUID.randomUUID(), UUID.randomUUID(), owner,
                        definition(), ModIdentity.id("earth"), 20, false).changed());
                case "claim" -> assertFalse(data.claim(UUID.randomUUID(), owner, 20).changed());
                case "cancel" -> assertFalse(data.cancel(UUID.randomUUID(), owner, false, 20).changed());
                case "scan" -> assertFalse(data.payScan(satellite, owner, false, 20).changed());
                default -> assertFalse(data.completeDue(20).changed());
            }
            assertTrue(data.mutationSequence() > sequence, operation);
            assertTrue(data.isDirty()); assertFalse(data.flushPending());
            data.flush(file()); assertEquals(epoch, data.saveEpoch());
            sequence = data.mutationSequence();
            assertFalse(data.completeDue(10).clockAdvanced());
            assertTrue(data.mutationSequence() > sequence, "Backward observed time is also a persisted clock mutation");
            assertTrue(data.isDirty()); assertFalse(data.flushPending());
        }
    }

    @Test void blockedRootFailuresNeverCreateProofAndEveryEntryPreservesRawPayload() {
        for (String entry : List.of("checkpoint", "barrier", "coalesced", "autosave", "stop")) {
            for (boolean unknown : List.of(false, true)) {
                CompoundTag raw = SatelliteMissionSavedData.create(0).save(new CompoundTag());
                raw.putInt("schema_version", 999); raw.putString("unrecognized", entry + unknown);
                var blocked = SatelliteMissionSavedData.load(raw); blocked.setDirty();
                var policy = new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
                    if (unknown) { CheckedSavedDataFile.atomicMove(staged, target); }
                    throw new IOException("blocked root cut");
                }, (path, type) -> {
                    if (unknown) { throw new IllegalStateException("readback unavailable"); }
                    return java.util.Optional.empty();
                });
                assertEquals(unknown ? SatelliteRegistryProof.Outcome.UNKNOWN : SatelliteRegistryProof.Outcome.FAILED,
                        checkpoint(blocked, policy).outcome());
                assertEquals(raw, blocked.save(new CompoundTag())); assertTrue(blocked.isDirty());
                if (entry.equals("checkpoint")) { checkpoint(blocked, NORMAL); }
                else { writeEntry(blocked, entry); }
                assertFalse(blocked.isDirty()); assertTrue(blocked.acknowledgement().isEmpty());
                assertEquals(raw, CheckedSavedDataFile.readPayload(file(), TYPE).orElseThrow());
            }
        }
    }

    private static void writeEntry(SatelliteMissionSavedData data, String entry, Path file) {
        if (entry.equals("autosave") || entry.equals("stop")) { data.save(file.toFile()); }
        else { data.flush(file); }
    }
    private void writeEntry(SatelliteMissionSavedData data, String entry) { writeEntry(data, entry, file()); }
    private SatelliteRegistryProof.Completion checkpoint(SatelliteMissionSavedData data, CheckedSavedDataPolicy policy,
                                                        SatelliteRegistryProof.Watch... watches) {
        return data.checkpoint(file(), List.of(watches), policy, directory -> { });
    }
    private Path file() { return temporary.resolve(TYPE.fileName()); }
    private SatelliteMissionSavedData reload() { return SatelliteMissionSavedData.load(CheckedSavedDataFile.readPayload(file(), TYPE).orElseThrow()); }
    static SatelliteDefinition definition() {
        return new SatelliteDefinition(1, ModIdentity.id("data_satellite"), 200, 120, 100,
                List.of(ModIdentity.id("earth"), ModIdentity.id("moon")));
    }
    static UUID launch(SatelliteMissionSavedData data) {
        UUID satellite = UUID.randomUUID();
        var result = data.launch(satellite, SatelliteManager.initialMissionId(satellite), UUID.randomUUID(), definition(),
                ModIdentity.id("earth"), 0, false);
        assertEquals(SatelliteOperationCode.SUCCESS, result.code());
        assertEquals(SatelliteManager.initialMissionId(satellite), result.mission().orElseThrow().missionId());
        assertEquals(ModIdentity.id("earth"), result.mission().orElseThrow().targetBodyId());
        return satellite;
    }
}
