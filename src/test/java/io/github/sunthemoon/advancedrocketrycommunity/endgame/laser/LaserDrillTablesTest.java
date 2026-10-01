package io.github.sunthemoon.advancedrocketrycommunity.endgame.laser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.StarSystemContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.resource.ResourceAlgorithms;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** ADR-055 section 2: the cross-checked table set, eligibility, the reload's all-or-nothing rule and built-in data. */
final class LaserDrillTablesTest {
    private static final Path DATA = Path.of("src", "generated", "v1.7", "resources", "data", "advancedrocketrycommunity",
            LaserDrillTableCodec.DIRECTORY);

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void ownTablesWinAndTheDefaultCoversOnlySurfaceBodies() {
        CelestialCatalog catalog = catalog();
        LaserDrillTable earth = table("earth", List.of(CelestialIds.EARTH_ID), false);
        LaserDrillTable giant = table("giant", List.of(PlanetaryContent.GAS_GIANT), false);
        LaserDrillTable fallback = table("default", List.of(), true);
        LaserDrillTables tables = LaserDrillTables.create(List.of(earth, giant, fallback), catalog).result().orElseThrow();

        assertEquals(Optional.of(earth), tables.forBody(body(catalog, CelestialIds.EARTH_ID)));
        assertEquals(Optional.of(giant), tables.forBody(body(catalog, PlanetaryContent.GAS_GIANT)),
                "an explicit table makes a gas giant eligible");
        assertEquals(Optional.of(fallback), tables.forBody(body(catalog, PlanetaryContent.MARS)));
        assertEquals(Optional.empty(), tables.forBody(body(catalog, StarSystemContent.TAU_CETI_E)),
                "an orbit-only planet has no surface Level, so it needs an explicit table");
        assertEquals(Optional.empty(), tables.forBody(body(catalog, StarSystemContent.TAU_CETI)),
                "a star needs an explicit table");
        LaserDrillTables withoutGiant = LaserDrillTables.create(List.of(earth, fallback), catalog).result().orElseThrow();
        assertEquals(Optional.empty(), withoutGiant.forBody(body(catalog, PlanetaryContent.GAS_GIANT)),
                "a gas giant gets no default");
        LaserDrillTables noDefault = LaserDrillTables.create(List.of(earth), catalog).result().orElseThrow();
        assertEquals(Optional.empty(), noDefault.forBody(body(catalog, PlanetaryContent.MARS)));
    }

    @Test
    void conflictingSetsAreRejectedAsAWhole() {
        CelestialCatalog catalog = catalog();
        assertError(LaserDrillTables.create(List.of(table("a", List.of(), true), table("b", List.of(), true)), catalog),
                "both default");
        assertError(LaserDrillTables.create(List.of(table("a", List.of(CelestialIds.MOON_ID), false),
                table("b", List.of(CelestialIds.MOON_ID), false)), catalog), "appears in laser drill tables");
        assertError(LaserDrillTables.create(List.of(table("a", List.of(ModIdentity.id("nowhere")), false)), catalog),
                "not a known body");
        List<LaserDrillTable> many = new ArrayList<>();
        for (int i = 0; i < 33; i++) {
            many.add(table("t" + i, List.of(), false));
        }
        assertError(LaserDrillTables.create(many, catalog), "More than 32");
    }

    @Test
    void oneBadFileRejectsTheWholeReload() {
        Map<ResourceLocation, byte[]> files = new LinkedHashMap<>();
        files.put(ModIdentity.id("good"), LaserDrillTableCodecTest.bytes(LaserDrillTableCodecTest.table("[]", "true",
                "[{\"item\": \"minecraft:stone\", \"count\": 1, \"weight\": 1}]")));
        assertTrue(LaserDrillTableReloadListener.build(files, List.of(), id -> true, catalog()).result().isPresent());
        files.put(ModIdentity.id("bad"), LaserDrillTableCodecTest.bytes("{}"));
        assertError(LaserDrillTableReloadListener.build(files, List.of(), id -> true, catalog()), "laser_drill_tables");
        files.remove(ModIdentity.id("bad"));
        assertError(LaserDrillTableReloadListener.build(files, List.of("x exceeds 16384 bytes"), id -> true, catalog()),
                "exceeds");
        assertError(LaserDrillTableReloadListener.build(files, List.of(), id -> true, null), "celestial catalog");
    }

    @Test
    void theBuiltInTablesCoverEarthMoonMarsVenusAndOneDefault() throws Exception {
        List<LaserDrillTable> tables = new ArrayList<>();
        try (Stream<Path> files = Files.list(DATA)) {
            for (Path file : files.sorted().toList()) {
                byte[] raw = Files.readAllBytes(file);
                String name = file.getFileName().toString().replace(".json", "");
                LaserDrillTable table = LaserDrillTableCodec.decode(ModIdentity.id(name), raw,
                        id -> id.getNamespace().equals("minecraft"));
                assertEquals(ResourceAlgorithms.sha256Hex16(raw), table.version());
                tables.add(table);
            }
        }
        CelestialCatalog catalog = catalog();
        LaserDrillTables set = LaserDrillTables.create(tables, catalog).result().orElseThrow();
        for (ResourceLocation body : List.of(CelestialIds.EARTH_ID, CelestialIds.MOON_ID, PlanetaryContent.MARS,
                PlanetaryContent.VENUS)) {
            LaserDrillTable own = set.forBody(body(catalog, body)).orElseThrow();
            assertEquals(List.of(body), own.bodies(), body.toString());
            assertOneInTenIsOre(own);
        }
        LaserDrillTable fallback = set.defaultTable().orElseThrow();
        assertOneInTenIsOre(fallback);
        LaserDrillTable.Entry filler = fallback.entries().get(0);
        assertEquals("minecraft:cobblestone", filler.item().toString(), "nine in ten are five cobblestone");
        assertEquals(5, filler.count());
        for (LaserDrillTable.Entry entry : fallback.entries().subList(1, fallback.entries().size())) {
            assertTrue(entry.count() >= 1 && entry.count() <= 5, "an ore hit gives 1 to 5 items");
        }
        assertEquals(5, tables.size());
    }

    private static void assertOneInTenIsOre(LaserDrillTable table) {
        int filler = table.entries().get(0).weight();
        assertEquals(table.totalWeight() * 9, filler * 10, table.id() + ": one hit in ten is an ore entry");
    }

    private static void assertError(com.mojang.serialization.DataResult<?> result, String expected) {
        String message = result.error().orElseThrow().message();
        assertTrue(message.contains(expected), message);
    }

    static LaserDrillTable table(String name, List<ResourceLocation> bodies, boolean isDefault) {
        return new LaserDrillTable(ModIdentity.id(name), bodies, isDefault,
                List.of(LaserDrawTest.entry("cobblestone", 5, 9), LaserDrawTest.entry("diamond", 1, 1)),
                "0123456789abcdef");
    }

    static CelestialBodyDefinition body(CelestialCatalog catalog, ResourceLocation id) {
        return catalog.get(id).orElseThrow();
    }

    static CelestialCatalog catalog() {
        List<CelestialBodyDefinition> bodies = new ArrayList<>(CelestialDefaults.definitions());
        bodies.addAll(PlanetaryContent.definitions());
        bodies.addAll(StarSystemContent.definitions());
        return CelestialCatalog.create(bodies).getOrThrow(false, message -> {
            throw new AssertionError(message);
        });
    }
}
