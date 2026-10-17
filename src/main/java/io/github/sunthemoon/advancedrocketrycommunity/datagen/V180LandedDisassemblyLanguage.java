package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import java.util.Map;

/** W85 (P1-F8): the status text of a landed disassembly that waits for its checked reservation removal. */
public final class V180LandedDisassemblyLanguage {
    private V180LandedDisassemblyLanguage() { }

    public static Map<String, String> entries(boolean chinese) {
        return Map.of("validation.advancedrocketrycommunity.rocket.disassembly_pending", chinese
                ? "拆解已开始：正在确认移除着陆预留，确认后火箭会自动拆解。"
                : "Disassembly pending: the landing reservation is being released; the rocket disassembles once that is saved.");
    }
}
