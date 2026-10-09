package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import java.util.Map;

public final class V180JackhammerLanguage {
    public static Map<String, String> translations(boolean chinese) {
        return Map.of(
                "item.advancedrocketrycommunity.jackhammer", chinese ? "钻锤" : "Jackhammer",
                "tooltip.advancedrocketrycommunity.jackhammer.mining", chinese ? "快速挖掘镐类方块" : "Fast mining of pickaxe blocks",
                "tooltip.advancedrocketrycommunity.jackhammer.repair", chinese ? "使用钛棒修理" : "Repair with titanium rods",
                "message.advancedrocketrycommunity.jackhammer.disabled", chinese ? "钻锤：已禁用" : "Jackhammer: disabled");
    }

    private V180JackhammerLanguage() { }
}
