package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.station.notice.StationNoticeKind;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ADR-065 section 5.3 warp notices in both languages. The first argument names the station; an outcome's second
 * argument is the existing station code key, and the cost class is its own key. Positional placeholders let Chinese
 * order the arguments differently.
 */
public final class V180StationNoticeLanguage {
    private V180StationNoticeLanguage() {
    }

    public static Map<String, String> translations(boolean chinese) {
        Map<String, String> entries = new LinkedHashMap<>();
        for (StationNoticeKind kind : StationNoticeKind.values()) {
            entries.put(kind.key(), chinese ? chinese(kind) : english(kind));
        }
        entries.put(StationNoticeKind.WHILE_AWAY, chinese ? "你离线期间：%s" : "While you were away: %s");
        entries.put(StationNoticeKind.costClassKey("in_system"), chinese ? "星系内" : "in-system");
        entries.put(StationNoticeKind.costClassKey("interstellar"), chinese ? "星际" : "interstellar");
        return Map.copyOf(entries);
    }

    private static String english(StationNoticeKind kind) {
        return switch (kind) {
            case WARP_STARTED -> "[%1$s] %2$s %3$s -> %4$s in %5$s s (%6$s, %7$s FE).";
            case WARP_COUNTDOWN -> "[%1$s] Warp to %2$s in %3$s s";
            case WARP_COMMITTED -> "[%1$s] %2$s %3$s -> %4$s (%5$s, %6$s FE); warp energy left %7$s FE.";
            case WARP_ABORTED -> "[%1$s] Warp to %3$s aborted: %2$s";
            case WARP_OUTCOME -> "[%1$s] %2$s";
        };
    }

    private static String chinese(StationNoticeKind kind) {
        return switch (kind) {
            case WARP_STARTED -> "[%1$s] %2$s %3$s -> %4$s，%5$s 秒后（%6$s，%7$s FE）。";
            case WARP_COUNTDOWN -> "[%1$s] %3$s 秒后跃迁至 %2$s";
            case WARP_COMMITTED -> "[%1$s] %2$s %3$s -> %4$s（%5$s，%6$s FE）；剩余跃迁能量 %7$s FE。";
            case WARP_ABORTED -> "[%1$s] 跃迁至 %3$s 已中止：%2$s";
            case WARP_OUTCOME -> "[%1$s] %2$s";
        };
    }
}
