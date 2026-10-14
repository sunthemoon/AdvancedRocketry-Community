package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.data.PackOutput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Checks the packaged model tree, including native parents and inherited #texture variables. */
class V180ClosureResourcesTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void unexpectedOutputLayoutsAreRejectedBeforeWritingSiblingRoots() {
        assertThrows(IllegalArgumentException.class, () -> new V180ClosureModels(new PackOutput(Path.of("build/w49/export"))));
        assertNotNull(new V180ClosureModels(new PackOutput(Path.of("src/generated/v1.8/resources"))));
    }

    @Test void allThirteenAuditedModelTargetsMatchTheProviderAndThePackagedResource() throws IOException {
        assertEquals(13, V180ClosureModels.bindings().size());
        for (var binding : V180ClosureModels.bindings().entrySet()) {
            assertEquals(binding.getValue(), JsonParser.parseString(Files.readString(
                    Path.of("src/generated").resolve(binding.getKey()))), binding.getKey());
            String resource = binding.getKey().substring(binding.getKey().indexOf("assets/"));
            assertEquals(binding.getValue(), read(resource), resource);
        }
    }

    @Test void everyModelParentAndEveryInheritedFaceTextureResolvesInTheRuntimePack() throws IOException {
        for (var binding : V180ClosureModels.bindings().entrySet()) {
            JsonObject model = binding.getValue();
            Map<String, String> textures = new HashMap<>();
            Set<String> faceTextures = new HashSet<>();
            collect(model, textures, faceTextures, new HashSet<>());
            for (String reference : textures.values()) { resolveTexture(reference, textures); }
            for (String reference : faceTextures) { resolveTexture(reference, textures); }
        }
    }

    @Test void everyNewIconAndBlockFaceIsActuallyBoundByAModel() {
        Set<String> bound = new HashSet<>();
        V180ClosureModels.bindings().values().forEach(model ->
                model.getAsJsonObject("textures").entrySet().forEach(entry -> bound.add(entry.getValue().getAsString())));
        for (String texture : V180ClosureArtTest.textures()) {
            if (!texture.startsWith("models/armor/")) {
                assertTrue(bound.contains("advancedrocketrycommunity:" + texture), texture);
            }
        }
    }

    private static void collect(JsonObject model, Map<String, String> textures, Set<String> faces,
                                Set<String> parents) throws IOException {
        if (model.has("parent")) {
            String parent = model.get("parent").getAsString();
            if (!parent.startsWith("builtin/")) {
                assertTrue(parents.add(parent), "cyclic model parent " + parent);
                collect(read(resource(parent, "models", ".json")), textures, faces, parents);
            }
        }
        if (model.has("textures")) {
            model.getAsJsonObject("textures").entrySet().forEach(entry -> textures.put(entry.getKey(), entry.getValue().getAsString()));
        }
        if (model.has("elements")) {
            for (JsonElement element : model.getAsJsonArray("elements")) {
                element.getAsJsonObject().getAsJsonObject("faces").entrySet().forEach(face ->
                        faces.add(face.getValue().getAsJsonObject().get("texture").getAsString()));
            }
        }
    }

    private static void resolveTexture(String reference, Map<String, String> textures) throws IOException {
        Set<String> seen = new HashSet<>();
        while (reference.startsWith("#")) {
            assertTrue(seen.add(reference), "cyclic texture " + reference);
            reference = textures.get(reference.substring(1));
            assertNotNull(reference, "unbound model texture variable");
        }
        String resource = resource(reference, "textures", ".png");
        try (InputStream input = V180ClosureResourcesTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, "missing texture " + resource);
            assertTrue(input.readNBytes(8).length == 8, resource);
        }
    }

    private static JsonObject read(String resource) throws IOException {
        try (InputStream input = V180ClosureResourcesTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, "missing model " + resource);
            return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static String resource(String id, String directory, String suffix) {
        String[] parts = id.contains(":") ? id.split(":", 2) : new String[] {"minecraft", id};
        return "assets/" + parts[0] + "/" + directory + "/" + parts[1] + suffix;
    }
}
