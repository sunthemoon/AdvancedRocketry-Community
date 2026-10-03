package io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * ADR-063 sections 6 and 8 with revision 6 (A0): an audit of the committed C15c DataGen output, with the contract's
 * numbers written here rather than read from the code.
 */
class V180ExoplanetResourcesTest {
    private static final String NS = "advancedrocketrycommunity";
    private static final Path ROOT = Path.of("src", "generated", "v1.8", "resources");
    private static final Path DATA = ROOT.resolve("data");
    private static final Path WORLDGEN = DATA.resolve(NS).resolve("worldgen");
    private static final List<String> BLOCKS = List.of("lightwood_log", "lightwood_leaves", "lightwood_sapling",
            "lightwood_planks", "violet_crystal_block", "blue_crystal_block", "green_crystal_block", "red_crystal_block",
            "yellow_crystal_block", "orange_crystal_block", "electric_mushroom");

    @Test
    void theBodiesAreRevisionSixsNumbers() throws IOException {
        JsonObject f = json(DATA.resolve(NS + "/celestial_bodies/tau_ceti_f.json"));
        JsonObject g = json(DATA.resolve(NS + "/celestial_bodies/tau_ceti_g.json"));
        for (JsonObject body : List.of(f, g)) {
            assertEquals(NS + ":tau_ceti", body.get("parent").getAsString());
            assertEquals(body.get("id").getAsString(), body.get("level").getAsString());
            assertEquals(body.get("id").getAsString(), body.get("visual_profile").getAsString());
            assertTrue(body.get("discovery_required").getAsBoolean() && body.get("environment_effects").getAsBoolean());
            JsonObject capabilities = body.getAsJsonObject("capabilities");
            assertTrue(capabilities.get("landable").getAsBoolean() && capabilities.get("orbitable").getAsBoolean());
            assertFalse(capabilities.get("gas_giant").getAsBoolean());
        }
        assertBody(f, 1.0, true, 1.0, 295.0, 199_000_000L, 20_764_800L, 0.8, 0.0);
        assertBody(g, 1.2, false, 1.4, 255.0, 19_400_000L, 646_400L, 1.2, 0.1);
    }

    private static void assertBody(JsonObject body, double gravity, boolean breathable, double pressure,
                                   double temperature, long distance, long period, double solar, double radiation) {
        assertEquals(gravity, body.get("gravity_multiplier").getAsDouble());
        JsonObject atmosphere = body.getAsJsonObject("atmosphere");
        assertEquals(breathable, atmosphere.get("breathable").getAsBoolean());
        assertEquals(pressure, atmosphere.get("pressure").getAsDouble());
        assertEquals(temperature, atmosphere.get("temperature_kelvin").getAsDouble());
        // Neither harms an unprotected player: cold below 240 K, pressure above 2 atm, sunlight above 1.5.
        assertTrue(temperature >= 240 && pressure <= 2 && solar <= 1.5);
        assertEquals(body.get("id").getAsString(), atmosphere.get("profile").getAsString());
        assertEquals(distance, body.getAsJsonObject("orbit").get("distance").getAsLong());
        assertEquals(period, body.getAsJsonObject("orbit").get("period_ticks").getAsLong());
        assertEquals(solar, body.get("solar_intensity").getAsDouble());
        assertEquals(radiation, body.get("radiation").getAsDouble());
    }

    @Test
    void theThreeRoutesStayInsideTauCeti() throws IOException {
        Map<String, String[]> routes = Map.of(
                "tau_ceti_f_surface_orbit", new String[] {"body_surface:tau_ceti_f", "orbit:tau_ceti_f", "25"},
                "tau_ceti_g_surface_orbit", new String[] {"body_surface:tau_ceti_g", "orbit:tau_ceti_g", "35"},
                "tau_ceti_f_g", new String[] {"body_surface:tau_ceti_f", "body_surface:tau_ceti_g", "60"});
        for (Map.Entry<String, String[]> route : routes.entrySet()) {
            JsonObject json = json(DATA.resolve(NS + "/travel_routes/" + route.getKey() + ".json"));
            assertEquals(NS + ":" + route.getKey(), json.get("id").getAsString());
            assertTrue(json.get("bidirectional").getAsBoolean());
            assertEquals(anchor(route.getValue()[0]), anchor(json.getAsJsonObject("from")));
            assertEquals(anchor(route.getValue()[1]), anchor(json.getAsJsonObject("to")));
            assertEquals(Integer.parseInt(route.getValue()[2]), json.get("distance_units").getAsInt());
        }
        try (var files = Files.list(DATA.resolve(NS + "/travel_routes"))) {
            assertEquals(3, files.count(), "C15c adds three routes and no Earth route");
        }
    }

    @Test
    void theV18DataSatelliteAddsBothWorldsToTheV15Targets() throws IOException {
        JsonObject satellite = json(DATA.resolve(NS + "/satellite_definitions/data_satellite.json"));
        JsonObject earlier = json(Path.of("src", "generated", "v1.5", "resources", "data", NS,
                "satellite_definitions", "data_satellite.json"));
        List<String> expected = new ArrayList<>(strings(earlier.getAsJsonArray("allowed_targets")));
        expected.add(NS + ":tau_ceti_f");
        expected.add(NS + ":tau_ceti_g");
        assertEquals(expected, strings(satellite.getAsJsonArray("allowed_targets")));
        for (String field : List.of("discovery_cost", "mission_duration_ticks", "research_yield", "schema_version")) {
            assertEquals(earlier.get(field), satellite.get(field), field);
        }
        assertEquals(200, satellite.get("mission_duration_ticks").getAsInt());
        assertEquals(120, satellite.get("research_yield").getAsInt());
        assertEquals(100, satellite.get("discovery_cost").getAsInt());
    }

    @Test
    void theLevelsAreLowReliefWithTheirBiomeSources() throws IOException {
        for (String body : List.of("tau_ceti_f", "tau_ceti_g")) {
            JsonObject type = json(DATA.resolve(NS + "/dimension_type/" + body + ".json"));
            assertFalse(type.has("fixed_time"), "a day cycle on " + body);
            assertEquals(NS + ":planetary", type.get("effects").getAsString());
            assertTrue(type.get("has_skylight").getAsBoolean());
            assertEquals(256, type.get("height").getAsInt());
            JsonObject generator = json(DATA.resolve(NS + "/dimension/" + body + ".json")).getAsJsonObject("generator");
            assertEquals(NS + ":" + body, generator.get("settings").getAsString());
        }
        JsonArray bands = json(DATA.resolve(NS + "/dimension/tau_ceti_f.json")).getAsJsonObject("generator")
                .getAsJsonObject("biome_source").getAsJsonArray("biomes");
        String[] biomes = {"ocean_spires", "marsh", "deep_swamp", "alien_forest"};
        double[] bounds = {-1.0, -0.3, -0.1, 0.1, 1.0};
        assertEquals(4, bands.size());
        for (int band = 0; band < 4; band++) {
            JsonObject entry = bands.get(band).getAsJsonObject();
            assertEquals(NS + ":" + biomes[band], entry.get("biome").getAsString());
            JsonArray range = entry.getAsJsonObject("parameters").getAsJsonArray("continentalness");
            assertEquals(bounds[band], range.get(0).getAsDouble(), 1.0E-6);
            assertEquals(bounds[band + 1], range.get(1).getAsDouble(), 1.0E-6);
        }
        JsonObject patches = json(DATA.resolve(NS + "/dimension/tau_ceti_g.json")).getAsJsonObject("generator")
                .getAsJsonObject("biome_source");
        assertEquals(NS + ":patches", patches.get("type").getAsString());
        assertEquals(List.of(NS + ":stormland", NS + ":crystal_chasms"), strings(patches.getAsJsonArray("biomes")));
        assertEquals(32, patches.get("cell_size").getAsInt());
        assertEquals(0x5441554345544947L, patches.get("salt").getAsLong());

        JsonObject f = json(WORLDGEN.resolve("noise_settings/tau_ceti_f.json"));
        assertEquals(63, f.get("sea_level").getAsInt());
        assertEquals("minecraft:water", f.getAsJsonObject("default_fluid").get("Name").getAsString());
        JsonObject g = json(WORLDGEN.resolve("noise_settings/tau_ceti_g.json"));
        assertEquals("minecraft:air", g.getAsJsonObject("default_fluid").get("Name").getAsString());
        for (JsonObject settings : List.of(f, g)) {
            assertFalse(settings.get("aquifers_enabled").getAsBoolean());
            assertFalse(settings.get("ore_veins_enabled").getAsBoolean());
            assertTrue(settings.get("disable_mob_generation").getAsBoolean());
            assertEquals("minecraft:stone", settings.getAsJsonObject("default_block").get("Name").getAsString());
        }
        assertTrue(f.get("surface_rule").toString().contains("minecraft:gravel"), "ocean spire gravel");
        // Revision 6: Tau Ceti f's ground noise is raised to the landing ground floor, in the terrain and the biomes.
        JsonObject continents = f.getAsJsonObject("noise_router").getAsJsonObject("continents");
        assertEquals("minecraft:max", continents.get("type").getAsString());
        JsonObject floor = continents.getAsJsonObject("argument2");
        assertEquals(NS + ":landing_ground_floor", floor.get("type").getAsString());
        assertEquals(160, floor.get("inner_radius").getAsInt());
        assertEquals(96, floor.get("slope_width").getAsInt());
        assertEquals(0.2, floor.get("level").getAsDouble(), 1.0E-9);
        assertTrue(f.getAsJsonObject("noise_router").get("final_density").toString().contains("landing_ground_floor"));
        assertFalse(g.getAsJsonObject("noise_router").toString().contains("landing_ground"), "g has no sea");
        String gRule = g.get("surface_rule").toString();
        assertTrue(gRule.contains("minecraft:snow_block") && gRule.contains("minecraft:packed_ice"), "chasm snow");
        assertEquals(-8, json(WORLDGEN.resolve("noise/tau_ceti_f_terrain.json")).get("firstOctave").getAsInt());
        assertEquals(-7, json(WORLDGEN.resolve("noise/tau_ceti_g_terrain.json")).get("firstOctave").getAsInt());
    }

    @Test
    void theBiomesSpawnNothingCarveNothingAndCarryTheirFeatures() throws IOException {
        Map<String, List<String>> features = Map.of(
                "alien_forest", List.of(NS + ":lightwood_tree", NS + ":alien_forest_grass"),
                "marsh", List.of("minecraft:disk_clay", "minecraft:patch_waterlily"),
                "deep_swamp", List.of(NS + ":giant_swamp_tree", "minecraft:trees_swamp", "minecraft:flower_swamp",
                        "minecraft:patch_grass_normal", "minecraft:brown_mushroom_swamp",
                        "minecraft:red_mushroom_swamp", "minecraft:patch_sugar_cane_swamp",
                        "minecraft:patch_waterlily"),
                "ocean_spires", List.of(NS + ":inverted_pillar", "minecraft:seagrass_normal"),
                "stormland", List.of(NS + ":stormland_charred_tree", NS + ":electric_mushrooms"),
                "crystal_chasms", List.of(NS + ":crystal_cluster"));
        for (Map.Entry<String, List<String>> biome : features.entrySet()) {
            JsonObject json = json(WORLDGEN.resolve("biome/" + biome.getKey() + ".json"));
            for (Map.Entry<String, JsonElement> group : json.getAsJsonObject("spawners").entrySet()) {
                assertEquals(0, group.getValue().getAsJsonArray().size(), biome.getKey() + " spawns " + group.getKey());
            }
            assertEquals(0, json.getAsJsonObject("carvers").size(), biome.getKey() + " carves");
            assertTrue(json.get("has_precipitation").getAsBoolean());
            List<String> listed = new ArrayList<>();
            json.getAsJsonArray("features").forEach(step -> listed.addAll(strings(step.getAsJsonArray())));
            assertEquals(new java.util.TreeSet<>(biome.getValue()), new java.util.TreeSet<>(listed), biome.getKey());
        }
        JsonObject forest = json(WORLDGEN.resolve("biome/alien_forest.json")).getAsJsonObject("effects");
        assertEquals(0x7777FF, forest.get("grass_color").getAsInt());
        assertEquals(0x55FFE1, forest.get("foliage_color").getAsInt());
        assertEquals(0x8888FF, forest.get("water_color").getAsInt());
        assertEquals(0xE0FFAE, json(WORLDGEN.resolve("biome/deep_swamp.json")).getAsJsonObject("effects")
                .get("water_color").getAsInt());
        assertEquals(0x202020, json(WORLDGEN.resolve("biome/stormland.json")).getAsJsonObject("effects")
                .get("sky_color").getAsInt());
        assertEquals(0.1, json(WORLDGEN.resolve("biome/crystal_chasms.json")).get("temperature").getAsDouble(), 1e-6);
    }

    @Test
    void eachFeatureStartsWithItsSwitchAndHasTheLegacyNumbers() throws IOException {
        Map<String, String> switches = Map.of("lightwood_tree", "lightwood_trees", "giant_swamp_tree", "swamp_trees",
                "inverted_pillar", "inverted_pillars", "crystal_cluster", "crystal_clusters", "electric_mushrooms",
                "electric_mushrooms", "stormland_charred_tree", "charred_trees");
        Map<String, Integer> rarity = Map.of("lightwood_tree", 20, "giant_swamp_tree", 100, "inverted_pillar", 2,
                "crystal_cluster", 36);
        for (Map.Entry<String, String> feature : switches.entrySet()) {
            JsonArray placement = json(WORLDGEN.resolve("placed_feature/" + feature.getKey() + ".json"))
                    .getAsJsonArray("placement");
            JsonObject first = placement.get(0).getAsJsonObject();
            assertEquals(NS + ":server_switch", first.get("type").getAsString(), feature.getKey());
            assertEquals(feature.getValue(), first.get("switch").getAsString(), feature.getKey());
            Integer chance = rarity.get(feature.getKey());
            JsonObject second = placement.get(1).getAsJsonObject();
            if (chance != null) {
                assertEquals("minecraft:rarity_filter", second.get("type").getAsString(), feature.getKey());
                assertEquals(chance.intValue(), second.get("chance").getAsInt(), feature.getKey());
            }
            assertEquals("minecraft:biome", placement.get(placement.size() - 1).getAsJsonObject().get("type")
                    .getAsString(), feature.getKey());
        }
        // Revision 6: every Tau Ceti feature, and the alien forest grass, starts outside the landing ground.
        List<String> landing = new ArrayList<>(switches.keySet());
        landing.add("alien_forest_grass");
        for (String feature : landing) {
            JsonArray placement = json(WORLDGEN.resolve("placed_feature/" + feature + ".json")).getAsJsonArray("placement");
            JsonObject filter = placement.get(placement.size() - 2).getAsJsonObject();
            assertEquals(NS + ":landing_ground", filter.get("type").getAsString(), feature);
            assertEquals(160, filter.get("radius").getAsInt(), feature);
        }
        JsonObject grass = json(WORLDGEN.resolve("placed_feature/alien_forest_grass.json"));
        assertEquals("minecraft:patch_grass_jungle", grass.get("feature").getAsString());
        assertEquals(25, grass.getAsJsonArray("placement").get(0).getAsJsonObject().get("count").getAsInt());
        JsonArray storm = json(WORLDGEN.resolve("placed_feature/stormland_charred_tree.json"))
                .getAsJsonArray("placement");
        assertEquals(6, storm.get(1).getAsJsonObject().get("count").getAsInt());
        JsonObject patch = json(WORLDGEN.resolve("configured_feature/electric_mushrooms.json")).getAsJsonObject("config");
        assertEquals(64, patch.get("tries").getAsInt());
        assertEquals(7, patch.get("xz_spread").getAsInt());
        assertEquals(3, patch.get("y_spread").getAsInt());
    }

    @Test
    void theBlocksHaveLootModelsTagsAndNames() throws IOException {
        Path assets = ROOT.resolve("assets");
        JsonObject english = json(assets.resolve(NS + "_v180/lang/en_us.json"));
        JsonObject chinese = json(assets.resolve(NS + "_v180/lang/zh_cn.json"));
        for (String block : BLOCKS) {
            assertTrue(Files.isRegularFile(assets.resolve(NS + "/blockstates/" + block + ".json")), block);
            assertTrue(Files.isRegularFile(assets.resolve(NS + "/models/item/" + block + ".json")), block);
            assertTrue(english.has("block." + NS + "." + block) && chinese.has("block." + NS + "." + block), block);
            JsonObject loot = json(DATA.resolve(NS + "/loot_tables/blocks/" + block + ".json"));
            JsonObject entry = loot.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0)
                    .getAsJsonObject();
            if (!block.equals("lightwood_leaves")) {
                assertEquals(NS + ":" + block, entry.get("name").getAsString(), block + " drops itself");
            }
        }
        JsonArray leaves = json(DATA.resolve(NS + "/loot_tables/blocks/lightwood_leaves.json")).getAsJsonArray("pools")
                .get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject().getAsJsonArray("children");
        assertEquals(NS + ":lightwood_leaves", leaves.get(0).getAsJsonObject().get("name").getAsString());
        assertTrue(leaves.get(0).toString().contains("minecraft:shears") && leaves.get(0).toString().contains("silk_touch"));
        JsonObject sapling = leaves.get(1).getAsJsonObject();
        assertEquals(NS + ":lightwood_sapling", sapling.get("name").getAsString());
        assertTrue(sapling.toString().contains("\"chance\":0.01"), "a sapling one time in a hundred: " + sapling);
        assertEquals(2, leaves.size(), "nothing else drops (legacy)");
        for (String biome : List.of("alien_forest", "marsh", "deep_swamp", "ocean_spires", "stormland",
                "crystal_chasms")) {
            assertTrue(english.has("biome." + NS + "." + biome) && chinese.has("biome." + NS + "." + biome), biome);
        }
        // The satellite terminal and the star map name bodies by these keys.
        assertEquals("Tau Ceti f", english.get("body." + NS + ".tau_ceti_f").getAsString());
        assertEquals("Tau Ceti g", english.get("body." + NS + ".tau_ceti_g").getAsString());
        assertTrue(chinese.has("body." + NS + ".tau_ceti_f") && chinese.has("body." + NS + ".tau_ceti_g"));
        Path tags = DATA.resolve("minecraft/tags");
        assertEquals(List.of(NS + ":lightwood_log"), values(tags.resolve("blocks/logs_that_burn.json")));
        assertEquals(List.of(NS + ":lightwood_planks"), values(tags.resolve("blocks/planks.json")));
        assertEquals(List.of(NS + ":lightwood_leaves"), values(tags.resolve("blocks/leaves.json")));
        assertEquals(List.of(NS + ":lightwood_sapling"), values(tags.resolve("blocks/saplings.json")));
        for (String name : List.of("logs_that_burn", "planks", "leaves", "saplings")) {
            assertEquals(values(tags.resolve("blocks/" + name + ".json")), values(tags.resolve("items/" + name + ".json")));
        }
        List<String> pickaxe = values(tags.resolve("blocks/mineable/pickaxe.json"));
        for (String colour : List.of("violet", "blue", "green", "red", "yellow", "orange")) {
            assertTrue(pickaxe.contains(NS + ":" + colour + "_crystal_block"), colour);
            JsonObject model = json(assets.resolve(NS + "/models/block/" + colour + "_crystal_block.json"));
            assertEquals("minecraft:block/leaves", model.get("parent").getAsString(), "tint index 0 on every face");
            assertEquals("minecraft:translucent", model.get("render_type").getAsString());
            assertEquals(NS + ":block/crystal", model.getAsJsonObject("textures").get("all").getAsString());
        }
        JsonObject planks = json(DATA.resolve(NS + "/recipes/lightwood_planks.json"));
        assertEquals(4, planks.getAsJsonObject("result").get("count").getAsInt());
        assertEquals(NS + ":lightwood_log", planks.getAsJsonArray("ingredients").get(0).getAsJsonObject().get("item")
                .getAsString());
    }

    @Test
    void theTwoSkyProfilesAreTheRevisionSixColours() throws IOException {
        Path visuals = ROOT.resolve("assets").resolve(NS).resolve("celestial_visuals");
        JsonObject f = json(visuals.resolve("tau_ceti_f.json"));
        JsonObject g = json(visuals.resolve("tau_ceti_g.json"));
        assertEquals(0x5FA8C8, f.get("day_color").getAsInt());
        assertEquals(0x202020, g.get("day_color").getAsInt(), "the legacy stormland sky");
        assertEquals(1, f.get("schema_version").getAsInt());
    }

    private static String anchor(String spec) {
        String[] parts = spec.split(":");
        return NS + ":" + parts[0] + "/" + NS + ":" + parts[1];
    }

    private static String anchor(JsonObject anchor) {
        return anchor.get("type").getAsString() + "/" + anchor.get("body_id").getAsString();
    }

    private static List<String> values(Path tag) throws IOException {
        return strings(json(tag).getAsJsonArray("values"));
    }

    private static List<String> strings(JsonArray array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.getAsString()));
        return values;
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
