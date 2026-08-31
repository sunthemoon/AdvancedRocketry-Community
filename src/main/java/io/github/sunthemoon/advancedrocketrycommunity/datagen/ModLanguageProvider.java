package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import java.util.Map;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;

public final class ModLanguageProvider extends LanguageProvider {
    private static final Map<String, String> EN_US = Map.ofEntries(
            Map.entry("block.advancedrocketrycommunity.electrolyzer", "Electrolyzer"),
            Map.entry("item.advancedrocketrycommunity.empty_canister", "Empty Canister"),
            Map.entry("item.advancedrocketrycommunity.hydrogen_canister", "Hydrogen Canister"),
            Map.entry("item.advancedrocketrycommunity.oxygen_canister", "Oxygen Canister"),
            Map.entry("menu.advancedrocketrycommunity.electrolyzer", "Electrolyzer"),
            Map.entry("status.advancedrocketrycommunity.electrolyzer.idle", "Idle"),
            Map.entry("status.advancedrocketrycommunity.electrolyzer.invalid_recipe", "Invalid recipe or saved data"),
            Map.entry("status.advancedrocketrycommunity.electrolyzer.needs_energy", "Needs energy"),
            Map.entry("status.advancedrocketrycommunity.electrolyzer.needs_water", "Needs water"),
            Map.entry("status.advancedrocketrycommunity.electrolyzer.no_recipe", "Insert two empty canisters"),
            Map.entry("status.advancedrocketrycommunity.electrolyzer.output_blocked", "Output blocked"),
            Map.entry("status.advancedrocketrycommunity.electrolyzer.redstone_disabled", "Paused by redstone signal"),
            Map.entry("status.advancedrocketrycommunity.electrolyzer.running", "Electrolyzing"),
            Map.entry("status.advancedrocketrycommunity.electrolyzer.unsupported_data", "Newer saved-data schema; processing disabled"),
            Map.entry("tooltip.advancedrocketrycommunity.energy", "Energy: %s / %s FE"),
            Map.entry("tooltip.advancedrocketrycommunity.water", "Water: %s / %s mB")
    );
    private static final Map<String, String> ZH_CN = Map.ofEntries(
            Map.entry("block.advancedrocketrycommunity.electrolyzer", "电解机"),
            Map.entry("item.advancedrocketrycommunity.empty_canister", "空罐"),
            Map.entry("item.advancedrocketrycommunity.hydrogen_canister", "氢气罐"),
            Map.entry("item.advancedrocketrycommunity.oxygen_canister", "氧气罐"),
            Map.entry("menu.advancedrocketrycommunity.electrolyzer", "电解机"),
            Map.entry("status.advancedrocketrycommunity.electrolyzer.idle", "待机"),
            Map.entry("status.advancedrocketrycommunity.electrolyzer.invalid_recipe", "配方或存档数据无效"),
            Map.entry("status.advancedrocketrycommunity.electrolyzer.needs_energy", "需要能量"),
            Map.entry("status.advancedrocketrycommunity.electrolyzer.needs_water", "需要水"),
            Map.entry("status.advancedrocketrycommunity.electrolyzer.no_recipe", "放入两个空罐"),
            Map.entry("status.advancedrocketrycommunity.electrolyzer.output_blocked", "输出槽已满"),
            Map.entry("status.advancedrocketrycommunity.electrolyzer.redstone_disabled", "红石信号暂停"),
            Map.entry("status.advancedrocketrycommunity.electrolyzer.running", "电解中"),
            Map.entry("status.advancedrocketrycommunity.electrolyzer.unsupported_data", "存档来自新版 schema，已禁用处理"),
            Map.entry("tooltip.advancedrocketrycommunity.energy", "能量：%s / %s FE"),
            Map.entry("tooltip.advancedrocketrycommunity.water", "水：%s / %s mB")
    );

    private final Map<String, String> translations;

    public ModLanguageProvider(PackOutput output, String locale) {
        super(output, AdvancedRocketryCommunity.MOD_ID + "_v020", locale);
        translations = switch (locale) {
            case "en_us" -> EN_US;
            case "zh_cn" -> ZH_CN;
            default -> throw new IllegalArgumentException("Unsupported locale: " + locale);
        };
    }

    @Override
    protected void addTranslations() {
        translations.forEach(this::add);
    }
}
