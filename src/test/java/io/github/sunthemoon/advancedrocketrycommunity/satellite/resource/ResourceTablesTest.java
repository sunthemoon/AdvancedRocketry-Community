package io.github.sunthemoon.advancedrocketrycommunity.satellite.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.StarSystemContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteRecordFit;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** ADR-052 sections 1, 2 and 8: strict decoding, version hashing, cross-checks and the record-fit check. */
final class ResourceTablesTest {
    private static final Path DATA = Path.of("src", "generated", "v1.6", "resources", "data", "advancedrocketrycommunity");
    private static final Predicate<ResourceLocation> ANY_ITEM = id -> true;

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void theBuiltInTablesDecodeWithRawByteVersionsAndCrossCheck() throws Exception {
        List<AsteroidType> types = new ArrayList<>();
        try (Stream<Path> files = Files.list(DATA.resolve(ResourceTableCodec.ASTEROID_DIRECTORY))) {
            for (Path file : files.sorted().toList()) {
                byte[] raw = Files.readAllBytes(file);
                AsteroidType type = ResourceTableCodec.decodeAsteroidType(ModIdentity.id(name(file)), raw, ANY_ITEM);
                assertEquals(ResourceAlgorithms.sha256Hex16(raw), type.tableVersion());
                types.add(type);
            }
        }
        byte[] gasRaw = Files.readAllBytes(DATA.resolve(ResourceTableCodec.GAS_DIRECTORY).resolve("gas_giant.json"));
        GasTable gas = ResourceTableCodec.decodeGasTable(ModIdentity.id("gas_giant"), gasRaw, ANY_ITEM);
        ResourceTables tables = ResourceTables.create(types, List.of(gas), catalog()).getOrThrow(false, message -> { });

        assertEquals(List.of(20, 15, 2, 1), weights(tables, "small_asteroid", "light_asteroid", "rich_asteroid",
                "strange_asteroid"));
        AsteroidType rich = tables.asteroidType(ModIdentity.id("rich_asteroid")).orElseThrow();
        assertEquals(75, rich.mass());
        assertEquals(30, rich.richnessVariabilityPct());
        assertEquals(ResourceLocation.tryParse("minecraft:cobblestone"), rich.baseItem());
        assertEquals(8, tables.gasTable(ModIdentity.id("gas_giant")).orElseThrow().products().get(0).amountPer1000Ticks());
        assertEquals("ba7816bf8f01cfea", ResourceAlgorithms.sha256Hex16("abc".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void malformedFilesAreRejected() {
        String valid = """
                {"schema_version":1,"id":"t:a","weight":1,"systems":[],"mass":10,"mass_variability_pct":0,
                 "richness_pct":50,"richness_variability_pct":0,"base_item":"minecraft:cobblestone",
                 "ores":[{"item":"minecraft:iron_ore","weight":1}],"time_multiplier_pct":100}""";
        ResourceLocation id = ResourceLocation.tryParse("t:a");
        assertEquals(10, ResourceTableCodec.decodeAsteroidType(id, bytes(valid), ANY_ITEM).mass());
        for (String bad : List.of(
                valid.replace("\"mass\":10", "\"mass\":10,\"extra\":1"),
                valid.replace("\"mass\":10,", ""),
                valid.replace("\"mass\":10", "\"mass\":10.5"),
                valid.replace("\"mass\":10", "\"mass\":4097"),
                valid.replace("\"weight\":1,\"systems\"", "\"weight\":0,\"systems\""),
                valid.replace("\"schema_version\":1", "\"schema_version\":2"),
                valid.replace("\"id\":\"t:a\"", "\"id\":\"t:b\""),
                valid.replace("\"systems\":[]", "\"systems\":[\"a:b\",\"a:b\"]"),
                valid.replace("minecraft:iron_ore", "minecraft:cobblestone"),
                valid.replace("\"mass\":10", "\"mass\":\"10\""))) {
            assertThrows(RuntimeException.class, () -> ResourceTableCodec.decodeAsteroidType(id, bytes(bad), ANY_ITEM), bad);
        }
        assertThrows(IllegalArgumentException.class, () -> ResourceTableCodec.decodeAsteroidType(id, bytes(valid),
                item -> !item.getPath().equals("iron_ore")));
        assertThrows(IllegalArgumentException.class, () -> ResourceTableCodec.decodeAsteroidType(id,
                new byte[ResourceTableCodec.MAX_ASTEROID_FILE_BYTES + 1], ANY_ITEM));
        String gas = "{\"schema_version\":1,\"body\":\"a:b\",\"products\":[{\"item\":\"a:c\",\"amount_per_1000_ticks\":65}]}";
        assertThrows(IllegalArgumentException.class, () -> ResourceTableCodec.decodeGasTable(id, bytes(gas), ANY_ITEM));
    }

    @Test
    void crossChecksRejectWrongBodiesAndDuplicates() {
        CelestialCatalog catalog = catalog();
        AsteroidType moonSystem = type("t:moon", List.of(ModIdentity.id("moon")), "minecraft:iron_ore");
        assertTrue(ResourceTables.create(List.of(moonSystem), List.of(), catalog).error().isPresent(),
                "the Moon is not a root body");
        AsteroidType earthSystem = type("t:earth", List.of(ModIdentity.id("earth")), "minecraft:iron_ore");
        assertTrue(ResourceTables.create(List.of(earthSystem), List.of(), catalog).result().isPresent());
        GasTable earthGas = new GasTable(ResourceLocation.tryParse("t:gas"), ModIdentity.id("earth"),
                List.of(new GasTable.Product(ResourceLocation.tryParse("a:b"), 8)), "0000000000000000");
        assertTrue(ResourceTables.create(List.of(), List.of(earthGas), catalog).error().isPresent(),
                "Earth is not a gas giant");
        GasTable first = new GasTable(ResourceLocation.tryParse("t:first"), ModIdentity.id("gas_giant"),
                List.of(new GasTable.Product(ResourceLocation.tryParse("a:b"), 8)), "0000000000000000");
        GasTable second = new GasTable(ResourceLocation.tryParse("t:second"), ModIdentity.id("gas_giant"),
                List.of(new GasTable.Product(ResourceLocation.tryParse("a:b"), 8)), "0000000000000000");
        assertTrue(ResourceTables.create(List.of(), List.of(first, second), catalog).error().isPresent(),
                "one gas table per body");
    }

    @Test
    void theRecordFitCheckRejectsATypeWhoseWorstCaseRecordIsTooLarge() {
        List<ResourceLocation> items = new ArrayList<>();
        for (int index = 0; index < 17; index++) {
            items.add(ResourceLocation.tryParse("f:" + index + "x".repeat(125 - String.valueOf(index).length())));
        }
        int instance = SatelliteRecordFit.instanceBytes(items.get(0), items);
        int mission = SatelliteRecordFit.resourceMissionBytes(MissionKind.ASTEROID,
                "asteroid-v1/" + items.get(0) + "/0123456789abcdef", items);
        System.out.printf("ARCE_V160_TABLE_RECORD_FIT instance=%d mission=%d%n", instance, mission);
        assertTrue(instance <= SatelliteLimits.MAX_INSTANCE_RECORD_NBT_BYTES && mission <= SatelliteLimits.MAX_MISSION_RECORD_NBT_BYTES,
                "128-character IDs always fit: instance " + instance + ", mission " + mission);

        List<AsteroidType.Ore> huge = new ArrayList<>();
        for (int index = 0; index < 16; index++) {
            huge.add(new AsteroidType.Ore(ResourceLocation.tryParse("f:" + index + "y".repeat(300)), 1));
        }
        AsteroidType tooLarge = new AsteroidType(ResourceLocation.tryParse("t:huge"), 1, List.of(), 100, 0, 50, 0,
                ResourceLocation.tryParse("minecraft:cobblestone"), huge, 100, "0000000000000000");
        String error = ResourceTables.create(List.of(tooLarge), List.of(), catalog()).error().orElseThrow().message();
        assertTrue(error.contains("record bound"), error);
    }

    private static AsteroidType type(String id, List<ResourceLocation> systems, String ore) {
        return new AsteroidType(ResourceLocation.tryParse(id), 1, systems, 10, 0, 50, 0,
                ResourceLocation.tryParse("minecraft:cobblestone"),
                List.of(new AsteroidType.Ore(ResourceLocation.tryParse(ore), 1)), 100, "0000000000000000");
    }

    private static List<Integer> weights(ResourceTables tables, String... names) {
        List<Integer> weights = new ArrayList<>();
        for (String name : names) {
            weights.add(tables.asteroidType(ModIdentity.id(name)).orElseThrow().weight());
        }
        return weights;
    }

    private static CelestialCatalog catalog() {
        List<CelestialBodyDefinition> bodies = new ArrayList<>(CelestialDefaults.definitions());
        bodies.addAll(PlanetaryContent.definitions());
        bodies.addAll(StarSystemContent.definitions());
        return CelestialCatalog.create(bodies).getOrThrow(false, message -> {
            throw new AssertionError(message);
        });
    }

    private static String name(Path file) {
        String name = file.getFileName().toString();
        return name.substring(0, name.length() - ".json".length());
    }

    private static byte[] bytes(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
