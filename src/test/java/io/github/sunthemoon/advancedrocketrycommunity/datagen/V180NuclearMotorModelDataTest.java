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
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class V180NuclearMotorModelDataTest {
    private static final String NS = ModIdentity.MOD_ID;
    private static final String ID = "nuclear_rocket_motor";
    private static final String ASSET = "assets/" + NS + "/";
    private static final Path IMPORTED = Path.of("src/imported/v1.8/resources", ASSET);
    private static final Path GENERATED = Path.of("src/generated/v1.8/resources");

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static JsonObject file(String path) {
        return V180PropulsionData.files(true, false).get(ASSET + path + ".json");
    }

    @Test void boundedNuclearMeshUsesItsRealStitchedMaterial() throws Exception {
        JsonObject model = file("models/block/" + ID);
        assertEquals("forge:obj", model.get("loader").getAsString());
        assertEquals(NS + ":models/block/models/nuclearrocketmotor.obj", model.get("model").getAsString());
        assertFalse(model.get("flip_v").getAsBoolean());
        assertFalse(model.get("automatic_culling").getAsBoolean());
        assertFalse(model.has("transform"));
        assertFalse(model.has("elements"));
        String sprite = NS + ":block/nuclearengine";
        assertEquals(sprite, model.getAsJsonObject("textures").get("particle").getAsString());
        Path mesh = IMPORTED.resolve("models/block/models/nuclearrocketmotor.obj");
        var obj = ObjModelCheck.checkObj(Files.readAllBytes(mesh), mesh.toString());
        assertEquals(new ObjModelCheck.Bounds(-0.0005, 0.005, -0.0005, 1.0005, 1, 1.0005), obj.bounds());
        assertEquals(162, obj.faces());
        assertTrue(obj.budgetVertices() <= ObjModelCheck.MAX_BAKED_OBJECT_VERTICES);
        Path mtlPath = mesh.resolveSibling(obj.materialLibrary());
        var mtl = ObjModelCheck.checkMtl(Files.readAllBytes(mtlPath), mtlPath.toString());
        assertEquals(Set.of("Material"), obj.materials());
        assertEquals(obj.materials(), mtl.materials().keySet());
        assertEquals(Map.of("map_Kd", sprite), mtl.materials().get("Material"));
        Path png = IMPORTED.resolve("textures/block/nuclearengine.png");
        ObjModelCheck.checkPng(Files.readAllBytes(png), png.toString());
    }

    @Test void everyExistingFacingPinsTheLegacyPlacementRotation() {
        JsonObject state = file("blockstates/" + ID);
        JsonObject variants = state.getAsJsonObject("variants");
        // AdvancedRocketry c5cd5af6, blockstates/nuclearrocketmotor.json; down is the identity.
        Map<String, List<Integer>> rotations = Map.of("down", List.of(0, 0), "up", List.of(180, 0),
                "north", List.of(90, 0), "south", List.of(90, 180),
                "east", List.of(90, 90), "west", List.of(90, 270));
        assertEquals(Set.of("facing=down", "facing=up", "facing=north", "facing=south", "facing=east", "facing=west"),
                variants.keySet());
        rotations.forEach((facing, angles) -> {
            JsonObject variant = variants.getAsJsonObject("facing=" + facing);
            assertEquals(Set.of("model", "x", "y"), variant.keySet());
            assertEquals(NS + ":block/" + ID, variant.get("model").getAsString());
            assertEquals(angles.get(0), variant.get("x").getAsInt(), facing);
            assertEquals(angles.get(1), variant.get("y").getAsInt(), facing);
        });
    }

    @Test void itemSharesTheBlockMeshAndPinsAllCameraComponents() {
        JsonObject item = file("models/item/" + ID);
        assertEquals(Set.of("parent", "display"), item.keySet());
        assertEquals(NS + ":block/" + ID, item.get("parent").getAsString());
        JsonObject expected = JsonParser.parseString("""
                {
                  "gui":{"rotation":[30,225,0],"translation":[0,0,0],"scale":[0.625,0.625,0.625]},
                  "ground":{"rotation":[0,0,0],"translation":[0,3,0],"scale":[0.25,0.25,0.25]},
                  "fixed":{"rotation":[0,0,0],"translation":[0,0,0],"scale":[0.5,0.5,0.5]},
                  "thirdperson_righthand":{"rotation":[75,45,0],"translation":[0,2.5,0],"scale":[0.375,0.375,0.375]},
                  "thirdperson_lefthand":{"rotation":[75,45,0],"translation":[0,2.5,0],"scale":[0.375,0.375,0.375]},
                  "firstperson_righthand":{"rotation":[0,45,0],"translation":[0,0,0],"scale":[0.4,0.4,0.4]},
                  "firstperson_lefthand":{"rotation":[0,225,0],"translation":[0,0,0],"scale":[0.4,0.4,0.4]},
                  "head":{"rotation":[0,0,0],"translation":[0,0,0],"scale":[1,1,1]}
                }
                """).getAsJsonObject();
        assertEquals(expected, item.getAsJsonObject("display"));
    }

    @Test void generatedNuclearResourcesReproduceTheProvider() throws Exception {
        for (String path : List.of("models/block/", "models/item/", "blockstates/")) {
            String relative = ASSET + path + ID + ".json";
            assertEquals(file(path + ID), JsonParser.parseString(Files.readString(GENERATED.resolve(relative))), relative);
        }
    }
}
