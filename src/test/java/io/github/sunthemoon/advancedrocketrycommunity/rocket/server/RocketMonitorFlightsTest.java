package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketMonitorFlightsTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void catalogFingerprintIsIndependentOfMapInsertionOrderButNotContent() {
        var left = JsonParser.parseString("{\"z\":[{\"b\":2,\"a\":1}],\"a\":true}");
        var right = JsonParser.parseString("{\"a\":true,\"z\":[{\"a\":1,\"b\":2}]}");
        assertEquals(RocketMonitorFlights.canonical(left).toString(), RocketMonitorFlights.canonical(right).toString());
        right.getAsJsonObject().addProperty("a", false);
        assertNotEquals(RocketMonitorFlights.canonical(left).toString(), RocketMonitorFlights.canonical(right).toString());
        assertEquals("{\"z\":[{\"b\":2,\"a\":1}],\"a\":true}", left.toString(), "Fingerprinting mutated catalog input");
    }
}
