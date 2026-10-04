package io.github.sunthemoon.advancedrocketrycommunity.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog.Entry;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog.Material;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog.Product;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * ADR-063 sections 1, 2, 4 and 8 (A0): an audit of the committed v1.8 DataGen output against the material table:
 * recipes, tags, ore placement, language, models and the tool tags that supersede the v1.7 copies.
 */
class V180MaterialResourcesTest {
    private static final String NS = "advancedrocketrycommunity";
    private static final Path ROOT = Path.of("src", "generated", "v1.8", "resources");
    private static final Path DATA = ROOT.resolve("data");
    private static final Path RECIPES = DATA.resolve(NS).resolve("recipes");

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void rollingRecipesRollEveryPlateAndSheetExceptIronIngots() throws IOException {
        Set<String> expected = new TreeSet<>();
        for (Material material : Material.values()) {
            boolean ingot = material.has(Product.INGOT) || material.vanillaIngot();
            if (material.has(Product.PLATE) && ingot && material != Material.IRON) {
                expected.add("rolling_" + material.id() + "_plate");
            }
            if (material.has(Product.SHEET)) {
                expected.add("rolling_" + material.id() + "_sheet");
            }
        }
        Set<String> allRolling = new TreeSet<>(expected);
        allRolling.add("rolling_iron_bars");
        assertEquals(allRolling, names("rolling_"));
        // The same-ID kernel override is audited independently by V180KernelRecipeResourcesTest.
        for (String name : expected) {
            JsonObject recipe = json(RECIPES.resolve(name + ".json"));
            assertEquals(Set.of("type", "schema_version", "ingredient", "input_count", "fluid", "result",
                    "processing_time", "energy_per_tick"), recipe.keySet(), name);
            assertEquals(NS + ":rolling", recipe.get("type").getAsString());
            assertEquals(Set.of("tag"), recipe.getAsJsonObject("ingredient").keySet(), name);
            String material = name.substring("rolling_".length(), name.lastIndexOf('_'));
            assertEquals("forge:" + (name.endsWith("_sheet") ? "plates/" : "ingots/") + material,
                    recipe.getAsJsonObject("ingredient").get("tag").getAsString(), name);
            assertEquals(1, recipe.get("input_count").getAsInt());
            assertEquals("minecraft:water", recipe.getAsJsonObject("fluid").get("fluid").getAsString());
            assertEquals(100, recipe.getAsJsonObject("fluid").get("amount").getAsInt());
            assertEquals(300, recipe.get("processing_time").getAsInt());
            assertEquals(name.endsWith("_sheet") ? 200 : 20, recipe.get("energy_per_tick").getAsInt(), name);
        }
    }

    @Test
    void pressRecipesTurnStorageBlocksIntoFourPlatesAndOresIntoTwoDust() throws IOException {
        Set<String> expected = new TreeSet<>();
        for (Material material : Material.values()) {
            boolean block = material.has(Product.BLOCK) || material.vanillaIngot();
            if (material.has(Product.PLATE) && block) {
                expected.add("pressing_" + material.id() + "_plate");
            }
            // Titanium's ore is rutile, which waits for the electric arc furnace (ADR-063 section 1).
            boolean ore = (material.hasOwnOre() && material != Material.TITANIUM) || material.vanillaIngot();
            if (material.has(Product.DUST) && ore) {
                expected.add("pressing_" + material.id() + "_dust");
            }
        }
        assertEquals(expected, names("pressing_"));
        for (String name : expected) {
            JsonObject recipe = json(RECIPES.resolve(name + ".json"));
            assertEquals(Set.of("type", "schema_version", "ingredient", "result"), recipe.keySet(), name);
            assertEquals(1, recipe.get("schema_version").getAsInt());
            String material = name.substring("pressing_".length(), name.lastIndexOf('_'));
            boolean plate = name.endsWith("_plate");
            assertEquals("forge:" + (plate ? "storage_blocks/" : "ores/") + material,
                    recipe.getAsJsonObject("ingredient").get("tag").getAsString(), name);
            JsonObject result = recipe.getAsJsonObject("result");
            assertEquals(NS + ":" + material + (plate ? "_plate" : "_dust"), result.get("item").getAsString());
            assertEquals(plate ? 4 : 2, result.get("count").getAsInt(), name);
        }
    }

    @Test
    void rutileNeverSmeltsAndOtherOresSmeltAndBlast() throws IOException {
        Set<String> cooking = names("").stream().filter(name -> name.endsWith("_smelting")
                || name.endsWith("_blasting")).collect(Collectors.toSet());
        for (String name : cooking) {
            String text = Files.readString(RECIPES.resolve(name + ".json"));
            assertFalse(text.contains("rutile") || text.contains("ores/titanium"), name + " smelts rutile");
            String pair = name.endsWith("_smelting") ? name.replace("_smelting", "_blasting")
                    : name.replace("_blasting", "_smelting");
            assertTrue(cooking.contains(pair), name + " has no " + pair);
        }
        assertTrue(cooking.contains("tin_ingot_from_ore_smelting"));
        assertTrue(cooking.contains("dilithium_dust_from_ore_blasting"));
    }

    @Test
    void everyEntryHasItsTagsModelsAndBothLanguages() throws IOException {
        JsonObject english = json(ROOT.resolve("assets/" + NS + "_v180/lang/en_us.json"));
        JsonObject chinese = json(ROOT.resolve("assets/" + NS + "_v180/lang/zh_cn.json"));
        assertEquals(english.keySet(), chinese.keySet());
        for (Entry entry : MaterialCatalog.entries()) {
            String key = (entry.isBlock() ? "block." : "item.") + NS + "." + entry.id();
            assertTrue(english.has(key) && chinese.has(key), key);
            assertTrue(Files.isRegularFile(ROOT.resolve("assets/" + NS + "/models/item/" + entry.id() + ".json")),
                    entry.id());
            if (entry.isBlock()) {
                assertTrue(Files.isRegularFile(ROOT.resolve("assets/" + NS + "/blockstates/" + entry.id() + ".json")));
                assertTrue(Files.isRegularFile(DATA.resolve(NS + "/loot_tables/blocks/" + entry.id() + ".json")));
            }
            if (entry.product().isPresent()) {
                String path = MaterialTags.productTag(entry.material(), entry.product().get()).toString();
                assertTrue(tagValues("items", path).contains(NS + ":" + entry.id()), path);
            }
        }
        assertEquals(Set.of(NS + ":rutile_ore", NS + ":deepslate_rutile_ore"),
                new HashSet<>(tagValues("blocks", "forge:ores/titanium")));
        assertEquals(tagValues("blocks", "forge:ores/titanium"), tagValues("blocks", "forge:ores/rutile"));
        assertEquals(Set.of(NS + ":raw_rutile"), new HashSet<>(tagValues("items", "forge:raw_materials/rutile")));
        Set<String> coils = Set.of("aluminum", "copper", "gold", "iridium", "titanium").stream()
                .map(material -> "#" + NS + ":coils/" + material).collect(Collectors.toSet());
        assertEquals(coils, new HashSet<>(tagValues("blocks", NS + ":coils")));
        assertEquals(coils, new HashSet<>(tagValues("items", NS + ":coils")));
    }

    @Test
    void theToolTagsAreCompleteCopiesAndTheHardOresNeedIron() throws IOException {
        List<String> iron = tagValues("blocks", "minecraft:needs_iron_tool");
        List<String> stone = tagValues("blocks", "minecraft:needs_stone_tool");
        List<String> pickaxe = tagValues("blocks", "minecraft:mineable/pickaxe");
        Path earlier = Path.of("src", "generated", "v1.7", "resources", "data", "minecraft", "tags", "blocks");
        for (String name : List.of("needs_iron_tool.json", "mineable/pickaxe.json")) {
            for (JsonElement value : json(earlier.resolve(name)).getAsJsonArray("values")) {
                List<String> current = name.startsWith("needs") ? iron : pickaxe;
                assertTrue(current.contains(value.getAsString()), value + " dropped from " + name);
            }
        }
        for (String ore : List.of("rutile_ore", "deepslate_rutile_ore", "iridium_ore", "dilithium_ore",
                "deepslate_dilithium_ore")) {
            assertTrue(iron.contains(NS + ":" + ore), ore);
        }
        for (String block : List.of("tin_ore", "aluminum_ore", "titanium_block", "copper_coil", "small_plate_press")) {
            assertTrue(stone.contains(NS + ":" + block), block);
            assertTrue(pickaxe.contains(NS + ":" + block), block);
        }
    }

    /**
     * C15bR1-L5: the v1.8 root holds exactly the placed features of the contract (C15a's Overworld ores, C15b's Moon
     * and Mars ores and charred tree, C15c's Tau Ceti features), so an unexpected one fails here.
     */
    @Test
    void theV18RootHoldsExactlyTheContractsPlacedFeatures() throws IOException {
        Set<String> features;
        try (Stream<Path> files = Files.list(DATA.resolve(NS + "/worldgen/placed_feature"))) {
            features = files.map(path -> path.getFileName().toString().replace(".json", ""))
                    .collect(Collectors.toCollection(TreeSet::new));
        }
        Set<String> expected = new TreeSet<>(List.of("charred_tree", "alien_forest_grass", "crystal_cluster",
                "electric_mushrooms", "giant_swamp_tree", "inverted_pillar", "lightwood_tree",
                "stormland_charred_tree"));
        for (String ore : List.of("aluminum", "dilithium", "rutile", "tin")) {
            expected.add("overworld_" + ore + "_ore");
        }
        for (String body : List.of("moon", "mars")) {
            for (String ore : List.of("aluminum", "copper", "dilithium", "iridium", "rutile", "tin")) {
                expected.add(body + "_" + ore + "_ore");
            }
        }
        assertEquals(expected, features);
    }

    @Test
    void overworldOresAreFourSwitchedFeaturesInOneBiomeModifier() throws IOException {
        Path placed = DATA.resolve(NS + "/worldgen/placed_feature");
        Set<String> features;
        try (Stream<Path> files = Files.list(placed)) {
            features = files.map(path -> path.getFileName().toString().replace(".json", ""))
                    .filter(name -> name.startsWith("overworld_"))
                    .collect(Collectors.toCollection(TreeSet::new));
        }
        assertEquals(new TreeSet<>(List.of("overworld_aluminum_ore", "overworld_dilithium_ore",
                "overworld_rutile_ore", "overworld_tin_ore")), features);
        for (String feature : features) {
            JsonArray placement = json(placed.resolve(feature + ".json")).getAsJsonArray("placement");
            JsonObject first = placement.get(0).getAsJsonObject();
            assertEquals(NS + ":server_switch", first.get("type").getAsString(), feature);
            assertEquals("overworld_ores", first.get("switch").getAsString());
            assertEquals(Set.of("type", "switch"), first.keySet(), "the switch placement stays flat");
        }
        JsonObject modifier = json(DATA.resolve(NS + "/forge/biome_modifier/overworld_ores.json"));
        assertEquals("#minecraft:is_overworld", modifier.get("biomes").getAsString());
        assertEquals("underground_ores", modifier.get("step").getAsString());
        assertFalse(modifier.toString().contains("iridium"));
    }

    /**
     * C15aR1-L2: the Overworld numbers of ADR-063 section 4, written here from the contract (not from the code): veins
     * per chunk and blocks per vein, uniform between y -16 and 64, stone and deepslate targets with their own ores.
     */
    @Test
    void overworldVeinNumbersAndTargetsAreTheContract() throws IOException {
        Map<String, int[]> contract = Map.of("tin", new int[] {10, 6}, "rutile", new int[] {6, 6},
                "aluminum", new int[] {1, 16}, "dilithium", new int[] {1, 16});
        for (Map.Entry<String, int[]> vein : contract.entrySet()) {
            String ore = vein.getKey();
            String name = "overworld_" + ore + "_ore";
            JsonObject configured = json(DATA.resolve(NS + "/worldgen/configured_feature/" + name + ".json"));
            assertEquals("minecraft:ore", configured.get("type").getAsString());
            JsonObject config = configured.getAsJsonObject("config");
            assertEquals(vein.getValue()[1], config.get("size").getAsInt(), name + " size");
            assertEquals(0.0, config.get("discard_chance_on_air_exposure").getAsDouble(), name);
            JsonArray targets = config.getAsJsonArray("targets");
            assertEquals(2, targets.size(), name);
            assertTarget(targets.get(0).getAsJsonObject(), "minecraft:stone_ore_replaceables", NS + ":" + ore + "_ore");
            assertTarget(targets.get(1).getAsJsonObject(), "minecraft:deepslate_ore_replaceables",
                    NS + ":deepslate_" + ore + "_ore");

            JsonArray placement = json(DATA.resolve(NS + "/worldgen/placed_feature/" + name + ".json"))
                    .getAsJsonArray("placement");
            assertEquals(List.of(NS + ":server_switch", "minecraft:count", "minecraft:in_square",
                    "minecraft:height_range", "minecraft:biome"), types(placement), name);
            assertEquals(vein.getValue()[0], placement.get(1).getAsJsonObject().get("count").getAsInt(), name);
            JsonObject height = placement.get(3).getAsJsonObject().getAsJsonObject("height");
            assertEquals("minecraft:uniform", height.get("type").getAsString(), name);
            assertEquals(-16, height.getAsJsonObject("min_inclusive").get("absolute").getAsInt(), name);
            assertEquals(64, height.getAsJsonObject("max_inclusive").get("absolute").getAsInt(), name);
        }
    }

    /** C15aR1-L2: every ore carries its own ore tag as block and item, and rutile also the titanium tag. */
    @Test
    void everyOreCarriesItsOreTagsAsBlockAndItem() throws IOException {
        int ores = 0;
        for (Entry entry : MaterialCatalog.entries()) {
            if (entry.kind() != MaterialCatalog.Kind.STONE_ORE && entry.kind() != MaterialCatalog.Kind.DEEPSLATE_ORE) {
                continue;
            }
            ores++;
            String id = NS + ":" + entry.id();
            String stem = entry.material().oreName().orElseThrow();
            List<String> tags = stem.equals("rutile") ? List.of("forge:ores/rutile", "forge:ores/titanium")
                    : List.of("forge:ores/" + stem);
            for (String tag : tags) {
                assertTrue(tagValues("blocks", tag).contains(id), id + " missing from block tag " + tag);
                assertTrue(tagValues("items", tag).contains(id), id + " missing from item tag " + tag);
            }
            String ground = entry.kind() == MaterialCatalog.Kind.STONE_ORE ? "forge:ores_in_ground/stone"
                    : "forge:ores_in_ground/deepslate";
            assertTrue(tagValues("blocks", ground).contains(id), id + " missing from " + ground);
            assertTrue(tagValues("items", ground).contains(id), id + " missing from item " + ground);
        }
        assertEquals(9, ores);
        for (String registry : List.of("blocks", "items")) {
            List<String> umbrella = tagValues(registry, "forge:ores");
            assertEquals(new HashSet<>(umbrella).size(), umbrella.size(), "duplicate entries in " + registry + " forge:ores");
        }
    }

    private static void assertTarget(JsonObject target, String tag, String block) {
        assertEquals("minecraft:tag_match", target.getAsJsonObject("target").get("predicate_type").getAsString());
        assertEquals(tag, target.getAsJsonObject("target").get("tag").getAsString());
        assertEquals(block, target.getAsJsonObject("state").get("Name").getAsString());
    }

    private static List<String> types(JsonArray placement) {
        List<String> types = new java.util.ArrayList<>();
        placement.forEach(modifier -> types.add(modifier.getAsJsonObject().get("type").getAsString()));
        return types;
    }

    private static Set<String> names(String prefix) throws IOException {
        try (Stream<Path> files = Files.list(RECIPES)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith(prefix) && name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - ".json".length()))
                    .collect(Collectors.toCollection(TreeSet::new));
        }
    }

    private static List<String> tagValues(String registry, String tag) throws IOException {
        String namespace = tag.substring(0, tag.indexOf(':'));
        Path file = DATA.resolve(namespace).resolve("tags").resolve(registry)
                .resolve(tag.substring(tag.indexOf(':') + 1) + ".json");
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        List<String> values = new java.util.ArrayList<>();
        for (JsonElement value : json(file).getAsJsonArray("values")) {
            values.add(value.isJsonObject() ? value.getAsJsonObject().get("id").getAsString() : value.getAsString());
        }
        return values;
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
