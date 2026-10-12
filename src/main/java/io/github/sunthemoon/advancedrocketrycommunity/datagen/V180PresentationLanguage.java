package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.ModuleKind;
import io.github.sunthemoon.advancedrocketrycommunity.sound.ClassicSound;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * NEW bilingual C18d presentation text (ADR-066 section 7.3): the hydrogen, suit and rocket HUD panels and the
 * subtitles of the classic sound events.
 */
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

    private static final Map<ClassicSound, String[]> SUBTITLES = new EnumMap<>(Map.of(
            ClassicSound.AIR_HISS_LOOP, new String[] {"Air hisses", "空气嘶嘶作响"},
            ClassicSound.BASIC_LASER_GUN, new String[] {"Laser fires", "激光开火"},
            ClassicSound.COMBUSTION_ROCKET, new String[] {"Rocket engine roars", "火箭发动机轰鸣"},
            ClassicSound.ELECTRIC_SHOCK_SMALL, new String[] {"Electricity crackles", "电流噼啪作响"},
            ClassicSound.GRAVITY_CONTROLLER, new String[] {"Gravity field hums", "重力场嗡鸣"},
            ClassicSound.LASER_DRILL, new String[] {"Laser drill whines", "激光钻尖啸"},
            ClassicSound.LATHE, new String[] {"Lathe whirs", "车床运转"},
            ClassicSound.MACHINE_LARGE, new String[] {"Large machine hums", "大型机器嗡鸣"},
            ClassicSound.RAILGUN_BANG, new String[] {"Railgun fires", "轨道炮发射"},
            ClassicSound.ROLLING_MACHINE, new String[] {"Rolling machine rumbles", "轧机隆隆作响"}));

    private V180PresentationLanguage() {
    }

    public static Map<String, String> translations(boolean chinese) {
        if (MODULES.size() != ModuleKind.values().length || SUBTITLES.size() != ClassicSound.values().length) {
            throw new IllegalStateException("Every module kind needs a HUD label and every sound a subtitle");
        }
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put(HUD + "hydrogen", chinese ? "氢 %s/%s" : "H2 %s/%s");
        entries.put(HUD + "suit", chinese ? "模块 // %s" : "MODULES // %s");
        MODULES.forEach((kind, labels) ->
                entries.put(HUD + "suit.module." + kind.stableId(), labels[chinese ? 1 : 0]));
        entries.put(HUD + "rocket", chinese ? "火箭 // %s   燃料 %s   高度 %s" : "ROCKET // %s   FUEL %s   ALT %s");
        SUBTITLES.forEach((sound, labels) -> entries.put(sound.subtitle(), labels[chinese ? 1 : 0]));
        return entries;
    }
}
