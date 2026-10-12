package io.github.sunthemoon.advancedrocketrycommunity.station.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.SingularityContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.StarSystemContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.ExoplanetContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * W07 review R4: the controller panel has room for every button, the five warp lines (with spare rows for a
 * wrapped balance) and the wrapped status of every station code in English and Chinese. Text widths are an
 * estimate from the default font's glyph advances; the rendered panel still needs V1 visual evidence.
 */
final class StationControllerLayoutTest {
    private static final Path LANG = Path.of("src", "generated", "v1.8", "resources", "assets",
            "advancedrocketrycommunity_v180", "lang");
    private static final int BUTTON_HEIGHT = 16;
    private static final int TITLE_BOTTOM = 16;

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void everyButtonFitsAboveTheLinesWithoutOverlap() {
        for (List<StationControllerMenu.Button> buttons : List.of(WarpControllerMenu.BUTTONS,
                GravityControllerMenu.BUTTONS, AltitudeControllerMenu.BUTTONS, OrientationControllerMenu.BUTTONS)) {
            for (StationControllerMenu.Button button : buttons) {
                assertTrue(button.x() >= StationControllerMenu.TEXT_LEFT
                        && button.x() + button.width() <= StationControllerMenu.PANEL_WIDTH - StationControllerMenu.TEXT_LEFT,
                        "Button outside the panel: " + button);
                assertTrue(button.y() >= TITLE_BOTTOM
                        && button.y() + BUTTON_HEIGHT <= StationControllerMenu.LINES_TOP - 2,
                        "Button over the title or the lines: " + button);
                for (StationControllerMenu.Button other : buttons) {
                    if (other != button) {
                        assertFalse(button.x() < other.x() + other.width() && other.x() < button.x() + button.width()
                                && button.y() < other.y() + BUTTON_HEIGHT && other.y() < button.y() + BUTTON_HEIGHT,
                                "Buttons overlap: " + button + " and " + other);
                    }
                }
            }
        }
    }

    @Test
    void theLinesAndTheStatusHaveTheirOwnRowsInsideThePanel() {
        assertEquals(StationControllerMenu.PANEL_WIDTH - 2 * StationControllerMenu.TEXT_LEFT,
                StationControllerMenu.TEXT_WIDTH);
        // The warp panel's five lines, plus two rows for a wrapped line.
        assertTrue(StationControllerMenu.LINE_ROWS >= 5 + 2);
        assertTrue(StationControllerMenu.LINES_TOP + StationControllerMenu.LINE_ROWS * StationControllerMenu.LINE_HEIGHT
                <= StationControllerMenu.STATUS_TOP);
        assertTrue(StationControllerMenu.STATUS_TOP + StationControllerMenu.STATUS_ROWS
                * StationControllerMenu.STATUS_LINE_HEIGHT <= StationControllerMenu.PANEL_HEIGHT - 4);
        // No taller than a vanilla six-row chest, so it fits a 720-pixel window at GUI scale 3.
        assertTrue(StationControllerMenu.PANEL_HEIGHT <= 222);
    }

    @Test
    void everyStatusAndWarpLineFitsItsRowsInBothLanguages() throws IOException {
        for (String language : List.of("en_us", "zh_cn")) {
            JsonObject strings = JsonParser.parseString(Files.readString(LANG.resolve(language + ".json"),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            String status = text(strings, StationControllerMenu.KEY + "status");
            for (StationManagementCode code : StationManagementCode.values()) {
                String line = fill(status, text(strings, code.translationKey()), code.name());
                assertTrue(rows(line) <= StationControllerMenu.STATUS_ROWS,
                        language + " " + code + " needs " + rows(line) + " rows: " + line);
            }
            // The widest warp lines: the warp energy cap as balance, pending credit and cost, the interstellar class,
            // the longest built-in body ID as orbit and target, and the largest countdown the view can carry.
            String energy = Integer.toString(StationLimits.MAX_WARP_ENERGY);
            String body = longestBodyId();
            Map<String, List<String>> warp = Map.of(
                    "balance", List.of(energy, energy),
                    "cost", List.of(energy, text(strings, StationControllerMenu.KEY + "class.interstellar")),
                    "orbit", List.of(body),
                    "target", List.of(body),
                    "countdown", List.of(Integer.toString(Short.MAX_VALUE)));
            int used = 0;
            for (Map.Entry<String, List<String>> entry : warp.entrySet()) {
                used += rows(fill(text(strings, StationControllerMenu.KEY + entry.getKey()),
                        entry.getValue().toArray(String[]::new)));
            }
            assertTrue(used <= StationControllerMenu.LINE_ROWS, language + " warp lines need " + used + " rows");
        }
    }

    private static String longestBodyId() {
        List<CelestialBodyDefinition> bodies = new ArrayList<>(CelestialDefaults.definitions());
        bodies.addAll(PlanetaryContent.definitions());
        bodies.addAll(StarSystemContent.definitions());
        bodies.addAll(SingularityContent.definitions());
        bodies.addAll(ExoplanetContent.definitions());
        return bodies.stream().map(definition -> definition.id().toString())
                .max(Comparator.comparingInt(String::length)).orElseThrow();
    }

    private static String text(JsonObject strings, String key) {
        assertTrue(strings.has(key), "Missing " + key);
        return strings.get(key).getAsString();
    }

    private static String fill(String format, String... arguments) {
        String filled = format.replace("%%", "%");
        for (String argument : arguments) {
            filled = filled.replaceFirst("%s", java.util.regex.Matcher.quoteReplacement(argument));
        }
        return filled;
    }

    /** Greedy word wrap at the text width, breaking a word wider than a row (such as CJK text) by characters. */
    private static int rows(String text) {
        int rows = 1;
        int line = 0;
        for (String word : text.split(" ", -1)) {
            int width = word.codePoints().map(StationControllerLayoutTest::advance).sum();
            int space = line == 0 ? 0 : advance(' ');
            if (line + space + width <= StationControllerMenu.TEXT_WIDTH) {
                line += space + width;
            } else if (width <= StationControllerMenu.TEXT_WIDTH) {
                rows++;
                line = width;
            } else {
                line += space;
                for (int codePoint : word.codePoints().toArray()) {
                    if (line + advance(codePoint) > StationControllerMenu.TEXT_WIDTH) {
                        rows++;
                        line = 0;
                    }
                    line += advance(codePoint);
                }
            }
        }
        return rows;
    }

    /** Default-font advances: 6 pixels for most ASCII, narrower punctuation and letters, 9 for CJK. */
    private static int advance(int codePoint) {
        if (codePoint >= 0x2E80) {
            return 9;
        }
        String narrow2 = "i!.,:;'|";
        String narrow3 = "l`";
        String narrow4 = "tI[] ";
        String narrow5 = "fk(){}<>\"*";
        String character = new String(Character.toChars(codePoint));
        if (narrow2.contains(character)) {
            return 2;
        }
        if (narrow3.contains(character)) {
            return 3;
        }
        if (narrow4.contains(character)) {
            return 4;
        }
        return narrow5.contains(character) ? 5 : 6;
    }
}
