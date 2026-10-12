package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import java.util.LinkedHashMap;
import java.util.Map;

/** ADR-066 section 5.2: the jetpack key, its category and the jetpack status lines, in both languages. */
public final class V180MotionLanguage {
    private static final String NS = "advancedrocketrycommunity";

    public static Map<String, String> translations(boolean chinese) {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("key.categories." + NS, chinese ? "高级火箭" : "Advanced Rocketry");
        names.put("key." + NS + ".toggle_jetpack", chinese ? "开关喷气背包（潜行时切换模式）" : "Toggle Jetpack (Sneak: Mode)");
        String jetpack = "message." + NS + ".jetpack.";
        names.put(jetpack + "enabled_normal", chinese ? "喷气背包已开启：普通模式" : "Jetpack on: normal mode");
        names.put(jetpack + "enabled_hover", chinese ? "喷气背包已开启：悬停模式" : "Jetpack on: hover mode");
        names.put(jetpack + "disabled", chinese ? "喷气背包已关闭" : "Jetpack off");
        names.put(jetpack + "mode_normal", chinese ? "喷气背包模式：普通" : "Jetpack mode: normal");
        names.put(jetpack + "mode_hover", chinese ? "喷气背包模式：悬停" : "Jetpack mode: hover");
        names.put(jetpack + "no_jetpack", chinese ? "喷气背包已关闭：穿戴的宇航服胸甲中没有喷气背包"
                : "Jetpack off: no jetpack in a worn suit chestplate");
        names.put(jetpack + "no_hover_upgrade", chinese ? "悬停需要穿戴的宇航服头盔中装有悬停升级"
                : "Hover needs a hover upgrade in a worn suit helmet");
        names.put(jetpack + "equipment_disabled", chinese ? "此服务器已禁用经典装备" : "Classic equipment is disabled on this server");
        return Map.copyOf(names);
    }

    private V180MotionLanguage() { }
}
