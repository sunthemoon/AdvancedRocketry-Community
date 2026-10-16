package io.github.sunthemoon.advancedrocketrycommunity.client;

import java.util.function.ToIntFunction;

/** Pure width arithmetic for single-line labels. A trailing stable reason code is never shortened. */
public final class BoundedTextLayout {
    private BoundedTextLayout() { }

    public record Line(String text, float scale, boolean shortened) { }

    public static Line fit(String text, int available, ToIntFunction<String> width) {
        if (available <= 0) { throw new IllegalArgumentException("A text box must have positive width"); }
        if (width.applyAsInt(text) <= available) { return new Line(text, 1.0F, false); }
        String code = reasonSuffix(text);
        if (!code.isEmpty() && width.applyAsInt(code) > available) {
            return new Line(code, (float) available / width.applyAsInt(code), true);
        }
        String suffix = code.isEmpty() ? "..." : "... " + code;
        if (width.applyAsInt(suffix) > available) {
            return new Line(code, 1.0F, true);
        }
        String body = code.isEmpty() ? text : text.substring(0, text.length() - code.length()).stripTrailing();
        int count = body.codePointCount(0, body.length());
        int low = 0;
        int high = count;
        while (low < high) {
            int middle = low + (high - low + 1) / 2;
            String candidate = body.substring(0, body.offsetByCodePoints(0, middle)) + suffix;
            if (width.applyAsInt(candidate) <= available) { low = middle; }
            else { high = middle - 1; }
        }
        return new Line(body.substring(0, body.offsetByCodePoints(0, low)) + suffix, 1.0F, true);
    }

    private static String reasonSuffix(String text) {
        int start = text.lastIndexOf('[');
        if (start < 0 || !text.endsWith("]")) { return ""; }
        String code = text.substring(start);
        return code.matches("\\[[A-Z][A-Z0-9_]*\\]") ? code : "";
    }
}
