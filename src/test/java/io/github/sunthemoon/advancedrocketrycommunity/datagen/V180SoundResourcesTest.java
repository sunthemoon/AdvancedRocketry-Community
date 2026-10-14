package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.sound.ClassicSound;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

/**
 * The committed v1.8 sound resources (ADR-066 sections 7.1 and 7.3): one current mod-namespace {@code sounds.json}
 * with the v0.1 {@code ui_select} and every classic sound event, each naming an existing file, every committed file
 * byte-identical to its generator, and exactly that {@code sounds.json} in the packaged jar.
 */
class V180SoundResourcesTest {
    private static final String MOD_ID = "advancedrocketrycommunity";
    private static final String PACKAGED = "assets/" + MOD_ID + "/sounds.json";
    private static final Path ASSETS = Path.of("src", "generated", "v1.8", "resources", "assets");
    private static final Path DEFINITIONS = ASSETS.resolve(MOD_ID).resolve("sounds.json");
    private static final Path FILES = ASSETS.resolve(MOD_ID).resolve("sounds");
    private static final Path V010_DEFINITIONS = Path.of("src", "generated", "resources", "assets", MOD_ID,
            "sounds.json");
    /** The v0.1 file's hash in docs/provenance/v0.1.0-generated-resources.json; the build only excludes it. */
    private static final String V010_SHA256 = "a76413e97bbbad694e6a25227286cb9fb3f3bcd1e4b41f4538fd6c58d78236e2";

    @Test
    void theModNamespaceFileDefinesExactlyTheElevenEventsWithExistingFiles() throws IOException {
        JsonObject definitions = definitions();
        Set<String> expected = Arrays.stream(ClassicSound.values()).map(ClassicSound::id)
                .collect(Collectors.toCollection(TreeSet::new));
        expected.add("ui_select");
        assertEquals(11, expected.size());
        assertEquals(expected, new TreeSet<>(definitions.keySet()));
        for (String event : definitions.keySet()) {
            JsonArray sounds = definitions.getAsJsonObject(event).getAsJsonArray("sounds");
            assertFalse(sounds.isEmpty(), event);
            for (JsonElement entry : sounds) {
                String[] name = name(entry).split(":", 2);
                Path file = Path.of("assets", name[0], "sounds", name[1] + ".ogg");
                assertEquals(1, resourceRoots().stream().filter(root -> Files.isRegularFile(root.resolve(file)))
                        .count(), event + " names " + file);
            }
        }
    }

    @Test
    void everyClassicEventsLocationIsItsRegistryIdAndNamesItsOwnFile() throws IOException {
        JsonObject definitions = definitions();
        for (ClassicSound sound : ClassicSound.values()) {
            assertEquals(MOD_ID, sound.registryId().getNamespace());
            assertEquals(sound.id(), sound.registryId().getPath());
            assertEquals(sound.registryId(), sound.location());
            JsonObject definition = definitions.getAsJsonObject(sound.id());
            assertEquals(sound.subtitle(), definition.get("subtitle").getAsString());
            JsonArray sounds = definition.getAsJsonArray("sounds");
            assertEquals(1, sounds.size(), sound.id());
            JsonElement entry = sounds.get(0);
            assertEquals(sound.file().toString(), name(entry));
            int distance = entry.isJsonObject() && entry.getAsJsonObject().has("attenuation_distance")
                    ? entry.getAsJsonObject().get("attenuation_distance").getAsInt() : 16;
            assertEquals(sound.range(), distance, sound.id());
            assertTrue(!entry.isJsonObject() || !entry.getAsJsonObject().has("stream")
                    || !entry.getAsJsonObject().get("stream").getAsBoolean(), sound.id());
        }
    }

    @Test
    void uiSelectIsTheUnchangedV010Definition() throws IOException {
        byte[] v010 = Files.readAllBytes(V010_DEFINITIONS);
        assertEquals(V010_SHA256, sha256(v010));
        JsonObject historical = JsonParser.parseString(new String(v010, StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(Set.of("ui_select"), historical.keySet());
        assertEquals(historical.get("ui_select"), definitions().get("ui_select"));
    }

    @Test
    void theV180NamespaceKeepsItsLanguageFilesButNoSoundDefinitions() {
        Path namespace = ASSETS.resolve(MOD_ID + "_v180");
        assertFalse(Files.exists(namespace.resolve("sounds.json")));
        assertTrue(Files.isRegularFile(namespace.resolve("lang").resolve("en_us.json")));
        assertTrue(Files.isRegularFile(namespace.resolve("lang").resolve("zh_cn.json")));
    }

    @Test
    void everyCommittedFileIsTheGeneratorsOutputAndNothingElseIsThere() throws IOException {
        Set<String> expected = Arrays.stream(ClassicSound.values()).map(sound -> sound.id() + ".ogg")
                .collect(Collectors.toCollection(TreeSet::new));
        try (Stream<Path> files = Files.list(FILES)) {
            assertEquals(expected, files.map(path -> path.getFileName().toString())
                    .collect(Collectors.toCollection(TreeSet::new)));
        }
        for (ClassicSound sound : ClassicSound.values()) {
            assertArrayEquals(V180SoundArt.ogg(sound), Files.readAllBytes(FILES.resolve(sound.id() + ".ogg")),
                    sound.id());
        }
    }

    @Test
    void thePackagedJarHasExactlyTheV180SoundsJsonAndEveryFileItNames() throws IOException {
        try (ZipFile jar = new ZipFile(System.getProperty("arce.runtimeJar"))) {
            List<String> definitionFiles = jar.stream().map(ZipEntry::getName)
                    .filter(name -> name.endsWith("/sounds.json")).toList();
            assertEquals(List.of(PACKAGED), definitionFiles);
            assertNull(jar.getEntry("assets/" + MOD_ID + "_v180/sounds.json"));
            try (var input = jar.getInputStream(jar.getEntry(PACKAGED))) {
                assertArrayEquals(Files.readAllBytes(DEFINITIONS), input.readAllBytes());
            }
            for (String event : definitions().keySet()) {
                for (JsonElement entry : definitions().getAsJsonObject(event).getAsJsonArray("sounds")) {
                    String[] name = name(entry).split(":", 2);
                    assertNotNull(jar.getEntry("assets/" + name[0] + "/sounds/" + name[1] + ".ogg"), event);
                }
            }
        }
    }

    private static JsonObject definitions() throws IOException {
        return JsonParser.parseString(Files.readString(DEFINITIONS, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    /** A sound entry is either its name or an object with a {@code name}. */
    private static String name(JsonElement entry) {
        return entry.isJsonPrimitive() ? entry.getAsString() : entry.getAsJsonObject().get("name").getAsString();
    }

    /** The packaged resource roots: the main resources and every generated version. */
    private static List<Path> resourceRoots() throws IOException {
        List<Path> roots = new ArrayList<>(List.of(Path.of("src", "main", "resources"),
                Path.of("src", "generated", "resources")));
        try (Stream<Path> versions = Files.list(Path.of("src", "generated"))) {
            versions.filter(path -> path.getFileName().toString().startsWith("v")).sorted()
                    .map(path -> path.resolve("resources")).filter(Files::isDirectory).forEach(roots::add);
        }
        return roots;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
