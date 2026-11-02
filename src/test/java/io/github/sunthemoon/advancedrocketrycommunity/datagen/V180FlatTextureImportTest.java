package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Resource composition and byte identity, not human provenance approval or client visual acceptance. */
class V180FlatTextureImportTest {
    private static final String NS = "advancedrocketrycommunity:";
    private static final String ASSETS = "assets/advancedrocketrycommunity/";
    private static final Path GENERATED = Path.of("src/generated/v1.8/resources");
    private static final Path IMPORTED = Path.of("src/imported/v1.8/resources");

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void staticFurnaceAndCrucibleFacesKeepTheirParentsAndStates() {
        var arc = V180ArcCuttingData.clientFiles();
        assertModel(arc, "block/blast_brick", "minecraft:block/cube_all", Map.of("all", "block/blastbrick"));
        assertModel(arc, "block/arc_furnace", "minecraft:block/orientable", Map.of(
                "front", "block/blastbrickfront", "side", "block/blastbrick", "top", "block/blastbrick"));
        var variants = arc.get(ASSETS + "blockstates/arc_furnace.json").getAsJsonObject("variants");
        assertEquals(Set.of("facing=north", "facing=east", "facing=south", "facing=west"), variants.keySet());
        String[] facings = {"north", "east", "south", "west"};
        for (int i = 0; i < facings.length; i++) {
            var variant = variants.getAsJsonObject("facing=" + facings[i]);
            assertEquals(NS + "block/arc_furnace", variant.get("model").getAsString());
            assertEquals(i * 90, variant.get("y").getAsInt());
        }
        var crucible = V180C16cMachineData.clientFiles();
        assertModel(crucible, "block/quartz_crucible", "minecraft:block/cube_all", Map.of(
                "all", "block/quartz_crucible", "down", "block/qcrucible_bottom"));
        assertEquals(Set.of(""), crucible.get(ASSETS + "blockstates/quartz_crucible.json")
                .getAsJsonObject("variants").keySet());
        assertEquals(NS + "block/quartz_crucible", crucible.get(ASSETS + "models/item/quartz_crucible.json")
                .get("parent").getAsString());
    }

    @Test void equipmentAndFlatIconsKeepGeneratedOrHandheldParents() {
        var equipment = V180EquipmentData.clientFiles();
        assertModel(equipment, "block/suit_workstation", "minecraft:block/cube_bottom_top", Map.of(
                "top", "block/suitworkstation", "side", "block/suit_workstation_side",
                "bottom", "block/suit_workstation_bottom"));
        for (var entry : Map.of("jetpack", "jetpack_classic", "hover_upgrade", "hoverupgrade",
                "flight_speed_upgrade", "flightspeedupgrade").entrySet()) {
            assertModel(equipment, "item/" + entry.getKey(), "minecraft:item/generated",
                    Map.of("layer0", "item/" + entry.getValue()));
        }
        assertModel(V180JackhammerData.clientFiles(), "item/jackhammer", "minecraft:item/handheld",
                Map.of("layer0", "item/jackhammer_classic"));
        assertModel(V180TrackingCircuitData.clientFiles(), "item/tracking_circuit", "minecraft:item/generated",
                Map.of("layer0", "item/trackingcircuit"));
        assertModel(V180LifeSupportData.clientFiles(), "item/carbon_scrubber_cartridge", "minecraft:item/generated",
                Map.of("layer0", "item/carbonscrubbercartrige"));
        assertModel(V180SealDetectorData.clientFiles(), "item/seal_detector", "minecraft:item/generated",
                Map.of("layer0", "item/sealdetector"));
    }

    @Test void airlockAndTorchFormsShareTheImportedFacesWithoutGeometryChanges() {
        var airlock = V180AirlockData.clientFiles();
        for (String half : List.of("bottom", "top")) {
            for (String hinge : List.of("left", "right")) {
                for (String open : List.of("", "_open")) {
                    String part = "door_" + half + "_" + hinge + open;
                    assertModel(airlock, "block/airlock_" + part, "minecraft:block/" + part,
                            Map.of("bottom", "block/smallairlockdoor_lower", "top", "block/smallairlockdoor_upper"));
                }
            }
        }
        assertEquals(32, airlock.get(ASSETS + "blockstates/airlock_door.json").getAsJsonObject("variants").size());
        assertModel(airlock, "item/airlock_door", "minecraft:item/generated", Map.of("layer0", "item/smallairlock"));
        var torch = V180ThermiteData.clientFiles();
        for (var entry : Map.of("thermite_torch", "template_torch", "thermite_wall_torch", "template_torch_wall").entrySet()) {
            assertModel(torch, "block/" + entry.getKey(), "minecraft:block/" + entry.getValue(),
                    Map.of("torch", "block/thermitetorch"));
            assertEquals("minecraft:cutout", torch.get(ASSETS + "models/block/" + entry.getKey() + ".json")
                    .get("render_type").getAsString());
        }
        assertModel(torch, "item/thermite_torch", "minecraft:item/generated", Map.of("layer0", "block/thermitetorch"));
        assertModel(torch, "item/thermite", "minecraft:item/generated", Map.of("layer0", "item/thermite_classic"));
    }

    @Test void allProviderJsonMatchesGeneratedResourcesAndEveryImportHasAConsumer() throws Exception {
        Map<String, JsonObject> files = clientFiles();
        Set<String> consumed = new HashSet<>();
        for (var entry : files.entrySet()) {
            assertEquals(entry.getValue(), read(GENERATED.resolve(entry.getKey())), entry.getKey());
            var textures = entry.getValue().getAsJsonObject("textures");
            if (textures == null) { continue; }
            for (var texture : textures.entrySet()) {
                String id = texture.getValue().getAsString();
                // W246-fix2 (R268): the plural 1.12 folders are stitched by no 1.20.1 block-atlas source.
                assertFalse(id.startsWith(NS + "blocks/") || id.startsWith(NS + "items/"), entry.getKey() + " names " + id);
                if (!id.startsWith(NS)) { continue; }
                String resource = ASSETS + "textures/" + id.substring(NS.length()) + ".png";
                if (Files.isRegularFile(IMPORTED.resolve(resource))) {
                    consumed.add(resource);
                } else {
                    assertTrue(inOtherRoot(resource), entry.getKey() + " names " + id + ", which no resource root holds");
                }
            }
        }
        var plan = read(Path.of("tools/import/v180-w246-plan.json"));
        Set<String> planned = new HashSet<>();
        for (var entry : plan.getAsJsonArray("entries")) {
            planned.add(entry.getAsJsonObject().get("target_path").getAsString().substring("src/imported/v1.8/resources/".length()));
        }
        assertEquals(16, planned.size());
        // R282 (W256): this provider now also consumes S1's two sprites. Keep W246's exact sixteen-file pin
        // and the exact consumer-set assertion; do not mistake the new saw sprites for unused W246 imports.
        Set<String> expectedConsumed = new HashSet<>(planned);
        expectedConsumed.add(ASSETS + "textures/block/cuttingmachine2.png");
        expectedConsumed.add(ASSETS + "textures/item/saw_blade_classic.png");
        assertEquals(expectedConsumed, consumed);
    }

    @Test void importsUseTheStitchedSingularFoldersAndRenameOnlyWhereNewArtHoldsTheName() throws Exception {
        var plan = read(Path.of("tools/import/v180-w246-plan.json"));
        Set<String> classic = new HashSet<>();
        for (var element : plan.getAsJsonArray("entries")) {
            var entry = element.getAsJsonObject();
            String target = entry.get("target_path").getAsString().substring("src/imported/v1.8/resources/".length());
            String source = entry.get("source_path").getAsString();
            String name = source.substring(source.lastIndexOf('/') + 1, source.length() - ".png".length());
            String folder = source.contains("/textures/blocks/") ? "block/" : "item/";
            assertTrue(source.contains("/textures/" + folder.replace("/", "s/")), source);
            assertEquals("rename", entry.getAsJsonObject("transformation").get("kind").getAsString(), target);
            // A name already taken by retained NEW art gets the _classic suffix; the NEW art keeps its path and bytes.
            boolean taken = inOtherRoot(ASSETS + "textures/" + folder + name + ".png");
            assertEquals(ASSETS + "textures/" + folder + name + (taken ? "_classic" : "") + ".png", target, source);
            assertFalse(inOtherRoot(target), target);
            if (taken) { classic.add(name); }
        }
        assertEquals(Set.of("jackhammer", "jetpack", "thermite"), classic);
    }

    @Test void importRecordPinsByteCopiesAndExcludesUnselectedFacesAndSawblade() throws Exception {
        var record = read(Path.of("docs/provenance/v1.8.0-w246-flat-textures.json"));
        Set<String> paths = new HashSet<>();
        assertEquals(16, record.getAsJsonArray("entries").size());
        for (var element : record.getAsJsonArray("entries")) {
            var entry = element.getAsJsonObject();
            String target = entry.get("target_path").getAsString();
            assertTrue(paths.add(target));
            String source = entry.get("source_sha256").getAsString();
            assertEquals(source, entry.get("target_sha256").getAsString());
            assertEquals(source, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(target)))));
            assertEquals("UPSTREAM_AR_MIT", entry.get("status").getAsString());
            assertEquals("MIT", entry.get("license").getAsString());
            assertEquals("c5cd5af62fc07cd4e0d24f06a16033f181c47c04", entry.get("source_commit").getAsString());
        }
        for (String excluded : List.of("blocks/blastbrickfront_active", "blocks/qcrucible_inner", "blocks/qcrucible_side",
                "blocks/qcrucible_top", "items/sawbladeiron")) {
            assertFalse(Files.exists(IMPORTED.resolve(ASSETS + "textures/" + excluded + ".png")), excluded);
            // The same excluded faces stay out of the singular folders the imports now use.
            String singular = excluded.replaceFirst("^blocks/", "block/").replaceFirst("^items/", "item/");
            assertFalse(Files.exists(IMPORTED.resolve(ASSETS + "textures/" + singular + ".png")), singular);
            assertFalse(Files.exists(IMPORTED.resolve(ASSETS + "textures/" + singular + "_classic.png")), singular);
        }
    }

    private static Map<String, JsonObject> clientFiles() {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        for (var provider : List.of(V180ArcCuttingData.clientFiles(), V180C16cMachineData.clientFiles(),
                V180EquipmentData.clientFiles(), V180AirlockData.clientFiles(), V180ThermiteData.clientFiles(),
                V180JackhammerData.clientFiles(), V180TrackingCircuitData.clientFiles(),
                V180LifeSupportData.clientFiles(), V180SealDetectorData.clientFiles())) {
            provider.forEach((path, value) -> assertNull(files.put(path, value), path));
        }
        return files;
    }

    private static void assertModel(Map<String, JsonObject> files, String name, String parent, Map<String, String> textures) {
        var model = files.get(ASSETS + "models/" + name + ".json");
        assertEquals(parent, model.get("parent").getAsString(), name);
        var expected = new JsonObject();
        textures.forEach((key, value) -> expected.addProperty(key, NS + value));
        assertEquals(expected, model.getAsJsonObject("textures"), name);
        assertFalse(model.has("elements"), name);
    }

    /** Whether the main, v0.1.0 DataGen or a versioned DataGen resource root holds the asset path. */
    private static boolean inOtherRoot(String resource) throws Exception {
        List<Path> roots = new ArrayList<>(List.of(Path.of("src/main/resources"), Path.of("src/generated/resources")));
        try (Stream<Path> versions = Files.list(Path.of("src/generated"))) {
            // src/generated/v<major>.<minor>/resources; src/generated/resources/resources does not exist.
            versions.map(version -> version.resolve("resources")).filter(Files::isDirectory).forEach(roots::add);
        }
        assertTrue(roots.contains(GENERATED), "the v1.8 DataGen root is missing");
        return roots.stream().anyMatch(root -> Files.isRegularFile(root.resolve(resource)));
    }

    private static JsonObject read(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
