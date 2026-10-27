package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.RegistryWriterHarness.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * ADR-074 item 6: a normal running server never waits for the registry writer; only GameTest and release-test servers
 * may. Research's failure injection fails one write on the bound target, never a fake path, and starts no streak.
 */
@Timeout(120)
final class SatelliteRegistryTestAwaitTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void aNormalServerRefusesEveryAwaitAndAReleaseTestServerMayAwait() throws Exception {
        var server = live(); var data = registry(server);
        Path file = directory.resolve(REGISTRY.fileName());
        launch(data);
        assertThrows(IllegalStateException.class, () -> data.awaitForTests(server));
        assertThrows(IllegalStateException.class, () -> data.flush(file), "A live registry awaited on a normal server");
        assertThrows(IllegalStateException.class, () -> data.writerForTests().await());
        assertTrue(data.isDirty(), "A refused await wrote nothing");
        System.setProperty(SatelliteRegistryTestAwait.PROPERTY, "true");
        try {
            assertTrue(SatelliteRegistryTestAwait.allowed(server));
            data.flush(file);
            assertFalse(data.isDirty());
            long epoch = data.saveEpoch();
            byte[] before = Files.readAllBytes(file);
            launch(data);
            try (var errors = new SatelliteRegistryCapacityTest.ErrorLines();
                 var info = new SatelliteRegistryLoggingTest.InfoLines()) {
                assertEquals(SatelliteRegistryProof.Outcome.FAILED, data.writerForTests().failOnce(file));
                assertTrue(errors.lines().isEmpty(), "The injected failure started an ERROR streak: " + errors.lines());
                assertTrue(info.lines().stream().anyMatch(line -> line.contains("ARCE_SATELLITE_REGISTRY_INJECTED_FAILURE outcome=FAILED")));
            }
            assertArrayEquals(before, Files.readAllBytes(file), "The injected failure replaced the file");
            assertEquals(epoch, data.saveEpoch()); assertTrue(data.isDirty());
            assertThrows(IllegalArgumentException.class, () -> data.writerForTests().failOnce(directory.resolve("other.dat")),
                    "The seam may not bind the live writer to another target");
            data.flush(file);
            assertEquals(epoch + 1, data.saveEpoch()); assertFalse(data.isDirty());
        } finally {
            System.clearProperty(SatelliteRegistryTestAwait.PROPERTY);
        }
        assertFalse(SatelliteRegistryTestAwait.allowed(server));
    }
}
