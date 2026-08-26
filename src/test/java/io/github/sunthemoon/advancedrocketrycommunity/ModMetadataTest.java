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
            assertTrue(metadata.contains("features={java_version=\"[17,)\"}"));
            assertFalse(metadata.contains("${"), "resource expansion must resolve every placeholder");
        }
    }
}
