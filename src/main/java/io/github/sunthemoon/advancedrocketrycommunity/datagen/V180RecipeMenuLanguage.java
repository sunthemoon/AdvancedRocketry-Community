package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.machine.menu.RecipeMenuReason;
import java.util.LinkedHashMap;
import java.util.Map;

/** Unique bounded recipe-refusal labels merged by the single v1.8 language provider. */
public final class V180RecipeMenuLanguage {
    private V180RecipeMenuLanguage() { }

    public static Map<String, String> translations(boolean chinese) {
        Map<String, String> result = new LinkedHashMap<>();
        for (RecipeMenuReason reason : RecipeMenuReason.values()) {
            result.put(reason.translationKey(), label(reason, chinese));
        }
        return Map.copyOf(result);
    }

    private static String label(RecipeMenuReason reason, boolean chinese) {
        return switch (reason) {
            case NONE -> chinese ? "配方无法处理" : "Recipe cannot run";
            case RECIPE_MISSING -> chinese ? "配方已移除" : "Recipe missing";
            case RECIPE_CHANGED -> chinese ? "配方已变更" : "Recipe changed";
            case RECIPE_TAGS_INVALID -> chinese ? "配方标签无效" : "Recipe tags invalid";
            case SIGNATURE_MIGRATION_PENDING -> chinese ? "正在检查旧配方" : "Checking old recipe";
            case SIGNATURE_MIGRATION_UNPROVEN -> chinese ? "旧配方需要修复" : "Old recipe needs repair";
            case RETAINED_PLAN_INVALID -> chinese ? "保存的计划无效" : "Stored plan invalid";
        };
    }
}
