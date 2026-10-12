package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.ClassicMilestoneAdvancements.Milestone;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Player-facing milestone text, merged by the existing v1.8 language provider. */
public final class V180ClassicMilestoneLanguage {
    private V180ClassicMilestoneLanguage() { }

    public static Map<String, String> translations(boolean chinese) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Milestone milestone : Milestone.values()) {
            result.put(milestone.titleKey(), title(milestone, chinese));
            result.put(milestone.descriptionKey(), description(milestone, chinese));
        }
        return Collections.unmodifiableMap(result);
    }

    private static String title(Milestone milestone, boolean chinese) {
        return switch (milestone) {
            case ARC_FURNACE -> chinese ? "感受热度" : "Feel the Heat";
            case LATHE -> chinese ? "旋转大师" : "Spin Doctor";
            case CRYSTALLIZER -> chinese ? "晶莹剔透" : "Crystalline";
            case DILITHIUM -> chinese ? "二锂晶体" : "Dilithium";
            case FORMED_MULTIBLOCK -> chinese ? "整体大于部分" : "More Than Its Parts";
            case SEAT_ON_TNT -> chinese ? "帮我拿下饮料" : "Hold My Beer";
            case MOON_LANDING -> chinese ? "登月成功" : "Moon Landing";
            case ONE_SMALL_STEP -> chinese ? "一小步" : "One Small Step";
            case MOON_SURFACE_VISIT -> chinese ? "去月球" : "Went to the Moon";
            case STATION_WARP -> chinese ? "全力以赴" : "Giving It All She's Got";
            case FIRST_STATION_WARP -> chinese ? "凤凰之翼" : "Flight of the Phoenix";
        };
    }

    private static String description(Milestone milestone, boolean chinese) {
        return switch (milestone) {
            case ARC_FURNACE -> chinese ? "在物品栏中取得一台电弧炉。" : "Obtain an electric arc furnace in your inventory.";
            case LATHE -> chinese ? "在物品栏中取得一台车床。" : "Obtain a lathe in your inventory.";
            case CRYSTALLIZER -> chinese ? "在物品栏中取得一台结晶器。" : "Obtain a crystallizer in your inventory.";
            case DILITHIUM -> chinese ? "在物品栏中取得一块二锂晶体。" : "Obtain a dilithium crystal in your inventory.";
            case FORMED_MULTIBLOCK -> chinese
                    ? "多方块机器（轧制机、精密装配机、车床、电弧炉或切割机）成型时，身处其控制器16格以内。"
                    : "Be within 16 blocks of a multiblock machine's controller (rolling machine, precision "
                            + "assembler, lathe, arc furnace or cutting machine) when its structure forms.";
            case SEAT_ON_TNT -> chinese ? "手持火箭座椅右击TNT。" : "Right-click TNT while holding a rocket seat.";
            case MOON_LANDING -> chinese ? "乘坐火箭在月球上完成着陆。" : "Ride a rocket until it lands on the Moon.";
            case ONE_SMALL_STEP -> chinese ? "成为本世界首次月球火箭着陆的乘员。"
                    : "Be aboard the first rocket to land on the Moon in this world.";
            case MOON_SURFACE_VISIT -> chinese ? "到达月球表面。" : "Arrive on the surface of the Moon.";
            case STATION_WARP -> chinese ? "空间站完成跃迁时，身处站内且拥有访问权限。"
                    : "Be aboard a space station you may visit when it completes a warp.";
            case FIRST_STATION_WARP -> chinese ? "身处本世界首次完成跃迁的空间站内。"
                    : "Be aboard the first space station to complete a warp in this world.";
        };
    }
}
