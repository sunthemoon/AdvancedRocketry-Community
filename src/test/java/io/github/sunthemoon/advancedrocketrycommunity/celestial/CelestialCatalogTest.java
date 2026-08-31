package io.github.sunthemoon.advancedrocketrycommunity.celestial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CelestialCatalogTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void fixedBaselineIsAcceptedAndSorted() {
        CelestialCatalog catalog = CelestialCatalog.create(List.of(
                        CelestialDefinitionCodecTest.spaceDefinition(),
                        CelestialDefinitionCodecTest.moonDefinition(),
                        CelestialDefinitionCodecTest.earthDefinition()
                ))
                .flatMap(CelestialCatalog::requireFixedBaseline)
                .getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });

        assertEquals(3, catalog.size());
        assertEquals(CelestialIds.EARTH_ID, catalog.definitions().get(0).id());
        assertEquals(CelestialIds.MOON_ID, catalog.forLevel(CelestialIds.MOON_LEVEL).orElseThrow().id());
    }

    @Test
    void duplicateIdIsRejected() {
        CelestialBodyDefinition earth = CelestialDefinitionCodecTest.earthDefinition();

        assertTrue(CelestialCatalog.create(List.of(earth, earth)).error().isPresent());
    }

    @Test
    void missingParentIsRejected() {
        CelestialBodyDefinition moon = CelestialDefinitionCodecTest.moonDefinition();

        assertTrue(CelestialCatalog.create(List.of(moon)).error().isPresent());
    }

    @Test
    void parentCycleIsRejected() {
        CelestialBodyDefinition earth = CelestialDefinitionCodecTest.earthDefinition();
        CelestialBodyDefinition moon = CelestialDefinitionCodecTest.moonDefinition();
        CelestialBodyDefinition earthWithMoonParent = new CelestialBodyDefinition(
                earth.id(),
                Optional.of(moon.id()),
                earth.levelKey(),
                earth.gravityMultiplier(),
                earth.atmosphere(),
                new io.github.sunthemoon.advancedrocketrycommunity.celestial.model.OrbitDefinition(1L, 1L, 0.0D),
                earth.visualProfile()
        );

        assertTrue(CelestialCatalog.create(List.of(earthWithMoonParent, moon)).error().isPresent());
    }

    @Test
    void catalogBodyCountIsBounded() {
        List<CelestialBodyDefinition> definitions = new ArrayList<>();
        CelestialBodyDefinition earth = CelestialDefinitionCodecTest.earthDefinition();
        definitions.add(earth);
        for (int index = 0; index < CelestialCatalog.MAX_BODIES; index++) {
            definitions.add(new CelestialBodyDefinition(
                    ModIdentity.id("body_" + index),
                    Optional.of(earth.id()),
                    CelestialIds.SPACE_LEVEL,
                    1.0D,
                    earth.atmosphere(),
                    new io.github.sunthemoon.advancedrocketrycommunity.celestial.model.OrbitDefinition(1L, 1L, 0.0D),
                    ModIdentity.id("body")
            ));
        }

        assertTrue(CelestialCatalog.create(definitions).error().isPresent());
    }
}
