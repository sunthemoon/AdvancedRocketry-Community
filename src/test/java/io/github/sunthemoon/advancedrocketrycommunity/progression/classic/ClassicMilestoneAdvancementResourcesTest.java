package io.github.sunthemoon.advancedrocketrycommunity.progression.classic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180ClassicMilestoneAdvancementData;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.ClassicInventoryAdvancements.Goal;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.ClassicMilestoneAdvancements.Milestone;
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

/** The milestone provider itself and its committed DataGen output. */
class ClassicMilestoneAdvancementResourcesTest {
    @TempDir
    Path temporary;

    @Test
    void dataProviderWritesExactlyTheElevenDeclaredFilesAndStableBytes() throws IOException {
        V180ClassicMilestoneAdvancementData provider = new V180ClassicMilestoneAdvancementData(new PackOutput(temporary));
        provider.run(CachedOutput.NO_CACHE).join();
        Map<String, JsonObject> expected = ClassicMilestoneAdvancements.files();
        assertEquals(11, expected.size());
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
            assertArrayEquals(before.get(index++), Files.readAllBytes(temporary.resolve(path)));
        }
    }

    @Test
    void committedGeneratedFilesMatchTheProvider() throws IOException {
        Path generated = Path.of("src/generated/v1.8/resources");
        for (var entry : ClassicMilestoneAdvancements.files().entrySet()) {
            Path file = generated.resolve(entry.getKey());
            assertEquals(entry.getValue(), read(file), "Stale generated " + file + "; rerun runData");
        }
        try (var files = Files.list(generated.resolve("data/advancedrocketrycommunity/advancements/classic"))) {
            assertEquals(17, files.count(), "The generated classic tree is not exactly the seventeen advancements");
        }
    }

    @Test
    void milestonePathsNeverOverwriteAnEarlierOrInventoryAdvancement() {
        List<Path> roots = new ArrayList<>(List.of(Path.of("src/main/resources"), Path.of("src/generated/resources")));
        for (String version : List.of("v0.2", "v0.3", "v0.4", "v0.5", "v0.6", "v0.7", "v0.8",
                "v1.2", "v1.3", "v1.4", "v1.5", "v1.6", "v1.7")) {
            roots.add(Path.of("src/generated", version, "resources"));
        }
        for (Milestone milestone : Milestone.values()) {
            for (Path root : roots) {
                assertFalse(Files.exists(root.resolve(milestone.file())), "Existing advancement collision: " + milestone.id());
            }
            for (Goal goal : Goal.values()) {
                assertFalse(goal.file().equals(milestone.file()), "Inventory collision: " + milestone.id());
            }
        }
    }

    private static JsonObject read(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
