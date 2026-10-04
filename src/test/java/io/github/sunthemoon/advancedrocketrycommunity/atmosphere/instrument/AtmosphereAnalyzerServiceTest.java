package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.serialization.DataResult;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.BreathabilityState;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.context.BodyContext;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.AtmosphereDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

class AtmosphereAnalyzerServiceTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static CelestialCatalog catalog() { return CelestialCatalog.create(CelestialDefaults.definitions()).result().orElseThrow(); }

    @Test void actualSurfaceProjectionUsesKnownAmbientNotSafetyFallback() {
        var ambient = AtmosphereAnalyzerService.project(catalog(), BodyContext.surface(CelestialIds.EARTH_ID), Level.OVERWORLD).orElseThrow();
        assertEquals(1, ambient.values().pressure());
        assertEquals(288, ambient.values().temperatureKelvin());
        assertTrue(AtmosphereAnalyzerService.project(catalog(), BodyContext.surface(CelestialIds.EARTH_ID), CelestialIds.MOON_LEVEL).isEmpty());
        assertTrue(AtmosphereAnalyzerService.project(catalog(), BodyContext.mission(CelestialIds.MOON_ID, UUID.randomUUID()), CelestialIds.MOON_LEVEL).isEmpty());
    }

    @Test void stationOrbitUsesActualSpaceAndKeepsLogicalOrbitBody() {
        var context = BodyContext.stationOrbit(CelestialIds.EARTH_ID, UUID.randomUUID());
        var ambient = AtmosphereAnalyzerService.project(catalog(), context, CelestialIds.SPACE_LEVEL).orElseThrow();
        var result = AtmosphereAnalyzerService.compose(context, ambient, BreathabilityState.VACUUM, false);
        assertEquals(CelestialIds.EARTH_ID.toString(), result.bodyId().orElseThrow());
        assertEquals(CelestialIds.SPACE_ID.toString(), result.ambientBodyId().orElseThrow());
        assertEquals(0, result.ambient().orElseThrow().pressure());
        assertEquals(3, result.ambient().orElseThrow().temperatureKelvin());
        assertEquals(AnalyzerReading.Locus.ORBIT, result.locus().orElseThrow());
    }

    @Test void suppliedAndPendingDoNotReplaceAmbientNumbers() {
        var context = BodyContext.surface(CelestialIds.MOON_ID);
        var ambient = AtmosphereAnalyzerService.project(catalog(), context, CelestialIds.MOON_LEVEL).orElseThrow();
        var supplied = AtmosphereAnalyzerService.compose(context, ambient, BreathabilityState.BREATHABLE, true);
        assertTrue(supplied.supplied());
        assertEquals(0, supplied.ambient().orElseThrow().pressure());
        var pending = AtmosphereAnalyzerService.compose(context, ambient, BreathabilityState.PENDING, true);
        assertFalse(pending.supplied());
        assertEquals(AnalyzerReading.State.PENDING, pending.state());
        assertEquals(supplied.ambient(), pending.ambient());
    }

    @Test void positivePressureNonBreathableDoesNotBecomeVacuumMetadata() {
        var ambient = new AtmosphereAnalyzerService.AmbientIdentity("test:hostile", new AnalyzerReading.Ambient(2.3456789D, 333.123D));
        var result = AtmosphereAnalyzerService.compose(BodyContext.surface(CelestialIds.MOON_ID), ambient, BreathabilityState.VACUUM, false);
        assertEquals(AnalyzerReading.State.NON_BREATHABLE, result.state());
        assertEquals(2.3456789D, result.ambient().orElseThrow().pressure());
    }

    @Test void ambiguousLevelAndUnknownBodyAreUnavailable() {
        var values = new ArrayList<>(CelestialDefaults.definitions());
        var earth = values.get(0);
        values.add(new CelestialBodyDefinition(new net.minecraft.resources.ResourceLocation("test", "other"), Optional.empty(), Level.OVERWORLD,
                1, new AtmosphereDefinition(1, true, 288, earth.atmosphere().profile()), earth.orbit(), earth.visualProfile()));
        var ambiguous = CelestialCatalog.create(values).result().orElseThrow();
        assertTrue(AtmosphereAnalyzerService.project(ambiguous, BodyContext.surface(CelestialIds.EARTH_ID), Level.OVERWORLD).isEmpty());
        assertTrue(AtmosphereAnalyzerService.project(catalog(), BodyContext.surface(new net.minecraft.resources.ResourceLocation("test", "unknown")), Level.OVERWORLD).isEmpty());
    }

    @Test void acceptedReloadHasNewProjectionAndRejectedReloadKeepsLastGoodIdentity() {
        var catalogs = new CelestialCatalogManager();
        var first = catalog(); assertTrue(catalogs.applyCandidate(DataResult.success(first)));
        assertFalse(catalogs.applyCandidate(DataResult.error(() -> "invalid candidate")));
        assertSame(first, catalogs.current().orElseThrow());
        var values = new ArrayList<>(CelestialDefaults.definitions());
        var earth = values.get(0);
        values.set(0, new CelestialBodyDefinition(earth.id(), earth.parentId(), Level.OVERWORLD, earth.gravityMultiplier(),
                new AtmosphereDefinition(0.75D, true, 301, earth.atmosphere().profile()), earth.orbit(), earth.visualProfile()));
        var next = CelestialCatalog.create(values).result().orElseThrow(); assertTrue(catalogs.applyCandidate(DataResult.success(next)));
        assertNotSame(first, catalogs.current().orElseThrow());
        assertEquals(0.75D, AtmosphereAnalyzerService.project(next, BodyContext.surface(CelestialIds.EARTH_ID), Level.OVERWORLD).orElseThrow().values().pressure());
    }
}
