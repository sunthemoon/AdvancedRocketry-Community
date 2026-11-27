package io.github.sunthemoon.advancedrocketrycommunity.client.model;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HullRenderTypesTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void cutoutKeepsEntityFormatAndAddsPolygonOffsetWithoutGlSetup() {
        var texture = ResourceLocation.parse("test:hull.png");
        var type = HullRenderTypes.cutout(texture);
        assertSame(type, HullRenderTypes.cutout(texture));
        assertNotSame(type, HullRenderTypes.cutout(ResourceLocation.parse("test:other.png")));
        assertSame(DefaultVertexFormat.NEW_ENTITY, type.format());
        assertEquals(VertexFormat.Mode.QUADS, type.mode());
        assertTrue(type.affectsCrumbling());
        assertTrue(type.toString().contains("polygon_offset_layering"), type.toString());
        assertTrue(type.toString().contains(texture.toString()), type.toString());
    }
}
