package io.github.sunthemoon.advancedrocketrycommunity.satellite.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class SatelliteComponentDefinitionTest {
    @Test
    void eachRoleNeedsExactlyItsOwnStat() {
        var power = SatelliteComponentDefinition.decode(json(
                "{\"schema_version\":1,\"item\":\"a:panel\",\"role\":\"power\",\"power_generation\":40}"));
        assertEquals(40, power.powerGeneration());
        assertEquals(0, power.batteryCapacity());
        var primary = SatelliteComponentDefinition.decode(json(
                "{\"schema_version\":1,\"item\":\"a:drill\",\"role\":\"primary\",\"kind\":\"asteroid_miner\",\"primary_rating\":10}"));
        assertEquals(Optional.of(SatelliteKind.ASTEROID_MINER), primary.kind());
        var chassis = SatelliteComponentDefinition.decode(json(
                "{\"schema_version\":1,\"item\":\"a:chassis\",\"role\":\"chassis\"}"));
        assertEquals(0, chassis.value());

        for (String invalid : List.of(
                "{\"schema_version\":1,\"item\":\"a:x\",\"role\":\"power\"}",
                "{\"schema_version\":1,\"item\":\"a:x\",\"role\":\"power\",\"power_generation\":0}",
                "{\"schema_version\":1,\"item\":\"a:x\",\"role\":\"power\",\"power_generation\":1001}",
                "{\"schema_version\":1,\"item\":\"a:x\",\"role\":\"power\",\"power_generation\":4,\"battery_capacity\":1}",
                "{\"schema_version\":1,\"item\":\"a:x\",\"role\":\"cargo\",\"cargo_stacks\":28}",
                "{\"schema_version\":1,\"item\":\"a:x\",\"role\":\"cargo\",\"cargo_stacks\":2.5}",
                "{\"schema_version\":1,\"item\":\"a:x\",\"role\":\"chassis\",\"power_generation\":1}",
                "{\"schema_version\":1,\"item\":\"a:x\",\"role\":\"primary\",\"primary_rating\":10}",
                "{\"schema_version\":1,\"item\":\"a:x\",\"role\":\"primary\",\"kind\":\"data\",\"primary_rating\":10}",
                "{\"schema_version\":1,\"item\":\"a:x\",\"role\":\"power\",\"kind\":\"solar\",\"power_generation\":4}",
                "{\"schema_version\":1,\"item\":\"a:x\",\"role\":\"power\",\"power_generation\":4,\"extra\":1}",
                "{\"schema_version\":2,\"item\":\"a:x\",\"role\":\"chassis\"}",
                "{\"schema_version\":1,\"item\":\"a:x\",\"role\":\"engine\"}",
                "{\"schema_version\":1,\"item\":\"not an id\",\"role\":\"chassis\"}")) {
            assertThrows(IllegalArgumentException.class, () -> SatelliteComponentDefinition.decode(json(invalid)), invalid);
        }
    }

    @Test
    void catalogRejectsUnknownOrDuplicateItemsAndTooManyFiles() {
        Map<ResourceLocation, JsonElement> resources = new LinkedHashMap<>();
        resources.put(id("a:one"), json("{\"schema_version\":1,\"item\":\"a:panel\",\"role\":\"power\",\"power_generation\":4}"));
        resources.put(id("a:two"), json("{\"schema_version\":1,\"item\":\"a:panel\",\"role\":\"battery\",\"battery_capacity\":10}"));
        assertTrue(SatelliteComponentCatalog.decode(resources, item -> true).error().orElseThrow().message()
                .contains("defined twice"));
        resources.remove(id("a:two"));
        assertTrue(SatelliteComponentCatalog.decode(resources, item -> false).error().isPresent());
        assertEquals(1, SatelliteComponentCatalog.decode(resources, item -> true).result().orElseThrow().size());

        Map<ResourceLocation, JsonElement> many = new LinkedHashMap<>();
        for (int index = 0; index <= 64; index++) {
            many.put(id("a:c" + index), json("{\"schema_version\":1,\"item\":\"a:i" + index + "\",\"role\":\"chassis\"}"));
        }
        assertTrue(SatelliteComponentCatalog.decode(many, item -> true).error().isPresent());
        many.remove(id("a:c64"));
        assertEquals(64, SatelliteComponentCatalog.decode(many, item -> true).result().orElseThrow().size());
    }

    private static JsonElement json(String raw) {
        return JsonParser.parseString(raw);
    }

    private static ResourceLocation id(String raw) {
        return ResourceLocation.tryParse(raw);
    }
}
