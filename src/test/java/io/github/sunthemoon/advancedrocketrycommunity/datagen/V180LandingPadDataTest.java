package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class V180LandingPadDataTest {
    @Test void originalArtAndDataAreDeterministicAndUseConcreteByRegistryIdOnly() throws Exception {
        assertArrayEquals(V180LandingPadData.texture(), V180LandingPadData.texture());
        var image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(V180LandingPadData.texture()));
        assertEquals(16, image.getWidth()); assertEquals(16, image.getHeight());
        for (int x = 0; x < 16; x++) { for (int y = 0; y < 16; y++) { assertEquals(255, image.getRGB(x, y) >>> 24); } }
        assertEquals(3, V180LandingPadData.clientFiles().size()); assertEquals(3, V180LandingPadData.serverFiles().size());
        var recipe = V180LandingPadData.serverFiles().get("data/advancedrocketrycommunity/recipes/landing_pad.json");
        assertEquals("minecraft:gray_concrete", recipe.getAsJsonObject("key").getAsJsonObject("C").get("item").getAsString());
        assertEquals(V180LandingPadData.language(false).keySet(), V180LandingPadData.language(true).keySet());
        assertTrue(V180LandingPadData.language(false).containsKey("message.advancedrocketrycommunity.pad.reason.block_budget"));
    }
}
