package io.github.sunthemoon.advancedrocketrycommunity.client;

import java.util.Optional;

/** A bounded notice rectangle below a panel, or inset in its unused bottom area on short windows. */
public final class ScreenNoticeLayout {
    private static final int HEIGHT = 17;
    private static final int GAP = 4;

    private ScreenNoticeLayout() { }

    public static Bounds bottom(int width, int height) {
        return new Bounds(8, height - 24, width - 16, HEIGHT);
    }

    public static Optional<Bounds> forPanel(int width, int height, int left, int top, int panelWidth,
                                           int panelHeight, int contentBottom) {
        Bounds bottom = bottom(width, height);
        if (bottom.y() >= top + panelHeight + GAP) { return Optional.of(bottom); }
        Bounds inset = new Bounds(left + GAP, top + panelHeight - GAP - HEIGHT, panelWidth - 2 * GAP, HEIGHT);
        // The panel already shows countdown/status. Do not cover it when a resource pack needs all status rows.
        return inset.y() >= contentBottom + GAP && inset.x() >= 0 && inset.y() >= 0
                && inset.x() + inset.width() <= width && inset.y() + inset.height() <= height
                ? Optional.of(inset) : Optional.empty();
    }

    public record Bounds(int x, int y, int width, int height) { }
}
