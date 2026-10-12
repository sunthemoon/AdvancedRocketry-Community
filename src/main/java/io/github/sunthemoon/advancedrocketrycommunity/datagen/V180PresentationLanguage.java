package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.ModuleKind;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** NEW bilingual C18d presentation text (ADR-066 section 7.3): the hydrogen, suit and rocket HUD panels. */
public final class V180PresentationLanguage {
    private static final String HUD = "hud." + ModIdentity.MOD_ID + ".";
    private static final Map<ModuleKind, String[]> MODULES = new EnumMap<>(Map.of(
            ModuleKind.PRESSURE_TANK, new String[] {"TANK", "储罐"},
            ModuleKind.JETPACK, new String[] {"JETPACK", "喷气背包"},
            ModuleKind.HOVER, new String[] {"HOVER", "悬停"},
            ModuleKind.FLIGHT_SPEED, new String[] {"SPEED", "速度"},
            ModuleKind.BIONIC, new String[] {"BIONIC", "仿生"},
            ModuleKind.PADDED, new String[] {"PADDED", "缓冲"},
            ModuleKind.ANTI_FOG, new String[] {"ANTI-FOG", "防雾"},
            ModuleKind.EARTHBRIGHT, new String[] {"EARTHBRIGHT", "地照"},
            ModuleKind.BEACON_FINDER, new String[] {"BEACON", "信标"}));

    private V180PresentationLanguage() {
    }

    public static Map<String, String> translations(boolean chinese) {
        if (MODULES.size() != ModuleKind.values().length) {
            throw new IllegalStateException("Every module kind needs a HUD label");
        }
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put(HUD + "hydrogen", chinese ? "氢 %s/%s" : "H2 %s/%s");
        entries.put(HUD + "suit", chinese ? "模块 // %s" : "MODULES // %s");
        MODULES.forEach((kind, labels) ->
                entries.put(HUD + "suit.module." + kind.stableId(), labels[chinese ? 1 : 0]));
        entries.put(HUD + "rocket", chinese ? "火箭 // %s   燃料 %s   高度 %s" : "ROCKET // %s   FUEL %s   ALT %s");
        return entries;
    }
}
