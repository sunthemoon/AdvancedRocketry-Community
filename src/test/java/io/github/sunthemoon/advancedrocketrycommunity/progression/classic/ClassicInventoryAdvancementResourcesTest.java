package io.github.sunthemoon.advancedrocketrycommunity.progression.classic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180ClassicAdvancementData;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180ClassicAdvancementLanguage;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.ClassicInventoryAdvancements.Goal;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the provider itself; Root separately verifies integrated resources and packaged singletons. */
class ClassicInventoryAdvancementResourcesTest {
    @TempDir
    Path temporary;

    @Test
    void dataProviderWritesExactlyTheSixDeclaredFilesAndStableBytes() throws IOException {
        V180ClassicAdvancementData provider = new V180ClassicAdvancementData(new PackOutput(temporary));
        provider.run(CachedOutput.NO_CACHE).join();
        Map<String, JsonObject> expected = ClassicInventoryAdvancements.files();
        try (var files = Files.walk(temporary)) {
            assertEquals(expected.keySet().stream().sorted().toList(), files.filter(Files::isRegularFile)
                    .map(path -> temporary.relativize(path).toString().replace('\\', '/')).sorted().toList());
        }
        List<byte[]> before = new ArrayList<>();
        for (String path : expected.keySet()) {
            assertEquals(expected.get(path), read(temporary.resolve(path)));
            before.add(Files.readAllBytes(temporary.resolve(path)));
        }
        provider.run(CachedOutput.NO_CACHE).join();
        int index = 0;
        for (String path : expected.keySet()) {
            org.junit.jupiter.api.Assertions.assertArrayEquals(before.get(index++), Files.readAllBytes(temporary.resolve(path)));
        }
    }

    @Test
    void newPathsNeverOverwriteAnEarlierDeclaredAdvancement() {
        for (Goal goal : Goal.values()) {
            for (Path root : historicalRoots()) {
                assertFalse(Files.exists(root.resolve(goal.file())), "Existing advancement collision: " + goal.id());
            }
        }
    }

    @Test
    void bothLanguageAdditionsDoNotOverrideEarlierOrExistingV18Labels() throws IOException {
        for (String language : List.of("en_us", "zh_cn")) {
            Map<String, String> added = V180ClassicAdvancementLanguage.translations(language.equals("zh_cn"));
            List<Path> roots = new ArrayList<>(historicalRoots());
            roots.add(Path.of("src/generated/v1.8/resources"));
            for (Path root : roots) {
                for (String namespace : List.of("advancedrocketrycommunity", "advancedrocketrycommunity_v180")) {
                    Path lang = root.resolve("assets/" + namespace + "/lang/" + language + ".json");
                    if (Files.isRegularFile(lang)) {
                        JsonObject existing = read(lang);
                        for (String key : added.keySet()) {
                            // An integrated copy may already contain exactly this leaf's labels.
                            assertTrue(!existing.has(key) || existing.get(key).getAsString().equals(added.get(key)), key);
                        }
                    }
                }
            }
        }
    }

    @Test
    void everyAcquisitionTargetHasAnExistingCraftingRecipeExceptVanillaTable() throws IOException {
        Map<Goal, String> versions = Map.of(Goal.BLOCK_PRESS, "v1.8", Goal.ROLLING, "v1.2",
                Goal.ELECTROLYSIS, "v0.2", Goal.SUITED_UP, "v0.4", Goal.WARP_CORE, "v1.5");
        for (var entry : versions.entrySet()) {
            for (String id : entry.getKey().items()) {
                String suffix = id.substring(id.indexOf(':') + 1);
                Path recipe = Path.of("src/generated", entry.getValue(), "resources/data/advancedrocketrycommunity/recipes",
                        suffix + ".json");
                JsonObject json = read(recipe);
                assertTrue(json.get("type").getAsString().startsWith("minecraft:crafting_"), id);
                assertEquals(id, json.getAsJsonObject("result").get("item").getAsString());
            }
        }
    }

    private static List<Path> historicalRoots() {
        List<Path> roots = new ArrayList<>(List.of(Path.of("src/main/resources"), Path.of("src/generated/resources")));
        for (String version : List.of("v0.2", "v0.3", "v0.4", "v0.5", "v0.6", "v0.7", "v0.8",
                "v1.2", "v1.3", "v1.4", "v1.5", "v1.6", "v1.7")) {
            roots.add(Path.of("src/generated", version, "resources"));
        }
        return roots;
    }

    private static JsonObject read(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
