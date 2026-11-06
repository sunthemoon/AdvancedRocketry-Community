package io.github.sunthemoon.advancedrocketrycommunity.client;

/** Tooltip row budgets include vanilla's padding, first-line gap and ten-pixel line spacing. */
final class TooltipPageLayout {
    static int capacity(int screenHeight) {
        return Math.max(1, (screenHeight - 24) / 10 - 1);
    }

    static int offset(int requested, int rows, int capacity) {
        return Math.max(0, Math.min(requested, Math.max(0, rows - capacity)));
    }

    private TooltipPageLayout() { }
}
