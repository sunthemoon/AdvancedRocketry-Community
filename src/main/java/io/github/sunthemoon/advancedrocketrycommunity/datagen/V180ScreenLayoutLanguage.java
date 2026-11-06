package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import java.util.Map;

/** Short screen labels complement the existing full status and action descriptions. */
public final class V180ScreenLayoutLanguage {
    public static Map<String, String> entries(boolean chinese) {
        return Map.of(
                "screen.advancedrocketrycommunity.satellite.launch_short", chinese ? "发射" : "Launch",
                "screen.advancedrocketrycommunity.satellite.power_short", chinese ? "电量 %s FE" : "Power %s FE",
                "screen.advancedrocketrycommunity.satellite_builder.ready", chinese ? "组装机就绪" : "Builder ready",
                "screen.advancedrocketrycommunity.satellite_builder.output_ready", chinese ? "卫星已组装，请取出" : "Satellite ready",
                "screen.advancedrocketrycommunity.tooltip.scroll", chinese ? "%s-%s / %s 行；滚轮翻阅" : "%s-%s / %s rows; scroll");
    }

    private V180ScreenLayoutLanguage() { }
}
