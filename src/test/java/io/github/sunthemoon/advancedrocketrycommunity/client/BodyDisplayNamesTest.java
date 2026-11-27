package io.github.sunthemoon.advancedrocketrycommunity.client;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.BodyNameFallback;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BodyDisplayNamesTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void untranslatedPackBodiesUseReadableLeadingMarkers() {
        assertEquals("[TEST] Test Mars", BodyNameFallback.readable(ResourceLocation.parse("advancedrocketrycommunity:test_mars")));
        assertEquals("[PACK] Red Giant 2", BodyNameFallback.readable(ResourceLocation.parse("other:stars/red-giant_2")));
        assertEquals("[TEST] Test Ice Moon", BodyNameFallback.readable(ResourceLocation.parse("other:system/test_ice.moon")));
        assertEquals("[PACK] Contest Mars", BodyNameFallback.readable(ResourceLocation.parse("test:contest_mars")));
        assertEquals("[TEST] Test Mars", BodyDisplayNames.bodyName("other:test_mars").getString());
    }

    @Test void translationStillWinsAndNestedKeysAreNormalized() {
        var contents = (TranslatableContents) BodyDisplayNames.bodyName("other:system/test_mars").getContents();
        assertEquals("body.other.system.test_mars", contents.getKey());
        assertEquals("[TEST] Test Mars", contents.getFallback());
        assertEquals("not an id", BodyDisplayNames.bodyName("not an id").getString());
    }
}
