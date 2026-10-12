package io.github.sunthemoon.advancedrocketrycommunity.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Placement of the life-support HUD panels (C18d-HUD-ENV-O2-01 CONTRACT-02, extended to the classic hydrogen, suit
 * and rocket panels by ADR-066 section 7.2). Pure and stdlib-only: the HUD adapter measures the visible panels with
 * the current Font, reads their CLIENT settings and calls {@link #placeAll} once per visible frame. Units are scaled
 * GUI pixels. Origins and edges are computed as long, so the {@link Mode#UNFIT} stack of int-max extents never wraps;
 * only that mode may leave the viewport.
 */
public final class LifeSupportHudLayout {
    /** Inclusive bounds of each x/y offset setting. */
    public static final int MIN_OFFSET = -4096;
    public static final int MAX_OFFSET = 4096;
    /** Environment, oxygen, hydrogen, suit and the rocket readout. */
    public static final int MAX_PANELS = 5;

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

    /** One visible panel: its requested position and its measured normal and compact sizes. */
    public record Panel(PanelSettings settings, Size normal, Size compact) {
        public Panel {
            Objects.requireNonNull(settings, "settings");
            Objects.requireNonNull(normal, "normal");
            Objects.requireNonNull(compact, "compact");
        }
    }

    /** The placed panels, in request order, and the mode they were placed in. */
    public record Placement(List<Rect> panels, Mode mode) {
        public Placement {
            panels = List.copyOf(panels);
            Objects.requireNonNull(mode, "mode");
        }
    }

    /**
     * The first of {@link #arrangements} whose two panels lie inside the four-pixel margin and are at least four
     * pixels apart on one axis; otherwise the compact vertical stack as {@link Mode#UNFIT}. The same result as
     * {@link #placeAll} for these two panels.
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
        Placement placement = placeAll(screenWidth, screenHeight, List.of(
                new Panel(environment, environmentNormal, environmentCompact),
                new Panel(oxygen, oxygenNormal, oxygenCompact)));
        return new Layout(placement.panels().get(0), placement.panels().get(1), placement.mode());
    }

    /**
     * Places one to {@link #MAX_PANELS} panels so that every panel lies inside the four-pixel margin and every pair
     * is at least four pixels apart on one axis. When every normal panel fits the margin, the panels are placed in
     * request order: each keeps its independently clamped requested rectangle if that is admissible beside the
     * panels already placed, and otherwise takes the first admissible of below, above, left and right of each
     * already placed panel in order, keeping its requested perpendicular coordinate and never clamping again
     * ({@link Mode#DISPLACED}). If a panel has no admissible place, or a normal panel cannot fit, all panels use their
     * compact sizes in a vertical stack, then a horizontal row, then (for three or more panels) columns filled top
     * down at the top-left margin ({@link Mode#COMPACT}). If none of these is admissible the compact vertical stack is
     * returned as {@link Mode#UNFIT}. At most {@code 1 + 4 * (n - 1)} candidates per panel and three compact
     * arrangements: no pixel search, other-overlay scan or world work.
     */
    public static Placement placeAll(int screenWidth, int screenHeight, List<Panel> panels) {
        if (screenWidth < 0 || screenHeight < 0) {
            throw new IllegalArgumentException(
                    "HUD viewport must not be negative: " + screenWidth + "x" + screenHeight);
        }
        Objects.requireNonNull(panels, "panels");
        if (panels.isEmpty() || panels.size() > MAX_PANELS) {
            throw new IllegalArgumentException("HUD panel count must be 1 to " + MAX_PANELS + ": " + panels.size());
        }
        panels.forEach(panel -> Objects.requireNonNull(panel, "panel"));

        if (panels.stream().allMatch(panel -> fits(screenWidth, screenHeight, panel.normal()))) {
            List<Rect> placed = new ArrayList<>(panels.size());
            Mode mode = Mode.REQUESTED;
            for (Panel panel : panels) {
                Rect requested = requested(screenWidth, screenHeight, panel.settings(), panel.normal());
                Rect chosen = admissible(requested, placed, screenWidth, screenHeight) ? requested : null;
                for (int i = 0; chosen == null && i < placed.size(); i++) {
                    chosen = besidePlaced(placed.get(i), requested, placed, screenWidth, screenHeight);
                }
                if (chosen == null) {
                    placed = null;
                    break;
                }
                if (chosen != requested) {
                    mode = Mode.DISPLACED;
                }
                placed.add(chosen);
            }
            if (placed != null) {
                return new Placement(placed, mode);
            }
        }
        List<Size> compact = panels.stream().map(Panel::compact).toList();
        List<List<Rect>> stacks = new ArrayList<>(List.of(compactStack(compact, true), compactStack(compact, false)));
        if (panels.size() > 2) {
            stacks.add(compactColumns(compact, screenHeight));
        }
        for (List<Rect> stack : stacks) {
            if (allAdmissible(stack, screenWidth, screenHeight)) {
                return new Placement(stack, Mode.COMPACT);
            }
        }
        return new Placement(stacks.get(0), Mode.UNFIT);
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

    /** Below, above, left and right of {@code anchor}, in that order; the first admissible one or null. */
    private static Rect besidePlaced(Rect anchor, Rect requested, List<Rect> placed, long screenWidth,
            long screenHeight) {
        List<Rect> candidates = List.of(
                new Rect(requested.x(), anchor.y() + anchor.height() + GAP, requested.width(), requested.height()),
                new Rect(requested.x(), anchor.y() - GAP - requested.height(), requested.width(), requested.height()),
                new Rect(anchor.x() - GAP - requested.width(), requested.y(), requested.width(), requested.height()),
                new Rect(anchor.x() + anchor.width() + GAP, requested.y(), requested.width(), requested.height()));
        for (Rect candidate : candidates) {
            if (admissible(candidate, placed, screenWidth, screenHeight)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean admissible(Rect rect, List<Rect> placed, long screenWidth, long screenHeight) {
        if (!inside(rect, screenWidth, screenHeight)) {
            return false;
        }
        for (Rect other : placed) {
            if (!separated(rect, other)) {
                return false;
            }
        }
        return true;
    }

    private static boolean allAdmissible(List<Rect> rects, long screenWidth, long screenHeight) {
        for (int i = 0; i < rects.size(); i++) {
            if (!admissible(rects.get(i), rects.subList(0, i), screenWidth, screenHeight)) {
                return false;
            }
        }
        return true;
    }

    /** Compact panels from the top-left margin, one below (or beside) the other with a four-pixel gap. */
    private static List<Rect> compactStack(List<Size> sizes, boolean vertical) {
        List<Rect> rects = new ArrayList<>(sizes.size());
        long next = MARGIN;
        for (Size size : sizes) {
            rects.add(vertical ? new Rect(MARGIN, next, size.width(), size.height())
                    : new Rect(next, MARGIN, size.width(), size.height()));
            next += (vertical ? size.height() : size.width()) + (long) GAP;
        }
        return rects;
    }

    /** Compact panels in columns from the top-left margin, starting a new column at the bottom margin. */
    private static List<Rect> compactColumns(List<Size> sizes, long screenHeight) {
        List<Rect> rects = new ArrayList<>(sizes.size());
        long x = MARGIN;
        long y = MARGIN;
        long columnWidth = 0;
        for (Size size : sizes) {
            if (y > MARGIN && y + size.height() > screenHeight - MARGIN) {
                x += columnWidth + GAP;
                y = MARGIN;
                columnWidth = 0;
            }
            rects.add(new Rect(x, y, size.width(), size.height()));
            y += size.height() + (long) GAP;
            columnWidth = Math.max(columnWidth, size.width());
        }
        return rects;
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
