package io.github.sunthemoon.advancedrocketrycommunity.celestial.surface;

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
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * ADR-063 sections 4, 5 and 8 with revision 5 (A0): an audit of the committed C15b DataGen output, with the contract's
 * numbers written here rather than read from the code.
 */
class V180PlanetResourcesTest {
    private static final String NS = "advancedrocketrycommunity";
    private static final Path DATA = Path.of("src", "generated", "v1.8", "resources", "data");
    private static final Path WORLDGEN = DATA.resolve(NS).resolve("worldgen");

    @Test
    void theMoonIsANoiseLevelWithTwoBiomesOnItsOwnTerrainNoise() throws IOException {
        JsonObject dimension = json(DATA.resolve(NS + "/dimension/moon.json"));
        assertEquals(NS + ":moon", dimension.get("type").getAsString());
        JsonObject generator = dimension.getAsJsonObject("generator");
        assertEquals("minecraft:noise", generator.get("type").getAsString());
        assertEquals(NS + ":moon", generator.get("settings").getAsString());
        JsonArray biomes = generator.getAsJsonObject("biome_source").getAsJsonArray("biomes");
        assertEquals(2, biomes.size());
        JsonObject lowlands = biomes.get(0).getAsJsonObject();
        JsonObject highlands = biomes.get(1).getAsJsonObject();
        assertEquals(NS + ":regolith_lowlands", lowlands.get("biome").getAsString());
        assertEquals(NS + ":regolith_highlands", highlands.get("biome").getAsString());
        assertEquals("[-1.0,0.0]", lowlands.getAsJsonObject("parameters").get("continentalness").toString());
        assertEquals("[0.0,1.0]", highlands.getAsJsonObject("parameters").get("continentalness").toString());

        JsonObject settings = json(WORLDGEN.resolve("noise_settings/moon.json"));
        assertEquals(0, settings.getAsJsonObject("noise").get("min_y").getAsInt());
        assertEquals(256, settings.getAsJsonObject("noise").get("height").getAsInt());
        assertEquals("minecraft:stone", settings.getAsJsonObject("default_block").get("Name").getAsString());
        assertFalse(settings.get("aquifers_enabled").getAsBoolean());
        assertFalse(settings.get("ore_veins_enabled").getAsBoolean());
        JsonObject router = settings.getAsJsonObject("noise_router");
        assertTrue(router.get("continents").toString().contains(NS + ":moon_terrain"),
                "the biome source reads the terrain noise");
        // Bedrock, then dark turf in the lowlands, then the highland turf for every other biome as top and filler
        // (C15bR1-M1: the plains of part-generated v1.7 chunks get turf too; C15bR1-L2: the pairing is exact).
        JsonArray rules = settings.getAsJsonObject("surface_rule").getAsJsonArray("sequence");
        assertEquals(4, rules.size());
        JsonObject lowlandRule = rules.get(1).getAsJsonObject();
        assertEquals(List.of(NS + ":regolith_lowlands"), strings(lowlandRule.getAsJsonObject("if_true")
                .getAsJsonArray("biome_is")));
        assertEquals(2, turfs(lowlandRule.getAsJsonObject("then_run"), NS + ":dark_moon_turf"));
        for (int index = 2; index <= 3; index++) {
            JsonObject fallback = rules.get(index).getAsJsonObject();
            assertEquals("minecraft:stone_depth", fallback.getAsJsonObject("if_true").get("type").getAsString());
            assertEquals(NS + ":moon_turf", fallback.getAsJsonObject("then_run").getAsJsonObject("result_state")
                    .get("Name").getAsString());
        }
        assertFalse(settings.get("surface_rule").toString().contains(NS + ":regolith_highlands"),
                "the highland turf does not depend on the biome");
        assertTrue(Files.isRegularFile(WORLDGEN.resolve("noise/moon_terrain.json")));
    }

    @Test
    void marsAndVenusKeepTheirV14RouterAndChangeOnlyTheirSurfaceAndBiomes() throws IOException {
        Path earlier = Path.of("src", "generated", "v1.4", "resources", "data", NS, "worldgen", "noise_settings");
        for (String body : List.of("mars", "venus")) {
            JsonObject before = json(earlier.resolve(body + ".json"));
            JsonObject after = json(WORLDGEN.resolve("noise_settings/" + body + ".json"));
            assertEquals(before.keySet(), after.keySet(), body);
            for (String key : before.keySet()) {
                if (!key.equals("surface_rule")) {
                    assertEquals(before.get(key), after.get(key), body + " " + key + " changed");
                }
            }
            assertTrue(!before.get("surface_rule").equals(after.get("surface_rule")), body + " surface unchanged");
        }
        assertTrue(json(WORLDGEN.resolve("noise_settings/mars.json")).get("surface_rule").toString()
                .contains(NS + ":ferric_sand"));
        assertFalse(json(WORLDGEN.resolve("noise_settings/venus.json")).get("surface_rule").toString()
                .contains("yellow_terracotta"));
        JsonObject mars = json(DATA.resolve(NS + "/dimension/mars.json")).getAsJsonObject("generator");
        assertEquals("minecraft:fixed", mars.getAsJsonObject("biome_source").get("type").getAsString());
        assertEquals(NS + ":ferric_regolith", mars.getAsJsonObject("biome_source").get("biome").getAsString());
        JsonObject venus = json(DATA.resolve(NS + "/dimension/venus.json")).getAsJsonObject("generator");
        assertEquals(NS + ":patches", venus.getAsJsonObject("biome_source").get("type").getAsString());
        assertEquals(List.of(NS + ":volcanic", NS + ":volcanic_lowlands"),
                strings(venus.getAsJsonObject("biome_source").getAsJsonArray("biomes")));
    }

    @Test
    void theBiomesSpawnNothingCarveNothingAndCarryTheirFeatures() throws IOException {
        Map<String, List<String>> features = Map.of(
                "regolith_highlands", ores("moon"), "regolith_lowlands", ores("moon"), "ferric_regolith", ores("mars"),
                "volcanic", List.of(NS + ":charred_tree"), "volcanic_lowlands", List.of());
        for (Map.Entry<String, List<String>> biome : features.entrySet()) {
            JsonObject json = json(WORLDGEN.resolve("biome/" + biome.getKey() + ".json"));
            assertFalse(json.get("has_precipitation").getAsBoolean());
            for (Map.Entry<String, JsonElement> category : json.getAsJsonObject("spawners").entrySet()) {
                assertEquals(0, category.getValue().getAsJsonArray().size(), biome.getKey() + " spawns");
            }
            JsonElement carvers = json.get("carvers");
            assertTrue(carvers == null || carvers.toString().equals("{}") || carvers.toString().equals("[]"),
                    biome.getKey() + " carves: " + carvers);
            List<String> all = new ArrayList<>();
            json.getAsJsonArray("features").forEach(step -> all.addAll(strings(step.getAsJsonArray())));
            assertEquals(new TreeSet<>(biome.getValue()), new TreeSet<>(all), biome.getKey());
        }
    }

    /** ADR-063 section 4: the Moon and Mars veins (count × size), y 4–40; dilithium follows the airless rule. */
    @Test
    void moonAndMarsVeinsAreTheContract() throws IOException {
        Map<String, int[]> veins = Map.of("copper", new int[] {10, 6}, "tin", new int[] {10, 6},
                "rutile", new int[] {6, 6}, "aluminum", new int[] {1, 16}, "iridium", new int[] {1, 16});
        for (String body : List.of("moon", "mars")) {
            for (String ore : List.of("copper", "tin", "rutile", "aluminum", "iridium", "dilithium")) {
                int count = ore.equals("dilithium") ? (body.equals("moon") ? 10 : 1) : veins.get(ore)[0];
                int size = ore.equals("dilithium") ? 16 : veins.get(ore)[1];
                String name = body + "_" + ore + "_ore";
                JsonObject config = json(WORLDGEN.resolve("configured_feature/" + name + ".json"))
                        .getAsJsonObject("config");
                assertEquals(size, config.get("size").getAsInt(), name);
                JsonArray targets = config.getAsJsonArray("targets");
                assertEquals(1, targets.size(), name);
                JsonObject target = targets.get(0).getAsJsonObject();
                String block = ore.equals("copper") ? "minecraft:copper_ore" : NS + ":" + ore + "_ore";
                assertEquals(block, target.getAsJsonObject("state").get("Name").getAsString(), name);
                JsonObject rule = target.getAsJsonObject("target");
                if (body.equals("moon")) {
                    assertEquals("minecraft:stone_ore_replaceables", rule.get("tag").getAsString(), name);
                } else {
                    assertEquals("minecraft:red_sandstone", rule.get("block").getAsString(), name);
                }
                JsonArray placement = json(WORLDGEN.resolve("placed_feature/" + name + ".json"))
                        .getAsJsonArray("placement");
                JsonObject first = placement.get(0).getAsJsonObject();
                assertEquals(NS + ":server_switch", first.get("type").getAsString());
                assertEquals("planet_ores", first.get("switch").getAsString());
                assertEquals(count, placement.get(1).getAsJsonObject().get("count").getAsInt(), name);
                JsonObject height = placement.get(3).getAsJsonObject().getAsJsonObject("height");
                assertEquals(4, height.getAsJsonObject("min_inclusive").get("absolute").getAsInt(), name);
                assertEquals(40, height.getAsJsonObject("max_inclusive").get("absolute").getAsInt(), name);
            }
        }
        JsonArray tree = json(WORLDGEN.resolve("placed_feature/charred_tree.json")).getAsJsonArray("placement");
        assertEquals("charred_trees", tree.get(0).getAsJsonObject().get("switch").getAsString());
        assertEquals(10, tree.get(1).getAsJsonObject().get("chance").getAsInt());
    }

    @Test
    void theStructuresAndTheirSetsAreTheContract() throws IOException {
        JsonObject moon = json(WORLDGEN.resolve("structure/moon_crater.json"));
        assertEquals(NS + ":crater", moon.get("type").getAsString());
        assertEquals(8, moon.get("min_radius").getAsInt());
        assertEquals(48, moon.get("max_radius").getAsInt());
        assertEquals(5, moon.get("floor_min_y").getAsInt());
        assertEquals(44, moon.get("rim_max_y").getAsInt());
        assertEquals("raw_generation", moon.get("step").getAsString());
        JsonObject mars = json(WORLDGEN.resolve("structure/mars_crater.json"));
        assertEquals(250, mars.get("rim_max_y").getAsInt());
        assertEquals("surface_structures", json(WORLDGEN.resolve("structure/volcano.json")).get("step").getAsString());
        assertEquals("underground_structures", json(WORLDGEN.resolve("structure/geode.json")).get("step").getAsString());
        Map<String, int[]> sets = Map.of("moon_craters", new int[] {6, 3}, "mars_craters", new int[] {6, 3},
                "volcanoes", new int[] {16, 8}, "geodes", new int[] {8, 4});
        Set<Integer> salts = new java.util.HashSet<>();
        for (Map.Entry<String, int[]> set : sets.entrySet()) {
            JsonObject placement = json(WORLDGEN.resolve("structure_set/" + set.getKey() + ".json"))
                    .getAsJsonObject("placement");
            assertEquals(set.getValue()[0], placement.get("spacing").getAsInt(), set.getKey());
            assertEquals(set.getValue()[1], placement.get("separation").getAsInt(), set.getKey());
            assertTrue(salts.add(placement.get("salt").getAsInt()), "salts must differ");
        }
        Path tags = DATA.resolve(NS + "/tags/worldgen/biome/has_structure");
        assertEquals(List.of(NS + ":regolith_highlands", NS + ":regolith_lowlands"), values(tags.resolve("moon_crater.json")));
        assertEquals(List.of(NS + ":ferric_regolith"), values(tags.resolve("mars_crater.json")));
        assertEquals(List.of(NS + ":volcanic", NS + ":volcanic_lowlands"), values(tags.resolve("volcano.json")));
        assertEquals(List.of(NS + ":volcanic", NS + ":volcanic_lowlands"), values(tags.resolve("geode.json")));
        assertEquals(List.of("minecraft:iron_ore", "minecraft:gold_ore", "minecraft:copper_ore", NS + ":tin_ore",
                "minecraft:redstone_ore"), values(DATA.resolve(NS + "/tags/blocks/geode_ores.json")));
    }

    @Test
    void theSurfaceBlocksHaveLootModelsToolsAndNames() throws IOException {
        Path assets = Path.of("src", "generated", "v1.8", "resources", "assets");
        JsonObject english = json(assets.resolve(NS + "_v180/lang/en_us.json"));
        JsonObject chinese = json(assets.resolve(NS + "_v180/lang/zh_cn.json"));
        for (String block : List.of("moon_turf", "dark_moon_turf", "ferric_sand", "charcoal_log", "geode_shell")) {
            assertTrue(Files.isRegularFile(DATA.resolve(NS + "/loot_tables/blocks/" + block + ".json")), block);
            assertTrue(Files.isRegularFile(assets.resolve(NS + "/blockstates/" + block + ".json")), block);
            assertTrue(Files.isRegularFile(assets.resolve(NS + "/models/item/" + block + ".json")), block);
            assertTrue(english.has("block." + NS + "." + block) && chinese.has("block." + NS + "." + block), block);
        }
        for (String biome : List.of("regolith_highlands", "regolith_lowlands", "ferric_regolith", "volcanic",
                "volcanic_lowlands")) {
            assertTrue(english.has("biome." + NS + "." + biome) && chinese.has("biome." + NS + "." + biome), biome);
        }
        Path tags = DATA.resolve("minecraft/tags/blocks/mineable");
        assertEquals(List.of(NS + ":moon_turf", NS + ":dark_moon_turf", NS + ":ferric_sand"),
                values(tags.resolve("shovel.json")));
        assertEquals(List.of(NS + ":charcoal_log"), values(tags.resolve("axe.json")));
        assertTrue(values(tags.resolve("pickaxe.json")).contains(NS + ":geode_shell"));
        // Legacy: the geode needed the jackhammer at harvest level 2; until C18b, an iron pickaxe.
        assertTrue(values(DATA.resolve("minecraft/tags/blocks/needs_iron_tool.json")).contains(NS + ":geode_shell"));
    }

    /** The turfs and the geode shell drop themselves; the charcoal log drops one charcoal, as the legacy log did. */
    @Test
    void theCharcoalLogDropsCharcoalAndTheOtherSurfaceBlocksThemselves() throws IOException {
        for (String block : List.of("moon_turf", "dark_moon_turf", "ferric_sand", "geode_shell")) {
            JsonObject entry = firstEntry(block);
            assertEquals("minecraft:item", entry.get("type").getAsString(), block);
            assertEquals(NS + ":" + block, entry.get("name").getAsString(), block);
        }
        JsonObject entry = firstEntry("charcoal_log");
        assertEquals("minecraft:alternatives", entry.get("type").getAsString());
        JsonArray children = entry.getAsJsonArray("children");
        assertEquals(2, children.size());
        JsonObject silk = children.get(0).getAsJsonObject();
        assertEquals(NS + ":charcoal_log", silk.get("name").getAsString());
        assertEquals("minecraft:silk_touch", silk.getAsJsonArray("conditions").get(0).getAsJsonObject()
                .getAsJsonObject("predicate").getAsJsonArray("enchantments").get(0).getAsJsonObject()
                .get("enchantment").getAsString());
        JsonObject charcoal = children.get(1).getAsJsonObject();
        assertEquals("minecraft:charcoal", charcoal.get("name").getAsString());
        assertFalse(charcoal.has("conditions"));
        JsonObject bonus = charcoal.getAsJsonArray("functions").get(0).getAsJsonObject();
        assertEquals("minecraft:apply_bonus", bonus.get("function").getAsString());
        assertEquals("minecraft:fortune", bonus.get("enchantment").getAsString());
        assertEquals("minecraft:uniform_bonus_count", bonus.get("formula").getAsString());
        assertEquals(1, bonus.getAsJsonObject("parameters").get("bonusMultiplier").getAsInt());
    }

    private static JsonObject firstEntry(String block) throws IOException {
        JsonArray pools = json(DATA.resolve(NS + "/loot_tables/blocks/" + block + ".json")).getAsJsonArray("pools");
        assertEquals(1, pools.size(), block);
        JsonArray entries = pools.get(0).getAsJsonObject().getAsJsonArray("entries");
        assertEquals(1, entries.size(), block);
        return entries.get(0).getAsJsonObject();
    }

    private static List<String> ores(String body) {
        List<String> names = new ArrayList<>();
        for (String ore : List.of("copper", "tin", "rutile", "aluminum", "iridium", "dilithium")) {
            names.add(NS + ":" + body + "_" + ore + "_ore");
        }
        return names;
    }

    private static List<String> values(Path tag) throws IOException {
        return strings(json(tag).getAsJsonArray("values"));
    }

    /** How many rules of a sequence place {@code block}. */
    private static int turfs(JsonObject sequence, String block) {
        int count = 0;
        for (JsonElement rule : sequence.getAsJsonArray("sequence")) {
            if (rule.getAsJsonObject().getAsJsonObject("then_run").getAsJsonObject("result_state").get("Name")
                    .getAsString().equals(block)) {
                count++;
            }
        }
        return count;
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
