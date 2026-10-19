package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class V180RocketCargoResourcesTest {
    @Test void currentTagRetainsEveryHistoricalValueInOrderAndAddsOnlyTank() throws Exception {
        String relative = "/resources/data/advancedrocketrycommunity/tags/blocks/rocket_movable.json";
        var old = JsonParser.parseString(Files.readString(Path.of("src/generated/v0.5" + relative))).getAsJsonObject();
        var current = JsonParser.parseString(Files.readString(Path.of("src/generated/v1.8" + relative))).getAsJsonObject();
        var expected = new ArrayList<String>(); old.getAsJsonArray("values").forEach(value -> expected.add(value.getAsString()));
        assertTrue(expected.contains("minecraft:chest")); assertTrue(expected.contains("minecraft:barrel"));
        expected.add("advancedrocketrycommunity:pressurized_tank");
        var actual = new ArrayList<String>(); current.getAsJsonArray("values").forEach(value -> actual.add(value.getAsString()));
        assertEquals(expected, actual);
        assertFalse(current.has("replace") && current.get("replace").getAsBoolean());
        assertFalse(actual.contains("minecraft:trapped_chest"));
    }
}
