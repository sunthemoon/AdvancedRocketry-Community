package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import java.util.LinkedHashMap;
import java.util.Map;

/** NEW bilingual scalar feedback. This helper owns no shared language output. */
public final class V180SolarLanguage {
    public static Map<String, String> entries(boolean chinese) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("block.advancedrocketrycommunity.solar_generator", chinese ? "太阳能发电机" : "Solar Generator");
        values.put("block.advancedrocketrycommunity.solar_panel", chinese ? "太阳能板" : "Solar Panel");
        String prefix = "screen.advancedrocketrycommunity.solar.";
        values.put(prefix + "rate", chinese ? "实际产能：%s FE/t" : "Actual credit: %s FE/t");
        values.put(prefix + "weather", chinese ? "天气透光：%s‰" : "Weather transmission: %s per mille");
        values.put(prefix + "sky.open", chinese ? "上方曝光：开放" : "UP exposure: open");
        values.put(prefix + "sky.blocked", chinese ? "上方曝光：未确认开放" : "UP exposure: not confirmed open");
        values.put(prefix + "day.0", chinese ? "地表：夜晚" : "Surface: night");
        values.put(prefix + "day.1", chinese ? "地表：白天" : "Surface: daylight");
        values.put(prefix + "day.2", chinese ? "昼夜：不适用" : "Daylight: not applicable");
        values.put(prefix + "context.0", chinese ? "环境：不可用" : "Context: unavailable");
        values.put(prefix + "context.1", chinese ? "环境：行星地表" : "Context: surface");
        values.put(prefix + "context.2", chinese ? "环境：已提交空间站轨道" : "Context: committed station orbit");
        values.put(prefix + "context.3", chinese ? "轨道缺失：使用空间回退值" : "Missing orbit: Space fallback");
        values.put(prefix + "reason.generating", chinese ? "正在收集太阳能" : "Collecting sunlight");
        values.put(prefix + "reason.disabled", chinese ? "配置已禁用；能量保留" : "Disabled; stored FE retained");
        values.put(prefix + "reason.buffer_full", chinese ? "能量缓存已满" : "Energy buffer full");
        values.put(prefix + "reason.sky_blocked", chinese ? "上方面被遮挡" : "UP face obstructed");
        values.put(prefix + "reason.not_daylight", chinese ? "地表没有日照" : "No surface daylight");
        values.put(prefix + "reason.context_unavailable", chinese ? "无法确认环境或设备身份" : "Context or owner unavailable");
        values.put(prefix + "reason.no_irradiance", chinese ? "日照不足以产生能量" : "Insufficient solar credit");
        values.put(prefix + "reason.repair_required", chinese ? "数据保留；备份后离线修复" : "Data retained; back up and repair offline");
        return Map.copyOf(values);
    }
    private V180SolarLanguage() { }
}
