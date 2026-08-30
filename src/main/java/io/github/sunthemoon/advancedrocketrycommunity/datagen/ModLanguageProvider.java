package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import java.util.Map;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;

public final class ModLanguageProvider extends LanguageProvider {
    private static final Map<String, String> EN_US = Map.ofEntries(
            Map.entry("block.advancedrocketrycommunity.machine_casing", "Machine Casing"),
            Map.entry("item.advancedrocketrycommunity.advanced_circuit", "Advanced Circuit"),
            Map.entry("item.advancedrocketrycommunity.basic_circuit", "Basic Circuit"),
            Map.entry("item.advancedrocketrycommunity.data_storage_unit", "Data Storage Unit"),
            Map.entry("item.advancedrocketrycommunity.silicon_wafer", "Silicon Wafer"),
            Map.entry("itemGroup.advancedrocketrycommunity.main", "Advanced Rocketry: Community Edition"),
            Map.entry("message.advancedrocketrycommunity.machine_casing_inert", "Machine behavior begins in v0.2.0"),
            Map.entry("subtitle.advancedrocketrycommunity.ui_select", "Interface selection"),
            Map.entry(
                    "tooltip.advancedrocketrycommunity.development_component",
                    "Development component — no machine behavior in v0.1.0"
            )
    );
    private static final Map<String, String> ZH_CN = Map.ofEntries(
            Map.entry("block.advancedrocketrycommunity.machine_casing", "机器外壳"),
            Map.entry("item.advancedrocketrycommunity.advanced_circuit", "高级芯片"),
            Map.entry("item.advancedrocketrycommunity.basic_circuit", "基础芯片"),
            Map.entry("item.advancedrocketrycommunity.data_storage_unit", "数据存储单位"),
            Map.entry("item.advancedrocketrycommunity.silicon_wafer", "硅晶片"),
            Map.entry("itemGroup.advancedrocketrycommunity.main", "高级火箭：社区版"),
            Map.entry("message.advancedrocketrycommunity.machine_casing_inert", "机器功能将在 v0.2.0 开始实现"),
            Map.entry("subtitle.advancedrocketrycommunity.ui_select", "界面选择"),
            Map.entry("tooltip.advancedrocketrycommunity.development_component", "开发组件——v0.1.0 尚无机器功能")
    );

    private final Map<String, String> translations;

    public ModLanguageProvider(PackOutput output, String locale) {
        super(output, AdvancedRocketryCommunity.MOD_ID, locale);
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
