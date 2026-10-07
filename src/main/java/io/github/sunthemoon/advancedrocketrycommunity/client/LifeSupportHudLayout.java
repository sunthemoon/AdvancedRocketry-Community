package io.github.sunthemoon.advancedrocketrycommunity.client;

import java.util.List;
import java.util.Objects;

/**
 * Placement of the life-support HUD's environment and oxygen panels (C18d-HUD-ENV-O2-01 CONTRACT-02). Pure and
 * stdlib-only: the HUD adapter measures both panels with the current Font, reads the two CLIENT settings and calls
 * {@link #place} once per visible frame. Units are scaled GUI pixels. Origins and edges are computed as long, so the
 * {@link Mode#UNFIT} stack of int-max extents never wraps; only that mode may leave the viewport.
 */
public final class LifeSupportHudLayout {
    /** Inclusive bounds of each x/y offset setting. */
    public static final int MIN_OFFSET = -4096;
    public static final int MAX_OFFSET = 4096;

    private static final int MARGIN = 4;
    private static final int GAP = 4;

    private LifeSupportHudLayout() {
    }

    public enum Anchor { START, CENTER, END }

    /** One panel's requested position: offsets from the chosen anchor on each axis. */
    public record PanelSettings(int x, int y, Anchor anchorX, Anchor anchorY) {
        public PanelSettings {
            requireOffset("x", x);
            requireOffset("y", y);
            Objects.requireNonNull(anchorX, "anchorX");
            Objects.requireNonNull(anchorY, "anchorY");
        }
    }

    public record Size(int width, int height) {
        public Size {
            requirePositive("width", width);
            requirePositive("height", height);
        }
    }

    /** A placed panel. The origin is not checked against any viewport. */
    public record Rect(long x, long y, int width, int height) {
        public Rect {
            requirePositive("width", width);
            requirePositive("height", height);
        }
    }

    public enum Mode { REQUESTED, DISPLACED, COMPACT, UNFIT }

    public record Layout(Rect environment, Rect oxygen, Mode mode) {
        public Layout {
            Objects.requireNonNull(environment, "environment");
            Objects.requireNonNull(oxygen, "oxygen");
            Objects.requireNonNull(mode, "mode");
        }
    }

    /**
     * The first of {@link #arrangements} whose two panels lie inside the four-pixel margin and are at least four
     * pixels apart on one axis; otherwise the compact vertical stack as {@link Mode#UNFIT}.
     */
    public static Layout place(
            int screenWidth,
            int screenHeight,
            PanelSettings environment,
            PanelSettings oxygen,
            Size environmentNormal,
            Size oxygenNormal,
            Size environmentCompact,
            Size oxygenCompact
    ) {
        for (Layout arrangement : arrangements(screenWidth, screenHeight, environment, oxygen,
                environmentNormal, oxygenNormal, environmentCompact, oxygenCompact)) {
            if (inside(arrangement.environment(), screenWidth, screenHeight)
                    && inside(arrangement.oxygen(), screenWidth, screenHeight)
                    && separated(arrangement.environment(), arrangement.oxygen())) {
                return arrangement;
            }
        }
        return compactVertical(environmentCompact, oxygenCompact, Mode.UNFIT);
    }

    /**
     * Every arrangement {@link #place} considers, in order. When both normal panels fit the margin on both axes:
     * the independently clamped requested pair, then oxygen below, above, left and right of the requested
     * environment panel, keeping oxygen's requested perpendicular coordinate and never clamping again. Always last:
     * the compact vertical, then horizontal, stack at the top-left margin. At most seven, whatever the viewport.
     */
    static List<Layout> arrangements(
            int screenWidth,
            int screenHeight,
            PanelSettings environment,
            PanelSettings oxygen,
            Size environmentNormal,
            Size oxygenNormal,
            Size environmentCompact,
            Size oxygenCompact
    ) {
        if (screenWidth < 0 || screenHeight < 0) {
            throw new IllegalArgumentException(
                    "HUD viewport must not be negative: " + screenWidth + "x" + screenHeight);
        }
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(oxygen, "oxygen");
        Objects.requireNonNull(environmentNormal, "environmentNormal");
        Objects.requireNonNull(oxygenNormal, "oxygenNormal");
        Objects.requireNonNull(environmentCompact, "environmentCompact");
        Objects.requireNonNull(oxygenCompact, "oxygenCompact");

        Layout compactVertical = compactVertical(environmentCompact, oxygenCompact, Mode.COMPACT);
        Layout compactHorizontal = new Layout(
                compactVertical.environment(),
                new Rect(MARGIN + (long) environmentCompact.width() + GAP, MARGIN,
                        oxygenCompact.width(), oxygenCompact.height()),
                Mode.COMPACT
        );
        if (!fits(screenWidth, screenHeight, environmentNormal) || !fits(screenWidth, screenHeight, oxygenNormal)) {
            return List.of(compactVertical, compactHorizontal);
        }
        Rect e = requested(screenWidth, screenHeight, environment, environmentNormal);
        Rect o = requested(screenWidth, screenHeight, oxygen, oxygenNormal);
        return List.of(
                new Layout(e, o, Mode.REQUESTED),
                displaced(e, o, o.x(), e.y() + e.height() + GAP),
                displaced(e, o, o.x(), e.y() - GAP - o.height()),
                displaced(e, o, e.x() - GAP - o.width(), o.y()),
                displaced(e, o, e.x() + e.width() + GAP, o.y()),
                compactVertical,
                compactHorizontal
        );
    }

    private static Layout compactVertical(Size environmentCompact, Size oxygenCompact, Mode mode) {
        return new Layout(
                new Rect(MARGIN, MARGIN, environmentCompact.width(), environmentCompact.height()),
                new Rect(MARGIN, MARGIN + (long) environmentCompact.height() + GAP,
                        oxygenCompact.width(), oxygenCompact.height()),
                mode
        );
    }

    private static Layout displaced(Rect environment, Rect oxygen, long x, long y) {
        return new Layout(environment, new Rect(x, y, oxygen.width(), oxygen.height()), Mode.DISPLACED);
    }

    private static Rect requested(int screenWidth, int screenHeight, PanelSettings settings, Size size) {
        return new Rect(
                axis(settings.anchorX(), screenWidth, size.width(), settings.x()),
                axis(settings.anchorY(), screenHeight, size.height(), settings.y()),
                size.width(),
                size.height()
        );
    }

    /** The anchored request clamped to [margin, extent - margin - size]; callers guarantee the size fits. */
    private static long axis(Anchor anchor, long extent, int size, int offset) {
        long request = switch (anchor) {
            case START -> offset;
            case CENTER -> Math.floorDiv(extent - size, 2L) + offset;
            case END -> extent - size + offset;
        };
        return Math.max(MARGIN, Math.min(extent - MARGIN - size, request));
    }

    private static boolean fits(long screenWidth, long screenHeight, Size size) {
        return size.width() <= screenWidth - 2L * MARGIN && size.height() <= screenHeight - 2L * MARGIN;
    }

    private static boolean inside(Rect rect, long screenWidth, long screenHeight) {
        return rect.x() >= MARGIN && rect.x() + rect.width() <= screenWidth - MARGIN
                && rect.y() >= MARGIN && rect.y() + rect.height() <= screenHeight - MARGIN;
    }

    private static boolean separated(Rect a, Rect b) {
        return a.x() + a.width() + GAP <= b.x() || b.x() + b.width() + GAP <= a.x()
                || a.y() + a.height() + GAP <= b.y() || b.y() + b.height() + GAP <= a.y();
    }

    private static void requireOffset(String axis, int offset) {
        if (offset < MIN_OFFSET || offset > MAX_OFFSET) {
            throw new IllegalArgumentException(
                    "HUD " + axis + " offset " + offset + " is outside [" + MIN_OFFSET + ", " + MAX_OFFSET + "]");
        }
    }

    private static void requirePositive(String name, int extent) {
        if (extent <= 0) {
            throw new IllegalArgumentException("HUD panel " + name + " must be positive: " + extent);
        }
    }
}
