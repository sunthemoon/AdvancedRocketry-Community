package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.ClassicInventoryAdvancements.Goal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Acquisition feedback only, merged by the existing v1.8 language provider. */
public final class V180ClassicAdvancementLanguage {
    private V180ClassicAdvancementLanguage() { }

    public static Map<String, String> translations(boolean chinese) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Goal goal : Goal.values()) {
            result.put(goal.titleKey(), title(goal, chinese));
            result.put(goal.descriptionKey(), description(goal, chinese));
        }
        return Collections.unmodifiableMap(result);
    }

    private static String title(Goal goal, boolean chinese) {
        return switch (goal) {
            case ROOT -> chinese ? "从工作台开始" : "Start with a Crafting Table";
            case BLOCK_PRESS -> chinese ? "取得小型压板机" : "Acquire a Small Plate Press";
            case ROLLING -> chinese ? "取得轧制机" : "Acquire a Rolling Machine";
            case ELECTROLYSIS -> chinese ? "取得电解机" : "Acquire an Electrolyzer";
            case SUITED_UP -> chinese ? "备齐航天服" : "Collect a Space Suit";
            case WARP_CORE -> chinese ? "取得跃迁核心" : "Acquire a Warp Core";
        };
    }

    private static String description(Goal goal, boolean chinese) {
        return switch (goal) {
            case ROOT -> chinese ? "在物品栏中取得一个工作台。" : "Obtain a crafting table in your inventory.";
            case BLOCK_PRESS -> chinese ? "在物品栏中取得一台小型压板机。"
                    : "Obtain a small plate press in your inventory.";
            case ROLLING -> chinese ? "在物品栏中取得一台轧制机。" : "Obtain a rolling machine in your inventory.";
            case ELECTROLYSIS -> chinese ? "在物品栏中取得一台电解机。" : "Obtain an electrolyzer in your inventory.";
            case SUITED_UP -> chinese ? "同时在物品栏中持有本模组航天服的头盔、胸甲、护腿和靴子。"
                    : "Hold this mod's space suit helmet, chestplate, leggings and boots in your inventory together.";
            case WARP_CORE -> chinese ? "在物品栏中取得一个跃迁核心。" : "Obtain a warp core in your inventory.";
        };
    }
}
