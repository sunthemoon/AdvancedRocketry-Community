package io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketMonitorLabelsTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void knownBodyIdsBecomeTranslatedComponentsWithoutChangingTheWireLabel() {
        String label = "advancedrocketrycommunity:moon";
        var displayed = RocketMonitorLabels.display(label, "body.advancedrocketrycommunity.moon"::equals);
        assertEquals("body.advancedrocketrycommunity.moon", ((TranslatableContents) displayed.getContents()).getKey());
        var custom = RocketMonitorLabels.display("pack:outer/moon", key -> true);
        assertEquals("body.pack.outer.moon", ((TranslatableContents) custom.getContents()).getKey());
    }

    @Test void stationNamesAndUnknownBodiesRemainLiteral() {
        for (String label : new String[]{"Moon Base 7", "Earth", "Station: Alpha", "body.pack.moon", "pack:unknown"}) {
            assertEquals(label, RocketMonitorLabels.display(label, key -> false).getString());
            assertFalse(RocketMonitorLabels.display(label, key -> false).getContents() instanceof TranslatableContents);
        }
    }
}
