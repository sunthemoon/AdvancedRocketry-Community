package io.github.sunthemoon.advancedrocketrycommunity.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialCapabilities;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.CelestialSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.client.starmap.RocketStarMap;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180ScreenLayoutLanguage;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.network.EndgameDeviceView;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayStatus;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Logical pixels and estimated advances exercise both locales; these checks do not approve a GPU visual Gate. */
class Round7ScreenTextTest {
    private static final String VIEW = "advancedrocketrycommunity.endgame.view.";
    private static final Map<String, Map<String, String>> LANGUAGES = new HashMap<>();

    @BeforeAll static void bootstrap() throws IOException {
        MinecraftBootstrap.initialize();
        for (String locale : List.of("en_us", "zh_cn")) {
            var language = new HashMap<String, String>();
            for (Path root : List.of(Path.of("src/generated"), Path.of("src/main/resources"))) {
                try (var files = Files.walk(root)) {
                    for (Path path : files.filter(file -> file.getFileName().toString().equals(locale + ".json")
                            && file.getParent().getFileName().toString().equals("lang")).toList()) {
                        JsonParser.parseString(Files.readString(path)).getAsJsonObject().entrySet()
                                .forEach(entry -> language.put(entry.getKey(), entry.getValue().getAsString()));
                    }
                }
            }
            LANGUAGES.put(locale, language);
        }
    }

    @Test void terminalStatusAndTargetHaveGapsFromBothArrowsAndActionRowAtBothScales() {
        for (int scale : new int[]{2, 3}) {
            assertTrue((10 + 18 + 3) * scale <= SatelliteScreenLayout.TERMINAL_SELECTION_X * scale);
            assertTrue((SatelliteScreenLayout.TERMINAL_SELECTION_X + SatelliteScreenLayout.TERMINAL_SELECTION_WIDTH + 3)
                    * scale <= 196 * scale);
            assertTrue((SatelliteScreenLayout.TERMINAL_TARGET_Y + 9 + 2) * scale
                    <= SatelliteScreenLayout.TERMINAL_STATUS_Y * scale);
            assertTrue((SatelliteScreenLayout.TERMINAL_STATUS_Y + 9 + 2) * scale <= 105 * scale);
        }
        for (var language : LANGUAGES.values()) {
            for (var code : SatelliteOperationCode.values()) {
                assertNotNull(language.get(code.translationKey()), code.translationKey());
                String full = language.get(code.translationKey()) + " [" + code.name() + "]";
                var fit = BoundedTextLayout.fit(full, SatelliteScreenLayout.TERMINAL_SELECTION_WIDTH, Round7ScreenTextTest::width);
                assertTrue(width(fit.text()) * fit.scale() <= SatelliteScreenLayout.TERMINAL_SELECTION_WIDTH);
                assertTrue(fit.text().endsWith("[" + code.name() + "]"));
            }
        }
    }

    @Test void bayNoticeClearsTheHatchAndSlotFramesInBothLocalesAndScales() {
        for (int scale : new int[]{2, 3}) {
            assertTrue((SatelliteScreenLayout.BAY_NOTICE_Y + 9 + 3) * scale <= SatelliteScreenLayout.BAY_HATCH_Y * scale);
            assertTrue((SatelliteScreenLayout.BAY_HATCH_Y + 3) * scale < 34 * scale);
            assertTrue((SatelliteScreenLayout.BAY_HATCH_BOTTOM + 2) * scale <= 63 * scale);
        }
        for (var language : LANGUAGES.values()) {
            for (var reason : SatelliteBayStatus.Reason.values()) {
                String notice = language.get(reason.translationKey());
                assertNotNull(notice, reason.translationKey());
                var fit = BoundedTextLayout.fit(notice, 160, Round7ScreenTextTest::width);
                assertTrue(width(fit.text()) * fit.scale() <= 160);
            }
        }
    }

    @Test void completeBuilderHintWrapsWithinItsPanelWithoutTouchingSlotsOrInventory() {
        var splitter = new StringSplitter((codePoint, style) -> glyphWidth(codePoint));
        for (var language : LANGUAGES.values()) {
            String hint = language.get("screen.advancedrocketrycommunity.satellite_builder.no_kind");
            assertNotNull(hint);
            var lines = splitter.splitLines(Component.literal(hint), SatelliteScreenLayout.BUILDER_HINT_WIDTH, Style.EMPTY);
            assertFalse(lines.isEmpty());
            assertTrue(lines.size() <= SatelliteScreenLayout.BUILDER_HINT_ROWS, hint);
            String rejoined = String.join("", lines.stream().map(line -> line.getString().replace(" ", "")).toList());
            assertEquals(hint.replace(" ", ""), rejoined);
            for (var line : lines) { assertTrue(width(line.getString()) <= SatelliteScreenLayout.BUILDER_HINT_WIDTH); }
        }
        for (int scale : new int[]{2, 3}) {
            assertTrue((70 + 3) * scale < SatelliteScreenLayout.BUILDER_HINT_Y * scale); // Last component row.
            assertTrue((SatelliteScreenLayout.BUILDER_HINT_Y + SatelliteScreenLayout.BUILDER_HINT_ROWS * 9 + 3) * scale < 104 * scale);
            assertTrue((SatelliteScreenLayout.BUILDER_HINT_Y + SatelliteScreenLayout.BUILDER_HINT_ROWS * 9 + 3) * scale < 108 * scale);
            assertTrue(12 + SatelliteScreenLayout.BUILDER_HINT_WIDTH + 3 < 197); // Charge slot's left frame.
        }
    }

    @Test void generatorOrbitBodyUsesTheSharedDrillTranslationInBothLanguages() {
        Component value = EndgameViewText.value(EndgameDeviceView.Line.text(VIEW + "body", "advancedrocketrycommunity:cygnus_x1"));
        assertEquals(BodyDisplayNames.bodyName("advancedrocketrycommunity:cygnus_x1"), value);
        assertEquals("Cygnus X-1", render(value, "en_us"));
        assertEquals("天鹅座 X-1", render(value, "zh_cn"));
        assertEquals("Orbit body: Cygnus X-1", render(EndgameViewText.line(
                EndgameDeviceView.Line.text(VIEW + "body", "advancedrocketrycommunity:cygnus_x1")), "en_us"));
    }

    @Test void fuelTableGetsANamedTranslationAndUnknownTablesRetainTheirIds() {
        Component value = EndgameViewText.value(EndgameDeviceView.Line.text(VIEW + "fuel_table", "advancedrocketrycommunity:default"));
        var contents = assertInstanceOf(TranslatableContents.class, value.getContents());
        for (String locale : LANGUAGES.keySet()) {
            String expected = V180ScreenLayoutLanguage.entries(locale.equals("zh_cn")).get(contents.getKey());
            assertNotNull(expected);
            assertEquals(expected, render(value, locale));
        }
        assertEquals("custom:unknown", EndgameViewText.value(
                EndgameDeviceView.Line.text(VIEW + "fuel_table", "custom:unknown")).getString());
    }

    @Test void railgunDestinationTranslatesOnlyTheLevelAndKeepsAllCoordinates() {
        Component value = EndgameViewText.value(EndgameDeviceView.Line.text(VIEW + "destination_at",
                "advancedrocketrycommunity:moon 14, 50, 12"));
        assertEquals("Moon 14, 50, 12", render(value, "en_us"));
        assertEquals("月球 14, 50, 12", render(value, "zh_cn"));
        assertEquals("Earth -14, 50, 12", render(EndgameViewText.value(EndgameDeviceView.Line.text(
                VIEW + "destination_at", "minecraft:overworld -14, 50, 12")), "en_us"));
    }

    @Test void selectedAnchorTranslatesKindAndLevelButKeepsIdentityCoordinatesAndSelection() {
        Component value = EndgameViewText.value(EndgameDeviceView.Line.text(VIEW + "anchor",
                "elevator_anchor e028cd7e advancedrocketrycommunity:tau_ceti_f 40, 71, 40 (1 / 2)"));
        for (String locale : LANGUAGES.keySet()) {
            var language = LANGUAGES.get(locale);
            assertEquals(language.get("block.advancedrocketrycommunity.elevator_anchor") + " e028cd7e "
                    + language.get("body.advancedrocketrycommunity.tau_ceti_f") + " 40, 71, 40 (1 / 2)", render(value, locale));
        }
        assertEquals("-", EndgameViewText.value(EndgameDeviceView.Line.text(VIEW + "anchor", "-")).getString());
    }

    @Test void starMapInfoShowsOnlyTheSharedTranslatedBodyName() {
        Component name = RocketStarMap.bodyLabel(new ResourceLocation("advancedrocketrycommunity", "moon"));
        assertEquals(BodyDisplayNames.bodyName("advancedrocketrycommunity:moon"), name);
        assertTrue(name.getSiblings().isEmpty());
        assertEquals("Moon", render(name, "en_us"));
        assertEquals("月球", render(name, "zh_cn"));
    }

    @Test void locationNamesUseTheSnapshotLevelMappingWithoutGuessingUnknownIds() {
        var body = new ResourceLocation("pack", "different_body");
        var level = new ResourceLocation("pack", "different_level");
        var snapshot = new CelestialSnapshot(2, List.of(new CelestialSnapshot.Entry(body, Optional.empty(), Optional.of(level),
                1, 0, false, 270, body, body, new CelestialCapabilities(true, true, false), 1, 0)));
        Component name = BodyDisplayNames.levelName(level.toString(), Optional.of(snapshot));
        assertEquals("body.pack.different_body", ((TranslatableContents) name.getContents()).getKey());
        assertEquals("pack:different_body", name.getString());
        assertEquals("pack:unknown", BodyDisplayNames.levelName("pack:unknown", Optional.of(snapshot)).getString());
    }

    @Test void unrelatedAndMalformedViewStringsStayIntactAndExplicitTranslationWins() {
        for (String value : List.of("", "-", "not an id 1, 2, 3", "elevator_anchor short", "elevator_anchor short ??? 1, 2, 3")) {
            for (String field : List.of("anchor", "destination_at", "far_end_at")) {
                assertEquals(value, EndgameViewText.value(EndgameDeviceView.Line.text(VIEW + field, value)).getString());
            }
        }
        assertEquals("IN_SYSTEM 200 FE 20 t", EndgameViewText.value(EndgameDeviceView.Line.text(VIEW + "route", "IN_SYSTEM 200 FE 20 t")).getString());
        Component translated = EndgameViewText.value(EndgameDeviceView.Line.key(VIEW + "body", "test.explicit"));
        assertEquals("test.explicit", ((TranslatableContents) translated.getContents()).getKey());
        assertEquals("body.pack.nested.body", ((TranslatableContents) BodyDisplayNames.bodyName("pack:nested/body").getContents()).getKey());
    }

    private static String render(Component component, String locale) {
        return render(Component.Serializer.toJsonTree(component).getAsJsonObject(), LANGUAGES.get(locale));
    }

    private static String render(JsonObject component, Map<String, String> language) {
        String text;
        if (component.has("translate")) {
            String key = component.get("translate").getAsString();
            String template = language.get(key);
            if (template == null) {
                assertTrue(component.has("fallback"), "Missing translation: " + key);
                template = component.get("fallback").getAsString();
            }
            var args = new java.util.ArrayList<String>();
            if (component.has("with")) {
                component.getAsJsonArray("with").forEach(arg -> args.add(arg.isJsonObject()
                        ? render(arg.getAsJsonObject(), language) : arg.getAsString()));
            }
            text = String.format(Locale.ROOT, template, args.toArray());
        } else { text = component.get("text").getAsString(); }
        if (component.has("extra")) {
            for (var extra : component.getAsJsonArray("extra")) { text += render(extra.getAsJsonObject(), language); }
        }
        return text;
    }

    private static int glyphWidth(int c) { return c >= 0x2E80 ? 9 : "il.,:;!".indexOf(c) >= 0 ? 2 : c == ' ' ? 4 : 6; }
    private static int width(String text) { return text.codePoints().map(Round7ScreenTextTest::glyphWidth).sum(); }
}
