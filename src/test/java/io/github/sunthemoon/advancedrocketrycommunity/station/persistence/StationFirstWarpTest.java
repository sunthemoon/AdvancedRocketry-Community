package io.github.sunthemoon.advancedrocketrycommunity.station.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootPresence;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootSupplier;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataMigrationException;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.WorldDataMigrationService;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEvent;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventDisposition;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventKind;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventOutbox;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventSourceView;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData.CheckedUpdate;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.TestDataStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** W44 A0 for the station warp source (sections 1.1 to 1.5, 3.6 and the station rows of section 10). */
final class StationFirstWarpTest {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.STATIONS;
    private static final ResourceLocation EARTH = ModIdentity.id("earth");
    private static final ResourceLocation MOON = ModIdentity.id("moon");
    private static final int BALANCE = 5_000_000;
    private static final int COST = 2_000_000;
    private static final UUID ACTOR = new UUID(7, 1);
    private static final List<UUID> PARTICIPANTS = List.of(new UUID(7, 3), new UUID(7, 2));
    private static final CheckedSavedDataPolicy.DirectoryForce NO_FORCE = directory -> { };
    @TempDir Path root;
    private Path file;
    private StationRegistrySavedData data;
    private StationState station;
    private StationState neighbor;

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @BeforeEach void createRegistry() {
        file = root.resolve(TYPE.fileName());
        data = new StationRegistrySavedData();
        station = commit("Winner");
        neighbor = commit("Neighbor");
        data.foldWarpCredits(Map.of(station.stationId(), BALANCE, neighbor.stationId(), BALANCE));
        CheckedSavedDataFile.replace(file, TYPE, () -> data.save(new CompoundTag()));
        data.setDirty(false);
    }

    @Test void anAllowedRelocationElectsInsideTheSameCandidateAndPublishesItTogether() {
        long sample = data.orbitalTick();
        assertEquals(CheckedUpdate.COMMITTED, relocate(station, request(FirstEventDisposition.ALLOW,
                FirstEventDisposition.ALLOW, PARTICIPANTS)));
        FirstEventOutbox outbox = data.firstWarpRam();
        assertEquals(FirstEventOutbox.State.ELECTED, outbox.state());
        FirstEvent.StationWarp event = (FirstEvent.StationWarp) outbox.event();
        assertEquals(station.stationId(), event.stationId());
        assertEquals(ACTOR, event.actorId());
        assertEquals(EARTH, event.fromBody());
        assertEquals(MOON, event.toBody());
        assertEquals(COST, event.cost());
        assertEquals(sample, event.orbitalTick());
        assertEquals(PARTICIPANTS.stream().sorted().toList(), event.participants());
        assertEquals(FirstEventSourceView.known(outbox), data.durableFirstWarp());
        CompoundTag onDisk = CheckedSavedDataFile.readPayload(file, TYPE).orElseThrow();
        assertEquals(onDisk, data.save(new CompoundTag()), "orbit, debit, clock and outbox are one candidate");
        StationRegistrySavedData restarted = StationRegistrySavedData.load(onDisk);
        assertEquals(outbox, restarted.firstWarpRam());
        assertEquals(BALANCE - COST, restarted.warpEnergy(station.stationId()));
        // No later warp can win, even if a stale ALLOW reached the checked entry.
        assertEquals(CheckedUpdate.COMMITTED, relocate(neighbor, request(FirstEventDisposition.ALLOW,
                FirstEventDisposition.ALLOW, PARTICIPANTS)));
        assertEquals(outbox, data.firstWarpRam());
        assertEquals(BALANCE - COST, data.warpEnergy(neighbor.stationId()), "the later warp is ordinary");
    }

    @Test void deferRefusesBeforeAnyWriteDebitOrDirtyStateAndNotFirstNeverElects() throws Exception {
        byte[] before = Files.readAllBytes(file);
        for (FirstWarpRequest deferred : List.of(request(FirstEventDisposition.DEFER, FirstEventDisposition.ALLOW, PARTICIPANTS),
                request(FirstEventDisposition.ALLOW, FirstEventDisposition.DEFER, PARTICIPANTS))) {
            assertEquals(CheckedUpdate.FIRST_EVENT_UNAVAILABLE, relocate(station, (from, to) -> { throw new AssertionError("A deferred warp must not be written"); }, deferred));
            assertArrayEquals(before, Files.readAllBytes(file));
            assertEquals(BALANCE, data.warpEnergy(station.stationId()));
            assertFalse(data.isDirty());
        }
        assertEquals(CheckedUpdate.COMMITTED, relocate(station, request(FirstEventDisposition.ALLOW,
                FirstEventDisposition.NOT_FIRST, PARTICIPANTS)));
        assertEquals(FirstEventOutbox.empty(), data.firstWarpRam(), "a recheck that is no longer ALLOW never elects");
        assertEquals(CheckedUpdate.COMMITTED, relocate(neighbor, FirstWarpRequest.NONE));
        assertEquals(FirstEventOutbox.empty(), data.firstWarpRam(), "the fixture overload never elects");
    }

    @Test void overBudgetRefusesWithoutTruncationWriteOrDebit() throws Exception {
        byte[] before = Files.readAllBytes(file);
        List<UUID> excess = new ArrayList<>();
        for (int index = 0; index <= FirstEventKind.FIRST_STATION_WARP.maxParticipants(); index++) {
            excess.add(new UUID(9, index));
        }
        assertEquals(CheckedUpdate.FIRST_EVENT_OVER_BUDGET, relocate(station,
                (from, to) -> { throw new AssertionError("An over-budget warp must not be written"); },
                request(FirstEventDisposition.ALLOW, FirstEventDisposition.ALLOW, excess)));
        assertArrayEquals(before, Files.readAllBytes(file));
        assertEquals(BALANCE, data.warpEnergy(station.stationId()));
        assertEquals(FirstEventOutbox.empty(), data.firstWarpRam());
        assertTrue(data.admitsFirstWarp(FirstEventKind.FIRST_STATION_WARP.maxParticipants()));
    }

    /** Section 1.3 and FW11: the root-only acknowledgement keeps every station, reservation and balance exactly. */
    @Test void theRootOnlyAcknowledgementSubstitutesOnlyTheOutboxAndTheClock() {
        relocate(station, request(FirstEventDisposition.ALLOW, FirstEventDisposition.ALLOW, PARTICIPANTS));
        FirstEvent event = data.firstWarpRam().event();
        data.reserve(new UUID(8, 1), new UUID(8, 2), "Pending", EARTH, 0);
        assertTrue(data.delete(station.stationId()).isPresent(), "deleting the winner does not prevent delivery");
        CompoundTag before = data.save(new CompoundTag());
        for (int tick = 0; tick < 40; tick++) { data.advanceOrbitalClock(); }
        FirstEvent other = FirstEvent.StationWarp.create(new UUID(5, 5), station.stationId(), ACTOR, EARTH, MOON, COST,
                0L, List.of());
        assertEquals(CheckedUpdate.STALE, deliver(event, null), "a receipt that is not durable");
        assertEquals(CheckedUpdate.STALE, deliver(event, other), "a different receipt");
        assertEquals(CheckedUpdate.STALE, deliver(other, other), "a different event");
        long sample = data.orbitalTick();
        assertEquals(CheckedUpdate.COMMITTED, deliver(event, event));
        assertEquals(FirstEventOutbox.elected(event).delivered(), data.firstWarpRam());
        CompoundTag onDisk = CheckedSavedDataFile.readPayload(file, TYPE).orElseThrow();
        assertEquals(before.get("stations"), onDisk.get("stations"));
        assertEquals(before.get("reservations"), onDisk.get("reservations"));
        assertEquals(before.get("warp_energy"), onDisk.get("warp_energy"));
        assertEquals(sample, onDisk.getCompound("orbital_clock").getLong("logical_tick"));
        assertEquals(sample, data.orbitalClockFloor());
        assertEquals(CheckedUpdate.UNCHANGED, deliver(event, event), "exact DELIVERED is idempotent");
    }

    @Test void aFailedAcknowledgementKeepsElectedAndAnUnknownOneQuarantines() throws Exception {
        relocate(station, request(FirstEventDisposition.ALLOW, FirstEventDisposition.ALLOW, PARTICIPANTS));
        FirstEvent event = data.firstWarpRam().event();
        byte[] before = Files.readAllBytes(file);
        assertEquals(CheckedUpdate.WRITE_FAILED, data.checkedDeliverFirstWarp(file, event, event,
                new CheckedSavedDataPolicy(TYPE, (from, to) -> { throw new IOException("injected"); }), NO_FORCE,
                StationCheckedWriter.named(station.stationId())));
        assertArrayEquals(before, Files.readAllBytes(file));
        assertEquals(FirstEventOutbox.State.ELECTED, data.firstWarpRam().state());
        assertEquals(CheckedUpdate.OUTCOME_UNKNOWN, data.checkedDeliverFirstWarp(file, event, event,
                new CheckedSavedDataPolicy(TYPE, (from, to) -> {
                    Files.write(to, new byte[] {1, 2, 3});
                    throw new IOException("injected torn replacement");
                }), NO_FORCE, StationCheckedWriter.named(station.stationId())));
        assertTrue(data.updatesQuarantined());
        assertEquals(FirstEventSourceView.unavailable(FirstEventSourceView.Reason.QUARANTINED), data.durableFirstWarp());
        assertEquals(FirstEventOutbox.elected(event), StationRegistrySavedData.load(data.save(new CompoundTag()))
                .firstWarpRam(), "ordinary saves reassert ELECTED; the event is kept");
    }

    /** A0 case d (FW6b): a moved candidate with a throwing readback is OUTCOME_UNKNOWN; reload yields the candidate. */
    @Test void aThrowingReadbackAfterTheMoveIsUnknownAndReloadYieldsTheCandidate() {
        var policy = new CheckedSavedDataPolicy(TYPE, (from, to) -> {
            CheckedSavedDataFile.atomicMove(from, to);
            throw new IOException("injected after the move");
        }, (path, type) -> { throw new java.io.UncheckedIOException(new IOException("injected readback failure")); });
        assertEquals(CheckedUpdate.OUTCOME_UNKNOWN, data.relocation(file, station, MOON, COST, policy, NO_FORCE,
                request(FirstEventDisposition.ALLOW, FirstEventDisposition.ALLOW, PARTICIPANTS)));
        assertTrue(data.updatesQuarantined());
        assertEquals(FirstEventOutbox.empty(), data.firstWarpRam(), "RAM has no event");
        StationRegistrySavedData reloaded = StationRegistrySavedData.load(
                CheckedSavedDataFile.readPayload(file, TYPE).orElseThrow());
        assertEquals(MOON, reloaded.find(station.stationId()).orElseThrow().orbitBody());
        assertEquals(BALANCE - COST, reloaded.warpEnergy(station.stationId()));
        assertEquals(FirstEventOutbox.State.ELECTED, reloaded.firstWarpRam().state());
        // R1: a failed directory force after the move resolves through the readback as COMMITTED.
        assertEquals(CheckedUpdate.COMMITTED, reloaded.relocation(file, reloaded.find(neighbor.stationId()).orElseThrow(),
                MOON, COST, CheckedSavedDataPolicy.atomic(TYPE), directory -> { throw new IOException("injected"); },
                FirstWarpRequest.NONE));
    }

    /** FP8 A0: a committed candidate whose publish failed exposes its event, never stale EMPTY, and refuses DELIVERED. */
    @Test void aCommittedOverrideExposesTheCommittedEventAndRefusesTheAcknowledgement() {
        assertEquals(CheckedUpdate.OUTCOME_UNKNOWN, relocate(station, (from, to) -> {
            CheckedSavedDataFile.atomicMove(from, to);
            data.addMember(station.stationId(), UUID.randomUUID());
        }, request(FirstEventDisposition.ALLOW, FirstEventDisposition.ALLOW, PARTICIPANTS)));
        assertEquals(FirstEventOutbox.empty(), data.firstWarpRam());
        FirstEventSourceView view = data.durableFirstWarp();
        assertEquals(FirstEventOutbox.State.ELECTED, view.outbox().state());
        FirstEvent event = view.outbox().event();
        assertEquals(CheckedUpdate.UNAVAILABLE, deliver(event, event));
        assertEquals(CheckedSavedDataFile.readPayload(file, TYPE).orElseThrow(), data.save(new CompoundTag()));
        assertFalse(data.accessGeneration().isPresent());
        assertTrue(data.stations().isEmpty());
    }

    @Test void aCandidateWithAnyOtherOutboxIsRefusedBeforeTheWrite() throws Exception {
        byte[] before = Files.readAllBytes(file);
        data.candidateTransform = candidate -> {
            candidate.put("classic_first_warp", FirstEventOutbox.elected(FirstEvent.StationWarp.create(new UUID(1, 1),
                    station.stationId(), ACTOR, EARTH, MOON, COST, 0L, List.of())).encode(FirstEventKind.FIRST_STATION_WARP));
            return candidate;
        };
        assertThrows(IllegalStateException.class, () -> relocate(station, FirstWarpRequest.NONE));
        assertArrayEquals(before, Files.readAllBytes(file));
    }

    /** Section 1.4 and FW10b to FW10f A0: the exact pre-release whitelist precedes CURRENT classification. */
    @Test void onlyTheExactPreReleaseRootMigratesAndEveryOtherShapeRefusesIntact() {
        CompoundTag current = data.save(new CompoundTag());
        CompoundTag preRelease = preRelease(current);
        var migrated = SavedDataSchemaMigrator.migrate(TYPE, preRelease);
        assertEquals(SavedDataSchemaMigrator.MigrationStatus.MIGRATED, migrated.status());
        assertEquals(current, migrated.payload(), "only the EMPTY outbox and the epoch stamp change");
        assertEquals(SavedDataSchemaMigrator.MigrationStatus.CURRENT,
                SavedDataSchemaMigrator.migrate(TYPE, migrated.payload()).status());
        assertFalse(StationRegistrySavedData.load(preRelease).operational(), "it needs the pre-start migration");
        CompoundTag newWithout = current.copy();
        newWithout.remove("classic_first_warp");
        CompoundTag oldWith = preRelease.copy();
        oldWith.put("classic_first_warp", current.get("classic_first_warp").copy());
        CompoundTag unknown = current.copy();
        unknown.putString("format_epoch", "v1.8.0-station-later");
        CompoundTag badClock = preRelease.copy();
        badClock.getCompound("orbital_clock").putLong("logical_tick", -1L);
        for (CompoundTag refused : List.of(newWithout, oldWith, unknown, badClock)) {
            CompoundTag original = refused.copy();
            assertThrows(SavedDataMigrationException.class, () -> SavedDataSchemaMigrator.migrate(TYPE, refused));
            assertEquals(original, refused);
        }
        CompoundTag invalidOutbox = current.copy();
        invalidOutbox.getCompound("classic_first_warp").putString("state", "LANDED");
        assertFalse(StationRegistrySavedData.load(invalidOutbox).operational(), "the semantic validator refuses it");
    }

    /** FW10b and FW10c A0 through the pre-start service: one backed-up migration, and a near-limit root refuses intact. */
    @Test void thePreStartServiceMigratesOnceWithABackupAndRefusesANearLimitRootIntact() throws Exception {
        Path world = world("migrates", preRelease(data.save(new CompoundTag())));
        var report = new WorldDataMigrationService().migrate(world);
        assertEquals(1, report.migratedFileCount());
        assertTrue(report.backupDirectory().isPresent());
        assertEquals(0, new WorldDataMigrationService().migrate(world).migratedFileCount(), "a second start is a no-op");

        CompoundTag nearLimit = preRelease(data.save(new CompoundTag()));
        int size = StationNbtSize.uncompressedBytes(nearLimit);
        nearLimit.putByteArray("test_padding", new byte[StationLimits.MAX_REGISTRY_NBT_BYTES - size - 20]);
        assertTrue(StationNbtSize.uncompressedBytes(nearLimit) <= StationLimits.MAX_REGISTRY_NBT_BYTES);
        Path refused = world("near-limit", nearLimit);
        byte[] before = Files.readAllBytes(refused.resolve("data").resolve(TYPE.fileName()));
        assertThrows(SavedDataMigrationException.class, () -> new WorldDataMigrationService().migrate(refused));
        assertArrayEquals(before, Files.readAllBytes(refused.resolve("data").resolve(TYPE.fileName())));
        assertFalse(Files.exists(refused.resolve("advancedrocketrycommunity-backups")));
    }

    /** A0 cases a and b for the station registry. */
    @Test void anUnreadableRegistryIsNeverWrittenAndAnAppearedFileIsKept() throws Exception {
        byte[] original = Files.readAllBytes(file);
        var storage = TestDataStorage.over(root);
        StationRegistrySavedData unreadable = ManagedRootSupplier.obtain(storage, file, TYPE, tag -> {
            throw new IllegalStateException("injected runtime read failure");
        }, StationRegistrySavedData::created, CheckedSavedDataFile::presence);
        assertEquals(ManagedRootPresence.UNREADABLE, unreadable.presence());
        assertFalse(unreadable.operational());
        assertFalse(unreadable.isDirty());
        assertEquals(FirstEventSourceView.unavailable(FirstEventSourceView.Reason.ROOT_UNREADABLE),
                unreadable.durableFirstWarp());
        assertThrows(IllegalStateException.class, () -> unreadable.reserve(UUID.randomUUID(), UUID.randomUUID(), "X",
                EARTH, 0));
        assertEquals(CheckedUpdate.UNAVAILABLE, unreadable.checkedRelocation(file, station, MOON, COST,
                CheckedSavedDataFile::atomicMove));
        unreadable.setDirty();
        storage.save();
        assertArrayEquals(original, Files.readAllBytes(file));
        assertFalse(Files.exists(root.resolve(TYPE.fileName() + ".arce-pending")));
        Path statDirectory = Files.createDirectory(root.resolve("stat"));
        assertEquals(ManagedRootPresence.UNREADABLE, ManagedRootSupplier.obtain(TestDataStorage.over(statDirectory),
                statDirectory.resolve(TYPE.fileName()), TYPE, StationRegistrySavedData::load,
                StationRegistrySavedData::created, path -> CheckedSavedDataFile.Presence.UNKNOWN).presence());
        Path absentDirectory = Files.createDirectory(root.resolve("absent"));
        StationRegistrySavedData absent = ManagedRootSupplier.obtain(TestDataStorage.over(absentDirectory),
                absentDirectory.resolve(TYPE.fileName()), TYPE, StationRegistrySavedData::load,
                StationRegistrySavedData::created, CheckedSavedDataFile::presence);
        assertEquals(ManagedRootPresence.PROVEN_ABSENT, absent.presence());
        assertEquals(FirstEventSourceView.known(FirstEventOutbox.empty()), absent.durableFirstWarp());

        // Case b: a file that appears before the first ordinary save or the first checked write is never replaced.
        Path appearedFile = absentDirectory.resolve(TYPE.fileName());
        absent.reserve(UUID.randomUUID(), UUID.randomUUID(), "Appeared", EARTH, 0);
        Files.writeString(appearedFile, "appeared after the proof");
        absent.save(appearedFile.toFile());
        assertEquals("appeared after the proof", Files.readString(appearedFile));
        assertEquals(ManagedRootPresence.UNREADABLE, absent.presence());
        assertFalse(absent.isDirty());
        StationRegistrySavedData checkedFirst = StationRegistrySavedData.created(ManagedRootPresence.PROVEN_ABSENT);
        UUID id = UUID.randomUUID();
        checkedFirst.reserve(id, UUID.randomUUID(), "Checked", EARTH, 0);
        StationState committed = checkedFirst.commit(id);
        Path checkedDirectory = Files.createDirectory(root.resolve("checked"));
        Path checkedFile = checkedDirectory.resolve(TYPE.fileName());
        Files.writeString(checkedFile, "appeared after the proof");
        assertEquals(CheckedUpdate.WRITE_FAILED, checkedFirst.checkedExpand(checkedFile, committed,
                CheckedSavedDataFile::atomicMove));
        assertEquals("appeared after the proof", Files.readString(checkedFile));
        assertEquals(ManagedRootPresence.UNREADABLE, checkedFirst.presence());
    }

    /**
     * Section 1.2: StationWarpService is the only production caller of the electing relocation. The isolated A1
     * GameTest fixtures (classes named *GameTests or *GameTestSupport) build their own requests over detached roots.
     */
    @Test void onlyTheWarpServiceBuildsAnElectingRequest() throws Exception {
        Path main = Path.of("src/main/java");
        List<String> builders;
        try (Stream<Path> files = Files.walk(main)) {
            builders = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().endsWith("GameTests.java")
                            && !path.getFileName().toString().endsWith("GameTestSupport.java")).filter(path -> {
                try { return Files.readString(path).contains("new FirstWarpRequest("); }
                catch (IOException unreadable) { throw new java.io.UncheckedIOException(unreadable); }
            }).map(path -> path.getFileName().toString()).sorted().toList();
        }
        assertEquals(List.of("FirstWarpRequest.java", "StationWarpService.java"), builders);
    }

    private CheckedUpdate relocate(StationState observed, FirstWarpRequest request) {
        return relocate(observed, CheckedSavedDataFile::atomicMove, request);
    }

    private CheckedUpdate relocate(StationState observed, CheckedSavedDataFile.Committer committer,
                                   FirstWarpRequest request) {
        return data.relocation(file, observed, MOON, COST, new CheckedSavedDataPolicy(TYPE, committer), NO_FORCE, request);
    }

    private CheckedUpdate deliver(FirstEvent event, FirstEvent receipt) {
        return data.checkedDeliverFirstWarp(file, event, receipt, CheckedSavedDataPolicy.atomic(TYPE), NO_FORCE,
                StationCheckedWriter.named(station.stationId()));
    }

    private static FirstWarpRequest request(FirstEventDisposition disposition, FirstEventDisposition recheck,
                                            List<UUID> participants) {
        return new FirstWarpRequest(ACTOR, participants, disposition, () -> recheck);
    }

    private static CompoundTag preRelease(CompoundTag current) {
        CompoundTag old = current.copy();
        old.remove("classic_first_warp");
        old.putString("format_epoch", ManagedSavedDataType.STATION_PRE_RELEASE_EPOCH);
        return old;
    }

    private Path world(String name, CompoundTag stations) throws IOException {
        Path world = Files.createDirectories(root.resolve(name).resolve("data")).getParent();
        CompoundTag outer = new CompoundTag();
        outer.put("data", stations);
        outer.putInt("DataVersion", 3465);
        NbtIo.writeCompressed(outer, world.resolve("data").resolve(TYPE.fileName()).toFile());
        return world;
    }

    private StationState commit(String name) {
        UUID stationId = UUID.randomUUID();
        data.reserve(stationId, UUID.randomUUID(), name, EARTH, 0);
        return data.commit(stationId);
    }
}
