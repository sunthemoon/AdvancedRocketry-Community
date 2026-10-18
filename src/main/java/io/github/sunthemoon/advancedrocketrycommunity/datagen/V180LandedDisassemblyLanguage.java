package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import java.util.Map;

/**
 * W85 (P1-F8): the status text of a landed disassembly that is still pending. It promises no outcome, because a pending
 * request may also end in a write-back without disassembling (W92-R5).
 */
public final class V180LandedDisassemblyLanguage {
    private V180LandedDisassemblyLanguage() { }

    public static Map<String, String> entries(boolean chinese) {
        return Map.of("validation.advancedrocketrycommunity.rocket.disassembly_pending", chinese
                ? "此火箭的拆解请求仍在处理中，请在处理完成后再试。"
                : "A disassembly request for this rocket is still being processed; try again when it finishes.");
    }
}
