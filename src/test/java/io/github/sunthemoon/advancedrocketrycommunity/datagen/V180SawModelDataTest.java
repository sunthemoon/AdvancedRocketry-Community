package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.client.model.ObjModelCheck;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class V180SawModelDataTest {
    private static final String NS = V180ArcCuttingData.NS;
    private static final String ASSETS = "assets/" + NS + "/";
    private static final Path IMPORTED = Path.of("src/imported/v1.8/resources/" + ASSETS);

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static JsonObject model(String kind) {
        return V180ArcCuttingData.clientFiles().get(ASSETS + "models/" + kind + "/saw_blade.json");
    }

    @Test void blockUsesBoundedStaticObjWithStitchedMaterialAndNoFittedTransform() throws Exception {
        JsonObject block = model("block");
        assertEquals("forge:obj", block.get("loader").getAsString());
        assertFalse(block.get("flip_v").getAsBoolean());
        assertFalse(block.get("automatic_culling").getAsBoolean());
        assertFalse(block.has("transform"));
        assertFalse(block.has("elements"));
        assertEquals(NS + ":models/block/models/sawblade.obj", block.get("model").getAsString());
        assertEquals(NS + ":block/cuttingmachine2", block.getAsJsonObject("textures").get("particle").getAsString());
        Path mesh = IMPORTED.resolve("models/block/models/sawblade.obj");
        var obj = ObjModelCheck.checkObj(Files.readAllBytes(mesh), mesh.toString());
        assertEquals(180, obj.faces());
        assertEquals(720, obj.budgetVertices());
        assertTrue(obj.budgetVertices() <= ObjModelCheck.MAX_BAKED_OBJECT_VERTICES);
        assertEquals(new ObjModelCheck.Bounds(0, -0.005880, -0.005879, 1, 1.005879, 1.005880), obj.bounds());
        assertEquals("sawblade.mtl", obj.materialLibrary());
        Path material = mesh.resolveSibling(obj.materialLibrary());
        var mtl = ObjModelCheck.checkMtl(Files.readAllBytes(material), material.toString());
        assertEquals(obj.materials(), mtl.materials().keySet());
        assertEquals(Set.of("Material"), obj.materials());
        assertEquals(NS + ":block/cuttingmachine2", mtl.materials().get("Material").get("map_Kd"));
        for (String png : List.of("block/cuttingmachine2", "item/saw_blade_classic")) {
            Path texture = IMPORTED.resolve("textures/" + png + ".png");
            ObjModelCheck.checkPng(Files.readAllBytes(texture), texture.toString());
        }
    }

    @Test void itemSharesBlockMeshAndUsesAssemblyIconOnlyForParticles() {
        JsonObject item = model("item");
        assertEquals(NS + ":block/saw_blade", item.get("parent").getAsString());
        assertFalse(item.has("loader"));
        assertFalse(item.has("overrides"));
        assertEquals("{\"particle\":\"" + NS + ":item/saw_blade_classic\"}", item.get("textures").toString());
        JsonObject display = item.getAsJsonObject("display");
        assertEquals(Set.of("gui", "ground", "fixed", "head", "firstperson_righthand", "firstperson_lefthand",
                "thirdperson_righthand", "thirdperson_lefthand"), display.keySet());
        assertEquals("[30,225,0]", display.getAsJsonObject("gui").get("rotation").toString());
        assertEquals("[0.625,0.625,0.625]", display.getAsJsonObject("gui").get("scale").toString());
        assertEquals("[0,3,0]", display.getAsJsonObject("ground").get("translation").toString());
        assertEquals("[0.25,0.25,0.25]", display.getAsJsonObject("ground").get("scale").toString());
        assertEquals("[0.5,0.5,0.5]", display.getAsJsonObject("fixed").get("scale").toString());
        for (String hand : List.of("righthand", "lefthand")) {
            JsonObject third = display.getAsJsonObject("thirdperson_" + hand);
            assertEquals("[75,45,0]", third.get("rotation").toString());
            assertEquals("[0,2.5,0]", third.get("translation").toString());
            assertEquals("[0.375,0.375,0.375]", third.get("scale").toString());
            assertEquals("[0.4,0.4,0.4]", display.getAsJsonObject("firstperson_" + hand).get("scale").toString());
        }
        assertEquals("[0,45,0]", display.getAsJsonObject("firstperson_righthand").get("rotation").toString());
        assertEquals("[0,225,0]", display.getAsJsonObject("firstperson_lefthand").get("rotation").toString());
        assertEquals("[1,1,1]", display.getAsJsonObject("head").get("scale").toString());
    }

    @Test void stateStaysStatelessAndIronSawAndCuttingHullAreNotRedirected() {
        var files = V180ArcCuttingData.clientFiles();
        JsonObject variants = files.get(ASSETS + "blockstates/saw_blade.json").getAsJsonObject("variants");
        assertEquals(Set.of(""), variants.keySet());
        assertEquals(Set.of("model"), variants.getAsJsonObject("").keySet());
        assertEquals(NS + ":block/saw_blade", variants.getAsJsonObject("").get("model").getAsString());
        JsonObject iron = files.get(ASSETS + "models/item/iron_saw_blade.json");
        assertEquals("minecraft:item/generated", iron.get("parent").getAsString());
        assertEquals(NS + ":item/iron_saw_blade", iron.getAsJsonObject("textures").get("layer0").getAsString());
        assertEquals("minecraft:block/orientable", files.get(ASSETS + "models/block/cutting_machine.json").get("parent").getAsString());
    }

    @Test void generatedSawModelsReproduceProvider() throws Exception {
        for (String kind : List.of("block", "item")) {
            Path file = Path.of("src/generated/v1.8/resources/" + ASSETS + "models/" + kind + "/saw_blade.json");
            assertEquals(model(kind), JsonParser.parseString(Files.readString(file)));
        }
    }
}
