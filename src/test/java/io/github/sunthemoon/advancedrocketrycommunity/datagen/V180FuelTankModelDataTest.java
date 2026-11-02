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

class V180FuelTankModelDataTest {
    private static final String NS = ModIdentity.MOD_ID;
    private static final String ASSET = "assets/" + NS + "/";
    private static final List<String> IDS = List.of("rocket_fuel_tank", "bipropellant_fuel_tank", "oxidizer_fuel_tank");
    private static final Path GENERATED = Path.of("src/generated/v1.8/resources");
    private static final Path IMPORTED = Path.of("src/imported/v1.8/resources", ASSET);

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static JsonObject model(String kind, String id) throws Exception {
        String path = ASSET + "models/" + kind + "/" + id + ".json";
        if (!id.equals("rocket_fuel_tank")) return V180PropulsionData.files(true, false).get(path);
        try (var input = V180FuelTankModelDataTest.class.getResourceAsStream("/" + path)) {
            assertNotNull(input, path);
            return JsonParser.parseString(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test void allThreeTanksShareTheBoundedMiddleMeshAndStitchedMaterial() throws Exception {
        for (String id : IDS) {
            JsonObject model = model("block", id);
            assertEquals("forge:obj", model.get("loader").getAsString(), id);
            assertEquals(NS + ":models/block/models/middletank.obj", model.get("model").getAsString());
            assertFalse(model.get("flip_v").getAsBoolean());
            assertFalse(model.get("automatic_culling").getAsBoolean());
            assertEquals(Set.of("loader", "model", "flip_v", "automatic_culling", "textures"), model.keySet());
            assertEquals(NS + ":block/tank", model.getAsJsonObject("textures").get("particle").getAsString());
        }
        Path mesh = IMPORTED.resolve("models/block/models/middletank.obj");
        var obj = ObjModelCheck.checkObj(Files.readAllBytes(mesh), mesh.toString());
        assertEquals("middletank.mtl", obj.materialLibrary());
        assertEquals(Set.of("None"), obj.materials());
        assertEquals(new ObjModelCheck.Bounds(0, 0, 0, 1, 1, 1), obj.bounds());
        assertEquals(140, obj.faces());
        assertEquals(560, obj.budgetVertices());
        assertTrue(obj.budgetVertices() <= ObjModelCheck.MAX_BAKED_OBJECT_VERTICES);
        Path material = mesh.resolveSibling(obj.materialLibrary());
        var mtl = ObjModelCheck.checkMtl(Files.readAllBytes(material), material.toString());
        assertEquals(obj.materials(), mtl.materials().keySet());
        assertEquals(Map.of("map_Kd", NS + ":block/tank"), mtl.materials().get("None"));
        Path image = IMPORTED.resolve("textures/block/tank.png");
        ObjModelCheck.checkPng(Files.readAllBytes(image), image.toString());
    }

    @Test void allItemsPinEveryCameraComponentAndInheritTheirOwnBlockIdentity() throws Exception {
        JsonObject display = JsonParser.parseString("""
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
        for (String id : IDS) {
            JsonObject item = model("item", id);
            assertEquals(Set.of("parent", "display"), item.keySet());
            assertEquals(NS + ":block/" + id, item.get("parent").getAsString());
            assertEquals(display, item.getAsJsonObject("display"), id);
        }
    }

    @Test void generatedModelsMatchThePackagedAndPropulsionModels() throws Exception {
        for (String id : IDS) {
            for (String kind : List.of("block", "item")) {
                Path path = GENERATED.resolve(ASSET + "models/" + kind + "/" + id + ".json");
                assertEquals(model(kind, id), JsonParser.parseString(Files.readString(path)), path.toString());
            }
        }
    }

    @Test void tanksKeepOneIdentityVariantAndNuclearTankKeepsItsSeparateArt() throws Exception {
        for (String id : IDS) {
            Path root = id.equals("rocket_fuel_tank") ? Path.of("src/generated/v0.5/resources") : GENERATED;
            JsonObject state = JsonParser.parseString(Files.readString(root.resolve(ASSET + "blockstates/" + id + ".json"))).getAsJsonObject();
            JsonObject variants = state.getAsJsonObject("variants");
            assertEquals(Set.of(""), variants.keySet());
            assertEquals(Set.of("model"), variants.getAsJsonObject("").keySet());
            assertEquals(NS + ":block/" + id, variants.getAsJsonObject("").get("model").getAsString());
        }
        JsonObject nuclear = V180PropulsionData.files(true, false).get(ASSET + "models/block/nuclear_fuel_tank.json");
        assertFalse(nuclear.has("loader"));
        assertEquals("minecraft:block/cube_bottom_top", nuclear.get("parent").getAsString());
        assertEquals(NS + ":block/nuclear_fuel_tank_side", nuclear.getAsJsonObject("textures").get("side").getAsString());
    }
}
