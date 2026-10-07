package io.github.sunthemoon.advancedrocketrycommunity.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.Anchor;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.Layout;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.Mode;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.PanelSettings;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.Rect;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.Size;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** A contract-derived oracle; it never obtains reference candidates from the implementation. */
class LifeSupportHudLayoutOracleTest {
    @Test
    void independentCandidatesAndSelectionCoverAnchorsEdgesAndOffsetEndpoints() {
        int[] extents = {0, 1, 7, 8, 9, 17, 31, 64, 160, 320, Integer.MAX_VALUE};
        int[] offsets = {-4096, -6, 0, 6, 4096};
        Size normalEnvironment = new Size(19, 11);
        Size normalOxygen = new Size(13, 17);
        Size compactEnvironment = new Size(7, 5);
        Size compactOxygen = new Size(9, 7);
        for (int width : extents) {
            for (int height : extents) {
                for (Anchor x : Anchor.values()) {
                    for (Anchor y : Anchor.values()) {
                        for (int offset : offsets) {
                            verify(width, height, new PanelSettings(offset, -offset, x, y),
                                    new PanelSettings(-offset, offset, y, x), normalEnvironment,
                                    normalOxygen, compactEnvironment, compactOxygen);
                        }
                    }
                }
            }
        }
    }

    @Test
    void independentOracleCoversAsymmetricSizesAndIntMaxLogicalEdges() {
        Random random = new Random(0xC18D03L);
        int[] extents = {0, 4, 8, 13, 29, 75, 200, 1920, Integer.MAX_VALUE};
        int[] sizes = {1, 2, 4, 9, 19, 28, 136, 4096, Integer.MAX_VALUE};
        Anchor[] anchors = Anchor.values();
        for (int i = 0; i < 4096; i++) {
            verify(extents[random.nextInt(extents.length)], extents[random.nextInt(extents.length)],
                    new PanelSettings(random.nextInt(8193) - 4096, random.nextInt(8193) - 4096,
                            anchors[random.nextInt(3)], anchors[random.nextInt(3)]),
                    new PanelSettings(random.nextInt(8193) - 4096, random.nextInt(8193) - 4096,
                            anchors[random.nextInt(3)], anchors[random.nextInt(3)]),
                    size(random, sizes), size(random, sizes), size(random, sizes), size(random, sizes));
        }
    }

    private static Size size(Random random, int[] sizes) {
        return new Size(sizes[random.nextInt(sizes.length)], sizes[random.nextInt(sizes.length)]);
    }

    private static void verify(int width, int height, PanelSettings environment, PanelSettings oxygen,
            Size normalEnvironment, Size normalOxygen, Size compactEnvironment, Size compactOxygen) {
        List<Layout> reference = candidates(width, height, environment, oxygen,
                normalEnvironment, normalOxygen, compactEnvironment, compactOxygen);
        assertEquals(reference, LifeSupportHudLayout.arrangements(width, height, environment, oxygen,
                normalEnvironment, normalOxygen, compactEnvironment, compactOxygen), "candidate coordinates/order");
        Layout selected = null;
        for (Layout candidate : reference) {
            if (admissible(width, height, candidate.environment(), candidate.oxygen())) {
                selected = candidate;
                break;
            }
        }
        if (selected == null) {
            Layout vertical = reference.get(reference.size() - 2);
            selected = new Layout(vertical.environment(), vertical.oxygen(), Mode.UNFIT);
        }
        assertEquals(selected, LifeSupportHudLayout.place(width, height, environment, oxygen,
                normalEnvironment, normalOxygen, compactEnvironment, compactOxygen), "first admissible placement");
    }

    private static List<Layout> candidates(int width, int height, PanelSettings environment, PanelSettings oxygen,
            Size normalEnvironment, Size normalOxygen, Size compactEnvironment, Size compactOxygen) {
        List<Layout> result = new ArrayList<>(7);
        if (Math.max(normalEnvironment.width(), normalOxygen.width()) + 8L <= width
                && Math.max(normalEnvironment.height(), normalOxygen.height()) + 8L <= height) {
            Rect e = anchored(width, height, environment, normalEnvironment);
            Rect o = anchored(width, height, oxygen, normalOxygen);
            result.add(new Layout(e, o, Mode.REQUESTED));
            long[][] positions = {
                    {o.x(), e.y() + (long) e.height() + 4L},
                    {o.x(), e.y() - (long) o.height() - 4L},
                    {e.x() - (long) o.width() - 4L, o.y()},
                    {e.x() + (long) e.width() + 4L, o.y()}
            };
            for (long[] position : positions) {
                result.add(new Layout(e, new Rect(position[0], position[1], o.width(), o.height()), Mode.DISPLACED));
            }
        }
        Rect e = new Rect(4L, 4L, compactEnvironment.width(), compactEnvironment.height());
        result.add(new Layout(e, new Rect(4L, 8L + compactEnvironment.height(),
                compactOxygen.width(), compactOxygen.height()), Mode.COMPACT));
        result.add(new Layout(e, new Rect(8L + compactEnvironment.width(), 4L,
                compactOxygen.width(), compactOxygen.height()), Mode.COMPACT));
        return List.copyOf(result);
    }

    private static Rect anchored(int width, int height, PanelSettings settings, Size size) {
        return new Rect(axis(width, size.width(), settings.x(), settings.anchorX()),
                axis(height, size.height(), settings.y(), settings.anchorY()), size.width(), size.height());
    }

    private static long axis(int viewport, int size, int offset, Anchor anchor) {
        long remaining = (long) viewport - size;
        long request = offset;
        if (anchor == Anchor.CENTER) {
            request += Math.floorDiv(remaining, 2L);
        } else if (anchor == Anchor.END) {
            request += remaining;
        }
        if (request < 4L) {
            return 4L;
        }
        return request > remaining - 4L ? remaining - 4L : request;
    }

    private static boolean admissible(int width, int height, Rect environment, Rect oxygen) {
        for (Rect rect : List.of(environment, oxygen)) {
            if (rect.x() < 4L || rect.y() < 4L || rect.x() + (long) rect.width() > (long) width - 4L
                    || rect.y() + (long) rect.height() > (long) height - 4L) {
                return false;
            }
        }
        long horizontalGap = Math.max(oxygen.x() - environment.x() - environment.width(),
                environment.x() - oxygen.x() - oxygen.width());
        long verticalGap = Math.max(oxygen.y() - environment.y() - environment.height(),
                environment.y() - oxygen.y() - oxygen.height());
        return Math.max(horizontalGap, verticalGap) >= 4L;
    }
}
