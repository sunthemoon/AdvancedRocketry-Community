package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import java.util.Map;

/** One name delegated to the existing bilingual provider; no additional file writer. */
public final class V180StationLightLanguage {
    private V180StationLightLanguage() { }

    public static Map<String, String> translations(boolean chinese) {
        return Map.of("block.advancedrocketrycommunity.station_light", chinese ? "空间站灯" : "Station Light");
    }
}
