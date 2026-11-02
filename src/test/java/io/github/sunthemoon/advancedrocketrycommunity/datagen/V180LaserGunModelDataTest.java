package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.math.Transformation;
import io.github.sunthemoon.advancedrocketrycommunity.client.model.ObjModelCheck;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import net.minecraftforge.common.util.TransformationHelper;
import org.joml.Vector4f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class V180LaserGunModelDataTest {
    private static final String NS = "advancedrocketrycommunity:";
    private static final String MODEL = "assets/advancedrocketrycommunity/models/item/basic_laser_gun.json";
    private static final Path IMPORTED = Path.of("src/imported/v1.8/resources/assets/advancedrocketrycommunity");

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static JsonObject model() { return V180LaserGunData.clientFiles().get(MODEL); }

    @Test void everyViewUsesOneBakedMeshAndStitchedParticleWithoutOverrides() {
        JsonObject model = model();
        assertEquals("forge:obj", model.get("loader").getAsString());
        assertEquals(NS + "models/item/models/basiclasergun.obj", model.get("model").getAsString());
        assertFalse(model.get("flip_v").getAsBoolean());
        assertFalse(model.get("automatic_culling").getAsBoolean());
        assertEquals("{\"particle\":\"" + NS + "block/basiclasergun\"}", model.get("textures").toString());
        for (String key : List.of("parent", "elements", "overrides", "mtl_override", "base", "perspectives")) {
            assertFalse(model.has(key), key);
        }
    }

    @Test void everyDisplayTransformIsExplicitAndPinnedIncludingBothHands() {
        JsonObject display = model().getAsJsonObject("display");
        assertNotNull(display);
        assertEquals(Set.of("gui", "ground", "fixed", "head", "firstperson_righthand", "firstperson_lefthand",
                "thirdperson_righthand", "thirdperson_lefthand"), display.keySet());
        transform(display, "firstperson_righthand", "[0,-90,0]", "[1.13,3.2,1.13]", "[0.68,0.68,0.68]");
        transform(display, "firstperson_lefthand", "[0,90,0]", "[1.13,3.2,1.13]", "[0.68,0.68,0.68]");
        transform(display, "thirdperson_righthand", "[-80,260,-150]", "[-1,5,2.5]", "[0.9,0.9,0.9]");
        transform(display, "thirdperson_lefthand", "[-80,-280,150]", "[-1,5,2.5]", "[0.9,0.9,0.9]");
        transform(display, "gui", "[30,135,0]", "[0,0,0]", "[0.8,0.8,0.8]");
        transform(display, "ground", "[0,0,0]", "[0,3,0]", "[0.5,0.5,0.5]");
        transform(display, "fixed", "[0,90,0]", "[0,0,0]", "[0.75,0.75,0.75]");
        transform(display, "head", "[0,0,0]", "[0,0,0]", "[1,1,1]");
    }

    @Test void importedMeshMaterialsAndNormalizationAreBoundedAndResolve() throws Exception {
        Path mesh = IMPORTED.resolve("models/item/models/basiclasergun.obj");
        var obj = ObjModelCheck.checkObj(Files.readAllBytes(mesh), mesh.toString());
        assertEquals(206, obj.positions());
        assertEquals(398, obj.faces());
        assertEquals(1592, obj.budgetVertices());
        assertTrue(obj.budgetVertices() <= ObjModelCheck.MAX_BAKED_OBJECT_VERTICES);
        assertEquals(new ObjModelCheck.Bounds(-0.6, -0.416498, -0.4, 0.6, 1.576, 2.899999), obj.bounds());
        assertEquals("basiclasergun.mtl", obj.materialLibrary());
        assertEquals(Set.of("Diff", "spec"), obj.materials());
        Path library = mesh.resolveSibling(obj.materialLibrary());
        var mtl = ObjModelCheck.checkMtl(Files.readAllBytes(library), library.toString());
        assertEquals(obj.materials(), mtl.materials().keySet());
        for (String material : obj.materials()) {
            assertEquals(NS + "block/basiclasergun", mtl.materials().get(material).get("map_Kd"));
        }
        Path png = IMPORTED.resolve("textures/block/basiclasergun.png");
        ObjModelCheck.checkPng(Files.readAllBytes(png), png.toString());

        var transform = new TransformationHelper.Deserializer().deserialize(model().get("transform"), Transformation.class, null);
        // Forge ObjModel.makeQuad converts the centered root transform to the OBJ's corner coordinates.
        var matrix = transform.blockCenterToCorner().getMatrix();
        Vector4f center = matrix.transform(new Vector4f(0, 0.579751F, 1.2499995F, 1));
        assertEquals(0.5F, center.x, 0.000001F);
        assertEquals(0.5F, center.y, 0.000001F);
        assertEquals(0.5F, center.z, 0.000001F);
        for (float x : new float[] {-0.6F, 0.6F}) {
            for (float y : new float[] {-0.416498F, 1.576F}) {
                for (float z : new float[] {-0.4F, 2.899999F}) {
                    Vector4f corner = matrix.transform(new Vector4f(x, y, z, 1));
                    assertTrue(corner.x >= 0 && corner.x <= 1 && corner.y >= 0 && corner.y <= 1
                            && corner.z >= 0 && corner.z <= 1, corner::toString);
                }
            }
        }
    }

    @Test void generatedModelReproducesProvider() throws Exception {
        assertEquals(model(), JsonParser.parseString(Files.readString(Path.of("src/generated/v1.8/resources/" + MODEL))));
    }

    private static void transform(JsonObject display, String view, String rotation, String translation, String scale) {
        JsonObject transform = display.getAsJsonObject(view);
        assertEquals(Set.of("rotation", "translation", "scale"), transform.keySet(), view);
        assertEquals(JsonParser.parseString(rotation), transform.get("rotation"), view);
        assertEquals(JsonParser.parseString(translation), transform.get("translation"), view);
        assertEquals(JsonParser.parseString(scale), transform.get("scale"), view);
    }
}
