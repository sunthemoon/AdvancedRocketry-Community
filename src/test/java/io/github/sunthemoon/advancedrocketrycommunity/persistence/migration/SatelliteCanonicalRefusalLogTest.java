package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.CanonicalSatelliteFixtures;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SatelliteCanonicalRefusalLogTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @Test void currentLegacyAndManyRecordsHaveOneIdenticalBoundedDiagnostic() throws Exception {
        String expected = null;
        for (int count : new int[]{1, 1000}) {
            for (boolean legacy : new boolean[]{false, true}) {
                Path world = Files.createDirectory(directory.resolve(count + "-" + legacy));
                Path data = Files.createDirectory(world.resolve("data"));
                CompoundTag root = CanonicalSatelliteFixtures.recordRoot(4090, count);
                CompoundTag outer = new CompoundTag();
                outer.put("data", legacy ? CanonicalSatelliteFixtures.legacy(root) : root);
                Path source = data.resolve(ManagedSavedDataType.SATELLITE_MISSIONS.fileName());
                NbtIo.writeCompressed(outer, source.toFile());
                byte[] before = Files.readAllBytes(source);
                var failure = assertThrows(SavedDataMigrationException.class, () -> new WorldDataMigrationService().migrate(world));
                var events = log(failure, true);
                assertEquals(1, events.size());
                var event = events.get(0);
                assertEquals(Level.ERROR, event.getLevel());
                assertNull(event.getThrown());
                String line = event.getMessage().getFormattedMessage();
                assertTrue(line.length() <= 512, line);
                assertFalse(line.contains("\n"));
                assertFalse(line.contains("\r"));
                assertTrue(line.contains("record=mission id=00000000-0000-001f-0000-000000000000 bytes=4280 bound=4096"));
                assertTrue(line.contains("back up the complete world"));
                assertTrue(line.contains("repair the named record"));
                if (expected == null) { expected = line; }
                else { assertEquals(expected, line); }
                assertArrayEquals(before, Files.readAllBytes(source));
            }
        }
    }

    @Test void unrelatedRefusalStillUsesItsExistingDiagnosticPath() {
        assertTrue(log(new SavedDataMigrationException(MigrationDiagnosticId.INVALID_SCHEMA, "unrelated"), false).isEmpty());
    }

    private static ArrayList<LogEvent> log(SavedDataMigrationException failure, boolean handled) {
        var events = new ArrayList<LogEvent>();
        var context = (org.apache.logging.log4j.core.LoggerContext) org.apache.logging.log4j.LogManager.getContext(false);
        var root = context.getConfiguration().getRootLogger();
        var appender = new org.apache.logging.log4j.core.appender.AbstractAppender("canonical-refusal", null, null, true,
                org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY) {
            @Override public void append(LogEvent event) { events.add(event.toImmutable()); }
        };
        appender.start();
        root.addAppender(appender, null, null);
        context.updateLoggers();
        try { assertEquals(handled, BetaWorldMigrationEvents.logSatelliteBoundsRefusal(failure)); }
        finally { root.removeAppender(appender.getName()); context.updateLoggers(); appender.stop(); }
        return events;
    }
}
