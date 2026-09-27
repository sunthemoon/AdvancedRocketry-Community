package io.github.sunthemoon.advancedrocketrycommunity.celestial.environment;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialEnvironmentService;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EnvironmentalDefinitionTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void optInRoundTripsInJsonAndNbt() {
        var body = PlanetaryContent.definitions().get(0);
        assertTrue(body.environmentEffects());
        var json = CelestialBodyDefinition.CODEC.encodeStart(JsonOps.INSTANCE, body).result().orElseThrow();
        assertEquals(body, CelestialBodyDefinition.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow());
        var nbt = CelestialBodyDefinition.CODEC.encodeStart(NbtOps.INSTANCE, body).result().orElseThrow();
        assertEquals(body, CelestialBodyDefinition.CODEC.parse(NbtOps.INSTANCE, nbt).result().orElseThrow());
    }

    @Test void omittedNewFieldAndLegacyDefinitionsStayOptedOut() {
        var json = CelestialBodyDefinition.CODEC.encodeStart(JsonOps.INSTANCE,
                PlanetaryContent.definitions().get(0)).result().orElseThrow().getAsJsonObject();
        json.remove("environment_effects");
        assertFalse(CelestialBodyDefinition.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow().environmentEffects());
        for (var body : CelestialDefaults.definitions()) {
            assertFalse(body.environmentEffects());
            var old = body.encodeLegacy(JsonOps.INSTANCE).result().orElseThrow();
            assertFalse(CelestialBodyDefinition.CODEC.parse(JsonOps.INSTANCE, old).result().orElseThrow().environmentEffects());
        }
    }

    @Test void onlyLiteralBooleansAreAcceptedAndLegacyCannotSilentlyIgnoreTheField() {
        var original = PlanetaryContent.definitions().get(0);
        for (String value : new String[]{"null", "1", "0", "\"true\"", "{}", "[]"}) {
            var json = CelestialBodyDefinition.CODEC.encodeStart(JsonOps.INSTANCE, original).result().orElseThrow().getAsJsonObject();
            json.add("environment_effects", JsonParser.parseString(value));
            assertTrue(CelestialBodyDefinition.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent(), value);
        }
        for (boolean flag : new boolean[]{true, false}) {
            var old = CelestialDefaults.definitions().get(0).encodeLegacy(JsonOps.INSTANCE).result().orElseThrow().getAsJsonObject();
            old.addProperty("environment_effects", flag);
            assertTrue(CelestialBodyDefinition.CODEC.parse(JsonOps.INSTANCE, old).error().isPresent());
        }
    }

    @Test void legacyEncoderRefusesLossOfOnlyTheNewFlag() {
        var old = CelestialDefaults.definitions().get(0);
        var optedIn = new CelestialBodyDefinition(old.id(), old.parentId(), old.levelKey(), old.gravityMultiplier(),
                old.atmosphere(), old.orbit(), old.visualProfile(), old.capabilities(), old.solarIntensity(), old.radiation(), true);
        assertTrue(optedIn.encodeLegacy(JsonOps.INSTANCE).error().isPresent());
    }

    @Test void exposureFollowsReloadAndUnresolvedLevelsDoNotInventSurfaceConditions() {
        var manager = new CelestialCatalogManager();
        var environments = new CelestialEnvironmentService(manager);
        assertEquals(EnvironmentalConditions.DISABLED, environments.surfaceExposure(PlanetaryContent.level(PlanetaryContent.MARS)));
        var bodies = new ArrayList<>(CelestialDefaults.definitions());
        bodies.addAll(PlanetaryContent.definitions());
        assertTrue(manager.applyCandidate(CelestialCatalog.create(bodies)));
        assertTrue(environments.surfaceExposure(PlanetaryContent.level(PlanetaryContent.MARS)).enabled());
        assertFalse(environments.surfaceExposure(Level.OVERWORLD).enabled());
        assertFalse(environments.surfaceExposure(CelestialIds.MOON_LEVEL).enabled());
        assertFalse(environments.surfaceExposure(CelestialIds.SPACE_LEVEL).enabled());
        assertFalse(environments.surfaceExposure(PlanetaryContent.level(PlanetaryContent.GAS_GIANT)).enabled());
        var mars = PlanetaryContent.definitions().get(0);
        bodies.add(new CelestialBodyDefinition(ModIdentity.id("ambiguous"), mars.parentId(), mars.levelKey(),
                mars.gravityMultiplier(), mars.atmosphere(), mars.orbit(), mars.visualProfile(), mars.capabilities(),
                mars.solarIntensity(), mars.radiation(), true));
        assertTrue(manager.applyCandidate(CelestialCatalog.create(bodies)));
        assertFalse(environments.surfaceExposure(mars.levelKey().orElseThrow()).enabled());
        manager.clear();
        assertFalse(environments.surfaceExposure(mars.levelKey().orElseThrow()).enabled());
    }
}
