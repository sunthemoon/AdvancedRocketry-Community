package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataMigrationException;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * W44 section 6 V rows: the pre-start fixtures of FM11a, FM11b, FW10a to FW10f, FP1a and FP1b, encoded by the
 * production codecs and checked against the migrator here. The native runner copies them from
 * {@code build/w44i-native-fixtures} into disposable worlds; nothing else reads that directory.
 */
class FirstEventNativeFixturesTest {
    static final Path OUTPUT = Path.of("build/w44i-native-fixtures");

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void writesEveryPreStartFixtureWithItsExpectedMigratorVerdict() throws IOException {
        Files.createDirectories(OUTPUT);
        Map<String, Fixture> fixtures = new LinkedHashMap<>();
        CompoundTag journal = new RocketTransferSavedData().save(new CompoundTag());
        CompoundTag rootOne = journal.copy();
        rootOne.remove(RocketTransferSavedData.FIRST_MOON_LANDING);
        rootOne.putInt("schema_version", 1);
        rootOne.remove("format_epoch");
        fixtures.put("FM11a", new Fixture(ManagedSavedDataType.ROCKET_TRANSFERS, rootOne, true));
        CompoundTag rootTwo = io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence
                .MoonLandingSourceTestSupport.committedMoonJournalRootTwo();
        fixtures.put("FM11b", new Fixture(ManagedSavedDataType.ROCKET_TRANSFERS, rootTwo, true));

        StationRegistrySavedData stations = new StationRegistrySavedData();
        UUID stationId = new UUID(44, 1);
        stations.reserve(stationId, new UUID(44, 2), "Native fixture", ModIdentity.id("earth"), 0);
        stations.commit(stationId);
        stations.foldWarpCredits(Map.of(stationId, 1_234_567));
        CompoundTag current = stations.save(new CompoundTag());
        CompoundTag preRelease = preRelease(current);
        CompoundTag rootFour = preRelease.copy();
        rootFour.putInt("schema_version", 4);
        rootFour.putString("format_epoch", "v1.5.0-station-warp");
        rootFour.remove("orbital_clock");
        rootFour.getList("stations", 10).forEach(raw -> {
            ((CompoundTag) raw).putInt("schema_version", 2);
            ((CompoundTag) raw).remove("orbital_controls");
        });
        fixtures.put("FW10a", new Fixture(ManagedSavedDataType.STATIONS, rootFour, true));
        fixtures.put("FW10b", new Fixture(ManagedSavedDataType.STATIONS, preRelease, true));
        CompoundTag nearLimit = preRelease.copy();
        int size = sizeOf(nearLimit);
        nearLimit.putByteArray("native_padding", new byte[4 * 1_024 * 1_024 - size - 20]);
        fixtures.put("FW10c", new Fixture(ManagedSavedDataType.STATIONS, nearLimit, false));
        CompoundTag newWithout = current.copy();
        newWithout.remove(ManagedSavedDataType.FIRST_STATION_WARP_KEY);
        fixtures.put("FW10d", new Fixture(ManagedSavedDataType.STATIONS, newWithout, false));
        CompoundTag oldWith = preRelease.copy();
        oldWith.put(ManagedSavedDataType.FIRST_STATION_WARP_KEY, current.get(ManagedSavedDataType.FIRST_STATION_WARP_KEY).copy());
        fixtures.put("FW10e", new Fixture(ManagedSavedDataType.STATIONS, oldWith, false));
        CompoundTag unknown = current.copy();
        unknown.putString("format_epoch", "v1.8.0-station-unknown");
        fixtures.put("FW10f", new Fixture(ManagedSavedDataType.STATIONS, unknown, false));

        var progression = ClassicProgressionSavedData.provenAbsent();
        progression.insert(FirstEventFixtures.warp(1, List.of(FirstEventFixtures.id(2))));
        CompoundTag future = progression.save(new CompoundTag());
        future.putInt("schema_version", 2);
        fixtures.put("FP1a", new Fixture(ManagedSavedDataType.CLASSIC_PROGRESSION, future, false));
        CompoundTag invalidReceipt = progression.save(new CompoundTag());
        invalidReceipt.getCompound("first_station_warp").getCompound("event").putString("fingerprint", "0".repeat(64));
        fixtures.put("FP1b", new Fixture(ManagedSavedDataType.CLASSIC_PROGRESSION, invalidReceipt, false));

        for (var entry : fixtures.entrySet()) {
            Fixture fixture = entry.getValue();
            if (fixture.migrates()) {
                assertEquals(SavedDataSchemaMigrator.MigrationStatus.MIGRATED,
                        SavedDataSchemaMigrator.migrate(fixture.type(), fixture.payload().copy()).status(), entry.getKey());
            } else if (entry.getKey().startsWith("FP1")) {
                assertFalse(ClassicProgressionSavedData.load(fixture.payload()).operational(), entry.getKey());
            } else {
                assertThrows(SavedDataMigrationException.class,
                        () -> SavedDataSchemaMigrator.migrate(fixture.type(), fixture.payload().copy()), entry.getKey());
            }
            CompoundTag outer = new CompoundTag();
            outer.put("data", fixture.payload());
            NbtUtils.addCurrentDataVersion(outer);
            Path directory = Files.createDirectories(OUTPUT.resolve(entry.getKey()));
            NbtIo.writeCompressed(outer, directory.resolve(fixture.type().fileName()).toFile());
        }
    }

    private static CompoundTag preRelease(CompoundTag current) {
        CompoundTag old = current.copy();
        old.remove(ManagedSavedDataType.FIRST_STATION_WARP_KEY);
        old.putString("format_epoch", ManagedSavedDataType.STATION_PRE_RELEASE_EPOCH);
        return old;
    }

    private static int sizeOf(CompoundTag tag) {
        return io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize.uncompressedBytes(tag);
    }

    private record Fixture(ManagedSavedDataType type, CompoundTag payload, boolean migrates) {
    }
}
