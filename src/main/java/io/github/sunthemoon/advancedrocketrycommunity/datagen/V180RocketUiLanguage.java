package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightFeedback;
import java.util.Map;

/** Rocket console and monitor feedback added in v1.8 without rewriting historical outputs. */
public final class V180RocketUiLanguage {
    private V180RocketUiLanguage() { }

    public static Map<String, String> entries(boolean chinese) {
        return Map.of(
                "screen.advancedrocketrycommunity.rocket.no_stations_short", chinese ? "暂无空间站" : "NO STATIONS",
                RocketFlightFeedback.BOARDED, chinese ? "已进入火箭座椅" : "Boarded the rocket",
                RocketFlightFeedback.LEFT, chinese ? "已离开火箭座椅" : "Left the rocket seat",
                "message.advancedrocketrycommunity.linker.monitor_selected", chinese
                        ? "已选择火箭监测站；现在用链接器点击它的火箭。" : "Monitor selected; now use the linker on its rocket.",
                "message.advancedrocketrycommunity.linker.monitor_bound", chinese
                        ? "火箭监测站已绑定到这枚火箭。" : "Monitor bound to this rocket.",
                "message.advancedrocketrycommunity.linker.monitor_unbound", chinese
                        ? "已清除火箭监测站的绑定。" : "Monitor binding cleared.");
    }
}
