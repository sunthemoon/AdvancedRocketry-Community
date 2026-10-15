package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Production-migrated V fixtures for a separate pre-W44 jar; never relies on another test's execution order. */
class FirstEventDowngradeFixturesTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void exportsTheFourProductionMigratedRoots() throws IOException {
        new FirstEventNativeFixturesTest().writesEveryPreStartFixtureWithItsExpectedMigratorVerdict();
        for (String id : List.of("FM11a", "FM11b", "FW10a", "FW10b")) {
            var type = id.startsWith("FM") ? ManagedSavedDataType.ROCKET_TRANSFERS : ManagedSavedDataType.STATIONS;
            CompoundTag old = NbtIo.readCompressed(FirstEventNativeFixturesTest.OUTPUT.resolve(id)
                    .resolve(type.fileName()).toFile()).getCompound("data");
            var result = SavedDataSchemaMigrator.migrate(type, old);
            assertEquals(SavedDataSchemaMigrator.MigrationStatus.MIGRATED, result.status(), id);
            CompoundTag current = result.payload();
            assertEquals(SavedDataSchemaMigrator.MigrationStatus.CURRENT,
                    SavedDataSchemaMigrator.migrate(type, current).status(), id);
            assertEquals("EMPTY", current.getCompound(id.startsWith("FM")
                    ? ManagedSavedDataType.FIRST_MOON_LANDING_KEY : ManagedSavedDataType.FIRST_STATION_WARP_KEY)
                    .getString("state"), id);
            for (String key : List.of("transfers", "reservations", "warp_energy")) {
                if (old.contains(key)) { assertEquals(old.get(key), current.get(key), id + ":" + key); }
            }
            CompoundTag outer = new CompoundTag();
            outer.put("data", current); NbtUtils.addCurrentDataVersion(outer);
            Path directory = Files.createDirectories(Path.of("build/w44l-downgrade-fixtures", id));
            NbtIo.writeCompressed(outer, directory.resolve(type.fileName()).toFile());
        }
    }
}
