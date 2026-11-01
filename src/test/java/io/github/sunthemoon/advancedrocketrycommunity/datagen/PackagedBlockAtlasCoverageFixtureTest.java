package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Small packaged trees exercise the same model and texture check as the real packaged resources. */
class PackagedBlockAtlasCoverageFixtureTest {
    private static final String NS = ModIdentity.MOD_ID + ":";
    private static final String ASSETS = "assets/" + ModIdentity.MOD_ID + "/";
    @TempDir Path root;
    private List<JsonObject> sources;

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @BeforeEach void completeTree() throws IOException {
        sources = new ArrayList<>(List.of(json("{\"type\":\"directory\",\"source\":\"block\",\"prefix\":\"block/\"}")));
        write("blockstates/fixture.json", "{\"variants\":{\"\":[{\"model\":\"" + NS + "block/first\"},"
                + "{\"model\":\"" + NS + "block/second\"}]},\"multipart\":[{\"apply\":[{\"model\":\"" + NS
                + "block/third\"},{\"model\":\"" + NS + "block/fourth\"}]}]}");
        for (String name : List.of("first", "second", "third", "fourth")) {
            write("models/block/" + name + ".json", "{\"parent\":\"" + NS + "block/base\"}");
        }
        write("models/block/base.json", "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\""
                + NS + "block/fixture\"}}");
        write("models/item/fixture.json", "{\"parent\":\"" + NS + "block/first\"}");
        write("textures/block/fixture.png", "fixture existence only; no PNG decoding in the atlas check");
    }

    @Test void completeFixturePasses() {
        assertDoesNotThrow(this::check);
    }

    @Test void missingModelNamedByVariantFails() throws IOException {
        missing("models/block/second.json", NS + "block/second");
    }

    @Test void missingModelNamedByMultipartFails() throws IOException {
        missing("models/block/fourth.json", NS + "block/fourth");
    }

    @Test void missingParentFails() throws IOException {
        // Keep texture coverage nonempty so this cannot pass by merely rejecting empty input.
        write("models/block/unreachable.json", "{\"textures\":{\"all\":\"" + NS + "block/fixture\"}}");
        missing("models/block/base.json", NS + "block/base");
    }

    @Test void missingRequiredItemModelFails() throws IOException {
        missing("models/item/fixture.json", NS + "item/fixture");
    }

    @Test void missingTransitiveParentFails() throws IOException {
        write("models/block/base.json", "{\"parent\":\"" + NS + "block/intermediate\",\"textures\":{\"all\":\""
                + NS + "block/fixture\"}}");
        write("models/block/intermediate.json", "{\"parent\":\"" + NS + "block/missing\"}");
        rejected(NS + "block/missing");
    }

    @Test void missingForeignModelFails() throws IOException {
        write("models/block/first.json", "{\"parent\":\"fixture_dependency:block/missing\"}");
        rejected("Missing classpath model fixture_dependency:block/missing");
    }

    @Test void modModelCannotFallBackToTheClasspath() throws IOException {
        // This model exists in the real mod tree, but not in this fixture's packaged tree.
        write("models/block/first.json", "{\"parent\":\"" + NS + "block/arc_furnace\"}");
        rejected("Missing packaged model " + NS + "block/arc_furnace");
    }

    @Test void generatedItemParentResolvesTheExactBuiltInMarker() throws IOException {
        write("models/item/fixture.json", "{\"parent\":\"minecraft:item/generated\"}");
        assertDoesNotThrow(this::check);
    }

    @Test void otherBuiltInNamesAreNotExempt() throws IOException {
        write("models/item/fixture.json", "{\"parent\":\"minecraft:builtin/not_a_real_model\"}");
        rejected("Missing classpath model minecraft:builtin/not_a_real_model");
    }

    @Test void properSubsetFailsEvenWithAStitchedTexture() throws IOException {
        try (var models = Files.walk(root.resolve(ASSETS + "models"))) {
            for (Path file : models.filter(Files::isRegularFile).toList()) {
                if (!file.equals(root.resolve(ASSETS + "models/block/base.json"))) { Files.delete(file); }
            }
        }
        rejected(NS + "block/first");
    }

    @Test void emptyInputFails() throws IOException {
        try (var models = Files.walk(root.resolve(ASSETS + "models"))) {
            for (Path file : models.filter(Files::isRegularFile).toList()) { Files.delete(file); }
        }
        rejected("No packaged model or texture reference");
    }

    @Test void filterSourceFails() {
        sources.add(json("{\"type\":\"filter\",\"pattern\":{\"namespace\":\"" + ModIdentity.MOD_ID + "\"}}"));
        rejected("Atlas source type filter");
    }

    @Test void unknownSourceFails() {
        sources.add(json("{\"type\":\"fixture:unknown\"}"));
        rejected("Atlas source type fixture:unknown");
    }

    @Test void unreachablePackagedModelStillChecksItsTexture() throws IOException {
        write("models/block/unreachable.json", "{\"textures\":{\"all\":\"" + NS + "outside/not_stitched\"}}");
        rejected(NS + "outside/not_stitched");
    }

    private void missing(String file, String model) throws IOException {
        Files.delete(root.resolve(ASSETS + file));
        rejected(model);
    }

    private void rejected(String message) {
        AssertionError failure = assertThrows(AssertionError.class, this::check);
        assertTrue(failure.getMessage().contains(message), failure::getMessage);
    }

    private void check() throws IOException {
        new PackagedBlockAtlasCoverageTest().checkModels(root, Set.of(NS + "item/fixture"), sources,
                List.of("fixture atlas"), new ArrayList<>(), root.resolve("report.md"));
    }

    private void write(String file, String value) throws IOException {
        Path path = root.resolve(ASSETS + file);
        Files.createDirectories(path.getParent());
        Files.writeString(path, value);
    }

    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
}
