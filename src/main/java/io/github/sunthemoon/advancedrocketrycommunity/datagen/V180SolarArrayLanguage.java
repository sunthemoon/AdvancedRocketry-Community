package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bilingual array-specific feedback, with the shared solar reason/context vocabulary. */
public final class V180SolarArrayLanguage {
    public static Map<String, String> entries(boolean chinese) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("block.advancedrocketrycommunity.solar_array", chinese ? "太阳能阵列" : "Solar Array");
        values.put("block.advancedrocketrycommunity.solar_array_panel", chinese ? "太阳能阵列板" : "Solar Array Panel");
        values.put("block.advancedrocketrycommunity.power_output_plug", chinese ? "能量输出接口" : "Power Output Plug");
        String prefix = "screen.advancedrocketrycommunity.solar_array.";
        values.put(prefix + "collectors", chinese ? "集热板：%s / 63" : "Collectors: %s / 63");
        String[] names = {"pending", "formed", "unformed", "unloaded", "no_collectors", "conflict", "unavailable"};
        String[] english = {"Structure: awaiting validation", "Structure: formed", "Structure: mismatch", "Structure: chunk unloaded",
                "Structure: no collectors", "Structure: output plug already claimed", "Structure: unavailable"};
        String[] chineseText = {"结构：等待验证", "结构：已形成", "结构：不匹配", "结构：区块未加载", "结构：缺少集热板", "结构：输出接口已被占用", "结构：不可用"};
        for (int index = 0; index < names.length; index++) { values.put(prefix + "structure." + names[index], chinese ? chineseText[index] : english[index]); }
        values.put("jei.advancedrocketrycommunity.solar_array", chinese
                ? "水平结构为 3 格宽、22 格深。前排：输出接口、控制器、输出接口。控制器面朝玩家，后方 63 格接受阵列板、空气或机器外壳；至少放置一块阵列板。控制器上方面须朝向天空或太空。地表仅在白天发电，雨和雷暴会降低产能。空间站采用已提交轨道天体的日照；轨道缺失时明确显示空间回退值。共用太阳能配置开关和倍率。缓存为 20,000 FE，两接口共享 1,000 FE/t 输出上限。普通拆除会销毁储能。"
                : "Horizontal layout: 3 wide, 22 deep. Front row: output plug, controller, output plug. With the controller facing you, the 63 cells behind it accept array panels, air or machine casing; at least one panel is required. The controller UP face must be exposed to sky or space. Surfaces generate only in daytime; rain and thunder reduce output. Stations use committed orbit-body sunlight; a missing orbit explicitly shows Space fallback. Uses the shared solar enable switch and multiplier. Buffer: 20,000 FE. Both plugs share 1,000 FE/t output. Ordinary breaking destroys stored FE.");
        return Map.copyOf(values);
    }
    private V180SolarArrayLanguage() { }
}
