package io.github.sunthemoon.advancedrocketrycommunity.station.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationEnvironmentProfile;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationGridCell;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationOrbitalControls;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationRegistryModel;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData.CheckedUpdate;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ADR-065 section 5.2 and 5.2.1: record 3 {@code orbital_controls}, root 5 {@code orbital_clock}, the root 4
 * to 5 migration, the fixed 8 KiB record and 4 MiB root bounds, and the checked orientation and altitude writes
 * that carry one sampled clock.
 */
final class StationOrbitalControlsSchemaTest {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.STATIONS;
    private static final StationOrbitalControls EXTREME = new StationOrbitalControls(190, 359_999, 359_999, 359_999,
            -60, 60, -60, 1_000L);

    @TempDir
    Path root;

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void rootFourMigratesToFiveReproducingEachSunAngleAndKeepingBalances() throws Exception {
        CompoundTag current = SavedDataSchemaMigrator.migrate(TYPE, rootThree()).payload();
        CompoundTag rootFour = asRootFour(current);
        rootFour.getList("warp_energy", Tag.TAG_COMPOUND).add(StationNbtCodec.encodeWarpEnergy(
                new UUID(21L << 32, 1L), 4_321));
        CompoundTag original = rootFour.copy();
        var migration = SavedDataSchemaMigrator.migrate(TYPE, rootFour);
        assertEquals(SavedDataSchemaMigrator.MigrationStatus.MIGRATED, migration.status());
        assertEquals(4, migration.sourceSchema());
        assertEquals(original, rootFour, "the source is not changed");
        CompoundTag expected = current.copy();
        expected.put("warp_energy", original.get("warp_energy").copy());
        assertEquals(expected, migration.payload(), "only the versions, the clock and the initial controls change");
        StationRegistrySavedData data = StationRegistrySavedData.load(migration.payload());
        assertTrue(data.operational());
        StationState station = data.stations().get(0);
        assertEquals(90_000, station.environment().solarAngleMilliDegrees());
        assertEquals(StationOrbitalControls.initial(90_000), station.orbitalControls());
        assertEquals(4_321, data.warpEnergy(station.stationId()));
        assertEquals(0L, data.orbitalTick());
        // A current root is a no-op.
        var again = SavedDataSchemaMigrator.migrate(TYPE, migration.payload());
        assertEquals(SavedDataSchemaMigrator.MigrationStatus.CURRENT, again.status());
        assertEquals(migration.payload(), again.payload());
    }

    @Test
    void savedClockAndEpochsRoundTripAndAFreshStartResumesAtTheSavedTick() {
        StationRegistrySavedData data = new StationRegistrySavedData();
        StationState station = commit(data, "Clock");
        for (int tick = 0; tick < 25; tick++) {
            data.advanceOrbitalClock();
        }
        assertEquals(25L, data.orbitalTick());
        CompoundTag saved = data.save(new CompoundTag());
        assertEquals(StationWarpSchemaTest.clock(25L), saved.getCompound("orbital_clock"));
        StationRegistrySavedData restarted = StationRegistrySavedData.load(saved);
        assertTrue(restarted.operational());
        assertEquals(25L, restarted.orbitalTick(), "restart resumes exactly at the saved tick, never a catch-up");
        assertEquals(station, restarted.find(station.stationId()).orElseThrow());
    }

    @Test
    void malformedControlsAndClocksAndEpochsAheadOfTheClockBlockTheWholeRootVerbatim() {
        StationRegistrySavedData data = new StationRegistrySavedData();
        commit(data, "Malformed");
        CompoundTag valid = data.save(new CompoundTag());
        List<Consumer<CompoundTag>> mutations = List.of(
                tag -> tag.remove("orbital_clock"),
                tag -> tag.getCompound("orbital_clock").putInt("schema_version", 2),
                tag -> tag.getCompound("orbital_clock").putLong("logical_tick", -1L),
                tag -> tag.getCompound("orbital_clock").putInt("logical_tick", 0),
                tag -> tag.getCompound("orbital_clock").putString("extra", "x"),
                tag -> tag.getCompound("orbital_clock").remove("schema_version"),
                tag -> controls(tag).putLong("epoch", 1L),
                tag -> controls(tag).putLong("epoch", -1L),
                tag -> controls(tag).putInt("format_version", 2),
                tag -> controls(tag).putInt("distance", 3),
                tag -> controls(tag).putInt("distance", 191),
                tag -> controls(tag).putIntArray("phases", new int[] {360_000, 0, 0}),
                tag -> controls(tag).putIntArray("phases", new int[] {0, 0}),
                tag -> controls(tag).putByteArray("rates", new byte[] {61, 0, 0}),
                tag -> controls(tag).putByteArray("rates", new byte[] {0, -61, 0}),
                tag -> controls(tag).putByteArray("rates", new byte[] {0, 0, 0, 0}),
                tag -> controls(tag).put("rates", new IntArrayTag(new int[] {0, 0, 0})),
                tag -> controls(tag).putString("extra", "x"),
                tag -> controls(tag).remove("epoch"),
                tag -> record(tag).remove("orbital_controls"),
                tag -> record(tag).putInt("orbital_controls", 1),
                tag -> record(tag).putInt("schema_version", 2));
        for (Consumer<CompoundTag> mutation : mutations) {
            CompoundTag source = valid.copy();
            mutation.accept(source);
            assertBlockedUnchanged(source);
        }
        assertTrue(StationRegistrySavedData.load(valid).operational());
    }

    @Test
    void aMaximumRecordWithExtremeControlsFitsTheRecordBound() {
        StationState maximum = maximumRecord(0, EXTREME);
        CompoundTag encoded = StationNbtCodec.encodeState(maximum);
        assertTrue(StationNbtSize.uncompressedBytes(encoded) <= StationLimits.MAX_STATION_RECORD_NBT_BYTES,
                "record bytes " + StationNbtSize.uncompressedBytes(encoded));
        assertEquals(maximum, StationNbtCodec.decodeState(encoded));
        CompoundTag clock = StationRegistryPayload.encodeClock(Long.MAX_VALUE);
        assertTrue(StationNbtSize.uncompressedBytes(clock) <= 128, "clock bytes " + StationNbtSize.uncompressedBytes(clock));
        assertEquals(Long.MAX_VALUE, StationRegistryPayload.decodeClock(clock));
        // The same record with its original 8 KiB filler is refused, not truncated.
        CompoundTag oversized = encoded.copy();
        oversized.putByteArray("filler", new byte[StationLimits.MAX_STATION_RECORD_NBT_BYTES]);
        assertThrows(IllegalArgumentException.class, () -> StationNbtCodec.decodeState(oversized));
    }

    @Test
    void theRegistryBoundHoldsAtMaximumPopulationAndRefusesOneMoreRecordIntact() {
        CompoundTag source = rootWith(new ArrayList<>(), Long.MAX_VALUE);
        ListTag stations = source.getList("stations", Tag.TAG_COMPOUND);
        int index = 0;
        long estimate = StationNbtSize.uncompressedBytes(source);
        while (true) {
            CompoundTag next = StationNbtCodec.encodeState(maximumRecord(index, EXTREME));
            long nextBytes = StationNbtSize.uncompressedBytes(next);
            stations.add(next);
            if (estimate + nextBytes > StationLimits.MAX_REGISTRY_NBT_BYTES - 32_768L) {
                // Exact measurement near the bound; the estimate above only over-counts.
                long actual = StationNbtSize.uncompressedBytes(source);
                if (actual > StationLimits.MAX_REGISTRY_NBT_BYTES) {
                    stations.remove(stations.size() - 1);
                    break;
                }
                estimate = actual;
            } else {
                estimate += nextBytes;
            }
            index++;
        }
        assertTrue(index > 1_000, "maximum records " + index);
        StationRegistrySavedData full = StationRegistrySavedData.load(source.copy());
        assertTrue(full.operational(), "a root at its bound with extreme controls loads");
        assertEquals(index, full.stations().size());
        assertEquals(source, full.save(new CompoundTag()), "the full root saves back exactly");
        stations.add(StationNbtCodec.encodeState(maximumRecord(index, EXTREME)));
        assertBlockedUnchanged(source);
    }

    @Test
    void orientationWritesSampleOneClockIntoTheCandidateAndRaiseTheFloor() throws Exception {
        Path file = root.resolve(TYPE.fileName());
        StationRegistrySavedData data = new StationRegistrySavedData();
        StationState station = commit(data, "Orientation");
        CheckedSavedDataFile.replace(file, TYPE, () -> data.save(new CompoundTag()));
        for (int tick = 0; tick < 300; tick++) {
            data.advanceOrbitalClock();
        }
        long sample = data.orbitalTick();
        StationOrbitalControls turning = station.orbitalControls().withRates(2, -3, 60, sample);
        assertEquals(CheckedUpdate.COMMITTED, data.checkedSetOrientation(file, station, turning, sample,
                CheckedSavedDataFile::atomicMove));
        StationState published = data.find(station.stationId()).orElseThrow();
        assertEquals(turning, published.orbitalControls());
        assertEquals(sample, data.orbitalClockFloor());
        CompoundTag onDisk = CheckedSavedDataFile.readPayload(file, TYPE).orElseThrow();
        assertEquals(StationWarpSchemaTest.clock(sample), onDisk.getCompound("orbital_clock"));
        assertEquals(published, StationRegistrySavedData.load(onDisk).find(station.stationId()).orElseThrow());
        // Rotation marks the registry dirty at most once per 20 ticks, without forcing a write.
        data.setDirty(false);
        int dirtied = 0;
        for (int tick = 0; tick < 100; tick++) {
            data.advanceOrbitalClock();
            if (data.isDirty()) {
                dirtied++;
                data.setDirty(false);
            }
        }
        assertEquals(5, dirtied);
        // A stale observation, a mismatched epoch and an altitude transition through the wrong method are refused.
        assertEquals(CheckedUpdate.STALE, data.checkedSetOrientation(file, station, turning, sample,
                CheckedSavedDataFile::atomicMove));
        long now = data.orbitalTick();
        assertThrows(IllegalArgumentException.class, () -> data.checkedSetOrientation(file, published,
                published.orbitalControls().stoppedAt(now), now - 1, CheckedSavedDataFile::atomicMove));
        assertThrows(IllegalArgumentException.class, () -> data.checkedSetOrientation(file, published,
                published.orbitalControls().withDistance(5).stoppedAt(now), now, CheckedSavedDataFile::atomicMove));
        assertThrows(IllegalArgumentException.class, () -> data.checkedSetOrientation(file, published,
                published.orbitalControls().stoppedAt(now + 1), now + 1, CheckedSavedDataFile::atomicMove),
                "a sample ahead of the runtime clock");
        // Altitude changes only the distance and carries the current clock.
        assertEquals(CheckedUpdate.COMMITTED, data.checkedSetAltitude(file, published, 179,
                CheckedSavedDataFile::atomicMove));
        StationState higher = data.find(station.stationId()).orElseThrow();
        assertEquals(published.orbitalControls().withDistance(179), higher.orbitalControls());
        assertEquals(now, data.orbitalClockFloor());
        assertEquals(CheckedUpdate.UNCHANGED, data.checkedSetAltitude(file, higher, 179,
                CheckedSavedDataFile::atomicMove));
        assertThrows(IllegalArgumentException.class, () -> data.checkedSetAltitude(file, higher, 191,
                CheckedSavedDataFile::atomicMove));
    }

    @Test
    void anUnknownWriteReassertsTheCheckedFloorAndNeverTheUncommittedSample() throws Exception {
        Path file = root.resolve(TYPE.fileName());
        StationRegistrySavedData data = new StationRegistrySavedData();
        StationState station = commit(data, "Quarantine");
        CheckedSavedDataFile.replace(file, TYPE, () -> data.save(new CompoundTag()));
        for (int tick = 0; tick < 40; tick++) {
            data.advanceOrbitalClock();
        }
        long sample = data.orbitalTick();
        StationOrbitalControls turning = station.orbitalControls().withRates(1, 0, 0, sample);
        assertEquals(CheckedUpdate.OUTCOME_UNKNOWN, data.checkedSetOrientation(file, station, turning, sample,
                (from, to) -> {
                    Files.write(to, new byte[] {1, 2, 3});
                    throw new java.io.IOException("injected torn replacement");
                }));
        assertTrue(data.updatesQuarantined());
        assertEquals(station, data.find(station.stationId()).orElseThrow(), "the uncommitted rate is not published");
        data.advanceOrbitalClock();
        assertEquals(sample, data.orbitalTick(), "the clock is suspended");
        CompoundTag reasserted = data.save(new CompoundTag());
        assertEquals(StationWarpSchemaTest.clock(0L), reasserted.getCompound("orbital_clock"),
                "the ordinary reassertion uses the last checked-commit floor");
        StationRegistrySavedData restarted = StationRegistrySavedData.load(reasserted);
        assertEquals(station, restarted.find(station.stationId()).orElseThrow());
        assertEquals(0L, restarted.orbitalTick());
    }

    @Test
    void aKnownRefusalKeepsTheOldRecordAndFloorWhileTheClockRunsOn() throws Exception {
        Path file = root.resolve(TYPE.fileName());
        StationRegistrySavedData data = new StationRegistrySavedData();
        StationState station = commit(data, "Refusal");
        CheckedSavedDataFile.replace(file, TYPE, () -> data.save(new CompoundTag()));
        byte[] before = Files.readAllBytes(file);
        data.advanceOrbitalClock();
        long sample = data.orbitalTick();
        assertEquals(CheckedUpdate.WRITE_FAILED, data.checkedSetOrientation(file, station,
                station.orbitalControls().withRates(0, 5, 0, sample), sample, (from, to) -> {
                    throw new java.nio.file.AtomicMoveNotSupportedException(from.toString(), to.toString(), "injected");
                }));
        assertEquals(station, data.find(station.stationId()).orElseThrow());
        assertEquals(0L, data.orbitalClockFloor());
        assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(file)));
        data.advanceOrbitalClock();
        assertEquals(sample + 1, data.orbitalTick(), "a clean refusal does not stop the clock");
    }

    private static void assertBlockedUnchanged(CompoundTag source) {
        CompoundTag original = source.copy();
        StationRegistrySavedData blocked = StationRegistrySavedData.load(source);
        assertFalse(blocked.operational());
        assertTrue(blocked.stations().isEmpty());
        assertEquals(original, blocked.save(new CompoundTag()));
        assertThrows(IllegalStateException.class, () -> blocked.delete(UUID.randomUUID()));
    }

    private static CompoundTag record(CompoundTag root) {
        return root.getList("stations", Tag.TAG_COMPOUND).getCompound(0);
    }

    private static CompoundTag controls(CompoundTag root) {
        return record(root).getCompound("orbital_controls");
    }

    private static StationState commit(StationRegistrySavedData data, String name) {
        UUID stationId = UUID.randomUUID();
        data.reserve(stationId, UUID.randomUUID(), name, ModIdentity.id("earth"), 0);
        return data.commit(stationId);
    }

    /** A 48-character name, 32 members, 32 invitations, a 128-character orbit body and the given controls. */
    private static StationState maximumRecord(int index, StationOrbitalControls controls) {
        StationGridCell cell = StationRegistryModel.spiralCell(index);
        List<UUID> members = new ArrayList<>();
        List<UUID> invitations = new ArrayList<>();
        for (int i = 0; i < StationLimits.MAX_MEMBERS; i++) {
            members.add(new UUID(1_000L + index, i));
            invitations.add(new UUID(1_000L + index, 1_000L + i));
        }
        String body = ModIdentity.MOD_ID + ":" + "b".repeat(128 - ModIdentity.MOD_ID.length() - 1);
        return new StationState(StationLimits.STATE_SCHEMA_VERSION, new UUID(-7L, index), new UUID(-8L, index),
                "N".repeat(StationLimits.MAX_NAME_LENGTH), cell, cell.region(StationLimits.EXPANDED_REGION_SIZE),
                cell.landingPad(), new ResourceLocation(body), Long.MAX_VALUE,
                new StationEnvironmentProfile(10_000, true, 359_999), members, invitations, controls);
    }

    private static CompoundTag rootWith(List<CompoundTag> records, long logicalTick) {
        CompoundTag source = new CompoundTag();
        SavedDataSchemaMigrator.stampCurrent(TYPE, source);
        ListTag stations = new ListTag();
        stations.addAll(records);
        source.put("stations", stations);
        source.put("reservations", new ListTag());
        source.put("warp_energy", new ListTag());
        source.put("orbital_clock", StationRegistryPayload.encodeClock(logicalTick));
        return source;
    }

    /** The root 4 that the ADR-044 path produced: no clock, records 2 without controls. */
    private static CompoundTag asRootFour(CompoundTag current) {
        CompoundTag rootFour = current.copy();
        rootFour.putInt("schema_version", 4);
        rootFour.putString("format_epoch", "v1.5.0-station-warp");
        rootFour.remove("orbital_clock");
        rootFour.getList("stations", Tag.TAG_COMPOUND).forEach(raw -> {
            ((CompoundTag) raw).putInt("schema_version", 2);
            ((CompoundTag) raw).remove("orbital_controls");
        });
        return rootFour;
    }

    private static CompoundTag rootThree() throws Exception {
        try (var stream = StationOrbitalControlsSchemaTest.class.getResourceAsStream("/migrations/v150/stations-v3.snbt")) {
            return TagParser.parseTag(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
