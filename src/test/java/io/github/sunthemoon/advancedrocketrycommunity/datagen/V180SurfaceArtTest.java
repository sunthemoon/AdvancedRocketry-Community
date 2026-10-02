package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** ADR-063 section 5 (A0): the drawn surface art is complete, opaque, deterministic and matches the committed output. */
class V180SurfaceArtTest {
    private static final Path TEXTURES = Path.of("src", "generated", "v1.8", "resources", "assets",
            "advancedrocketrycommunity", "textures");

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void everySurfaceTextureIsOpaqueDeterministicAndCommitted() throws IOException {
        Map<String, V180SurfaceArt.Texture> textures = V180SurfaceArt.textures();
        assertEquals(6, textures.size());
        for (Map.Entry<String, V180SurfaceArt.Texture> texture : textures.entrySet()) {
            String[] grid = texture.getValue().grid();
            assertFalse(String.join("", grid).contains("."), texture.getKey() + " is a full block face");
            byte[] png = V180MaterialArt.png(grid, texture.getValue().tint());
            assertArrayEquals(png, V180MaterialArt.png(grid, texture.getValue().tint()), texture.getKey());
            assertArrayEquals(png, Files.readAllBytes(TEXTURES.resolve(texture.getKey() + ".png")),
                    texture.getKey() + " differs from the committed DataGen output");
        }
    }
}
