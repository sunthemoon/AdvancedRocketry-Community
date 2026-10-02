package io.github.sunthemoon.advancedrocketrycommunity.material.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** ADR-061 section 3.5 and ADR-063 section 4: the server switch placement decodes only known, flat switches. */
class SwitchPlacementTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void aKnownSwitchRoundTripsAsOneFlatField() {
        SwitchPlacement placement = SwitchPlacement.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString("{\"switch\":\"overworld_ores\"}"))
                .getOrThrow(false, message -> { });
        assertEquals(SwitchPlacement.OVERWORLD_ORES, placement.name());
        JsonElement encoded = SwitchPlacement.CODEC.encodeStart(JsonOps.INSTANCE, placement)
                .getOrThrow(false, message -> { });
        assertEquals(JsonParser.parseString("{\"switch\":\"overworld_ores\"}"), encoded);
        // Until the COMMON config loads, the switch reads its default (on).
        assertTrue(placement.enabled());
    }

    @Test
    void unknownOrMissingSwitchesFailToDecode() {
        assertTrue(SwitchPlacement.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"switch\":\"moon_ores\"}"))
                .error().isPresent());
        assertTrue(SwitchPlacement.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).error().isPresent());
        assertThrows(IllegalArgumentException.class, () -> SwitchPlacement.of("moon_ores"));
    }
}
