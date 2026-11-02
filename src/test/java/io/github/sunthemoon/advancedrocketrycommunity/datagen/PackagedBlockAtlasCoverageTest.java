package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * W246-fix2 (Root decision R268, review finding W246-R2): every texture that a packaged block or item model names
 * must be a sprite of the 1.20.1 block atlas.
 *
 * <p>Since 1.19.3 the model bakery looks every block and item model texture up in
 * {@code minecraft:textures/atlas/blocks.png}. {@code SpriteResourceLoader} builds that atlas only from the sources
 * in the resource stack of {@code assets/minecraft/atlases/blocks.json}: vanilla's (the client resources, pinned here
 * by SHA-256), Forge's and any that the mod packages. A texture that no source stitches bakes to the missing sprite in
 * a real client. No server GameTest builds this atlas, so W246's sixteen imports in {@code textures/blocks/} and
 * {@code textures/items/} passed every earlier check.
 *
 * <p>The models and the mod's textures are the packaged resources ({@code build/resources/main}, after the build's
 * superseded-file exclusions), matched by exact path as in the JAR. A texture of another namespace must be in a
 * dependency JAR. The 1.20.1 source types {@code directory}, {@code single}, {@code paletted_permutations} and
 * {@code unstitch} add sprites. A {@code filter} source removes sprites in pack order and an unknown type is not
 * modelled, so either fails this test until it is. W249 follows ordinary {@code forge:obj} models through their OBJ
 * and MTL to every atlas texture. Formed hull renderers that bind textures directly are a separate route. Every check
 * runs before the assertions, and the result is written to {@code build/reports/block-atlas-coverage.md}.
 *
 * <p>W246-fix3 (R270): blockstate model references, registered item models (the shared registration capture) and all
 * parent chains must resolve too. Checking only the packaged models would accept a partial tree.
 */
class PackagedBlockAtlasCoverageTest {
    private static final String NAMESPACE = ModIdentity.MOD_ID;
    private static final String ATLAS = "assets/minecraft/atlases/blocks.json";
    /** The pinned 1.20.1 client's {@code assets/minecraft/atlases/blocks.json}, as in W229's client-1.20.1.jar. */
    private static final String VANILLA_SOURCES_SHA256 = "1b388e8c8a29884fe116803caf00249ab9150e5490bab735c567fabe8b39df6b";
    private static final Pattern MODEL = Pattern.compile("assets/[a-z0-9_.-]+/models/.+\\.json");
    private static final Pattern LOCATION = Pattern.compile("(?:[a-z0-9_.-]+:)?[a-z0-9/._-]+");
    private static final Path REPORT = Path.of("build", "reports", "block-atlas-coverage.md");

    private final Map<String, Boolean> dependencyFiles = new HashMap<>();
    private Set<String> packaged;

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void everyPackagedModelTextureIsStitchedIntoTheBlockAtlas() throws IOException, ReflectiveOperationException {
        Path root = packagedRoot();
        List<String> problems = new ArrayList<>();
        List<JsonObject> sources = new ArrayList<>();
        List<String> stack = new ArrayList<>();
        int vanilla = 0;
        boolean packagedInStack = false;
        for (URL url : Collections.list(getClass().getClassLoader().getResources(ATLAS))) {
            byte[] bytes = read(url);
            String digest = sha256(bytes);
            vanilla += digest.equals(VANILLA_SOURCES_SHA256) ? 1 : 0;
            packagedInStack |= url.getProtocol().equals("file") && file(url).equals(root.resolve(ATLAS));
            stack.add(url + " (SHA-256 " + digest + ")");
            for (JsonElement source : JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("sources")) {
                sources.add(source.getAsJsonObject());
            }
        }
        if (vanilla != 1) {
            problems.add("The atlas stack holds " + vanilla + " copies of the pinned vanilla 1.20.1 sources, not one: " + stack);
        }
        if (Files.isRegularFile(root.resolve(ATLAS)) && !packagedInStack) {
            problems.add("The packaged " + ATLAS + " is not in the atlas stack on the test classpath");
        }
        checkModels(root, RegisteredContentResourcesTest.requiredItemModels(), sources, stack, problems, REPORT);
    }

    /** Shared packaged-tree check; fixtures supply their own model roots and atlas sources. */
    void checkModels(Path root, Set<String> requiredItemModels, List<JsonObject> sources, List<String> stack,
            List<String> problems, Path report) throws IOException {
        dependencyFiles.clear();
        packaged = new TreeSet<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(file -> packaged.add(root.relativize(file).toString().replace('\\', '/')));
        }
        for (JsonObject source : sources) {
            String type = string(source, "type").replaceFirst("^minecraft:", "");
            if (!Set.of("directory", "single", "paletted_permutations", "unstitch").contains(type)) {
                problems.add("Atlas source type " + type + " is not modelled by this test: " + source);
            }
        }

        Map<String, List<String>> sprites = new TreeMap<>();
        int requiredModels = requiredModels(root, requiredItemModels, sprites, problems);
        Map<String, List<String>> uncovered = new TreeMap<>();
        int models = 0;
        for (String path : packaged) {
            if (!MODEL.matcher(path).matches()) {
                continue;
            }
            models++;
            JsonElement textures = JsonParser.parseString(Files.readString(root.resolve(path))).getAsJsonObject().get("textures");
            if (textures == null) {
                continue;
            }
            for (var texture : textures.getAsJsonObject().entrySet()) {
                String use = path.substring("assets/".length()) + "#" + texture.getKey();
                if (!texture.getValue().isJsonPrimitive() || !texture.getValue().getAsJsonPrimitive().isString()) {
                    problems.add(use + " is not a string");
                    continue;
                }
                String value = texture.getValue().getAsString();
                if (value.startsWith("#")) {
                    continue;
                }
                if (!LOCATION.matcher(value).matches()) {
                    problems.add(use + " names " + value + ", which is not a resource location");
                    continue;
                }
                String sprite = value.contains(":") ? value : "minecraft:" + value;
                sprites.computeIfAbsent(sprite, key -> new ArrayList<>()).add(use);
            }
        }
        int references = sprites.values().stream().mapToInt(List::size).sum();
        sprites.forEach((sprite, uses) -> {
            if (!stitched(sprite, sources)) { uncovered.put(sprite, uses); }
        });
        writeReport(report, stack, models, requiredModels, references, uncovered, problems);

        assertTrue(models > 0 && references > 0, "No packaged model or texture reference was found under " + root);
        assertEquals(List.of(), problems);
        StringBuilder message = new StringBuilder();
        uncovered.forEach((sprite, uses) -> message.append("\n  ").append(sprite).append(" (PNG packaged: ")
                .append(exists(namespace(sprite), "assets/" + namespace(sprite) + "/textures/" + path(sprite) + ".png"))
                .append(") <- ").append(String.join(", ", uses)));
        assertTrue(uncovered.isEmpty(), uncovered.size() + " sprites of " + references + " texture references in " + models
                + " packaged models are in no block-atlas source:" + message);
    }

    /** Resolve roots and parents before the texture walk; missing roots cannot shrink the set being checked. */
    private int requiredModels(Path root, Set<String> requiredItemModels, Map<String, List<String>> sprites,
            List<String> problems) throws IOException {
        Map<String, List<String>> expected = new TreeMap<>();
        for (String item : requiredItemModels) {
            expected.computeIfAbsent(item, key -> new ArrayList<>()).add("registered item");
        }
        for (String file : packaged) {
            if (file.startsWith("assets/" + NAMESPACE + "/blockstates/") && file.endsWith(".json")) {
                blockstateModels(JsonParser.parseString(Files.readString(root.resolve(file))), file, expected, problems);
            }
            if (MODEL.matcher(file).matches()) {
                String namespace = file.substring("assets/".length(), file.indexOf("/models/"));
                String model = file.substring(file.indexOf("/models/") + "/models/".length(), file.length() - 5);
                expected.computeIfAbsent(namespace + ":" + model, key -> new ArrayList<>()).add("packaged model");
            }
        }
        Set<String> visited = new TreeSet<>();
        var pending = new ArrayDeque<>(expected.keySet());
        while (!pending.isEmpty()) {
            String model = pending.removeFirst();
            if (!visited.add(model)) { continue; }
            // ModelBakery synthesizes this marker; minecraft:item/generated inherits it, but no JSON file exists.
            // Only this exact built-in is exempt, not arbitrary minecraft:builtin/* parents.
            if (model.equals("minecraft:builtin/generated")) { continue; }
            String file = "assets/" + namespace(model) + "/models/" + path(model) + ".json";
            JsonObject definition;
            if (namespace(model).equals(NAMESPACE)) {
                if (!packaged.contains(file)) {
                    problems.add("Missing packaged model " + model + " <- " + expected.get(model));
                    continue;
                }
                definition = JsonParser.parseString(Files.readString(root.resolve(file))).getAsJsonObject();
            } else {
                URL url = getClass().getClassLoader().getResource(file);
                if (url == null) {
                    problems.add("Missing classpath model " + model + " <- " + expected.get(model));
                    continue;
                }
                definition = JsonParser.parseString(new String(read(url), StandardCharsets.UTF_8)).getAsJsonObject();
            }
            PackagedObjAtlasReferences.collect(definition, model, location -> {
                String resource = "assets/" + location.getNamespace() + "/" + location.getPath();
                if (location.getNamespace().equals(NAMESPACE)) {
                    return packaged.contains(resource) ? Files.readAllBytes(root.resolve(resource)) : null;
                }
                URL url = getClass().getClassLoader().getResource(resource);
                return url == null ? null : read(url);
            }, sprites, problems);
            if (definition.has("parent")) {
                String parent = modelLocation(definition.get("parent"), model + "#parent", problems);
                if (parent != null) {
                    expected.computeIfAbsent(parent, key -> new ArrayList<>()).add(model + "#parent");
                    pending.addLast(parent);
                }
            }
        }
        return visited.size();
    }

    /** Both variant and multipart forms may contain arrays. Inspect every model value, not just the first one. */
    private static void blockstateModels(JsonElement value, String use, Map<String, List<String>> expected,
            List<String> problems) {
        if (value.isJsonArray()) {
            for (int index = 0; index < value.getAsJsonArray().size(); index++) {
                blockstateModels(value.getAsJsonArray().get(index), use + "[" + index + "]", expected, problems);
            }
        } else if (value.isJsonObject()) {
            for (var entry : value.getAsJsonObject().entrySet()) {
                String field = use + "/" + entry.getKey();
                if (entry.getKey().equals("model")) {
                    String model = modelLocation(entry.getValue(), field, problems);
                    if (model != null) {
                        expected.computeIfAbsent(model, key -> new ArrayList<>()).add(field);
                    }
                } else {
                    blockstateModels(entry.getValue(), field, expected, problems);
                }
            }
        }
    }

    private static String modelLocation(JsonElement value, String use, List<String> problems) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || !LOCATION.matcher(value.getAsString()).matches()) {
            problems.add(use + " names " + value + ", which is not a model resource location");
            return null;
        }
        return location(value.getAsString());
    }

    /** Whether a source of the stack adds {@code sprite}, following the 1.20.1 sprite-source rules. */
    private boolean stitched(String sprite, List<JsonObject> sources) {
        String namespace = namespace(sprite);
        String path = path(sprite);
        for (JsonObject source : sources) {
            switch (string(source, "type").replaceFirst("^minecraft:", "")) {
                case "directory" -> {
                    // DirectoryLister: textures/<source>/<rest>.png in any namespace becomes <namespace>:<prefix><rest>.
                    String prefix = string(source, "prefix");
                    if (path.startsWith(prefix) && exists(namespace, "assets/" + namespace + "/textures/"
                            + string(source, "source") + "/" + path.substring(prefix.length()) + ".png")) {
                        return true;
                    }
                }
                case "single" -> {
                    // SingleFile: the resource's texture, under its optional sprite ID.
                    String resource = location(string(source, "resource"));
                    String id = source.has("sprite") ? location(string(source, "sprite")) : resource;
                    if (id.equals(sprite) && exists(namespace(resource),
                            "assets/" + namespace(resource) + "/textures/" + path(resource) + ".png")) {
                        return true;
                    }
                }
                case "paletted_permutations" -> {
                    // PalettedPermutations: <texture>_<permutation> for every texture and permutation key.
                    for (JsonElement texture : source.getAsJsonArray("textures")) {
                        for (String key : source.getAsJsonObject("permutations").keySet()) {
                            if ((location(texture.getAsString()) + "_" + key).equals(sprite)) {
                                return true;
                            }
                        }
                    }
                }
                case "unstitch" -> {
                    // Unstitcher: one sprite per region of the resource.
                    String resource = location(string(source, "resource"));
                    for (JsonElement region : source.getAsJsonArray("regions")) {
                        if (location(string(region.getAsJsonObject(), "sprite")).equals(sprite) && exists(namespace(resource),
                                "assets/" + namespace(resource) + "/textures/" + path(resource) + ".png")) {
                            return true;
                        }
                    }
                }
                default -> { }
            }
        }
        return false;
    }

    /** The mod's files are matched in the packaged tree by exact path; another namespace's in a dependency JAR. */
    private boolean exists(String namespace, String file) {
        if (namespace.equals(NAMESPACE)) {
            return packaged.contains(file);
        }
        return dependencyFiles.computeIfAbsent(file, name -> {
            try {
                return Collections.list(getClass().getClassLoader().getResources(name)).stream()
                        .anyMatch(url -> url.getProtocol().equals("jar"));
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    private static String location(String value) {
        return value.contains(":") ? value : "minecraft:" + value;
    }

    private static String namespace(String location) {
        return location.substring(0, location.indexOf(':'));
    }

    private static String path(String location) {
        return location.substring(location.indexOf(':') + 1);
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        assertNotNull(value, "Atlas source lacks " + key + ": " + object);
        return value.getAsString();
    }

    private static Path packagedRoot() {
        URL url = PackagedBlockAtlasCoverageTest.class.getClassLoader().getResource("assets/" + NAMESPACE + "/sounds.json");
        assertNotNull(url, "The packaged mod sounds.json is not on the test classpath");
        assertEquals("file", url.getProtocol(), "Packaged resources are not an exploded directory: " + url);
        return file(url).getParent().getParent().getParent();
    }

    private static Path file(URL url) {
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] read(URL url) throws IOException {
        try (InputStream input = url.openStream()) {
            return input.readAllBytes();
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void writeReport(Path output, List<String> stack, int models, int requiredModels, int references,
            Map<String, List<String>> uncovered,
            List<String> problems) throws IOException {
        StringBuilder report = new StringBuilder("# Block atlas coverage\n\n")
                .append("W246-fix2 (R268), fix3 (R270), W249 (R271). Model/OBJ/MTL references and block-atlas sources.\n\n")
                .append("- Model files: ").append(models).append('\n')
                .append("- Required/referenced models checked (including built-in marker): ").append(requiredModels).append('\n')
                .append("- Texture references checked: ").append(references).append('\n')
                .append("- Uncovered sprites: ").append(uncovered.size()).append('\n')
                .append("- Other problems: ").append(problems.size()).append("\n\n## Atlas stack\n\n");
        stack.forEach(entry -> report.append("- ").append(entry).append('\n'));
        report.append("\n## Uncovered sprites\n\n");
        uncovered.forEach((sprite, uses) -> report.append("- `").append(sprite).append("` <- ")
                .append(String.join(", ", uses)).append('\n'));
        report.append("\n## Other problems\n\n");
        problems.forEach(problem -> report.append("- ").append(problem).append('\n'));
        Files.createDirectories(output.getParent());
        Files.writeString(output, report.toString(), StandardCharsets.UTF_8);
    }
}
