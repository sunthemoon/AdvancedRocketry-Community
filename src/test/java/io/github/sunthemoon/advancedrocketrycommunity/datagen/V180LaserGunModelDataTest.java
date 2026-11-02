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
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class V180LaserGunModelDataTest {
    private static final String NS = "advancedrocketrycommunity:";
    private static final String MODEL = "assets/advancedrocketrycommunity/models/item/basic_laser_gun.json";
    private static final Path IMPORTED = Path.of("src/imported/v1.8/resources/assets/advancedrocketrycommunity");

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static JsonObject model() { return V180LaserGunData.clientFiles().get(MODEL); }

    @Test void everyViewUsesUpstreamFlatIconWithoutMeshOrOverrides() {
        JsonObject model = model();
        assertFalse(model.has("loader"), "R293 uses vanilla generated items, not the unused upstream mesh");
        assertEquals("minecraft:item/generated", model.get("parent").getAsString());
        assertEquals("{\"layer0\":\"" + NS + "item/basiclasergun\"}", model.get("textures").toString());
        assertEquals(Set.of("parent", "textures", "display"), model.keySet());
    }

    @Test void upstreamFourHandTransformsAreExactAndOtherViewsInheritVanillaDefaults() {
        JsonObject display = model().getAsJsonObject("display");
        assertNotNull(display);
        assertEquals(Set.of("firstperson_righthand", "firstperson_lefthand",
                "thirdperson_righthand", "thirdperson_lefthand"), display.keySet());
        transform(display, "firstperson_righthand", "[0,-90,0]", "[1.13,3.2,1.13]", "[0.68,0.68,0.68]");
        transform(display, "firstperson_lefthand", "[0,90,0]", "[1.13,3.2,1.13]", "[0.68,0.68,0.68]");
        transform(display, "thirdperson_righthand", "[-80,260,-150]", "[-1,5,2.5]", "[0.9,0.9,0.9]");
        transform(display, "thirdperson_lefthand", "[-80,-280,150]", "[-1,5,2.5]", "[0.9,0.9,0.9]");
    }

    @Test void importedIconIsByteIdenticalAndUnusedMeshAssetsAreAbsent() throws Exception {
        Path png = IMPORTED.resolve("textures/item/basiclasergun.png");
        assertEquals("bf4f99b9b44f4028c48f08e8a39eb424457c26b0ef174b936e4dde6e9de1d888",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(png))));
        var image = ImageIO.read(png.toFile());
        assertNotNull(image);
        assertEquals(64, image.getWidth());
        assertEquals(64, image.getHeight());
        for (String obsolete : List.of("models/item/models/basiclasergun.obj",
                "models/item/models/basiclasergun.mtl", "textures/block/basiclasergun.png")) {
            assertFalse(Files.exists(IMPORTED.resolve(obsolete)), obsolete);
        }
    }

    @Test void retainedNewIconRemainsUnchangedAndUnbound() throws Exception {
        byte[] retained = Files.readAllBytes(Path.of("src/generated/v1.8/resources/" + V180LaserGunData.TEXTURE));
        assertArrayEquals(V180LaserGunData.texture(), retained);
        assertEquals("f885eff929ef551e80d340e5612f89a0e41c8567af0ae70c036966848b0d116f",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(retained)));
        assertFalse(model().getAsJsonObject("textures").has("particle"));
        assertNotEquals(NS + "item/basic_laser_gun", model().getAsJsonObject("textures").get("layer0").getAsString());
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
