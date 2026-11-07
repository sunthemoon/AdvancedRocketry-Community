package io.github.sunthemoon.advancedrocketrycommunity.client.station;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** Fixed warp field slots, independent of Minecraft's font and rendering lifecycle. */
public final class WarpControllerLayout {
    public static final int ORBIT_ROW = 0;
    public static final int TARGET_ROW = 1;
    public static final int BALANCE_ROW = 2;
    public static final int BALANCE_ROWS = 2;
    public static final int COST_ROW = BALANCE_ROW + BALANCE_ROWS;
    public static final int COST_ROWS = 2;
    public static final int WARNING_ROW = COST_ROW + COST_ROWS;
    public static final int WARNING_ROWS = 4;
    public static final int COUNTDOWN_ROW = WARNING_ROW + WARNING_ROWS;
    /** Warp buttons end at 54; these eleven rows still end before the shared status at 170. */
    public static final int LINES_TOP = 58;
    public static final int LINE_ROWS = COUNTDOWN_ROW + 1;
    private static final String ELLIPSIS = "...";

    private WarpControllerLayout() {
    }

    /** Fit the localized body line on one row, retaining the entire ID in its hover text. */
    public static BodyLine bodyLine(String fullText, int width, ToIntFunction<String> measure) {
        if (measure.applyAsInt(fullText) <= width) {
            return new BodyLine(fullText, fullText);
        }
        if (measure.applyAsInt(ELLIPSIS) > width) {
            return new BodyLine("", fullText);
        }
        int end = fullText.length();
        while (end > 0 && measure.applyAsInt(fullText.substring(0, end) + ELLIPSIS) > width) {
            end = fullText.offsetByCodePoints(end, -1);
        }
        return new BodyLine(fullText.substring(0, end) + ELLIPSIS, fullText);
    }

    /**
     * Balance and cost each own two wrapped rows, followed by four warning rows and the countdown. Abbreviated
     * body lines cannot displace them. A clipped price or warning is not eligible for confirmation.
     */
    public static <T> Layout<T> arrange(T orbit, T target, List<T> balance, List<T> cost, List<T> warning, List<T> countdown,
                                      int drawnRows) {
        List<Row<T>> rows = new ArrayList<>();
        add(rows, List.of(orbit), ORBIT_ROW, 1, drawnRows);
        add(rows, List.of(target), TARGET_ROW, 1, drawnRows);
        add(rows, balance, BALANCE_ROW, BALANCE_ROWS, drawnRows);
        add(rows, cost, COST_ROW, COST_ROWS, drawnRows);
        add(rows, warning, WARNING_ROW, WARNING_ROWS, drawnRows);
        add(rows, countdown, COUNTDOWN_ROW, 1, drawnRows);
        boolean costVisible = !cost.isEmpty() && cost.size() <= COST_ROWS && COST_ROW + cost.size() <= drawnRows;
        boolean warningVisible = warning.isEmpty()
                || warning.size() <= WARNING_ROWS && WARNING_ROW + warning.size() <= drawnRows;
        return new Layout<>(rows, costVisible, warningVisible);
    }

    private static <T> void add(List<Row<T>> rows, List<T> text, int first, int limit, int drawnRows) {
        for (int index = 0; index < Math.min(limit, text.size()) && first + index < drawnRows; index++) {
            rows.add(new Row<>(first + index, text.get(index)));
        }
    }

    public record BodyLine(String text, String tooltip) {
    }

    public record Row<T>(int index, T text) {
    }

    public record Layout<T>(List<Row<T>> rows, boolean costVisible, boolean warningVisible) {
        public Layout {
            rows = List.copyOf(rows);
        }

        /** The positive server quote token remains mandatory, even when the price is visible. */
        public boolean confirmEnabled(long quoteToken) {
            return quoteToken > 0L && costVisible && warningVisible;
        }
    }
}
