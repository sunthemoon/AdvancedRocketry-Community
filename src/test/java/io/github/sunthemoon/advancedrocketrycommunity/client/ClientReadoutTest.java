package io.github.sunthemoon.advancedrocketrycommunity.client;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClientReadoutTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void lifeSupportIsHiddenForF3F1OrNoPlayer() {
        for (boolean player : new boolean[]{false, true}) {
            for (boolean hidden : new boolean[]{false, true}) {
                for (boolean debug : new boolean[]{false, true}) {
                    assertEquals(player && !hidden && !debug, LifeSupportHud.visible(player, hidden, debug));
                }
            }
        }
    }

    @Test void orbitalBodyUsesTheCelestialTranslationKeyWithAnIdFallback() {
        var earth = BodyDisplayNames.bodyName("advancedrocketrycommunity:earth");
        var contents = assertInstanceOf(TranslatableContents.class, earth.getContents());
        assertEquals("body.advancedrocketrycommunity.earth", contents.getKey());
        assertEquals("advancedrocketrycommunity:earth", contents.getFallback());
        assertEquals("not a body id", BodyDisplayNames.bodyName("not a body id").getString());
        assertEquals("custom:untranslated", BodyDisplayNames.bodyName("custom:untranslated").getString());
    }
}
