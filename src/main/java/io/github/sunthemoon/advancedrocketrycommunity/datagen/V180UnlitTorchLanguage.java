package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import java.util.Map;

/** Unlit torch names and the torch rule messages, delegated to the existing bilingual provider. */
public final class V180UnlitTorchLanguage {
    private V180UnlitTorchLanguage() { }

    public static Map<String, String> translations(boolean chinese) {
        String message = "message.advancedrocketrycommunity.torch.";
        return Map.of(
                "block.advancedrocketrycommunity.unlit_torch", chinese ? "熄灭的火把" : "Unlit Torch",
                "block.advancedrocketrycommunity.unlit_wall_torch", chinese ? "熄灭的墙上火把" : "Unlit Wall Torch",
                message + "no_combustion", chinese ? "这里的空气无法支持燃烧" : "The air here cannot sustain a flame",
                message + "unsupported", chinese ? "这种火把无法放在不能燃烧的地方" : "This torch cannot be placed where nothing can burn",
                message + "busy", chinese ? "火把检查已满，请稍后再放置" : "Torch checks are full here; place it again shortly",
                message + "quarantined", chinese ? "这支火把的数据需要修复" : "This torch's data needs repair",
                message + "unknown", chinese ? "无法确定这支火把原来的样子" : "This torch's original kind is unknown");
    }
}
