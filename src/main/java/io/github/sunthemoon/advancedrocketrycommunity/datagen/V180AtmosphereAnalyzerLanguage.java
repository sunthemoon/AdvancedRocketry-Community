package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument.AtmosphereAnalyzerFeedback;
import java.util.LinkedHashMap;
import java.util.Map;

/** Delegated to Root's single bilingual v180 provider; no duplicate language-file writer. */
public final class V180AtmosphereAnalyzerLanguage {
    public static Map<String, String> translations(boolean chinese) {
        Map<String, String> keys = new LinkedHashMap<>();
        String p = AtmosphereAnalyzerFeedback.PREFIX;
        keys.put("item.advancedrocketrycommunity.atmosphere_analyzer", chinese ? "大气分析仪" : "Atmosphere Analyzer");
        keys.put("body.advancedrocketrycommunity.space", chinese ? "太空" : "Space");
        keys.put(p + "reading", chinese ? "%s | 有效呼吸状态：%s | 环境 %s：压力 %s，温度 %s K | %s"
                : "%s | Effective breathing: %s | Ambient %s: pressure %s, temperature %s K | %s");
        keys.put(p + "unavailable", chinese ? "大气分析仪：%s" : "Atmosphere analyzer: %s");
        keys.put(p + "location", "%s (%s)");
        keys.put(p + "ambient_body", chinese ? "%s（实际环境）" : "%s (actual environment)");
        keys.put(p + "supplied", chinese ? "受控供气" : "Controlled air supply");
        keys.put(p + "not_supplied", chinese ? "无已确认的受控供气" : "No confirmed controlled supply");
        keys.put(p + "locus.surface", chinese ? "地表" : "Surface");
        keys.put(p + "locus.orbit", chinese ? "轨道" : "Orbit");
        keys.put(p + "state.breathable", chinese ? "可呼吸" : "Breathable");
        keys.put(p + "state.non_breathable", chinese ? "不可呼吸" : "Non-breathable");
        keys.put(p + "state.pending", chinese ? "待判定" : "Pending");
        keys.put(p + "state.unavailable", chinese ? "信息不可用" : "Unavailable");
        keys.put(p + "state.disabled", chinese ? "已禁用" : "Disabled");
        return Map.copyOf(keys);
    }

    private V180AtmosphereAnalyzerLanguage() { }
}
