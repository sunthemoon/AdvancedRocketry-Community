package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowLoaderS6NamedSetupTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void setupWaitsForBothNativeChunkAndEntitySectionUnload() {
        assertFalse(EscrowLoaderS6NamedSetup.sourceReady(false, false, false));
        assertFalse(EscrowLoaderS6NamedSetup.sourceReady(false, false, true));
        assertFalse(EscrowLoaderS6NamedSetup.sourceReady(false, true, false));
        assertTrue(EscrowLoaderS6NamedSetup.sourceReady(false, true, true));
    }

    @Test void measuredFillAndEveryRestartRejectEitherKindOfSourceReload() {
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6NamedSetup.sourceReady(true, false, false));
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6NamedSetup.sourceReady(true, false, true));
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6NamedSetup.sourceReady(true, true, false));
        assertTrue(EscrowLoaderS6NamedSetup.sourceReady(true, true, true));
    }

    @Test void retainedUnadmittedImagesStaySetupNotAnO3jObservation() throws Exception {
        String replay = System.getenv("ARCE_S6_NAMED_SETUP_REPLAY");
        if (replay == null) { return; }
        int count = 0;
        for (String file : replay.split(";")) {
            var row = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of(file))).getAsJsonObject();
            assertEquals("FAILED", row.get("status").getAsString());
            for (var item : row.getAsJsonArray("starts")) {
                var image = item.getAsJsonObject().getAsJsonObject("image");
                assertEquals("KNOWN", image.get("read_status").getAsString());
                var root = image.getAsJsonObject("native").getAsJsonObject("ledger");
                assertEquals(0, root.get("issued_through").getAsInt());
                assertEquals(0, root.getAsJsonArray("records").size());
                assertEquals(1, root.get("writer_generation").getAsLong());
                assertFalse(EscrowLoaderS6NamedSetup.sourceReady(false, false, false));
                assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6NamedSetup.sourceReady(true, false, false));
                count++;
            }
        }
        assertEquals(6, count);
        System.out.println("S6 named setup retained unadmitted-image predicate replays=" + count);
    }
}
