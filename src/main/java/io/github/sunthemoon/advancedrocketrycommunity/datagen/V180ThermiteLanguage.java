package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import java.util.Map;

/** Three names delegated to the existing bilingual provider; no additional file writer. */
public final class V180ThermiteLanguage {
    private V180ThermiteLanguage() { }

    public static Map<String, String> translations(boolean chinese) {
        return Map.of(
                "item.advancedrocketrycommunity.thermite", chinese ? "铝热剂" : "Thermite",
                "block.advancedrocketrycommunity.thermite_torch", chinese ? "铝热火把" : "Thermite Torch",
                "block.advancedrocketrycommunity.thermite_wall_torch", chinese ? "壁挂铝热火把" : "Thermite Wall Torch");
    }
}
