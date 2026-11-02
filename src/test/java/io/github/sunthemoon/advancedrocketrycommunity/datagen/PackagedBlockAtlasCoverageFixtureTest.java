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

    @Test void objMaterialInBlockDirectoryPasses() throws IOException {
        objFixture("block/fixture");
        assertDoesNotThrow(this::check);
    }

    @Test void objMaterialOutsideAtlasFailsEvenWhenPngExists() throws IOException {
        objFixture("models/not_stitched");
        write("textures/models/not_stitched.png", "present but not stitched");
        rejected(NS + "models/not_stitched");
    }

    @Test void objCannotFallBackToClasspathWhenMeshMissing() throws IOException {
        objFixture("block/fixture");
        missing("models/block/fixture.obj", "fixture.obj");
    }

    @Test void missingObjLibraryFails() throws IOException {
        objFixture("block/fixture");
        missing("models/block/fixture.mtl", "fixture.mtl");
    }

    @Test void missingObjTextureFails() throws IOException {
        objFixture("block/absent");
        rejected(NS + "block/absent");
    }

    @Test void everyLibraryMaterialAndMapIsChecked() throws IOException {
        objFixture("block/fixture");
        write("models/block/fixture.mtl", "newmtl Material\nmap_Kd " + NS + "block/fixture\n"
                + "newmtl Unused\nmap_Kd " + NS + "block/fixture\nmap_Ks " + NS + "models/unstitched\n");
        write("textures/models/unstitched.png", "present but not stitched");
        rejected(NS + "models/unstitched");
    }

    @Test void objMaterialTheLibraryDoesNotDefineFails() throws IOException {
        // W249-R7: Forge's ObjMaterialLibrary.getMaterial throws for a usemtl name that the MTL does not define.
        objFixture("block/fixture");
        write("models/block/fixture.obj", "mtllib fixture.mtl\nv 0 0 0\nv 1 0 0\nv 0 1 0\n"
                + "vt 0 0\nvt 1 0\nvt 0 1\nusemtl Absent\nf 1/1 2/2 3/3\n");
        rejected("fixture.obj uses material Absent, which " + NS + "models/block/fixture.mtl does not define");
    }

    @Test void usedObjMaterialWithoutDiffuseMapFails() throws IOException {
        objFixture("block/fixture");
        write("models/block/fixture.mtl", "newmtl Material\nmap_Ks " + NS + "block/fixture\n");
        rejected("fixture.obj material Material has no map_Kd in " + NS + "models/block/fixture.mtl");
    }

    @Test void malformedObjFails() throws IOException {
        objFixture("block/fixture");
        write("models/block/fixture.obj", "not_an_obj\n");
        rejected("fixture.obj");
    }

    @Test void objMaterialOverrideFailsUntilModelled() throws IOException {
        objFixture("block/fixture");
        JsonObject model = json(Files.readString(root.resolve(ASSETS + "models/block/first.json")));
        model.addProperty("mtl_override", NS + "models/block/other.mtl");
        write("models/block/first.json", model.toString());
        rejected("mtl_override");
    }

    @Test void unsupportedModelLoaderFails() throws IOException {
        write("models/block/first.json", "{\"loader\":\"fixture:unknown\"}");
        rejected("loader fixture:unknown");
    }

    @Test void itemOverridesFailUntilModelled() throws IOException {
        write("models/item/fixture.json", "{\"parent\":\"" + NS + "block/first\",\"overrides\":[]}");
        rejected("overrides");
    }

    @Test void itemRootObjMaterialsAreStitched() throws IOException {
        itemObjFixture("block/fixture");
        assertDoesNotThrow(this::check);
    }

    @Test void itemRootMissingMeshFails() throws IOException {
        itemObjFixture("block/fixture");
        missing("models/block/fixture.obj", "fixture.obj");
    }

    @Test void itemRootMissingMaterialLibraryFails() throws IOException {
        itemObjFixture("block/fixture");
        missing("models/block/fixture.mtl", "fixture.mtl");
    }

    @Test void itemRootMaterialOutsideAtlasFails() throws IOException {
        itemObjFixture("models/not_stitched");
        write("textures/models/not_stitched.png", "present but not stitched");
        rejected(NS + "models/not_stitched");
    }

    @Test void itemRootUnknownLoaderStillFails() throws IOException {
        write("models/item/fixture.json", "{\"loader\":\"fixture:unknown\"}");
        rejected("loader fixture:unknown");
    }

    private void itemObjFixture(String texture) throws IOException {
        objFixture(texture);
        String obj = Files.readString(root.resolve(ASSETS + "models/block/first.json"));
        write("models/item/fixture.json", obj);
        write("models/block/first.json", "{\"parent\":\"" + NS + "block/base\"}");
    }

    private void objFixture(String texture) throws IOException {
        write("models/block/first.json", "{\"loader\":\"forge:obj\",\"model\":\"" + NS + "models/block/fixture.obj\"}");
        write("models/block/fixture.obj", "mtllib fixture.mtl\nv 0 0 0\nv 1 0 0\nv 0 1 0\n"
                + "vt 0 0\nvt 1 0\nvt 0 1\nusemtl Material\nf 1/1 2/2 3/3\n");
        write("models/block/fixture.mtl", "newmtl Material\nmap_Kd " + NS + texture + "\n");
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
