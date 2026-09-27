package io.github.sunthemoon.advancedrocketrycommunity.celestial;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.AtmosphereDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialCapabilities;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.OrbitDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogDecoder;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Optional;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CelestialSchemaV2Test {
    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void actualLegacyResourcesPreserveEveryValueAndAddOnlyExplicitDefaults() throws Exception {
        for (var expected : CelestialDefaults.definitions()) {
            String resource = "data/advancedrocketrycommunity/celestial_bodies/" + expected.id().getPath() + ".json";
            try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(stream, resource);
                var old = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                assertFalse(old.has("schema_version"));
                assertEquals(expected, decode(old));
                old.addProperty("schema_version", 1);
                assertEquals(expected, decode(old));
                assertEquals(1.0, expected.solarIntensity());
                assertEquals(0.0, expected.radiation());
                assertEquals(!expected.id().equals(CelestialIds.SPACE_ID), expected.capabilities().landable());
                assertTrue(expected.capabilities().orbitable());
                assertFalse(expected.capabilities().gasGiant());
            }
        }
    }

    @Test
    void gasGiantRoundTripsThroughJsonAndNbtWithoutManufacturingALevel() {
        var gas = gasGiant();
        var json = encoded(gas);
        assertEquals(2, json.get("schema_version").getAsInt());
        assertFalse(json.has("level"));
        assertEquals(gas, decode(json));
        var nbt = CelestialBodyDefinition.CODEC.encodeStart(NbtOps.INSTANCE, gas).result().orElseThrow();
        assertEquals(gas, CelestialBodyDefinition.CODEC.parse(NbtOps.INSTANCE, nbt).result().orElseThrow());
        assertFalse(gas.supportsSurfaceArrival());
    }

    @Test
    void optionalFieldsAreAbsentRatherThanNullOrMalformed() {
        for (String field : new String[] {"parent", "level"}) {
            for (JsonElement invalid : new JsonElement[] {JsonNull.INSTANCE, JsonParser.parseString("7"),
                    JsonParser.parseString("{}"), JsonParser.parseString("\"Not a resource\"")}) {
                var json = encoded(CelestialDefaults.definitions().get(1));
                json.add(field, invalid);
                rejected(json);
            }
        }
    }

    @Test
    void explicitNullCannotMasqueradeAsAnAbsentOptionalOrLegacyField() {
        var unmapped = encoded(gasGiant());
        unmapped.add("level", JsonNull.INSTANCE);
        rejected(unmapped);
        var root = encoded(CelestialDefaults.definitions().get(0));
        root.add("parent", JsonNull.INSTANCE);
        rejected(root);
        for (String field : new String[] {"schema_version", "parent", "capabilities", "solar_intensity", "radiation"}) {
            var old = legacyEarth();
            old.add(field, JsonNull.INSTANCE);
            rejected(old);
        }
    }

    @Test
    void schemaMustBeAnExplicitSupportedIntegerAndNewIntentCannotBeLost() {
        for (String version : new String[] {"null", "true", "\"2\"", "2.0", "1.5", "2e0", "-1", "0", "3", "9223372036854775808"}) {
            var json = encoded(gasGiant());
            json.add("schema_version", JsonParser.parseString(version));
            rejected(json);
        }
        for (String field : new String[] {"capabilities", "solar_intensity", "radiation"}) {
            var old = legacyEarth();
            old.add(field, encoded(gasGiant()).get(field));
            rejected(old);
            old.addProperty("schema_version", 1);
            rejected(old);
        }
    }

    @Test
    void requiredAndUnknownFieldsAreCheckedAtEverySchema2Object() {
        for (String field : new String[] {"id", "gravity_multiplier", "atmosphere", "orbit", "visual_profile",
                "capabilities", "solar_intensity", "radiation"}) {
            var json = encoded(gasGiant());
            json.remove(field);
            rejected(json);
        }
        for (String nested : new String[] {"", "atmosphere", "orbit", "capabilities"}) {
            var json = encoded(gasGiant());
            (nested.isEmpty() ? json : json.getAsJsonObject(nested)).addProperty("misspelled", 1);
            rejected(json);
        }
        var old = legacyEarth();
        old.addProperty("old_ignored_note", "still accepted");
        old.getAsJsonObject("atmosphere").addProperty("old_ignored_note", 1);
        assertEquals(CelestialDefaults.definitions().get(0), decode(old));
    }

    @Test
    void capabilityCombinationsAndBooleanTypesAreValidated() {
        var gas = encoded(gasGiant());
        gas.addProperty("level", "minecraft:overworld");
        rejected(gas);
        var noMapping = encoded(gasGiant());
        noMapping.getAsJsonObject("capabilities").addProperty("gas_giant", false);
        noMapping.getAsJsonObject("capabilities").addProperty("landable", true);
        rejected(noMapping);
        for (String field : new String[] {"landable", "orbitable", "gas_giant"}) {
            for (String invalid : new String[] {"0", "1", "\"true\"", "null"}) {
                var json = encoded(gasGiant());
                json.getAsJsonObject("capabilities").add(field, JsonParser.parseString(invalid));
                rejected(json);
            }
        }
        var json = encoded(gasGiant());
        json.getAsJsonObject("atmosphere").addProperty("breathable", 1);
        rejected(json);
    }

    @Test
    void finiteRangesRejectInvalidNumbersInsteadOfTruncatingOrCoercing() {
        for (String field : new String[] {"gravity_multiplier", "solar_intensity", "radiation"}) {
            for (String invalid : new String[] {"true", "\"1\"", "-0.001", "1e999", "null"}) {
                var json = encoded(gasGiant());
                json.add(field, JsonParser.parseString(invalid));
                rejected(json);
            }
            var tooLarge = encoded(gasGiant());
            tooLarge.addProperty(field, switch (field) { case "gravity_multiplier" -> 4.001; case "solar_intensity" -> 16.001; default -> 1.001; });
            rejected(tooLarge);
        }
        var json = encoded(gasGiant());
        json.addProperty("gravity_multiplier", Double.NaN);
        rejected(json);
        for (String field : new String[] {"distance", "period_ticks"}) {
            for (String invalid : new String[] {"1.5", "1.0", "true", "\"1\"", "1e50"}) {
                json = encoded(gasGiant());
                json.getAsJsonObject("orbit").add(field, JsonParser.parseString(invalid));
                rejected(json);
            }
        }
    }

    @Test
    void numberEndpointsAndProgrammaticConstructionHaveTheSameInvariants() {
        var gas = gasGiant();
        for (double[] values : new double[][] {{0, 0, 0}, {4, 16, 1}}) {
            var value = new CelestialBodyDefinition(gas.id(), gas.parentId(), gas.levelKey(), values[0],
                    gas.atmosphere(), gas.orbit(), gas.visualProfile(), gas.capabilities(), values[1], values[2]);
            assertEquals(value, decode(encoded(value)));
        }
        assertThrows(IllegalArgumentException.class, () -> new AtmosphereDefinition(0, true, 10, ModIdentity.id("vacuum")));
        assertThrows(IllegalArgumentException.class, () -> new AtmosphereDefinition(11, false, 10, ModIdentity.id("vacuum")));
        assertThrows(IllegalArgumentException.class, () -> new AtmosphereDefinition(0, false, Double.NaN, ModIdentity.id("vacuum")));
        assertThrows(IllegalArgumentException.class, () -> new OrbitDefinition(0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new OrbitDefinition(-1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new OrbitDefinition(1, 1, 181));
        assertThrows(IllegalArgumentException.class, () -> new CelestialCapabilities(true, true, true));
        assertThrows(IllegalArgumentException.class, () -> new CelestialBodyDefinition(gas.id(), gas.parentId(),
                Optional.of(Level.OVERWORLD), 1, gas.atmosphere(), gas.orbit(), gas.visualProfile(), gas.capabilities(), 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new CelestialBodyDefinition(gas.id(), gas.parentId(),
                Optional.empty(), 1, gas.atmosphere(), gas.orbit(), gas.visualProfile(), new CelestialCapabilities(true, true, false), 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new CelestialBodyDefinition(gas.id(), gas.parentId(),
                gas.levelKey(), Double.NaN, gas.atmosphere(), gas.orbit(), gas.visualProfile(), gas.capabilities(), 1, 0));
    }

    @Test
    void legacyEncoderPreservesAuditedFormatAndRefusesMetadataLoss() {
        for (var body : CelestialDefaults.definitions()) {
            var old = body.encodeLegacy(JsonOps.INSTANCE).result().orElseThrow();
            assertFalse(old.getAsJsonObject().has("schema_version"));
            assertEquals(body, decode(old));
        }
        assertTrue(gasGiant().encodeLegacy(JsonOps.INSTANCE).error().isPresent());
        var earth = CelestialDefaults.definitions().get(0);
        var changed = new CelestialBodyDefinition(earth.id(), earth.parentId(), earth.levelKey(), earth.gravityMultiplier(),
                earth.atmosphere(), earth.orbit(), earth.visualProfile(), new CelestialCapabilities(false, true, false), 1, 0);
        assertTrue(changed.encodeLegacy(JsonOps.INSTANCE).error().isPresent());
    }

    @Test
    void unmappedBodiesDoNotEnterTheLevelIndexOrBreakTheFixedBaseline() {
        var values = new ArrayList<>(CelestialDefaults.definitions());
        values.add(gasGiant());
        var catalog = CelestialCatalog.create(values).flatMap(CelestialCatalog::requireFixedBaseline).result().orElseThrow();
        assertEquals(4, catalog.size());
        assertTrue(catalog.get(gasGiant().id()).orElseThrow().levelKey().isEmpty());
        assertEquals(1, catalog.candidatesForLevel(CelestialIds.SPACE_LEVEL).size());
        assertEquals(CelestialIds.EARTH_ID, catalog.forLevel(Level.OVERWORLD).orElseThrow().id());
        var resources = new LinkedHashMap<ResourceLocation, JsonElement>();
        values.forEach(body -> resources.put(body.id(), encoded(body)));
        assertEquals(values.size(), CelestialCatalogDecoder.decode(resources).result().orElseThrow().size());
        resources.put(ModIdentity.id("mismatched"), encoded(gasGiant()));
        assertTrue(CelestialCatalogDecoder.decode(resources).error().isPresent());
    }

    @Test
    void graphAndIdentityBoundsAreNotRelaxedByOptionalMappings() {
        var gas = encoded(gasGiant());
        gas.addProperty("id", "t:" + "x".repeat(127));
        rejected(gas);
        gas = encoded(gasGiant());
        gas.addProperty("parent", gasGiant().id().toString());
        rejected(gas);
        var child = gasGiant();
        assertTrue(CelestialCatalog.create(java.util.List.of(child)).error().isPresent());
        var earth = encoded(CelestialDefaults.definitions().get(0));
        earth.remove("level");
        earth.getAsJsonObject("capabilities").addProperty("landable", false);
        var values = new ArrayList<>(CelestialDefaults.definitions());
        values.set(0, decode(earth));
        assertTrue(CelestialCatalog.create(values).flatMap(CelestialCatalog::requireFixedBaseline).error().isPresent());
    }

    static CelestialBodyDefinition gasGiant() {
        return new CelestialBodyDefinition(ModIdentity.id("test_gas"), Optional.of(CelestialIds.EARTH_ID), Optional.empty(),
                2.5, new AtmosphereDefinition(10, false, 150, ModIdentity.id("test_gas")),
                new OrbitDefinition(100, 1_000, 0), ModIdentity.id("test_gas"),
                new CelestialCapabilities(false, true, true), 0.04, 0.5);
    }

    static JsonObject encoded(CelestialBodyDefinition body) {
        return CelestialBodyDefinition.CODEC.encodeStart(JsonOps.INSTANCE, body).result().orElseThrow().getAsJsonObject();
    }

    private static JsonObject legacyEarth() {
        return CelestialDefaults.definitions().get(0).encodeLegacy(JsonOps.INSTANCE).result().orElseThrow().getAsJsonObject();
    }

    private static CelestialBodyDefinition decode(JsonElement json) {
        return CelestialBodyDefinition.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
    }

    private static void rejected(JsonElement json) {
        assertTrue(CelestialBodyDefinition.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent(), json::toString);
    }
}
