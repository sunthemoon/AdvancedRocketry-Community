package io.github.sunthemoon.advancedrocketrycommunity.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class RocketFlightScreenLayoutTest {
    @Test
    void defaultFontFuelRowHasSpaceBeforeThePanelBorderAndDestinationControls() {
        int defaultFontLineHeight = 9;
        assertTrue(RocketFlightScreenLayout.PANEL_TOP < RocketFlightScreenLayout.FUEL_TEXT_Y);
        assertTrue(RocketFlightScreenLayout.FUEL_TEXT_Y + defaultFontLineHeight + 2
                < RocketFlightScreenLayout.PANEL_BOTTOM,
                "Fuel text needs padding before the panel's lower border");
        assertTrue(RocketFlightScreenLayout.PANEL_BOTTOM + 4
                <= RocketFlightScreenLayout.DESTINATION_BUTTON_Y,
                "The panel must leave room before the destination controls");
    }

    @Test
    void selectedUnselectedAndCountdownStationLabelsKeepTheirInsetInBothLocalesAndScales() throws Exception {
        assertEquals(6, RocketFlightScreenLayout.BUTTON_TEXT_INSET);
        assertEquals(98, RocketFlightScreenLayout.STATION_BUTTON_WIDTH);
        for (String locale : List.of("en_us", "zh_cn")) {
            Path language = Path.of("src/generated/v0.7/resources/assets/advancedrocketrycommunity_v070/lang/" + locale + ".json");
            String template = JsonParser.parseString(Files.readString(language)).getAsJsonObject()
                    .get("screen.advancedrocketrycommunity.rocket.station_choice").getAsString();
            for (String name : List.of("ArceTester's station", "空间站测试名称很长", "短", "A", "A".repeat(64))) {
                String choice = template.formatted(name, 1, 12);
                for (String full : List.of(choice, "[ " + choice + " ]", "[ " + name + " ]")) {
                    var fit = RocketFlightScreenLayout.stationLabel(full, RocketFlightScreenLayoutTest::width);
                    assertEquals(1, fit.scale()); // Button labels cannot apply the readout's scaling fallback.
                    int displayedWidth = width(fit.text());
                    int leftInset = (RocketFlightScreenLayout.STATION_BUTTON_WIDTH - displayedWidth) / 2;
                    for (int scale : new int[]{2, 3}) {
                        assertTrue(leftInset * scale >= RocketFlightScreenLayout.BUTTON_TEXT_INSET * scale, full);
                        assertTrue((leftInset + displayedWidth) * scale
                                <= (RocketFlightScreenLayout.STATION_BUTTON_WIDTH - RocketFlightScreenLayout.BUTTON_TEXT_INSET) * scale, full);
                    }
                    if (fit.shortened()) { assertTrue(fit.text().endsWith("...")); }
                    else { assertEquals(full, fit.text()); }
                }
            }
        }
    }

    private static int width(String text) {
        return text.codePoints().map(c -> c >= 0x2E80 ? 9 : "i!.,:;'|".indexOf(c) >= 0 ? 2 : "l`".indexOf(c) >= 0 ? 3
                : "tI[] ".indexOf(c) >= 0 ? 4 : "fk(){}<>\"*".indexOf(c) >= 0 ? 5 : 6).sum();
    }
}
