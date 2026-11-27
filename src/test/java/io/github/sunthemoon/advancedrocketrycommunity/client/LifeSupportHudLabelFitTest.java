package io.github.sunthemoon.advancedrocketrycommunity.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.PlayerProtectionStatus;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.Anchor;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.Mode;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.Panel;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.PanelSettings;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.Placement;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.Rect;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.Size;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W504 review: real HUD measurement/placement with the default-font advance model used by screen layout tests. */
class LifeSupportHudLabelFitTest {
    private static final String PREFIX = "hud.advancedrocketrycommunity.life_support.";

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void longestLocalizedStatusKeepsEveryPreviouslyFittingWindowAndScale() throws Exception {
        Font font = new MetricsFont();
        int checked = 0;
        int baselineFits = 0;
        for (String locale : List.of("en_us", "zh_cn")) {
            Map<String, String> language = language(locale);
            String old = language.get(PREFIX + "status").formatted(language.get(PREFIX + "state.exposed"));
            String longest = longestStatus(language, font);
            // Full counters have the largest numeric width; they are identical for the old and new label.
            String oxygen = language.get(PREFIX + "oxygen").formatted(
                    AtmosphereLimits.SUIT_OXYGEN_CAPACITY, AtmosphereLimits.SUIT_OXYGEN_CAPACITY, 4);
            for (int[] window : new int[][] {{854, 480}, {1280, 720}}) {
                for (int requested : new int[] {2, 3, 4}) {
                    int scale = guiScale(window[0], window[1], requested);
                    int width = Mth.ceil(window[0] / (double) scale);
                    int height = Mth.ceil(window[1] / (double) scale);
                    var oldText = LifeSupportHud.measure(font, Component.literal(old), width, false);
                    var newText = LifeSupportHud.measure(font, Component.literal(longest), width, false);
                    var oxygenText = LifeSupportHud.measure(font, Component.literal(oxygen), width, true);
                    boolean chinese = locale.equals("zh_cn");
                    boolean large = window[0] == 1280 && requested == 2;
                    assertEquals(window[0] == 854 || requested == 2 ? 2 : 3, scale);
                    assertEquals(large ? 640 : 427, width);
                    assertEquals(large ? 360 : 240, height);
                    assertEquals(74, font.width(old));
                    assertEquals(chinese ? 92 : 186, font.width(longest));
                    assertEquals(new Size(136, 19), oldText.normalSize());
                    assertEquals(new Size(chinese ? 136 : 200, 19), newText.normalSize());
                    assertEquals(new Size(chinese ? 145 : 140, 28), oxygenText.normalSize());
                    String context = window[0] + "x" + window[1] + " scale=" + requested + " actual=" + scale
                            + " " + locale + " status=" + longest;
                    for (boolean allPanels : new boolean[] {false, true}) {
                        Placement before = place(width, height, oldText, oxygenText, allPanels);
                        Placement after = place(width, height, newText, oxygenText, allPanels);
                        assertEquals(Mode.REQUESTED, before.mode(), context);
                        assertEquals(Mode.REQUESTED, after.mode(), context);
                        int oldStatusX = large ? 498 : 285;
                        int newStatusX = chinese ? oldStatusX : large ? 434 : 221;
                        int oxygenX = chinese ? large ? 489 : 276 : large ? 494 : 281;
                        assertEquals(new Rect(oldStatusX, 6, 136, 19), before.panels().get(0), context);
                        assertEquals(new Rect(newStatusX, 6, chinese ? 136 : 200, 19), after.panels().get(0), context);
                        assertEquals(new Rect(oxygenX, 30, chinese ? 145 : 140, 28), after.panels().get(1), context);
                        assertEquals(before.panels().subList(1, before.panels().size()),
                                after.panels().subList(1, after.panels().size()), context);
                        if (before.mode() != Mode.UNFIT) {
                            assertNotEquals(Mode.UNFIT, after.mode(), context);
                            assertAdmissible(after.panels(), width, height);
                            baselineFits++;
                        }
                        System.out.printf("W504_FIT %s panels=%d viewport=%dx%d oldText=%d newText=%d old=%s new=%s oxygen=%s before=%s after=%s%n",
                                context, before.panels().size(), width, height, font.width(old), font.width(longest),
                                oldText.normalSize(), newText.normalSize(), oxygenText.normalSize(), before, after);
                    }
                    checked++;
                }
            }
        }
        assertEquals(12, checked);
        assertEquals(24, baselineFits, "Both the original pair and the five-panel fixture must have been tested");
    }

    private static Placement place(int width, int height, LifeSupportHud.PanelText status,
                                   LifeSupportHud.PanelText oxygen, boolean allPanels) {
        var panels = new ArrayList<>(List.of(
                new Panel(new PanelSettings(-6, 6, Anchor.END, Anchor.START), status.normalSize(), status.compactSize()),
                new Panel(new PanelSettings(-6, 30, Anchor.END, Anchor.START), oxygen.normalSize(), oxygen.compactSize())));
        if (allPanels) {
            // The same hydrogen/suit/rocket sizes and default anchors as LifeSupportHudPanelsTest.
            panels.add(new Panel(new PanelSettings(-6, 62, Anchor.END, Anchor.START), new Size(136, 28), new Size(90, 30)));
            panels.add(new Panel(new PanelSettings(-6, 94, Anchor.END, Anchor.START), new Size(136, 19), new Size(100, 22)));
            panels.add(new Panel(new PanelSettings(6, 6, Anchor.START, Anchor.START), new Size(136, 28), new Size(90, 30)));
        }
        return LifeSupportHudLayout.placeAll(width, height, panels);
    }

    private static String longestStatus(Map<String, String> language, Font font) {
        List<String> states = new ArrayList<>(List.of("non_breathable_atmosphere", "vacuum", "no_air"));
        for (PlayerProtectionStatus status : PlayerProtectionStatus.values()) {
            if (status != PlayerProtectionStatus.EXPOSED) {
                states.add(status.diagnosticKey());
            }
        }
        return states.stream().map(key -> language.get(PREFIX + "status")
                        .formatted(language.get(PREFIX + "state." + key)))
                .max(Comparator.comparingInt(font::width)).orElseThrow();
    }

    private static Map<String, String> language(String locale) throws Exception {
        Map<String, String> language = new HashMap<>();
        try (var files = Files.walk(Path.of("src/generated"))) {
            for (Path file : files.filter(p -> p.endsWith("lang/" + locale + ".json")).sorted().toList()) {
                JsonParser.parseString(Files.readString(file)).getAsJsonObject().entrySet()
                        .forEach(entry -> language.put(entry.getKey(), entry.getValue().getAsString()));
            }
        }
        return language;
    }

    /** Same Window.calculateScale restatement as LifeSupportHudPanelsTest; default force-Unicode is off. */
    private static int guiScale(int width, int height, int requested) {
        int scale = 1;
        while (scale != requested && scale < width && scale < height
                && width / (scale + 1) >= 320 && height / (scale + 1) >= 240) {
            scale++;
        }
        return scale;
    }

    private static void assertAdmissible(List<Rect> panels, int width, int height) {
        for (int i = 0; i < panels.size(); i++) {
            Rect a = panels.get(i);
            assertTrue(a.x() >= 4 && a.y() >= 4 && a.x() + a.width() + 4 <= width
                    && a.y() + a.height() + 4 <= height, a.toString());
            for (int j = 0; j < i; j++) {
                Rect b = panels.get(j);
                assertTrue(Math.max(a.x(), b.x()) - Math.min(a.x() + a.width(), b.x() + b.width()) >= 4
                        || Math.max(a.y(), b.y()) - Math.min(a.y() + a.height(), b.y() + b.height()) >= 4,
                        a + " overlaps " + b);
            }
        }
    }

    /** No textures or GPU: use the established ASCII/CJK advances with Minecraft's actual line splitter. */
    private static final class MetricsFont extends Font {
        private final StringSplitter splitter = new StringSplitter((codePoint, style) -> advance(codePoint));

        private MetricsFont() {
            super(id -> { throw new AssertionError("Layout measurement must not load GPU font textures"); }, false);
        }

        @Override public int width(FormattedText text) { return Mth.ceil(splitter.stringWidth(text)); }
        @Override public int width(FormattedCharSequence text) { return Mth.ceil(splitter.stringWidth(text)); }
        @Override public int width(String text) { return Mth.ceil(splitter.stringWidth(text)); }
        @Override public List<FormattedCharSequence> split(FormattedText text, int width) {
            return splitter.splitLines(text, width, Style.EMPTY).stream()
                    .map(line -> FormattedCharSequence.forward(line.getString(), Style.EMPTY)).toList();
        }

        private static int advance(int codePoint) {
            return codePoint >= 0x2E80 ? 9 : "i!.,:;'|".indexOf(codePoint) >= 0 ? 2 : "l`".indexOf(codePoint) >= 0 ? 3
                    : "tI[] ".indexOf(codePoint) >= 0 ? 4 : "fk(){}<>\"*".indexOf(codePoint) >= 0 ? 5 : 6;
        }
    }
}
