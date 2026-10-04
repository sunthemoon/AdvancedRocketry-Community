package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.machine.pump.PumpCode;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;

/** Separate unique pump keys; never duplicates a historical language key. */
public final class V180PumpLanguage {
    /** Root's single v1.8 LanguageProvider includes this map, avoiding duplicate file writers. */
    public static Map<String, String> translations(boolean chinese) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("block.advancedrocketrycommunity.pump", chinese ? "流体泵" : "Fluid Pump");
        result.put("message.advancedrocketrycommunity.pump.status", "%s | %s FE | %s mB");
        for (PumpCode code : PumpCode.values()) {
            result.put("status.advancedrocketrycommunity.pump." + code.name().toLowerCase(Locale.ROOT), label(code, chinese));
        }
        return Map.copyOf(result);
    }
    public static String label(PumpCode code, boolean chinese) {
        return switch (code) {
            case SEARCHING -> chinese ? "搜索中" : "Searching";
            case SOURCE_READY -> chinese ? "发现源方块" : "Source ready";
            case DRAINED -> chinese ? "已采集" : "Pumped";
            case COOLDOWN -> chinese ? "冷却中" : "Cooling down";
            case NO_OWNER -> chinese ? "无玩家所有者" : "No player owner";
            case NO_ENERGY -> chinese ? "能量不足" : "Not enough energy";
            case DISABLED -> chinese ? "已禁用" : "Disabled";
            case TANK_FULL -> chinese ? "储罐空间不足" : "Not enough tank space";
            case TANK_INCOMPATIBLE -> chinese ? "流体不兼容" : "Incompatible fluid";
            case NO_FLUID -> chinese ? "下方无流体" : "No fluid below";
            case SOURCE_UNSUPPORTED -> chinese ? "不支持此源方块" : "Unsupported source adapter";
            case SOURCE_CHANGED -> chinese ? "源方块已变化" : "Source changed";
            case TARGET_UNLOADED -> chinese ? "区块未加载" : "Target chunk not loaded";
            case TARGET_OUT_OF_BOUNDS -> chinese ? "目标超出世界边界" : "Target outside world bounds";
            case TARGET_PROTECTED -> chinese ? "目标受保护" : "Target protected";
            case SEARCH_EXHAUSTED -> chinese ? "连通流体搜索结束" : "Connected search exhausted";
            case SEARCH_LIMIT -> chinese ? "达到搜索上限" : "Search limit reached";
            case REPAIR_REQUIRED -> chinese ? "数据需要管理员修复" : "Administrator repair required";
        };
    }
    private V180PumpLanguage() { }
}
