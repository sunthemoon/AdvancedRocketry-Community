package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor.RocketMonitorStatus;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class V180RocketMonitorDataTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void originalTextureAndDataAreDeterministic() throws Exception {
        var png = V180RocketMonitorData.texture();
        assertArrayEquals(png, V180RocketMonitorData.texture());
        var image = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(16, image.getWidth()); assertEquals(16, image.getHeight());
        assertEquals(3, V180RocketMonitorData.clientFiles().size());
        assertEquals(3, V180RocketMonitorData.serverFiles().size());
        var files = V180RocketMonitorData.serverFiles();
        String base = "data/advancedrocketrycommunity/";
        var recipe = files.get(base + "recipes/rocket_monitoring_station.json");
        assertEquals("advancedrocketrycommunity:tracking_circuit", recipe.getAsJsonObject("key").getAsJsonObject("T").get("item").getAsString());
        var loot = files.get(base + "loot_tables/blocks/rocket_monitoring_station.json").toString();
        assertFalse(loot.contains("copy_nbt")); assertFalse(loot.contains("BlockEntityTag"));
    }
    @Test void bilingualTextCoversEveryMonitorStatusAndAction() {
        var en = V180RocketMonitorData.language(false); var zh = V180RocketMonitorData.language(true);
        assertEquals(en.keySet(), zh.keySet());
        for (var state : RocketMonitorStatus.values()) { assertTrue(en.containsKey(state.translationKey())); }
        for (var action : new String[] {"previous", "next", "quote", "arm", "disarm", "launch", "mode"}) {
            assertTrue(en.containsKey("screen.advancedrocketrycommunity.monitor." + action));
        }
        assertNotEquals(en.values(), zh.values());
    }
}
