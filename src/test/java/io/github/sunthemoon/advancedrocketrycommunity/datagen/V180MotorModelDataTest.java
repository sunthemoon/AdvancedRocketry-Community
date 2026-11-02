package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.client.model.ObjModelCheck;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class V180MotorModelDataTest {
    private static final String NS = ModIdentity.MOD_ID;
    private static final Path IMPORTED = Path.of("src/imported/v1.8/resources/assets", NS);
    private static final Path GENERATED = Path.of("src/generated/v1.8/resources");
    private static final List<String> IDS = List.of("rocket_motor", "advanced_rocket_motor");

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void bothBoundedMeshesResolveTheirMaterialsAndAtlasTextures() throws Exception {
        for (String id : IDS) {
            JsonObject model = V180MotorModelData.block(id);
            assertEquals("forge:obj", model.get("loader").getAsString());
            assertFalse(model.get("flip_v").getAsBoolean());
            assertFalse(model.get("automatic_culling").getAsBoolean());
            assertFalse(model.has("transform"));
            assertFalse(model.has("elements"));
            Path mesh = IMPORTED.resolve(model.get("model").getAsString().substring(NS.length() + 1));
            var obj = ObjModelCheck.checkObj(Files.readAllBytes(mesh), mesh.toString());
            assertTrue(obj.faces() > 0);
            assertEquals(new ObjModelCheck.Bounds(-0.0005, 0.005, -0.0005, 1.0005, 1, 1.0005), obj.bounds());
            assertTrue(obj.budgetVertices() <= ObjModelCheck.MAX_BAKED_OBJECT_VERTICES);
            Path mtlPath = mesh.resolveSibling(obj.materialLibrary());
            var mtl = ObjModelCheck.checkMtl(Files.readAllBytes(mtlPath), mtlPath.toString());
            assertEquals(obj.materials(), mtl.materials().keySet());
            String sprite = model.getAsJsonObject("textures").get("particle").getAsString();
            assertTrue(sprite.startsWith(NS + ":block/"));
            for (var maps : mtl.materials().values()) {
                assertEquals(sprite, maps.get("map_Kd"));
                Path png = IMPORTED.resolve("textures/" + sprite.substring(NS.length() + 1) + ".png");
                ObjModelCheck.checkPng(Files.readAllBytes(png), png.toString());
            }
        }
    }

    @Test void itemsInheritTheSameBlockMeshWithExplicitCameraTransforms() {
        for (String id : IDS) {
            JsonObject item = V180MotorModelData.item(id);
            assertEquals(NS + ":block/" + id, item.get("parent").getAsString());
            assertFalse(item.has("loader"));
            JsonObject display = item.getAsJsonObject("display");
            assertEquals(Set.of("gui", "ground", "fixed", "head", "firstperson_righthand", "firstperson_lefthand",
                    "thirdperson_righthand", "thirdperson_lefthand"), display.keySet());
            assertEquals("[30,225,0]", display.getAsJsonObject("gui").get("rotation").toString());
            assertEquals("[0.625,0.625,0.625]", display.getAsJsonObject("gui").get("scale").toString());
            assertEquals("[0,225,0]", display.getAsJsonObject("firstperson_lefthand").get("rotation").toString());
            assertEquals("[0,45,0]", display.getAsJsonObject("firstperson_righthand").get("rotation").toString());
            assertEquals(display.get("thirdperson_righthand"), display.get("thirdperson_lefthand"));
            assertEquals(item, V180MotorModelData.item(id));
        }
        assertThrows(IllegalArgumentException.class, () -> V180MotorModelData.block("nuclear_rocket_motor"));
    }

    @Test void generatedModelsReproduceBothProvidersWithoutChangingOtherFamilies() throws Exception {
        for (String id : IDS) {
            for (String kind : List.of("block", "item")) {
                JsonObject expected = kind.equals("block") ? V180MotorModelData.block(id) : V180MotorModelData.item(id);
                Path file = GENERATED.resolve("assets/" + NS + "/models/" + kind + "/" + id + ".json");
                assertEquals(expected, JsonParser.parseString(Files.readString(file)));
                if (id.equals("advanced_rocket_motor")) {
                    assertEquals(expected, V180PropulsionData.files(true, false).get("assets/" + NS + "/models/" + kind + "/" + id + ".json"));
                }
            }
        }
        assertEquals(2, V180MotorModelData.files().size());
        for (String id : V180PropulsionData.IDS) {
            JsonObject block = V180PropulsionData.files(true, false).get("assets/" + NS + "/models/block/" + id + ".json");
            if (id.equals("nuclear_rocket_motor")) {
                assertEquals(V180NuclearMotorModelData.block(), block);
            } else if (!IDS.contains(id)) {
                assertFalse(block.has("loader"));
                assertEquals("minecraft:block/cube_bottom_top", block.get("parent").getAsString());
            }
        }
    }

    @Test void monoStatesKeepTheSingleDownwardIdentityVariant() throws Exception {
        for (String id : IDS) {
            Path base = id.equals("rocket_motor") ? Path.of("src/generated/v0.5/resources") : GENERATED;
            Path file = base.resolve("assets/" + NS + "/blockstates/" + id + ".json");
            JsonObject variants = JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonObject("variants");
            assertEquals(Set.of(""), variants.keySet());
            assertEquals(Set.of("model"), variants.getAsJsonObject("").keySet());
            assertEquals(NS + ":block/" + id, variants.getAsJsonObject("").get("model").getAsString());
        }
    }
}
