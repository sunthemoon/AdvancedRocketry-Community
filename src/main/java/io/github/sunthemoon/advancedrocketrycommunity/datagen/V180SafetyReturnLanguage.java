package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.station.safety.SpaceSafetyReturnService;
import java.util.LinkedHashMap;
import java.util.Map;

/** ADR-066 section 3.3: the space safety return's destination and refusal messages in both languages. */
public final class V180SafetyReturnLanguage {
    private V180SafetyReturnLanguage() {
    }

    public static Map<String, String> translations(boolean chinese) {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put(SpaceSafetyReturnService.ARRIVED_STATION, chinese
                ? "你在太空中漂流过久，已被送回你可以访问的最近空间站：%s。"
                : "You were adrift in space and have been returned to the nearest station you may visit: %s.");
        entries.put(SpaceSafetyReturnService.ARRIVED_OVERWORLD, chinese
                ? "你在太空中漂流过久，且没有可访问的空间站拥有已加载的安全到达点，已被送回主世界。"
                : "You were adrift in space and no station you may visit had a loaded, safe arrival, so you have"
                        + " been returned to the Overworld.");
        entries.put(SpaceSafetyReturnService.REFUSED, chinese
                ? "安全返回被拒绝：%s如果你仍在漂流，10 秒后将再次尝试。"
                : "The safety return was refused: %s If you are still adrift, another attempt follows in 10 seconds.");
        for (SpaceSafetyReturnService.Refusal reason : SpaceSafetyReturnService.Refusal.values()) {
            entries.put(reason.key(), chinese ? chinese(reason) : english(reason));
        }
        return Map.copyOf(entries);
    }

    private static String english(SpaceSafetyReturnService.Refusal reason) {
        return switch (reason) {
            case AUTHORITY_UNAVAILABLE -> "the station registry or the celestial catalog is unavailable.";
            case PROTECTED -> "the arrival is protected or outside the world.";
            case CANCELLED -> "the teleport was cancelled.";
        };
    }

    private static String chinese(SpaceSafetyReturnService.Refusal reason) {
        return switch (reason) {
            case AUTHORITY_UNAVAILABLE -> "空间站注册表或天体目录不可用。";
            case PROTECTED -> "到达点受到保护或位于世界之外。";
            case CANCELLED -> "传送被取消。";
        };
    }
}
