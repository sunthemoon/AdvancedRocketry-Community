package io.github.sunthemoon.advancedrocketrycommunity;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModMetadataTest {
    @Test
    void processedMetadataContainsApprovedIdentity() throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("META-INF/mods.toml")) {
            assertNotNull(stream, "processed mods.toml must be on the test runtime classpath");
            String metadata = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(metadata.contains("modId=\"advancedrocketrycommunity\""));
            assertTrue(metadata.contains("displayName=\"Advanced Rocketry: Community Edition\""));
            assertTrue(metadata.contains("license=\"MIT\""));
            assertTrue(metadata.contains("version=\"1.20.1-1.7.0-dev\""));
            assertTrue(metadata.contains("This v1.7.0 development build"));
            assertTrue(metadata.contains("This v1.7.0 development build adds the endgame framework to the satellite "
                    + "and resource mission development baseline: per-system switches, an authority matrix, a "
                    + "protection chain with operator zones and a cancelable effect event, and a versioned endgame "
                    + "root with an endpoint index. Endgame devices and release validation remain in progress"));
            assertTrue(metadata.contains("features={java_version=\"[17,)\"}"));
            assertTrue(metadata.contains("modId=\"jei\""));
            assertTrue(metadata.contains("mandatory=false"));
            assertTrue(metadata.contains("versionRange=\"[15.56.0.205,16)\""));
            assertTrue(metadata.contains("side=\"CLIENT\""));
            assertFalse(metadata.contains("${"), "resource expansion must resolve every placeholder");
        }
    }
}
