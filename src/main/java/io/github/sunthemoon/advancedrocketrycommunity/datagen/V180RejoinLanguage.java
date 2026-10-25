package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import java.util.LinkedHashMap;
import java.util.Map;

final class V180RejoinLanguage {
    private V180RejoinLanguage() { }
    static Map<String, String> entries(boolean chinese) {
        var entries = new LinkedHashMap<String, String>();
        String prefix = "message.advancedrocketrycommunity.rejoin.";
        entries.put(prefix + "guard", chinese ? "火箭乘员恢复保护：%s。请等待安全放置与存档确认。"
                : "Rocket passenger recovery protection: %s. Waiting for safe placement and save verification.");
        entries.put(prefix + "disconnect", chinese ? "火箭乘员恢复尚未完成：%s。已安全断开；下次登录会重试。请联系管理员检查安全停机坪或存储。"
                : "Rocket passenger recovery is pending: %s. Disconnected under protection; your next login retries. Ask an administrator to check the safe pad or storage.");
        String[] reasons = {"waiting", "capacity", "conflict", "unsafe", "tickets", "io", "oversized", "dead"};
        String[] en = {"waiting for recovery", "recovery capacity reached", "conflicting recovery assignments",
                "no verified safe position", "waiting for chunk ticket capacity", "waiting for exact saved-file proof",
                "player file exceeds the readback ceiling", "waiting for respawn"};
        String[] zh = {"等待恢复", "恢复容量已满", "恢复记录冲突", "没有已验证的安全位置", "等待区块票容量",
                "等待精确存档回读证明", "玩家文件超过回读上限", "等待重生"};
        for (int i = 0; i < reasons.length; i++) { entries.put(prefix + reasons[i], chinese ? zh[i] : en[i]); }
        return Map.copyOf(entries);
    }
}
