package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import java.util.Map;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;

public final class AtmosphereLanguageProvider extends LanguageProvider {
    private static final Map<String, String> EN_US = Map.ofEntries(
            Map.entry("block.advancedrocketrycommunity.oxygen_vent", "Oxygen Vent"),
            Map.entry("item.advancedrocketrycommunity.space_suit_helmet", "Space Suit Helmet"),
            Map.entry("item.advancedrocketrycommunity.space_suit_chestplate", "Space Suit Chestplate"),
            Map.entry("item.advancedrocketrycommunity.space_suit_leggings", "Space Suit Leggings"),
            Map.entry("item.advancedrocketrycommunity.space_suit_boots", "Space Suit Boots"),
            Map.entry("tooltip.advancedrocketrycommunity.suit_oxygen", "Oxygen: %s / %s"),
            Map.entry("tooltip.advancedrocketrycommunity.suit_oxygen_future", "Newer oxygen schema; refill disabled"),
            Map.entry("tooltip.advancedrocketrycommunity.suit_oxygen_invalid", "Invalid oxygen data; refill disabled"),
            Map.entry("message.advancedrocketrycommunity.oxygen.requires_chestplate", "Equip the Space Suit Chestplate first"),
            Map.entry("message.advancedrocketrycommunity.oxygen.no_capacity", "The suit cannot accept a whole oxygen canister"),
            Map.entry("message.advancedrocketrycommunity.oxygen.suit_refilled", "Suit oxygen: %s"),
            Map.entry("message.advancedrocketrycommunity.vent.oxygen_added", "Vent oxygen: %s"),
            Map.entry("message.advancedrocketrycommunity.vent.oxygen_rejected", "Vent cannot accept a whole oxygen canister (%s)"),
            Map.entry("message.advancedrocketrycommunity.vent.energy_added", "Vent energy: %s FE"),
            Map.entry("message.advancedrocketrycommunity.vent.energy_rejected", "Vent cannot accept this redstone (%s FE)"),
            Map.entry("message.advancedrocketrycommunity.vent.status", "Vent: %s | O2 %s | %s FE"),
            Map.entry("hud.advancedrocketrycommunity.life_support.status", "ATM // %s"),
            Map.entry("hud.advancedrocketrycommunity.life_support.oxygen", "O2 %s/%s   SUIT %s/4"),
            Map.entry("hud.advancedrocketrycommunity.life_support.state.exempt", "EXEMPT"),
            Map.entry("hud.advancedrocketrycommunity.life_support.state.breathable_environment", "BREATHABLE"),
            Map.entry("hud.advancedrocketrycommunity.life_support.state.breathable_volume", "ROOM SEALED"),
            Map.entry("hud.advancedrocketrycommunity.life_support.state.suit_oxygen", "SUIT OXYGEN"),
            Map.entry("hud.advancedrocketrycommunity.life_support.state.exposed", "VACUUM"),
            Map.entry("hud.advancedrocketrycommunity.life_support.state.partial_suit", "SUIT INCOMPLETE"),
            Map.entry("hud.advancedrocketrycommunity.life_support.state.oxygen_empty", "OXYGEN EMPTY"),
            Map.entry("hud.advancedrocketrycommunity.life_support.state.volume_pending", "SCANNING"),
            Map.entry("status.advancedrocketrycommunity.vent.scanning", "Scanning"),
            Map.entry("status.advancedrocketrycommunity.vent.active", "Active"),
            Map.entry("status.advancedrocketrycommunity.vent.standby_shared_volume", "Standby (shared room)"),
            Map.entry("status.advancedrocketrycommunity.vent.no_power", "No power"),
            Map.entry("status.advancedrocketrycommunity.vent.no_oxygen", "No oxygen"),
            Map.entry("status.advancedrocketrycommunity.vent.open", "Room open to vacuum"),
            Map.entry("status.advancedrocketrycommunity.vent.too_large", "Room exceeds scan limit"),
            Map.entry("status.advancedrocketrycommunity.vent.pending_unloaded_chunk", "Waiting for loaded chunk"),
            Map.entry("status.advancedrocketrycommunity.vent.busy", "Scanner busy"),
            Map.entry("status.advancedrocketrycommunity.vent.cancelled", "Scan cancelled"),
            Map.entry("status.advancedrocketrycommunity.vent.invalid_data", "Invalid saved data"),
            Map.entry("status.advancedrocketrycommunity.vent.unsupported_data", "Newer saved-data schema"),
            Map.entry("death.attack.advancedrocketrycommunity.vacuum", "%1$s was exposed to vacuum")
    );
    private static final Map<String, String> ZH_CN = Map.ofEntries(
            Map.entry("block.advancedrocketrycommunity.oxygen_vent", "氧气通风口"),
            Map.entry("item.advancedrocketrycommunity.space_suit_helmet", "宇航服头盔"),
            Map.entry("item.advancedrocketrycommunity.space_suit_chestplate", "宇航服胸甲"),
            Map.entry("item.advancedrocketrycommunity.space_suit_leggings", "宇航服护腿"),
            Map.entry("item.advancedrocketrycommunity.space_suit_boots", "宇航服靴子"),
            Map.entry("tooltip.advancedrocketrycommunity.suit_oxygen", "氧气：%s / %s"),
            Map.entry("tooltip.advancedrocketrycommunity.suit_oxygen_future", "氧气数据来自新版 schema，已禁用补充"),
            Map.entry("tooltip.advancedrocketrycommunity.suit_oxygen_invalid", "氧气数据无效，已禁用补充"),
            Map.entry("message.advancedrocketrycommunity.oxygen.requires_chestplate", "请先装备宇航服胸甲"),
            Map.entry("message.advancedrocketrycommunity.oxygen.no_capacity", "宇航服无法容纳一整罐氧气"),
            Map.entry("message.advancedrocketrycommunity.oxygen.suit_refilled", "宇航服氧气：%s"),
            Map.entry("message.advancedrocketrycommunity.vent.oxygen_added", "通风口氧气：%s"),
            Map.entry("message.advancedrocketrycommunity.vent.oxygen_rejected", "通风口无法容纳一整罐氧气（%s）"),
            Map.entry("message.advancedrocketrycommunity.vent.energy_added", "通风口能量：%s FE"),
            Map.entry("message.advancedrocketrycommunity.vent.energy_rejected", "通风口无法接收这份红石（%s FE）"),
            Map.entry("message.advancedrocketrycommunity.vent.status", "通风口：%s | O2 %s | %s FE"),
            Map.entry("hud.advancedrocketrycommunity.life_support.status", "环境 // %s"),
            Map.entry("hud.advancedrocketrycommunity.life_support.oxygen", "O2 %s/%s   宇航服 %s/4"),
            Map.entry("hud.advancedrocketrycommunity.life_support.state.exempt", "已豁免"),
            Map.entry("hud.advancedrocketrycommunity.life_support.state.breathable_environment", "可呼吸环境"),
            Map.entry("hud.advancedrocketrycommunity.life_support.state.breathable_volume", "房间已密闭"),
            Map.entry("hud.advancedrocketrycommunity.life_support.state.suit_oxygen", "宇航服供氧"),
            Map.entry("hud.advancedrocketrycommunity.life_support.state.exposed", "真空暴露"),
            Map.entry("hud.advancedrocketrycommunity.life_support.state.partial_suit", "宇航服不完整"),
            Map.entry("hud.advancedrocketrycommunity.life_support.state.oxygen_empty", "氧气耗尽"),
            Map.entry("hud.advancedrocketrycommunity.life_support.state.volume_pending", "正在扫描"),
            Map.entry("status.advancedrocketrycommunity.vent.scanning", "正在扫描"),
            Map.entry("status.advancedrocketrycommunity.vent.active", "运行中"),
            Map.entry("status.advancedrocketrycommunity.vent.standby_shared_volume", "待机（共享房间）"),
            Map.entry("status.advancedrocketrycommunity.vent.no_power", "能量不足"),
            Map.entry("status.advancedrocketrycommunity.vent.no_oxygen", "氧气不足"),
            Map.entry("status.advancedrocketrycommunity.vent.open", "房间与真空连通"),
            Map.entry("status.advancedrocketrycommunity.vent.too_large", "房间超过扫描上限"),
            Map.entry("status.advancedrocketrycommunity.vent.pending_unloaded_chunk", "等待区块加载"),
            Map.entry("status.advancedrocketrycommunity.vent.busy", "扫描器繁忙"),
            Map.entry("status.advancedrocketrycommunity.vent.cancelled", "扫描已取消"),
            Map.entry("status.advancedrocketrycommunity.vent.invalid_data", "存档数据无效"),
            Map.entry("status.advancedrocketrycommunity.vent.unsupported_data", "存档来自新版 schema"),
            Map.entry("death.attack.advancedrocketrycommunity.vacuum", "%1$s 暴露在真空中")
    );

    private final Map<String, String> translations;

    public AtmosphereLanguageProvider(PackOutput output, String locale) {
        super(output, AdvancedRocketryCommunity.MOD_ID + "_v040", locale);
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
