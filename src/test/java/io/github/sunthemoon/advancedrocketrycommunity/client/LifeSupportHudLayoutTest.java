package io.github.sunthemoon.advancedrocketrycommunity.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.Anchor;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.Layout;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.Mode;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.PanelSettings;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.Rect;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.Size;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pure geometry of C18d-HUD-ENV-O2-01 CONTRACT-02; Font, config and pose binding are tested with the adapter. */
class LifeSupportHudLayoutTest {
    private static final Size ENV = new Size(100, 20);
    private static final Size OXY = new Size(60, 30);
    private static final Size ENV_COMPACT = new Size(50, 40);
    private static final Size OXY_COMPACT = new Size(30, 50);

    private static PanelSettings at(int x, int y, Anchor anchorX, Anchor anchorY) {
        return new PanelSettings(x, y, anchorX, anchorY);
    }

    private static Layout place(int width, int height, PanelSettings environment, PanelSettings oxygen) {
        return LifeSupportHudLayout.place(width, height, environment, oxygen, ENV, OXY, ENV_COMPACT, OXY_COMPACT);
    }

    private static Layout layout(Rect environment, Rect oxygen, Mode mode) {
        return new Layout(environment, oxygen, mode);
    }

    private static Rect env(long x, long y) {
        return new Rect(x, y, ENV.width(), ENV.height());
    }

    private static Rect oxy(long x, long y) {
        return new Rect(x, y, OXY.width(), OXY.height());
    }

    /** Offsets that keep every anchor unclamped in a 400x300 viewport. */
    private static int offset(Anchor anchor) {
        return switch (anchor) {
            case START -> 10;
            case CENTER -> 3;
            case END -> -10;
        };
    }

    private static Anchor opposite(Anchor anchor) {
        return anchor == Anchor.START ? Anchor.END : Anchor.START;
    }

    private static long envX(Anchor anchor) {
        return switch (anchor) {
            case START -> 10;
            case CENTER -> 153; // floor((400 - 100) / 2) + 3
            case END -> 290; // 400 - 100 - 10
        };
    }

    private static long envY(Anchor anchor) {
        return switch (anchor) {
            case START -> 10;
            case CENTER -> 143; // floor((300 - 20) / 2) + 3
            case END -> 270; // 300 - 20 - 10
        };
    }

    private static long oxyX(Anchor anchor) {
        return switch (anchor) {
            case START -> 10;
            case CENTER -> 173; // floor((400 - 60) / 2) + 3
            case END -> 330; // 400 - 60 - 10
        };
    }

    private static long oxyY(Anchor anchor) {
        return switch (anchor) {
            case START -> 10;
            case CENTER -> 138; // floor((300 - 30) / 2) + 3
            case END -> 260; // 300 - 30 - 10
        };
    }

    @Test
    void allNineAnchorPairsPlaceTheEnvironmentPanelOnBothAxes() {
        for (Anchor ax : Anchor.values()) {
            for (Anchor ay : Anchor.values()) {
                Anchor ox = opposite(ax);
                Anchor oy = opposite(ay);
                Layout actual = place(400, 300,
                        at(offset(ax), offset(ay), ax, ay), at(offset(ox), offset(oy), ox, oy));
                assertEquals(layout(env(envX(ax), envY(ay)), oxy(oxyX(ox), oxyY(oy)), Mode.REQUESTED), actual,
                        "environment anchors " + ax + "/" + ay);
            }
        }
    }

    @Test
    void allNineAnchorPairsPlaceTheOxygenPanelOnBothAxes() {
        for (Anchor ax : Anchor.values()) {
            for (Anchor ay : Anchor.values()) {
                Anchor ex = opposite(ax);
                Anchor ey = opposite(ay);
                Layout actual = place(400, 300,
                        at(offset(ex), offset(ey), ex, ey), at(offset(ax), offset(ay), ax, ay));
                assertEquals(layout(env(envX(ex), envY(ey)), oxy(oxyX(ax), oxyY(ay)), Mode.REQUESTED), actual,
                        "oxygen anchors " + ax + "/" + ay);
            }
        }
    }

    @Test
    void offsetsAcceptBothInclusiveEndpointsAndRejectTheNextValue() {
        assertEquals(-4096, LifeSupportHudLayout.MIN_OFFSET);
        assertEquals(4096, LifeSupportHudLayout.MAX_OFFSET);
        assertDoesNotThrow(() -> at(-4096, 4096, Anchor.START, Anchor.END));
        assertDoesNotThrow(() -> at(4096, -4096, Anchor.CENTER, Anchor.CENTER));
        assertThrows(IllegalArgumentException.class, () -> at(-4097, 0, Anchor.START, Anchor.START));
        assertThrows(IllegalArgumentException.class, () -> at(4097, 0, Anchor.START, Anchor.START));
        assertThrows(IllegalArgumentException.class, () -> at(0, -4097, Anchor.START, Anchor.START));
        assertThrows(IllegalArgumentException.class, () -> at(0, 4097, Anchor.START, Anchor.START));
        assertThrows(IllegalArgumentException.class,
                () -> at(Integer.MIN_VALUE, 0, Anchor.START, Anchor.START));
        assertThrows(IllegalArgumentException.class,
                () -> at(0, Integer.MAX_VALUE, Anchor.START, Anchor.START));
    }

    @Test
    void signedEndpointOffsetsApplyExactlyWhereTheViewportAllowsThem() {
        assertEquals(layout(env(4096, 4096), oxy(15844, 15874), Mode.REQUESTED), place(20000, 20000,
                at(4096, 4096, Anchor.START, Anchor.START), at(-4096, -4096, Anchor.END, Anchor.END)));
        // floor((20000 - 100) / 2) - 4096 and floor((20000 - 20) / 2) + 4096.
        assertEquals(layout(env(5854, 14086), oxy(4, 4), Mode.REQUESTED), place(20000, 20000,
                at(-4096, 4096, Anchor.CENTER, Anchor.CENTER), at(-4096, -4096, Anchor.START, Anchor.START)));
    }

    @Test
    void eachRequestedPanelClampsIndependentlyToTheMargin() {
        // Environment: x past the right edge, y before the top. Oxygen: x before the left edge, y past the bottom.
        assertEquals(layout(env(296, 4), oxy(4, 266), Mode.REQUESTED), place(400, 300,
                at(4096, -4096, Anchor.END, Anchor.START), at(-4096, 4096, Anchor.START, Anchor.END)));
        assertEquals(layout(env(4, 4), oxy(336, 266), Mode.REQUESTED), place(400, 300,
                at(-4096, -1, Anchor.START, Anchor.START), at(4096, 1, Anchor.END, Anchor.END)));
    }

    @Test
    void centerRoundsOddRemaindersDown() {
        PanelSettings oxygen = at(10, 10, Anchor.START, Anchor.START);
        // floor((401 - 100) / 2) = 150 and floor((301 - 20) / 2) = 140.
        assertEquals(layout(env(150, 140), oxy(10, 10), Mode.REQUESTED),
                place(401, 301, at(0, 0, Anchor.CENTER, Anchor.CENTER), oxygen));
        assertEquals(layout(env(149, 139), oxy(10, 10), Mode.REQUESTED),
                place(401, 301, at(-1, -1, Anchor.CENTER, Anchor.CENTER), oxygen));
        assertEquals(layout(env(151, 141), oxy(10, 10), Mode.REQUESTED),
                place(401, 301, at(1, 1, Anchor.CENTER, Anchor.CENTER), oxygen));
    }

    @Test
    void defaultTopRightPanelsKeepTheirRequestedPlacesWithTheVanillaLineHeight() {
        // Normal sizes for a 9-pixel line: 136 x (9 + 10) and 136 x (9 + 19); a 1920x1080 window at GUI scale 4.
        Layout actual = LifeSupportHudLayout.place(480, 270,
                at(-6, 6, Anchor.END, Anchor.START), at(-6, 30, Anchor.END, Anchor.START),
                new Size(136, 19), new Size(136, 28), ENV_COMPACT, OXY_COMPACT);
        assertEquals(layout(new Rect(338, 6, 136, 19), new Rect(338, 30, 136, 28), Mode.REQUESTED), actual);
    }

    @Test
    void invalidInternalInputsAreRejected() {
        PanelSettings settings = at(0, 0, Anchor.START, Anchor.START);
        assertThrows(IllegalArgumentException.class, () -> place(-1, 300, settings, settings));
        assertThrows(IllegalArgumentException.class, () -> place(400, -1, settings, settings));
        assertThrows(IllegalArgumentException.class, () -> place(Integer.MIN_VALUE, 0, settings, settings));
        assertThrows(NullPointerException.class, () -> place(400, 300, null, settings));
        assertThrows(NullPointerException.class, () -> place(400, 300, settings, null));
        assertThrows(NullPointerException.class, () -> LifeSupportHudLayout.place(400, 300, settings, settings,
                null, OXY, ENV_COMPACT, OXY_COMPACT));
        assertThrows(NullPointerException.class, () -> LifeSupportHudLayout.place(400, 300, settings, settings,
                ENV, null, ENV_COMPACT, OXY_COMPACT));
        assertThrows(NullPointerException.class, () -> LifeSupportHudLayout.place(400, 300, settings, settings,
                ENV, OXY, null, OXY_COMPACT));
        assertThrows(NullPointerException.class, () -> LifeSupportHudLayout.place(400, 300, settings, settings,
                ENV, OXY, ENV_COMPACT, null));

        assertThrows(NullPointerException.class, () -> at(0, 0, null, Anchor.START));
        assertThrows(NullPointerException.class, () -> at(0, 0, Anchor.START, null));
        assertThrows(IllegalArgumentException.class, () -> new Size(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new Size(1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Size(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Size(1, Integer.MIN_VALUE));
        assertThrows(IllegalArgumentException.class, () -> new Rect(0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new Rect(0, 0, 1, -1));
        Rect rect = oxy(4, 4);
        assertThrows(NullPointerException.class, () -> new Layout(null, rect, Mode.UNFIT));
        assertThrows(NullPointerException.class, () -> new Layout(rect, null, Mode.UNFIT));
        assertThrows(NullPointerException.class, () -> new Layout(rect, rect, null));
        // Rect checks only its own extents; origins outside any viewport are legitimate UNFIT results.
        assertDoesNotThrow(() -> new Rect(Long.MIN_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE, 1));
    }

    @Test
    void wideViewportsUseLongArithmeticBeforeClamping() {
        int max = Integer.MAX_VALUE;
        // END + 4096 exceeds int: an int sum would wrap negative and clamp to the margin instead.
        assertEquals(layout(env(2147483543L, 2147483623L), oxy(4, 4), Mode.REQUESTED), place(max, max,
                at(4096, 4096, Anchor.END, Anchor.END), at(0, 0, Anchor.START, Anchor.START)));
        // floor((max - 100) / 2) + 4096 and floor((max - 20) / 2) - 4096; oxygen END clamps to max - 4 - size.
        assertEquals(layout(env(1073745869L, 1073737717L), oxy(2147483583L, 2147483613L), Mode.REQUESTED),
                place(max, max, at(4096, -4096, Anchor.CENTER, Anchor.CENTER),
                        at(4096, 4096, Anchor.END, Anchor.END)));
    }

    @Test
    void intMaxCompactExtentsProduceLongUnfitOrigins() {
        Size tallest = new Size(5, Integer.MAX_VALUE);
        Size widest = new Size(Integer.MAX_VALUE, 5);
        Size small = new Size(5, 5);
        PanelSettings settings = at(0, 0, Anchor.START, Anchor.START);

        Layout tall = LifeSupportHudLayout.place(400, 300, settings, settings, new Size(500, 5), small, tallest,
                small);
        assertEquals(layout(new Rect(4, 4, 5, Integer.MAX_VALUE), new Rect(4, 2147483655L, 5, 5), Mode.UNFIT),
                tall);
        assertEquals(2147483660L, tall.oxygen().y() + tall.oxygen().height());

        List<Layout> wide = LifeSupportHudLayout.arrangements(400, 300, settings, settings, new Size(500, 5),
                small, widest, small);
        assertEquals(new Rect(2147483655L, 4, 5, 5), wide.get(1).oxygen());
        assertEquals(Mode.UNFIT, LifeSupportHudLayout.place(400, 300, settings, settings, new Size(500, 5),
                small, widest, small).mode());

        // Int-max normal extents can never fit the margin, even in an int-max viewport.
        Layout huge = LifeSupportHudLayout.place(Integer.MAX_VALUE, Integer.MAX_VALUE, settings, settings,
                new Size(Integer.MAX_VALUE, 5), small, small, small);
        assertEquals(layout(new Rect(4, 4, 5, 5), new Rect(4, 13, 5, 5), Mode.COMPACT), huge);
    }

    @Test
    void marginContactFitsAndOnePixelLessDoesNot() {
        // 100 = 108 - 2 * 4: the environment panel touches both side margins.
        assertEquals(layout(env(4, 4), oxy(4, 100), Mode.REQUESTED), place(108, 300,
                at(0, 0, Anchor.START, Anchor.START), at(0, 100, Anchor.START, Anchor.START)));
        assertEquals(layout(new Rect(4, 4, 50, 40), new Rect(4, 48, 30, 50), Mode.COMPACT), place(107, 300,
                at(0, 0, Anchor.START, Anchor.START), at(0, 100, Anchor.START, Anchor.START)));
        // Same on the vertical axis: 30 = 38 - 2 * 4 for oxygen; at 37 only the two compact stacks remain.
        assertEquals(2, LifeSupportHudLayout.arrangements(400, 37, at(0, 0, Anchor.START, Anchor.START),
                at(200, 0, Anchor.START, Anchor.START), ENV, OXY, ENV_COMPACT, OXY_COMPACT).size());
        assertEquals(layout(env(4, 4), oxy(200, 4), Mode.REQUESTED), place(400, 38,
                at(0, 0, Anchor.START, Anchor.START), at(200, 0, Anchor.START, Anchor.START)));
    }

    @Test
    void aFourPixelGapOnEitherAxisKeepsTheRequestedPanels() {
        PanelSettings environment = at(10, 10, Anchor.START, Anchor.START); // [10, 110] x [10, 30]
        assertEquals(layout(env(10, 10), oxy(114, 10), Mode.REQUESTED),
                place(400, 300, environment, at(114, 10, Anchor.START, Anchor.START)));
        assertEquals(layout(env(10, 10), oxy(113, 34), Mode.DISPLACED),
                place(400, 300, environment, at(113, 10, Anchor.START, Anchor.START)));
        assertEquals(layout(env(10, 10), oxy(20, 34), Mode.REQUESTED),
                place(400, 300, environment, at(20, 34, Anchor.START, Anchor.START)));
        assertEquals(layout(env(10, 10), oxy(20, 34), Mode.DISPLACED),
                place(400, 300, environment, at(20, 33, Anchor.START, Anchor.START)));
        // Oxygen on the other side of the environment panel: 70 + 4 = 74 and 40 + 4 = 44.
        assertEquals(Mode.REQUESTED, place(400, 300, at(74, 10, Anchor.START, Anchor.START),
                at(10, 10, Anchor.START, Anchor.START)).mode());
        assertEquals(Mode.REQUESTED, place(400, 300, at(100, 44, Anchor.START, Anchor.START),
                at(80, 10, Anchor.START, Anchor.START)).mode());
        assertEquals(Mode.DISPLACED, place(400, 300, at(100, 43, Anchor.START, Anchor.START),
                at(80, 10, Anchor.START, Anchor.START)).mode());
    }

    @Test
    void displacementTriesBelowAboveLeftRightInThatExactOrder() {
        // Centred overlap: every candidate fits, so below wins; the full ordered list is fixed.
        PanelSettings environment = at(0, 0, Anchor.CENTER, Anchor.CENTER); // (150, 140)
        PanelSettings oxygen = at(0, 0, Anchor.CENTER, Anchor.CENTER); // (170, 135)
        Rect e = env(150, 140);
        assertEquals(List.of(
                layout(e, oxy(170, 135), Mode.REQUESTED),
                layout(e, oxy(170, 164), Mode.DISPLACED),
                layout(e, oxy(170, 106), Mode.DISPLACED),
                layout(e, oxy(86, 135), Mode.DISPLACED),
                layout(e, oxy(254, 135), Mode.DISPLACED),
                layout(new Rect(4, 4, 50, 40), new Rect(4, 48, 30, 50), Mode.COMPACT),
                layout(new Rect(4, 4, 50, 40), new Rect(58, 4, 30, 50), Mode.COMPACT)
        ), LifeSupportHudLayout.arrangements(400, 300, environment, oxygen, ENV, OXY, ENV_COMPACT, OXY_COMPACT));
        assertEquals(layout(e, oxy(170, 164), Mode.DISPLACED), place(400, 300, environment, oxygen));

        // Bottom edge: below leaves the margin, so above wins although left and right also fit.
        assertEquals(layout(env(150, 270), oxy(170, 236), Mode.DISPLACED), place(400, 300,
                at(0, -10, Anchor.CENTER, Anchor.END), at(0, -15, Anchor.CENTER, Anchor.END)));

        // A 60-pixel-high viewport: neither below nor above fits, so left wins although right also fits.
        assertEquals(layout(env(200, 20), oxy(136, 15), Mode.DISPLACED), place(400, 60,
                at(200, 0, Anchor.START, Anchor.CENTER), at(210, 0, Anchor.START, Anchor.CENTER)));

        // Environment at the left margin: only right remains.
        assertEquals(layout(env(4, 20), oxy(108, 15), Mode.DISPLACED), place(400, 60,
                at(4, 0, Anchor.START, Anchor.CENTER), at(10, 0, Anchor.START, Anchor.CENTER)));

        // No candidate fits a 108x60 viewport: the compact horizontal stack is the first admissible arrangement.
        assertEquals(layout(new Rect(4, 4, 50, 40), new Rect(58, 4, 30, 50), Mode.COMPACT), place(108, 60,
                at(0, 0, Anchor.START, Anchor.CENTER), at(0, 0, Anchor.START, Anchor.CENTER)));
    }

    @Test
    void candidatesAreNeverClampedAgain() {
        PanelSettings environment = at(10, 10, Anchor.START, Anchor.START); // [10, 110] x [10, 30]
        PanelSettings oxygen = at(20, 20, Anchor.START, Anchor.START);
        // Below at y 34 ends at 64 = 68 - 4: accepted on the margin.
        assertEquals(layout(env(10, 10), oxy(20, 34), Mode.DISPLACED), place(400, 68, environment, oxygen));
        // One pixel lower is rejected rather than pulled back into the overlap; above and left leave the margin.
        assertEquals(layout(env(10, 10), oxy(114, 20), Mode.DISPLACED), place(400, 67, environment, oxygen));
        assertEquals(List.of(oxy(20, 34), oxy(20, -24), oxy(-54, 20), oxy(114, 20)),
                LifeSupportHudLayout.arrangements(400, 67, environment, oxygen, ENV, OXY, ENV_COMPACT, OXY_COMPACT)
                        .subList(1, 5).stream().map(Layout::oxygen).toList());
    }

    @Test
    void anOversizedNormalPanelSkipsRequestedAndDisplacedArrangements() {
        PanelSettings settings = at(0, 0, Anchor.START, Anchor.START);
        List<Layout> wideOxygen = LifeSupportHudLayout.arrangements(400, 300, settings, settings, ENV,
                new Size(393, 30), ENV_COMPACT, OXY_COMPACT);
        assertEquals(2, wideOxygen.size());
        assertTrue(wideOxygen.stream().allMatch(arrangement -> arrangement.mode() == Mode.COMPACT));
        List<Layout> tallEnvironment = LifeSupportHudLayout.arrangements(400, 300, settings, settings,
                new Size(100, 293), OXY, ENV_COMPACT, OXY_COMPACT);
        assertEquals(2, tallEnvironment.size());
        assertEquals(7, LifeSupportHudLayout.arrangements(400, 300, settings, settings, ENV, new Size(392, 30),
                ENV_COMPACT, OXY_COMPACT).size());
    }

    @Test
    void compactPrefersTheVerticalStackThenTheHorizontalPair() {
        PanelSettings environment = at(-6, 6, Anchor.END, Anchor.START);
        PanelSettings oxygen = at(-6, 30, Anchor.END, Anchor.START);
        Rect compactEnvironment = new Rect(4, 4, 50, 40);
        // 100 wide cannot hold the 100-pixel normal panel; both stacks fit, vertical wins.
        assertEquals(layout(compactEnvironment, new Rect(4, 48, 30, 50), Mode.COMPACT),
                place(100, 200, environment, oxygen));
        // 60 high cannot hold the vertical stack (98 > 56) but holds the pair (54 <= 56).
        assertEquals(layout(compactEnvironment, new Rect(58, 4, 30, 50), Mode.COMPACT),
                place(100, 60, environment, oxygen));
        // 80 wide also loses the pair (88 > 76).
        assertEquals(layout(compactEnvironment, new Rect(4, 48, 30, 50), Mode.UNFIT),
                place(80, 60, environment, oxygen));
    }

    @Test
    void impossibleViewportsReturnTheFullCompactStackAsUnfit() {
        PanelSettings environment = at(-6, 6, Anchor.END, Anchor.START);
        PanelSettings oxygen = at(-6, 30, Anchor.END, Anchor.START);
        Layout expected = layout(new Rect(4, 4, 50, 40), new Rect(4, 48, 30, 50), Mode.UNFIT);
        // 57 wide fits neither the normal (100) nor the compact (50) environment width; 37 high fits neither the
        // normal oxygen height (30) nor the compact environment height (40).
        for (int[] viewport : new int[][] {{0, 0}, {1, 1}, {8, 8}, {57, 300}, {400, 37}, {0, 300}}) {
            assertEquals(expected, place(viewport[0], viewport[1], environment, oxygen),
                    viewport[0] + "x" + viewport[1]);
        }
    }

    @Test
    void placementIsDeterministicAndTakesTheFirstOfAtMostSevenArrangements() {
        int checked = 0;
        for (int width = 0; width <= 240; width += 5) {
            for (int height = 0; height <= 240; height += 5) {
                for (Anchor ax : Anchor.values()) {
                    for (Anchor ay : Anchor.values()) {
                        PanelSettings environment = at(offset(ax), offset(ay), ax, ay);
                        PanelSettings oxygen = at(-6, 30, Anchor.END, Anchor.START);
                        List<Layout> arrangements = LifeSupportHudLayout.arrangements(width, height,
                                environment, oxygen, ENV, OXY, ENV_COMPACT, OXY_COMPACT);
                        Layout result = place(width, height, environment, oxygen);
                        assertEquals(result, place(width, height, environment, oxygen));
                        assertTrue(arrangements.size() == 7 || arrangements.size() == 2, "arrangement count");

                        int chosen = arrangements.indexOf(result);
                        for (int i = 0; i < (chosen < 0 ? arrangements.size() : chosen); i++) {
                            assertFalse(admissible(arrangements.get(i), width, height),
                                    "an earlier admissible arrangement was skipped at " + width + "x" + height);
                        }
                        if (chosen < 0) {
                            Layout stack = arrangements.get(arrangements.size() - 2);
                            assertEquals(layout(stack.environment(), stack.oxygen(), Mode.UNFIT), result);
                        } else {
                            assertTrue(admissible(result, width, height));
                        }
                        checked++;
                    }
                }
            }
        }
        assertEquals(49 * 49 * 9, checked);
    }

    /** Independent restatement of the contract's acceptance rule for the sweep above. */
    private static boolean admissible(Layout layout, long width, long height) {
        return withinMargin(layout.environment(), width, height) && withinMargin(layout.oxygen(), width, height)
                && apart(layout.environment(), layout.oxygen());
    }

    private static boolean withinMargin(Rect rect, long width, long height) {
        return rect.x() >= 4 && rect.y() >= 4
                && rect.x() + rect.width() + 4 <= width && rect.y() + rect.height() + 4 <= height;
    }

    private static boolean apart(Rect a, Rect b) {
        boolean horizontal = Math.max(a.x(), b.x()) - Math.min(a.x() + a.width(), b.x() + b.width()) >= 4;
        boolean vertical = Math.max(a.y(), b.y()) - Math.min(a.y() + a.height(), b.y() + b.height()) >= 4;
        return horizontal || vertical;
    }
}
