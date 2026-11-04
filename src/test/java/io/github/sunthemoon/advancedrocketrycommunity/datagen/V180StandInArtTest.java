package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import javax.imageio.ImageIO;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class V180StandInArtTest {
    private static final Path ROOT = Path.of("src/generated/v1.8/resources");
    private static final String ASSETS = V180StandInArtData.ASSETS;
    private static final Path RECORD = Path.of("docs/provenance/v1.8.0-w275-stand-in-art.json");

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    static Map<String, JsonObject> allModels() {
        Map<String, JsonObject> models = new LinkedHashMap<>(V180StandInArtData.models());
        String assembler = ASSETS + "models/block/precision_assembler.json";
        models.put(assembler, V180ClosureModels.bindings().get(assembler));
        V180EquipmentData.clientFiles().forEach((path, json) -> {
            if (V180StandInArt.CONTAINERS.stream().anyMatch(id -> path.equals(ASSETS + "models/item/" + id + ".json"))) {
                models.put(path, json);
            }
        });
        return models;
    }

    @Test void deterministicTexturesHaveOpaqueFacesAndTransparentCylinderMargins() throws Exception {
        assertEquals(13, V180StandInArt.textures().size());
        Set<String> distinct = new HashSet<>();
        for (String name : V180StandInArt.textures()) {
            byte[] png = V180StandInArt.png(name);
            assertArrayEquals(png, V180StandInArt.png(name), name);
            assertArrayEquals(png, Files.readAllBytes(ROOT.resolve(ASSETS + "textures/" + name + ".png")), name);
            assertTrue(distinct.add(sha(png)), "duplicate texture " + name);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            assertEquals(16, image.getWidth()); assertEquals(16, image.getHeight());
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int alpha = image.getRGB(x, y) >>> 24;
                    if (name.startsWith("block/")) { assertEquals(255, alpha, name); }
                    else if (x == 0 || x == 15 || y == 0 || y == 15) { assertEquals(0, alpha, name); }
                }
            }
        }
        assertThrows(IllegalArgumentException.class, () -> V180StandInArt.png("block/not_a_texture"));
    }

    @Test void portArrowsReverseWhileGasBandsAndTierCountsDiffer() {
        int[] input = V180StandInArt.pixels("block/machine_item_input_port");
        int[] output = V180StandInArt.pixels("block/machine_item_output_port");
        for (int y = 5; y <= 9; y++) {
            for (int x = 5; x <= 10; x++) { assertEquals(input[y * 16 + x], output[y * 16 + 15 - x]); }
        }
        assertNotEquals(input[5 * 16 + 8], output[5 * 16 + 8]);
        String[] tiers = {"low_pressure_tank", "pressure_tank", "high_pressure_tank", "super_high_pressure_tank"};
        for (int i = 0; i < tiers.length; i++) {
            int[] p = V180StandInArt.pixels("item/" + tiers[i] + "_cylinder");
            int bands = 0;
            for (int y = 6; y <= 12; y += 2) { if (p[y * 16 + 6] != 0xFF95A6A0) { bands++; } }
            assertEquals(i + 1, bands, tiers[i]);
        }
        int[] empty = V180StandInArt.pixels("item/empty_canister_cylinder");
        int[] oxygen = V180StandInArt.pixels("item/oxygen_canister_cylinder");
        int[] hydrogen = V180StandInArt.pixels("item/hydrogen_canister_cylinder");
        assertNotEquals(empty[6 * 16 + 6], oxygen[6 * 16 + 6]);
        assertNotEquals(oxygen[6 * 16 + 6], hydrogen[6 * 16 + 6]);
        assertEquals(0xFFD3A65E, empty[16 + 6], "top valve");
    }

    @Test void bindingsChangeOnlyTexturesAndRetainAllHistoricalModelShapes() throws Exception {
        assertEquals(16, allModels().size());
        for (var entry : allModels().entrySet()) {
            String path = entry.getKey();
            String version = path.contains("canister.json") ? "v0.2" : path.contains("models/block/") ? "v1.2" : "v1.8";
            JsonObject old = JsonParser.parseString(Files.readString(Path.of("src/generated", version, "resources", path))).getAsJsonObject();
            JsonObject changed = entry.getValue().deepCopy();
            assertEquals(old.getAsJsonObject("textures").keySet(), changed.getAsJsonObject("textures").keySet(), path);
            old.remove("textures"); changed.remove("textures");
            assertEquals(old, changed, path);
            assertEquals(entry.getValue(), JsonParser.parseString(Files.readString(ROOT.resolve(path))), path);
            for (var face : entry.getValue().getAsJsonObject("textures").entrySet()) {
                assertTrue(face.getValue().getAsString().startsWith("advancedrocketrycommunity:"), path);
            }
        }
        for (String port : V180StandInArtData.PORTS) {
            JsonObject faces = allModels().get(ASSETS + "models/block/" + port + ".json").getAsJsonObject("textures");
            assertEquals("advancedrocketrycommunity:block/machine_panel_side", faces.get("side").getAsString());
            assertEquals("advancedrocketrycommunity:block/machine_casing_top", faces.get("bottom").getAsString());
        }
    }

    @Test void providerWritesOnlyItsDeclaredFilesInsideArbitraryOutput(@TempDir Path output) throws Exception {
        new V180StandInArtData(new PackOutput(output)).run(CachedOutput.NO_CACHE).get(10, TimeUnit.SECONDS);
        Set<String> expected = new HashSet<>(V180StandInArtData.models().keySet());
        V180StandInArt.textures().forEach(name -> expected.add(ASSETS + "textures/" + name + ".png"));
        Set<String> actual = new HashSet<>();
        try (var files = Files.walk(output)) {
            files.filter(Files::isRegularFile).forEach(path -> actual.add(output.relativize(path).toString().replace('\\', '/')));
        }
        assertEquals(expected, actual);
    }

    @Test void provenanceNamesEveryNewTextureAndEveryChangedModel() throws Exception {
        JsonObject record = JsonParser.parseString(Files.readString(RECORD)).getAsJsonObject();
        assertEquals(2, record.get("schema_version").getAsInt());
        Set<String> expected = new HashSet<>();
        allModels().keySet().forEach(path -> expected.add("src/generated/v1.8/resources/" + path));
        V180StandInArt.textures().forEach(name -> expected.add("src/generated/v1.8/resources/" + ASSETS + "textures/" + name + ".png"));
        Set<String> actual = new HashSet<>();
        for (var value : record.getAsJsonArray("entries")) {
            JsonObject entry = value.getAsJsonObject();
            String path = entry.get("target_path").getAsString();
            assertTrue(actual.add(path));
            assertEquals("NEW", entry.get("status").getAsString());
            assertEquals("MIT", entry.get("license").getAsString());
            assertEquals(sha(Files.readAllBytes(Path.of(path))), entry.get("target_sha256").getAsString(), path);
            assertEquals(sourceHash(entry), entry.get("source_sha256").getAsString(), path);
        }
        assertEquals(expected, actual);
        String status = record.getAsJsonObject("review").get("status").getAsString();
        assertTrue(Set.of("PENDING_HUMAN_REVIEW", "APPROVED").contains(status));
    }

    @Test void runtimeJarHasExactlyOneCopyOfEveryModelAndItsNewTextures() throws Exception {
        String runtimeJar = System.getProperty("arce.runtimeJar");
        assertNotNull(runtimeJar);
        Set<String> resources = new HashSet<>(allModels().keySet());
        V180StandInArt.textures().forEach(name -> resources.add(ASSETS + "textures/" + name + ".png"));
        try (JarFile jar = new JarFile(runtimeJar)) {
            for (String resource : resources) {
                assertEquals(1, jar.stream().filter(e -> e.getName().equals(resource)).count(), resource);
                try (var stream = jar.getInputStream(jar.getJarEntry(resource))) {
                    assertArrayEquals(Files.readAllBytes(ROOT.resolve(resource)), stream.readAllBytes(), resource);
                }
            }
        }
    }

    @Test void screeningBindsAllNewImagesToBothPinnedVanillaClients() throws Exception {
        JsonObject screening = JsonParser.parseString(Files.readString(RECORD)).getAsJsonObject().getAsJsonObject("screening");
        assertNotNull(screening);
        assertEquals("bb9935fbf7fcc62818084b68ef4784208fb15304adc62db666f5462374c04516",
                sha(Files.readAllBytes(Path.of("tools/audit/screen_generated_art.py"))));
        Set<String> clients = new HashSet<>();
        JsonObject vanilla = screening.getAsJsonObject("vanilla");
        assertEquals(Set.of("1.12.2", "1.20.1"), vanilla.keySet());
        vanilla.entrySet().forEach(value -> clients.add(value.getValue().getAsJsonObject().get("sha256").getAsString()));
        assertEquals(Set.of("8ada07da5ee77dad3527bd7278fbd05ee1fc8a597813b216a871a2d7d64cc64f",
                "56b71336d2b4fdffd197f56595b0da93e32a946f78f382a299b8f4b92758bb0f"), clients);
        JsonObject screened = screening.getAsJsonObject("targets");
        assertEquals(13, screened.size());
        for (String name : V180StandInArt.textures()) {
            JsonObject target = screened.getAsJsonObject(name);
            assertNotNull(target, name);
            assertEquals("CLEAR", target.get("verdict").getAsString(), name);
            assertEquals(sha(V180StandInArt.png(name)), target.get("sha256").getAsString(), name);
        }
    }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String sourceHash(JsonObject entry) throws Exception {
        String path = entry.get("source_path").getAsString();
        // Shared providers may change for a later slice; their recorded revision remains auditable.
        assertTrue(entry.has("authoring_commit"), path);
        String revision = entry.get("authoring_commit").getAsString();
        assertTrue(revision.matches("[0-9a-f]{40}"), path);
        Process git = new ProcessBuilder("git", "show", revision + ":" + path).start();
        byte[] bytes = git.getInputStream().readAllBytes();
        assertTrue(git.waitFor(10, TimeUnit.SECONDS));
        assertEquals(0, git.exitValue(), path);
        return sha(bytes);
    }
}
