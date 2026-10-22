package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoStatus;
import java.util.Map;

/** Original item-only text; existing shared cargo messages remain unchanged. */
public final class V180ItemCargoStatusLanguage {
    public static Map<String, String> language(boolean chinese) {
        return Map.of(
                RocketCargoStatus.DEVICE_EMPTY.itemTranslationKey(), chinese
                        ? "设备的物品缓冲区为空。" : "The device's item buffer is empty.",
                RocketCargoStatus.DEVICE_FULL.itemTranslationKey(), chinese
                        ? "设备的物品缓冲区已满。" : "The device's item buffer is full.",
                RocketCargoStatus.INCOMPATIBLE_CARGO.itemTranslationKey(), chinese
                        ? "储存的物品或物品数据不匹配；未移动货物。"
                        : "The stored items or item data do not match; no cargo moved.");
    }

    private V180ItemCargoStatusLanguage() { }
}
