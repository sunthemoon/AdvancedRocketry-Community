package io.github.sunthemoon.advancedrocketrycommunity.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.HashMap;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketNavigationStatus;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketFlightScreenLayoutTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void everyNavigationInstructionFitsTwoRowsWithoutTouchingBoardOrLaunch() throws Exception {
        var splitter = new StringSplitter((cp, style) -> width(new String(Character.toChars(cp))));
        for (String locale : List.of("en_us", "zh_cn")) {
            var language = new HashMap<String, String>();
            try (var files = Files.walk(Path.of("src/generated"))) {
                for (var path : files.filter(p -> p.endsWith("lang/" + locale + ".json")).sorted().toList()) {
                    JsonParser.parseString(Files.readString(path)).getAsJsonObject().entrySet()
                            .forEach(e -> language.put(e.getKey(), e.getValue().getAsString()));
                }
            }
            for (var status : RocketNavigationStatus.values()) {
                String full = language.get(status.translationKey()); assertNotNull(full, status.translationKey());
                var lines = splitter.splitLines(Component.literal(full), RocketFlightScreenLayout.STATUS_WIDTH, Style.EMPTY);
                assertTrue(lines.size() <= RocketFlightScreenLayout.STATUS_ROWS, locale + " " + full);
                assertEquals(full.replace(" ", ""), String.join("", lines.stream().map(l -> l.getString().replace(" ", "")).toList()));
                for (var line : lines) { assertTrue(width(line.getString()) <= RocketFlightScreenLayout.STATUS_WIDTH); }
                var map = BoundedTextLayout.fit(full, 204, RocketFlightScreenLayoutTest::width);
                assertTrue(width(map.text()) <= 204);
            }
        }
        assertTrue(RocketFlightScreenLayout.STATUS_Y + 9 * RocketFlightScreenLayout.STATUS_ROWS + 2
                <= RocketFlightScreenLayout.BOARD_BUTTON_Y);
        assertTrue(RocketFlightScreenLayout.BOARD_BUTTON_Y + RocketFlightScreenLayout.BOARD_BUTTON_HEIGHT + 2 <= 176);
    }
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

    @Test void packBodyButtonUsesBoundedLabelAndKeepsLeadingMarker() {
        for (String full : List.of("[TEST] Test Mars", "[PACK] A Very Long Datapack Body Name")) {
            var fit = RocketFlightScreenLayout.stationLabel(full, RocketFlightScreenLayoutTest::width);
            assertTrue(width(fit.text()) <= 86);
            assertTrue(fit.text().startsWith(full.substring(0, 6)));
            if (fit.shortened()) { assertTrue(fit.text().endsWith("...")); }
        }
    }
}
