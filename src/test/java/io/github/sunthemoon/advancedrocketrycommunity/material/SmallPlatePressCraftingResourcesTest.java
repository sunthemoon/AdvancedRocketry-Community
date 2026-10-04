package io.github.sunthemoon.advancedrocketrycommunity.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Audits the generated acquisition route, separate from press processing recipes. */
class SmallPlatePressCraftingResourcesTest {
    private static final String ID = "advancedrocketrycommunity:small_plate_press";
    private static final Path ROOT = Path.of("src/generated/v1.8/resources/data/advancedrocketrycommunity");

    @Test
    void craftingUsesVanillaPistonAndIronIngotTag() throws IOException {
        JsonObject recipe = json(ROOT.resolve("recipes/small_plate_press.json"));
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        JsonObject keys = recipe.getAsJsonObject("key");
        assertEquals(Set.of("P", "I"), keys.keySet());
        assertEquals(Set.of("item"), keys.getAsJsonObject("P").keySet());
        assertEquals("minecraft:piston", keys.getAsJsonObject("P").get("item").getAsString());
        assertEquals(Set.of("tag"), keys.getAsJsonObject("I").keySet());
        assertEquals("forge:ingots/iron", keys.getAsJsonObject("I").get("tag").getAsString());
        assertEquals(ID, recipe.getAsJsonObject("result").get("item").getAsString());
        JsonObject result = recipe.getAsJsonObject("result");
        assertEquals(1, result.has("count") ? result.get("count").getAsInt() : 1);
    }

    @Test
    void normalizedGridPreservesOneCenteredPistonAboveThreeIngots() throws IOException {
        var pattern = json(ROOT.resolve("recipes/small_plate_press.json")).getAsJsonArray("pattern");
        assertEquals(2, pattern.size());
        assertEquals(" P ", pattern.get(0).getAsString());
        assertEquals("III", pattern.get(1).getAsString());
        String cells = pattern.get(0).getAsString() + pattern.get(1).getAsString();
        assertEquals(1, cells.chars().filter(c -> c == 'P').count());
        assertEquals(3, cells.chars().filter(c -> c == 'I').count());
    }

    @Test
    void recipeUnlockStartsFromAcquiringVanillaPiston() throws IOException {
        JsonObject unlock = json(ROOT.resolve("advancements/recipes/redstone/small_plate_press.json"));
        assertEquals("minecraft:recipes/root", unlock.get("parent").getAsString());
        JsonObject criteria = unlock.getAsJsonObject("criteria");
        assertEquals(Set.of("has_piston", "has_the_recipe"), criteria.keySet());
        JsonObject piston = criteria.getAsJsonObject("has_piston");
        assertEquals("minecraft:inventory_changed", piston.get("trigger").getAsString());
        var items = piston.getAsJsonObject("conditions").getAsJsonArray("items");
        assertEquals(1, items.size());
        var accepted = items.get(0).getAsJsonObject().getAsJsonArray("items");
        assertEquals(1, accepted.size());
        assertEquals("minecraft:piston", accepted.get(0).getAsString());
        var rewards = unlock.getAsJsonObject("rewards").getAsJsonArray("recipes");
        assertEquals(1, rewards.size());
        assertEquals(ID, rewards.get(0).getAsString());
        var requirements = unlock.getAsJsonArray("requirements");
        assertEquals(1, requirements.size());
        assertEquals(2, requirements.get(0).getAsJsonArray().size());
    }

    @Test
    void craftingIdHasExactlyOneDeclaredResourceDefinition() throws IOException {
        List<Path> roots = new ArrayList<>(List.of(Path.of("src/main/resources"), Path.of("src/generated/resources")));
        try (var versions = Files.list(Path.of("src/generated"))) {
            versions.filter(Files::isDirectory).filter(path -> path.getFileName().toString().startsWith("v"))
                    .map(path -> path.resolve("resources")).forEach(roots::add);
        }
        List<Path> matches = roots.stream().map(path -> path.resolve(
                "data/advancedrocketrycommunity/recipes/small_plate_press.json"))
                .filter(Files::isRegularFile).toList();
        assertEquals(1, matches.size());
        assertTrue(matches.get(0).startsWith(Path.of("src/generated/v1.8/resources")));
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
