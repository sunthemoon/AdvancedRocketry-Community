package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Static R287 composition and provenance checks, not human origin or real-GPU approval. */
class V180LandingPadTextureImportTest {
    private static final String ASSETS = "assets/advancedrocketrycommunity/";
    private static final Path GENERATED = Path.of("src/generated/v1.8/resources");
    private static final Path IMPORTED = Path.of("src/imported/v1.8/resources");
    private static final String SOURCE = "src/main/resources/assets/advancedrocketry/textures/blocks/rocketpad_noedge.png";
    private static final String TARGET = "src/imported/v1.8/resources/" + ASSETS + "textures/block/rocketpad_noedge.png";
    private static final String HASH = "06b1ed723649858c4e21003f4fb92ecd3422ad49706e86570bc201b1f6f57320";

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void surroundedCellTileCoversTopAndBottomWithoutChangingSidesGeometryOrStates() {
        var files = V180LandingPadData.clientFiles();
        var model = files.get(ASSETS + "models/block/landing_pad.json");
        assertEquals(Set.of("parent", "textures"), model.keySet());
        assertEquals("minecraft:block/cube_all", model.get("parent").getAsString());
        assertEquals(JsonParser.parseString("""
                {"all":"advancedrocketrycommunity:block/landing_pad",
                 "up":"advancedrocketrycommunity:block/rocketpad_noedge",
                 "down":"advancedrocketrycommunity:block/rocketpad_noedge"}
                """), model.getAsJsonObject("textures"));
        assertEquals(JsonParser.parseString("""
                {"variants":{"":{"model":"advancedrocketrycommunity:block/landing_pad"}}}
                """), files.get(ASSETS + "blockstates/landing_pad.json"));
        assertEquals(JsonParser.parseString("""
                {"parent":"advancedrocketrycommunity:block/landing_pad"}
                """), files.get(ASSETS + "models/item/landing_pad.json"));
    }

    @Test void generatedResourcesMatchProvidersAndRetainedNewArtKeepsItsBytes() throws Exception {
        for (var entry : V180LandingPadData.clientFiles().entrySet()) {
            assertEquals(entry.getValue(), read(GENERATED.resolve(entry.getKey())), entry.getKey());
        }
        for (var entry : V180LandingPadData.serverFiles().entrySet()) {
            assertEquals(entry.getValue(), read(GENERATED.resolve(entry.getKey())), entry.getKey());
        }
        var retained = Files.readAllBytes(GENERATED.resolve(V180LandingPadData.TEXTURE));
        assertArrayEquals(V180LandingPadData.texture(), retained);
        assertEquals("0485c02e998660e22d79ed361554e20505089396d3a96b4992b145db6647fc2a", sha256(retained));
    }

    @Test void oneLfPlanAndPendingRecordPinTheExactByteCopyInTheStitchedFolder() throws Exception {
        Path planPath = Path.of("tools/import/v180-w261-plan.json");
        assertFalse(Files.readString(planPath).contains("\r"));
        var plan = read(planPath);
        assertEquals(1, plan.getAsJsonArray("entries").size());
        var planned = plan.getAsJsonArray("entries").get(0).getAsJsonObject();
        assertEquals(SOURCE, planned.get("source_path").getAsString());
        assertEquals(TARGET, planned.get("target_path").getAsString());
        assertEquals(HASH, planned.get("source_sha256").getAsString());
        assertEquals("rename", planned.getAsJsonObject("transformation").get("kind").getAsString());
        assertEquals(1, planned.get("asset_coverage_row").getAsInt());
        assertEquals(JsonParser.parseString("[\"block:landingPad\"]"), planned.get("units"));
        var record = read(Path.of("docs/provenance/v1.8.0-w261-landing-pad.json"));
        assertEquals(2, record.get("schema_version").getAsInt());
        assertEquals("PENDING_HUMAN_REVIEW", record.getAsJsonObject("review").get("status").getAsString());
        assertEquals(sha256(Files.readAllBytes(planPath)), record.get("plan_sha256").getAsString());
        assertEquals(1, record.getAsJsonArray("entries").size());
        var entry = record.getAsJsonArray("entries").get(0).getAsJsonObject();
        assertEquals(SOURCE, entry.get("source_path").getAsString());
        assertEquals(TARGET, entry.get("target_path").getAsString());
        assertEquals(HASH, entry.get("source_sha256").getAsString());
        assertEquals(HASH, entry.get("target_sha256").getAsString());
        assertEquals(HASH, sha256(Files.readAllBytes(Path.of(TARGET))));
        assertEquals("MIT", entry.get("license").getAsString());
        assertEquals("Copyright (c) 2017", entry.get("copyright_notice").getAsString());
        assertEquals("UPSTREAM_AR_MIT", entry.get("status").getAsString());
        assertEquals("c5cd5af62fc07cd4e0d24f06a16033f181c47c04", entry.get("source_commit").getAsString());
        // The original source name is free in every non-imported root; no _classic suffix is needed.
        assertFalse(Files.exists(GENERATED.resolve(ASSETS + "textures/block/rocketpad_noedge.png")));
        assertFalse(Files.exists(Path.of("src/main/resources").resolve(ASSETS + "textures/block/rocketpad_noedge.png")));
    }

    @Test void otherFifteenConnectedVariantsAreNotImported() throws Exception {
        var excluded = List.of("", "blcorner", "bottomedge", "brcorner", "leftedge", "nbedge", "nledge",
                "nredge", "ntedge", "rightedge", "tlcorner", "topedge", "trcorner", "xcross", "ycross");
        assertEquals(15, Set.copyOf(excluded).size());
        for (String suffix : excluded) {
            for (String folder : List.of("block", "blocks")) {
                assertFalse(Files.exists(IMPORTED.resolve(ASSETS + "textures/" + folder + "/rocketpad_" + suffix + ".png")));
                assertFalse(Files.exists(IMPORTED.resolve(ASSETS + "textures/" + folder + "/rocketpad_" + suffix + "_classic.png")));
            }
        }
        try (var paths = Files.walk(IMPORTED)) {
            assertEquals(List.of(Path.of(TARGET)), paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("rocketpad_")).sorted().toList());
        }
    }

    private static JsonObject read(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
}
