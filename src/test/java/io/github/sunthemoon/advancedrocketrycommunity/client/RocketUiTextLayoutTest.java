package io.github.sunthemoon.advancedrocketrycommunity.client;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180FluidFuelLoaderData;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180RocketUiLanguage;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightFeedback;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Logical-pixel/default-font advance estimates, not a live-client visual approval. */
class RocketUiTextLayoutTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void noStationsFitsTheExistingNinetyEightPixelButtonInBothLanguages() {
        for (boolean chinese : new boolean[]{false, true}) {
            var text = V180RocketUiLanguage.entries(chinese).get("screen.advancedrocketrycommunity.rocket.no_stations_short");
            assertTrue(width(text) <= 98 - 6, text);
            assertFalse(BoundedTextLayout.fit(text, 98 - 6, RocketUiTextLayoutTest::width).shortened());
        }
    }

    @Test void everyFluidLoaderStatusFitsInsideTheReadoutAndLongReasonsAreMarkedForTooltip() {
        int width = FluidFuelLoaderScreen.TEXT_RIGHT - FluidFuelLoaderScreen.TEXT_X;
        assertEquals(132, width);
        assertTrue(FluidFuelLoaderScreen.TEXT_RIGHT <= 176 - 8);
        for (boolean chinese : new boolean[]{false, true}) {
            var entries = V180FluidFuelLoaderData.language(chinese);
            for (var status : FluidFuelLoaderStatus.values()) {
                String text = entries.get(status.translationKey());
                if (text == null) { continue; } // OWNER_PENDING_FULL is supplied by its own existing provider.
                var line = BoundedTextLayout.fit(text, width, RocketUiTextLayoutTest::width);
                assertTrue(width(line.text()) * line.scale() <= width, status.toString());
                assertEquals(width(text) > width, line.shortened());
            }
        }
    }

    @Test void successfulSeatRepliesAndAllMonitorLinkerRepliesHaveBilingualEntries() {
        for (boolean chinese : new boolean[]{false, true}) {
            var entries = V180RocketUiLanguage.entries(chinese);
            for (String key : new String[]{RocketFlightFeedback.BOARDED, RocketFlightFeedback.LEFT,
                    "message.advancedrocketrycommunity.linker.monitor_selected",
                    "message.advancedrocketrycommunity.linker.monitor_bound",
                    "message.advancedrocketrycommunity.linker.monitor_unbound"}) {
                assertNotNull(entries.get(key), key);
                assertFalse(entries.get(key).contains("%s"), key);
            }
        }
    }

    private static int width(String text) {
        return text.codePoints().map(c -> c >= 0x2E80 ? 9 : "i!.,:;'|".indexOf(c) >= 0 ? 2 : "l`".indexOf(c) >= 0 ? 3
                : "tI[] ".indexOf(c) >= 0 ? 4 : "fk(){}<>\"*".indexOf(c) >= 0 ? 5 : 6).sum();
    }
}
