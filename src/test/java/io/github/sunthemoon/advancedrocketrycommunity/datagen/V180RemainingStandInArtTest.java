package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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

class V180RemainingStandInArtTest {
    private static final Path ROOT = Path.of("src/generated/v1.8/resources");
    private static final String ASSETS = V180RemainingStandInArtData.ASSETS;
    private static final String NS = "advancedrocketrycommunity:";
    private static final List<String> DRAWER_MODELS = List.of("astrobody_data_processor", "centrifuge", "chemical_reactor",
            "crystallizer", "cutting_machine", "electrolyzer", "electrolyzer_active", "observatory", "orbital_laser_drill",
            "precision_laser_etcher", "railgun", "satellite_builder", "black_hole_generator", "elevator_anchor",
            "endgame_casing", "laser_target", "microwave_receiver", "satellite_terminal", "fuel_loader", "guidance_computer",
            "rocket_assembler", "oxygen_vent", "oxygen_vent_active", "machine_casing");

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    static Map<String, JsonObject> allModels() {
        Map<String, JsonObject> models = new LinkedHashMap<>(V180RemainingStandInArtData.models());
        V180ClosureModels.bindings().forEach((path, json) -> {
            if (List.of("electrolyzer", "electrolyzer_active", "orbital_laser_drill", "railgun", "satellite_builder", "warp_core")
                    .stream().anyMatch(id -> path.equals(ASSETS + "models/block/" + id + ".json"))) { models.put(path, json); }
        });
        V180StandInArtData.PORTS.forEach(id -> {
            String path = ASSETS + "models/block/" + id + ".json";
            models.put(path, V180StandInArtData.models().get(path));
        });
        for (Map<String, JsonObject> source : List.of(V180ResearchData.clientFiles(), V180C16cMachineData.clientFiles(),
                V180ArcCuttingData.clientFiles())) {
            source.forEach((path, json) -> {
                if (DRAWER_MODELS.stream().anyMatch(id -> path.equals(ASSETS + "models/block/" + id + ".json"))) {
                    assertNull(models.put(path, json), "two providers own " + path);
                }
            });
        }
        models.put(ASSETS + "models/item/nitrogen_canister.json", JsonParser.parseString(
                "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\""
                        + NS + "item/nitrogen_canister_cylinder\"}}").getAsJsonObject());
        return models;
    }

    @Test void deterministicOriginalTexturesHaveOpaqueFacesAndCleanIconMargins() throws Exception {
        assertEquals(21, V180RemainingStandInArt.textures().size());
        Set<String> hashes = new HashSet<>();
        for (String name : V180RemainingStandInArt.textures()) {
            byte[] png = V180RemainingStandInArt.png(name);
            assertArrayEquals(png, V180RemainingStandInArt.png(name), name);
            assertArrayEquals(png, Files.readAllBytes(ROOT.resolve(ASSETS + "textures/" + name + ".png")), name);
            assertTrue(hashes.add(sha(png)), "duplicate drawing " + name);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            assertEquals(16, image.getWidth()); assertEquals(16, image.getHeight());
            Set<Integer> colours = new HashSet<>();
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    colours.add(image.getRGB(x, y));
                    if (name.startsWith("block/")) { assertEquals(255, image.getRGB(x, y) >>> 24, name); }
                    else if (x == 0 || x == 15 || y == 0 || y == 15) { assertEquals(0, image.getRGB(x, y) >>> 24, name); }
                }
            }
            assertTrue(colours.size() >= 5, name);
        }
        assertThrows(IllegalArgumentException.class, () -> V180RemainingStandInArt.png("block/not_a_texture"));
    }

    @Test void nitrogenUsesTheSameCylinderAndADistinctGasBandWithoutChangingOtherPixels() {
        int[] nitrogen = V180RemainingStandInArt.pixels("item/nitrogen_canister_cylinder");
        int[] oxygen = V180StandInArt.pixels("item/oxygen_canister_cylinder");
        int[] hydrogen = V180StandInArt.pixels("item/hydrogen_canister_cylinder");
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int i = y * 16 + x;
                if (y == 6 && x >= 5 && x <= 10) {
                    assertEquals(0xFF7296D6, nitrogen[i]);
                    assertNotEquals(oxygen[i], nitrogen[i]); assertNotEquals(hydrogen[i], nitrogen[i]);
                } else { assertEquals(oxygen[i], nitrogen[i]); }
            }
        }
    }

    @Test void everyPortFaceIdentifiesItsTypeAndAllDrawerModelsUseTheNewPanel() {
        assertEquals(24, DRAWER_MODELS.size());
        Map<String, JsonObject> models = allModels();
        assertEquals(37, models.size());
        DRAWER_MODELS.forEach(id -> assertEquals(NS + "block/machine_panel_side",
                models.get(ASSETS + "models/block/" + id + ".json").getAsJsonObject("textures").get("side").getAsString(), id));
        Set<String> types = new HashSet<>();
        for (String id : V180StandInArtData.PORTS) {
            JsonObject faces = models.get(ASSETS + "models/block/" + id + ".json").getAsJsonObject("textures");
            String type = id.substring(id.indexOf('_', id.indexOf('_') + 1) + 1);
            String icon = NS + "block/machine_" + type;
            assertEquals(Set.of("top", "bottom", "side"), faces.keySet());
            faces.entrySet().forEach(face -> assertEquals(icon, face.getValue().getAsString(), id + ":" + face.getKey()));
            types.add(icon);
        }
        assertEquals(4, types.size());
    }

    @Test void everyBindingPreservesItsPreviousParentTextureKeysAndAllGeometry() throws Exception {
        JsonObject record = record();
        for (var model : allModels().entrySet()) {
            String path = "src/generated/v1.8/resources/" + model.getKey();
            JsonObject entry = V180W277ProvenanceAssertions.replacement(path);
            assertNotNull(entry, path);
            byte[] baseline = git(record.get("preauthoring_commit").getAsString(), entry.get("baseline_path").getAsString());
            assertEquals(sha(baseline), entry.get("baseline_sha256").getAsString());
            JsonObject before = JsonParser.parseString(new String(baseline, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject after = model.getValue().deepCopy();
            JsonObject oldFaces = before.remove("textures").getAsJsonObject();
            JsonObject newFaces = after.remove("textures").getAsJsonObject();
            assertEquals(before, after, path);
            assertEquals(oldFaces.keySet(), newFaces.keySet(), path);
            assertNotEquals(oldFaces, newFaces, "unnecessary model copy " + path);
            assertEquals(model.getValue(), JsonParser.parseString(Files.readString(Path.of(path))), path);
            newFaces.entrySet().forEach(face -> {
                assertTrue(face.getValue().getAsString().startsWith(NS), path);
                assertNotEquals(NS + "block/machine_casing_side", face.getValue().getAsString(), path);
                String old = oldFaces.get(face.getKey()).getAsString();
                if (!old.startsWith("minecraft:") && !old.endsWith("machine_casing_side")
                        && !path.contains("_port.json") && !path.endsWith("nitrogen_canister.json")) {
                    assertEquals(oldFaces.get(face.getKey()), face.getValue(), "changed non-stand-in " + path);
                }
            });
        }
    }

    @Test void existingArtAndAlreadyReplacedModelsRemainByteIdentical() throws Exception {
        String base = record().get("preauthoring_commit").getAsString();
        for (String name : V180StandInArt.textures()) {
            String path = "src/generated/v1.8/resources/" + ASSETS + "textures/" + name + ".png";
            assertArrayEquals(git(base, path), Files.readAllBytes(Path.of(path)), path);
        }
        for (String path : List.of("src/main/resources/" + ASSETS + "textures/block/machine_casing_side.png",
                "src/generated/v1.8/resources/" + ASSETS + "textures/block/electrolyzer_front_active.png",
                "src/generated/v1.8/resources/" + ASSETS + "models/block/rocket_motor.json",
                "src/generated/v1.8/resources/" + ASSETS + "models/block/rocket_fuel_tank.json")) {
            assertArrayEquals(git(base, path), Files.readAllBytes(Path.of(path)), path);
        }
    }

    @Test void newProviderWritesExactlyItsDeclaredFilesAndFluidProviderBindsTheCylinder(@TempDir Path output) throws Exception {
        new V180RemainingStandInArtData(new PackOutput(output)).run(CachedOutput.NO_CACHE).get(10, TimeUnit.SECONDS);
        Set<String> expected = new HashSet<>(V180RemainingStandInArtData.models().keySet());
        V180RemainingStandInArt.textures().forEach(name -> expected.add(ASSETS + "textures/" + name + ".png"));
        Set<String> actual = new HashSet<>();
        try (var files = Files.walk(output)) {
            files.filter(Files::isRegularFile).forEach(path -> actual.add(output.relativize(path).toString().replace('\\', '/')));
        }
        assertEquals(expected, actual);
        Path fluid = output.resolve("fluid");
        new V180FluidData(new PackOutput(fluid), true, false).run(CachedOutput.NO_CACHE).get(10, TimeUnit.SECONDS);
        String nitrogen = ASSETS + "models/item/nitrogen_canister.json";
        assertEquals(allModels().get(nitrogen), JsonParser.parseString(Files.readString(fluid.resolve(nitrogen))));
    }

    @Test void provenanceCoversEveryTargetWithActualHashesAndReproducibleSourceRevisions() throws Exception {
        JsonObject record = record();
        assertEquals(2, record.get("schema_version").getAsInt());
        Set<String> expected = new HashSet<>();
        allModels().keySet().forEach(path -> expected.add("src/generated/v1.8/resources/" + path));
        V180RemainingStandInArt.textures().forEach(name -> expected.add("src/generated/v1.8/resources/" + ASSETS + "textures/" + name + ".png"));
        Set<String> actual = new HashSet<>();
        for (var value : record.getAsJsonArray("entries")) {
            JsonObject entry = value.getAsJsonObject();
            String target = entry.get("target_path").getAsString();
            assertTrue(actual.add(target), target);
            assertEquals("NEW", entry.get("status").getAsString()); assertEquals("MIT", entry.get("license").getAsString());
            assertEquals(sha(Files.readAllBytes(Path.of(target))), entry.get("target_sha256").getAsString(), target);
            String source = entry.get("source_path").getAsString();
            byte[] sourceBytes = git(entry.get("authoring_commit").getAsString(), source);
            assertEquals(sha(sourceBytes), entry.get("source_sha256").getAsString(), source);
        }
        assertEquals(expected, actual);
        assertEquals(1, record.getAsJsonArray("supporting_sources").size());
        JsonObject shared = record.getAsJsonArray("supporting_sources").get(0).getAsJsonObject();
        assertEquals("src/main/java/io/github/sunthemoon/advancedrocketrycommunity/datagen/V180StandInArt.java",
                shared.get("source_path").getAsString());
        assertEquals(sha(git(shared.get("source_commit").getAsString(), shared.get("source_path").getAsString())),
                shared.get("source_sha256").getAsString());
        V180OwnerReviewAssertions.assertStandInArtApproval(record.getAsJsonObject("review"));
    }

    @Test void screeningBindsEveryImageToBothPinnedVanillaClientsAndTheUnchangedTools() throws Exception {
        JsonObject screening = record().getAsJsonObject("screening");
        assertEquals(sha(Files.readAllBytes(Path.of(screening.get("tool_path").getAsString()))), screening.get("tool_sha256").getAsString());
        assertEquals(sha(Files.readAllBytes(Path.of(screening.get("derivation_tool_path").getAsString()))), screening.get("derivation_tool_sha256").getAsString());
        Set<String> clients = new HashSet<>();
        screening.getAsJsonObject("vanilla").entrySet().forEach(v -> clients.add(v.getValue().getAsJsonObject().get("sha256").getAsString()));
        assertEquals(Set.of("1.12.2", "1.20.1"), screening.getAsJsonObject("vanilla").keySet());
        assertEquals(Set.of("8ada07da5ee77dad3527bd7278fbd05ee1fc8a597813b216a871a2d7d64cc64f",
                "56b71336d2b4fdffd197f56595b0da93e32a946f78f382a299b8f4b92758bb0f"), clients);
        JsonObject targets = screening.getAsJsonObject("targets");
        assertEquals(21, targets.size());
        for (String name : V180RemainingStandInArt.textures()) {
            assertEquals("CLEAR", targets.getAsJsonObject(name).get("verdict").getAsString(), name);
            assertEquals(sha(V180RemainingStandInArt.png(name)), targets.getAsJsonObject(name).get("sha256").getAsString(), name);
        }
    }

    @Test void runtimeJarContainsEveryUpdatedModelAndNewTextureExactlyOnce() throws Exception {
        String runtimeJar = System.getProperty("arce.runtimeJar");
        assertNotNull(runtimeJar);
        Set<String> resources = new HashSet<>(allModels().keySet());
        V180RemainingStandInArt.textures().forEach(name -> resources.add(ASSETS + "textures/" + name + ".png"));
        Set<String> bound = new HashSet<>();
        allModels().values().forEach(model -> model.getAsJsonObject("textures").entrySet().forEach(e -> bound.add(e.getValue().getAsString())));
        try (JarFile jar = new JarFile(runtimeJar)) {
            for (String path : resources) {
                assertEquals(1, jar.stream().filter(e -> path.equals(e.getName())).count(), path);
                try (var stream = jar.getInputStream(jar.getJarEntry(path))) {
                    assertArrayEquals(Files.readAllBytes(ROOT.resolve(path)), stream.readAllBytes(), path);
                }
            }
            for (String texture : bound) {
                String[] id = texture.split(":", 2);
                String resource = "assets/" + id[0] + "/textures/" + id[1] + ".png";
                assertNotNull(jar.getJarEntry(resource), "missing bound texture " + resource);
                assertEquals(1, jar.stream().filter(e -> resource.equals(e.getName())).count(), resource);
            }
        }
        V180RemainingStandInArt.textures().forEach(name -> assertTrue(bound.contains(NS + name), "unbound drawing " + name));
    }

    private static JsonObject record() throws Exception {
        return JsonParser.parseString(Files.readString(V180W277ProvenanceAssertions.RECORD)).getAsJsonObject();
    }

    private static String sha(byte[] bytes) throws Exception { return V180W277ProvenanceAssertions.sha(bytes); }

    private static byte[] git(String revision, String path) throws Exception {
        assertTrue(revision.matches("[0-9a-f]{40}"), revision);
        Process git = new ProcessBuilder("git", "show", revision + ":" + path).start();
        byte[] bytes = git.getInputStream().readAllBytes();
        assertTrue(git.waitFor(10, TimeUnit.SECONDS)); assertEquals(0, git.exitValue(), path);
        return bytes;
    }
}
