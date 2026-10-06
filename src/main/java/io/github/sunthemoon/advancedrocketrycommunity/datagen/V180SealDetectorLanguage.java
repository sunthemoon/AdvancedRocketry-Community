package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import java.util.LinkedHashMap;
import java.util.Map;

/** Root adds these NEW keys to its existing bilingual provider; no second language-file writer. */
public final class V180SealDetectorLanguage {
    public static Map<String, String> translations(boolean chinese) {
        Map<String, String> keys = new LinkedHashMap<>();
        String p = "message.advancedrocketrycommunity.seal_detector.";
        keys.put("item.advancedrocketrycommunity.seal_detector", chinese ? "密封检测器" : "Seal Detector");
        keys.put("tooltip.advancedrocketrycommunity.seal_detector.measurement_only",
                chinese ? "单个边界不能证明整个房间密封" : "One boundary does not certify a room");
        keys.put(p + "reading", chinese ? "密封检测器 | 边界：%s | 相邻供气：%s" : "Seal detector | Boundary: %s | Adjacent supply: %s");
        keys.put(p + "disabled", chinese ? "密封检测器：已禁用" : "Seal detector: disabled");
        keys.put(p + "unavailable", chinese ? "密封检测器：信息不可用" : "Seal detector: unavailable");
        keys.put(p + "boundary.sealed", chinese ? "密封" : "SEALED");
        keys.put(p + "boundary.open", chinese ? "开放" : "OPEN");
        keys.put(p + "boundary.unavailable", chinese ? "信息不可用" : "UNAVAILABLE");
        keys.put(p + "supply.supplied", chinese ? "已确认供气" : "SUPPLIED");
        keys.put(p + "supply.not_known_supplied", chinese ? "无已确认供气" : "NOT KNOWN SUPPLIED");
        keys.put(p + "supply.pending", chinese ? "待判定" : "PENDING");
        keys.put(p + "supply.unavailable", chinese ? "信息不可用" : "UNAVAILABLE");
        return Map.copyOf(keys);
    }

    private V180SealDetectorLanguage() { }
}
