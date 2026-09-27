package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;

/** Additive labels for planetary environments and server-authoritative navigation. */
public final class V140PlanetaryLanguageProvider extends LanguageProvider {
    private final boolean chinese;

    public V140PlanetaryLanguageProvider(PackOutput output, String locale) {
        super(output, ModIdentity.MOD_ID + "_v140", locale);
        if (!locale.equals("en_us") && !locale.equals("zh_cn")) {
            throw new IllegalArgumentException("Unsupported locale " + locale);
        }
        chinese = locale.equals("zh_cn");
    }

    @Override
    protected void addTranslations() {
        add("body.advancedrocketrycommunity.mars", chinese ? "火星" : "Mars");
        add("body.advancedrocketrycommunity.venus", chinese ? "金星" : "Venus");
        add("body.advancedrocketrycommunity.gas_giant", chinese ? "气态巨行星" : "Gas Giant");
        add("biome.advancedrocketrycommunity.mars", chinese ? "红色荒原" : "Red Barrens");
        add("biome.advancedrocketrycommunity.venus", chinese ? "灼热高地" : "Scorched Highlands");
        add("environment.advancedrocketrycommunity.exposed", chinese ? "环境危险：%s" : "Environmental exposure: %s");
        add("environment.advancedrocketrycommunity.cold", chinese ? "低温" : "Cold");
        add("environment.advancedrocketrycommunity.heat", chinese ? "高温" : "Heat");
        add("environment.advancedrocketrycommunity.pressure", chinese ? "高压" : "High pressure");
        add("environment.advancedrocketrycommunity.solar", chinese ? "强日照" : "Intense sunlight");
        death("cold", chinese ? "%1$s 未能抵御严寒" : "%1$s succumbed to the planetary cold");
        death("heat", chinese ? "%1$s 未能抵御酷热" : "%1$s succumbed to the planetary heat");
        death("pressure", chinese ? "%1$s 未能抵御高压" : "%1$s succumbed to atmospheric pressure");
        death("solar", chinese ? "%1$s 未能抵御强烈日照" : "%1$s succumbed to intense sunlight");
        navigation("ready", "可发射", "Ready to launch");
        navigation("current", "已在此目的地", "Already at destination");
        navigation("no_route", "没有可用航线", "No available route");
        navigation("missing_components", "缺少火箭组件", "Missing rocket components");
        navigation("insufficient_thrust", "推力不足", "Insufficient thrust");
        navigation("fuel_state_mismatch", "燃料状态不匹配", "Fuel state mismatch");
        navigation("insufficient_capacity", "燃料容量不足", "Insufficient fuel capacity");
        navigation("insufficient_fuel", "燃料不足", "Insufficient fuel");
        navigation("invalid_state", "火箭尚不能发射", "Rocket is not launchable");
        navigation("unauthorized", "没有控制或访问权限", "Control or access denied");
        navigation("unavailable", "目的地不可用", "Destination unavailable");
        navigation("arithmetic_overflow", "航线数值超出限制", "Route values exceed limits");
        map("open", "星图", "Star map");
        map("title", "航行星图", "Navigation map");
        map("back", "控制台", "Console");
        map("hint", "关系示意图 · 拖动平移 / 滚轮缩放 / < > 切换天体", "Schematic · Drag / scroll to zoom / < > focus bodies");
        map("surface", "选择地表", "Use surface");
        map("stations", "无空间站", "No stations");
        map("no_stations", "此天体没有可访问空间站", "No accessible stations around this body");
        map("station_count", "空间站 %s/%s", "Station %s/%s");
        map("use_station", "选择空间站", "Use station");
        map("gravity_temperature", "重力 %s× · 温度 %s K", "Gravity %s× · Temperature %s K");
        map("atmosphere", "气压 %s · %s", "Pressure %s · %s");
        map("breathable", "可呼吸", "Breathable");
        map("unbreathable", "不可呼吸", "Unbreathable");
        map("solar_radiation", "日照 %s · 辐射 %s（显示数据）", "Sunlight %s · Radiation %s (display data)");
        map("quote", "%s · 预计燃料 %s mB", "%s · Fuel quote %s mB");
        map("syncing", "正在同步导航数据；暂不可发射", "Synchronizing navigation; launch disabled");
        map("no_surface", "无可抵达地表；可检查轨道空间站", "No surface arrival; inspect orbit stations");
    }

    private void navigation(String suffix, String zh, String en) {
        add("navigation.advancedrocketrycommunity." + suffix, chinese ? zh : en);
    }

    private void map(String suffix, String zh, String en) {
        add("starmap.advancedrocketrycommunity." + suffix, chinese ? zh : en);
    }

    private void death(String hazard, String message) {
        add("death.attack.advancedrocketrycommunity.planetary_" + hazard, message);
        add("death.attack.advancedrocketrycommunity.planetary_" + hazard + ".player",
                message + (chinese ? "，此前曾与 %2$s 战斗" : " while fighting %2$s"));
    }
}
