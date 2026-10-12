package io.github.sunthemoon.advancedrocketrycommunity.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.Anchor;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.Mode;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.Panel;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.PanelSettings;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.Placement;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.Rect;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.Size;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The ADR-066 section 7.2 multi-panel placement: environment, oxygen, hydrogen, suit and rocket readout. */
class LifeSupportHudPanelsTest {
    /** Normal sizes for a 9-pixel line, as the HUD adapter measures them. */
    private static final Size ENV = new Size(136, 19);
    private static final Size OXY = new Size(136, 28);
    private static final Size H2 = new Size(136, 28);
    private static final Size SUIT = new Size(136, 19);
    private static final Size ROCKET = new Size(136, 28);
    /** Two wrapped lines for text panels, plus the bar for gauge panels. */
    private static final Size ENV_COMPACT = new Size(80, 22);
    private static final Size OXY_COMPACT = new Size(90, 30);
    private static final Size H2_COMPACT = new Size(90, 30);
    private static final Size SUIT_COMPACT = new Size(100, 22);
    private static final Size ROCKET_COMPACT = new Size(90, 30);

    private static final PanelSettings ENV_DEFAULT = at(-6, 6, Anchor.END, Anchor.START);
    private static final PanelSettings OXY_DEFAULT = at(-6, 30, Anchor.END, Anchor.START);
    private static final PanelSettings H2_DEFAULT = at(-6, 62, Anchor.END, Anchor.START);
    private static final PanelSettings SUIT_DEFAULT = at(-6, 94, Anchor.END, Anchor.START);
    private static final PanelSettings ROCKET_FIXED = at(6, 6, Anchor.START, Anchor.START);

    private static PanelSettings at(int x, int y, Anchor anchorX, Anchor anchorY) {
        return new PanelSettings(x, y, anchorX, anchorY);
    }

    private static List<Panel> defaults() {
        return List.of(new Panel(ENV_DEFAULT, ENV, ENV_COMPACT), new Panel(OXY_DEFAULT, OXY, OXY_COMPACT),
                new Panel(H2_DEFAULT, H2, H2_COMPACT), new Panel(SUIT_DEFAULT, SUIT, SUIT_COMPACT),
                new Panel(ROCKET_FIXED, ROCKET, ROCKET_COMPACT));
    }

    private static List<Panel> withSettings(PanelSettings... settings) {
        List<Panel> base = defaults();
        List<Panel> panels = new ArrayList<>();
        for (int i = 0; i < settings.length; i++) {
            panels.add(new Panel(settings[i], base.get(i).normal(), base.get(i).compact()));
        }
        return panels;
    }

    @Test
    void defaultPanelsKeepTheirRequestedPlacesBesideTheRocketReadout() {
        // A 1920x1080 window at GUI scale 4 is 480x270 scaled pixels.
        Placement placement = LifeSupportHudLayout.placeAll(480, 270, defaults());
        assertEquals(new Placement(List.of(new Rect(338, 6, 136, 19), new Rect(338, 30, 136, 28),
                new Rect(338, 62, 136, 28), new Rect(338, 94, 136, 19), new Rect(6, 6, 136, 28)),
                Mode.REQUESTED), placement);
    }

    @Test
    void anyVisibleSubsetKeepsItsOwnRequestedPlaces() {
        Placement withoutHydrogen = LifeSupportHudLayout.placeAll(480, 270, List.of(
                defaults().get(0), defaults().get(1), defaults().get(3)));
        assertEquals(new Placement(List.of(new Rect(338, 6, 136, 19), new Rect(338, 30, 136, 28),
                new Rect(338, 94, 136, 19)), Mode.REQUESTED), withoutHydrogen);
        Placement rocketOnly = LifeSupportHudLayout.placeAll(480, 270, List.of(defaults().get(4)));
        assertEquals(new Placement(List.of(new Rect(6, 6, 136, 28)), Mode.REQUESTED), rocketOnly);
    }

    @Test
    void collidingRequestsAreDisplacedBesideEarlierPanelsInOrder() {
        Size size = new Size(100, 20);
        PanelSettings centre = at(0, 0, Anchor.CENTER, Anchor.CENTER); // (150, 140) in 400x300
        List<Panel> panels = List.of(new Panel(centre, size, size), new Panel(centre, size, size),
                new Panel(centre, size, size), new Panel(centre, size, size), new Panel(centre, size, size));
        Placement placement = LifeSupportHudLayout.placeAll(400, 300, panels);
        // Oxygen goes below the environment panel; hydrogen finds below taken and goes above; the suit panel finds
        // both taken and goes left; the rocket readout goes right.
        assertEquals(new Placement(List.of(new Rect(150, 140, 100, 20), new Rect(150, 164, 100, 20),
                new Rect(150, 116, 100, 20), new Rect(46, 140, 100, 20), new Rect(254, 140, 100, 20)),
                Mode.DISPLACED), placement);
    }

    @Test
    void laterPanelsAlsoTryPlacesBesideEveryEarlierPanel() {
        // In a 400x60 viewport every panel shares one row: the third panel is left of neither the first nor the
        // second, so it takes the first free place right of the second.
        Size size = new Size(100, 20);
        PanelSettings left = at(4, 0, Anchor.START, Anchor.CENTER);
        List<Panel> panels = List.of(new Panel(left, size, size), new Panel(left, size, size),
                new Panel(left, size, size));
        Placement placement = LifeSupportHudLayout.placeAll(400, 60, panels);
        assertEquals(new Placement(List.of(new Rect(4, 20, 100, 20), new Rect(108, 20, 100, 20),
                new Rect(212, 20, 100, 20)), Mode.DISPLACED), placement);
    }

    @Test
    void compactColumnsWrapWhenNeitherStackNorRowFits() {
        Size normal = new Size(300, 20);
        Size compact = new Size(50, 40);
        PanelSettings any = at(0, 0, Anchor.START, Anchor.START);
        List<Panel> panels = List.of(new Panel(any, normal, compact), new Panel(any, normal, compact),
                new Panel(any, normal, compact), new Panel(any, normal, compact));
        // Vertical needs 4..172 > 96, horizontal needs 4..216 > 196; two columns of two fit.
        assertEquals(new Placement(List.of(new Rect(4, 4, 50, 40), new Rect(4, 48, 50, 40),
                new Rect(58, 4, 50, 40), new Rect(58, 48, 50, 40)), Mode.COMPACT),
                LifeSupportHudLayout.placeAll(200, 100, panels));
        // The vertical stack is preferred whenever it fits.
        assertEquals(new Placement(List.of(new Rect(4, 4, 50, 40), new Rect(4, 48, 50, 40),
                new Rect(4, 92, 50, 40), new Rect(4, 136, 50, 40)), Mode.COMPACT),
                LifeSupportHudLayout.placeAll(200, 180, panels));
        // Then the horizontal row.
        assertEquals(new Placement(List.of(new Rect(4, 4, 50, 40), new Rect(58, 4, 50, 40),
                new Rect(112, 4, 50, 40), new Rect(166, 4, 50, 40)), Mode.COMPACT),
                LifeSupportHudLayout.placeAll(220, 100, panels));
    }

    @Test
    void impossibleViewportsReturnTheFullCompactVerticalStackAsUnfit() {
        List<Rect> stack = List.of(new Rect(4, 4, 80, 22), new Rect(4, 30, 90, 30), new Rect(4, 64, 90, 30),
                new Rect(4, 98, 100, 22), new Rect(4, 124, 90, 30));
        for (int[] viewport : new int[][] {{0, 0}, {1, 1}, {8, 8}, {60, 400}, {400, 20}}) {
            assertEquals(new Placement(stack, Mode.UNFIT),
                    LifeSupportHudLayout.placeAll(viewport[0], viewport[1], defaults()),
                    viewport[0] + "x" + viewport[1]);
        }
    }

    @Test
    void invalidPanelListsAreRejected() {
        assertThrows(NullPointerException.class, () -> LifeSupportHudLayout.placeAll(400, 300, null));
        assertThrows(IllegalArgumentException.class, () -> LifeSupportHudLayout.placeAll(400, 300, List.of()));
        List<Panel> six = new ArrayList<>(defaults());
        six.add(defaults().get(0));
        assertThrows(IllegalArgumentException.class, () -> LifeSupportHudLayout.placeAll(400, 300, six));
        assertThrows(NullPointerException.class,
                () -> LifeSupportHudLayout.placeAll(400, 300, Arrays.asList(defaults().get(0), null)));
        assertThrows(IllegalArgumentException.class, () -> LifeSupportHudLayout.placeAll(-1, 300, defaults()));
        assertThrows(IllegalArgumentException.class, () -> LifeSupportHudLayout.placeAll(400, -1, defaults()));
        assertThrows(NullPointerException.class, () -> new Panel(null, ENV, ENV_COMPACT));
        assertThrows(NullPointerException.class, () -> new Panel(ENV_DEFAULT, null, ENV_COMPACT));
        assertThrows(NullPointerException.class, () -> new Panel(ENV_DEFAULT, ENV, null));
        assertThrows(NullPointerException.class, () -> new Placement(List.of(), null));
        assertThrows(NullPointerException.class, () -> new Placement(null, Mode.UNFIT));
    }

    @Test
    void extremeOffsetsAndEveryAnchorNeverOverlapOrLeaveTheMargin() {
        int[] offsets = {LifeSupportHudLayout.MIN_OFFSET, -100, -6, 0, 6, 100, LifeSupportHudLayout.MAX_OFFSET};
        int[][] viewports = {{320, 240}, {427, 240}, {480, 270}, {640, 360}, {960, 540}, {1920, 1080}, {3840, 2160}};
        Anchor[] anchors = Anchor.values();
        int checked = 0;
        for (int[] viewport : viewports) {
            for (int ax = 0; ax < 3; ax++) {
                for (int ay = 0; ay < 3; ay++) {
                    for (int dx : offsets) {
                        for (int dy : offsets) {
                            // Rotate the anchors and negate the offsets so the five panels request different corners.
                            PanelSettings[] settings = new PanelSettings[5];
                            for (int i = 0; i < 5; i++) {
                                settings[i] = at(i % 2 == 0 ? dx : -dx, i < 2 ? dy : -dy,
                                        anchors[(ax + i) % 3], anchors[(ay + 2 * i) % 3]);
                            }
                            Placement placement = LifeSupportHudLayout.placeAll(viewport[0], viewport[1],
                                    withSettings(settings));
                            assertNotEquals(Mode.UNFIT, placement.mode(), "legal GUI viewports always fit");
                            assertAdmissible(placement.panels(), viewport[0], viewport[1]);
                            checked++;
                        }
                    }
                }
            }
        }
        assertEquals(7 * 9 * 7 * 7, checked);
    }

    @Test
    void everyWindowSizeAndGuiScaleKeepsTheDefaultPanelsWholeAndApart() {
        int[][] windows = {{320, 240}, {640, 480}, {854, 480}, {1280, 720}, {1366, 768}, {1920, 1080},
                {2560, 1440}, {3840, 2160}, {7680, 4320}, {1080, 1920}};
        int checked = 0;
        for (int[] window : windows) {
            for (int requestedScale = 0; requestedScale <= 8; requestedScale++) {
                int scale = guiScale(window[0], window[1], requestedScale);
                int width = (int) Math.ceil(window[0] / (double) scale);
                int height = (int) Math.ceil(window[1] / (double) scale);
                Placement placement = LifeSupportHudLayout.placeAll(width, height, defaults());
                assertEquals(Mode.REQUESTED, placement.mode(), width + "x" + height);
                assertAdmissible(placement.panels(), width, height);
                for (int panels = 1; panels <= 5; panels++) {
                    Placement subset = LifeSupportHudLayout.placeAll(width, height, defaults().subList(0, panels));
                    assertEquals(placement.panels().subList(0, panels), subset.panels());
                }
                checked++;
            }
        }
        assertEquals(10 * 9, checked);
    }

    @Test
    void placementIsDeterministicAndEveryResultIsAdmissibleOrTheUnfitStack() {
        Size wide = new Size(150, 30);
        Size compact = new Size(70, 24);
        int checked = 0;
        for (int width = 0; width <= 400; width += 25) {
            for (int height = 0; height <= 300; height += 20) {
                for (Anchor anchor : Anchor.values()) {
                    PanelSettings settings = at(0, 0, anchor, anchor);
                    List<Panel> panels = List.of(new Panel(settings, wide, compact),
                            new Panel(settings, wide, compact), new Panel(settings, wide, compact),
                            new Panel(settings, wide, compact));
                    Placement placement = LifeSupportHudLayout.placeAll(width, height, panels);
                    assertEquals(placement, LifeSupportHudLayout.placeAll(width, height, panels));
                    if (placement.mode() == Mode.UNFIT) {
                        assertEquals(List.of(new Rect(4, 4, 70, 24), new Rect(4, 32, 70, 24),
                                new Rect(4, 60, 70, 24), new Rect(4, 88, 70, 24)), placement.panels());
                    } else {
                        assertAdmissible(placement.panels(), width, height);
                    }
                    checked++;
                }
            }
        }
        assertEquals(17 * 16 * 3, checked);
    }

    /** Minecraft's Window.calculateScale: the requested scale, or the largest that keeps 320x240 (0 = auto). */
    private static int guiScale(int width, int height, int requested) {
        int scale = 1;
        while (scale != requested && scale < width && scale < height
                && width / (scale + 1) >= 320 && height / (scale + 1) >= 240) {
            scale++;
        }
        return scale;
    }

    /** Independent restatement: inside the four-pixel margin and at least four pixels apart on one axis. */
    private static void assertAdmissible(List<Rect> rects, long width, long height) {
        for (int i = 0; i < rects.size(); i++) {
            Rect a = rects.get(i);
            assertTrue(a.x() >= 4 && a.y() >= 4 && a.x() + a.width() + 4 <= width
                    && a.y() + a.height() + 4 <= height, "panel " + i + " " + a + " leaves " + width + "x" + height);
            for (int j = 0; j < i; j++) {
                Rect b = rects.get(j);
                boolean horizontal = Math.max(a.x(), b.x()) - Math.min(a.x() + a.width(), b.x() + b.width()) >= 4;
                boolean vertical = Math.max(a.y(), b.y()) - Math.min(a.y() + a.height(), b.y() + b.height()) >= 4;
                assertTrue(horizontal || vertical, "panels " + j + " and " + i + " overlap: " + b + " " + a);
            }
        }
    }
}
