package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * ADR-063 section 6, revision 6 (A0): the seven drawn C15c textures are complete and deterministic and match the
 * committed output; block faces are opaque, the plants and leaves have see-through pixels, and the crystal texture is
 * drawn untinted for the block colours.
 */
class V180ExoplanetArtTest {
    private static final Path TEXTURES = Path.of("src", "generated", "v1.8", "resources", "assets",
            "advancedrocketrycommunity", "textures");
    private static final Set<String> SEE_THROUGH = Set.of("block/lightwood_leaves", "block/lightwood_sapling",
            "block/electric_mushroom");

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void everyExoplanetTextureIsDeterministicAndCommitted() throws IOException {
        Map<String, V180SurfaceArt.Texture> textures = V180ExoplanetArt.textures();
        assertEquals(7, textures.size());
        for (Map.Entry<String, V180SurfaceArt.Texture> texture : textures.entrySet()) {
            String[] grid = texture.getValue().grid();
            assertEquals(16, grid.length, texture.getKey());
            for (String row : grid) {
                assertTrue(row.matches("[0-9.]{16}"), texture.getKey() + " row " + row);
            }
            assertEquals(SEE_THROUGH.contains(texture.getKey()), String.join("", grid).contains("."),
                    texture.getKey() + " transparency");
            byte[] png = V180MaterialArt.png(grid, texture.getValue().tint());
            assertArrayEquals(png, V180MaterialArt.png(grid, texture.getValue().tint()), texture.getKey());
            assertArrayEquals(png, Files.readAllBytes(TEXTURES.resolve(texture.getKey() + ".png")),
                    texture.getKey() + " differs from the committed DataGen output");
        }
        assertEquals(0xFFFFFF, textures.get("block/crystal").tint(), "the block colours tint the crystal");
    }
}
