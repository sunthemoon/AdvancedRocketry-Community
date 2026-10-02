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
        assertEquals(expected, names("rolling_"));
        for (String name : expected) {
            JsonObject recipe = json(RECIPES.resolve(name + ".json"));
            assertEquals(Set.of("type", "schema_version", "ingredient", "input_count", "fluid", "result",
                    "processing_time", "energy_per_tick"), recipe.keySet(), name);
            assertEquals(NS + ":rolling", recipe.get("type").getAsString());
            // The kernel resolves its ingredient when recipes load, before tags are bound: items only.
            assertTrue(recipe.getAsJsonObject("ingredient").has("item"), name);
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

    @Test
    void overworldOresAreFourSwitchedFeaturesInOneBiomeModifier() throws IOException {
        Path placed = DATA.resolve(NS + "/worldgen/placed_feature");
        Set<String> features;
        try (Stream<Path> files = Files.list(placed)) {
            features = files.map(path -> path.getFileName().toString().replace(".json", ""))
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
