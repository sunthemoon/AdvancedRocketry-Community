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
        prefix = "command.advancedrocketrycommunity.rejoin.";
        entries.put(prefix + "permission", chinese ? "释放与恢复查询需要权限等级 3。" : "Release and rejoin inspection require permission level 3.");
        entries.put(prefix + "admitted", chinese ? "已接受释放 %s；阶段：%s。后续证明完成后才会解除保护。"
                : "Release %s admitted; stage: %s. Protection remains until the later proofs complete.");
        entries.put(prefix + "pending", chinese ? "释放 %s 已在处理中；阶段：%s。未创建新批次。"
                : "Release %s is already pending; stage: %s. No new batch was created.");
        entries.put(prefix + "refused", chinese ? "拒绝释放：需要唯一且已加载的遗留火箭、有效的离线乘员等待、无冲突的乘员与可用存储。未更改数据。"
                : "Release refused: a unique loaded legacy hold, absent riders, conflict-free assignments and available storage are required. Nothing changed.");
        entries.put(prefix + "unavailable", chinese ? "恢复服务或检查写入暂不可用；未更改数据。"
                : "Recovery service or checked storage is unavailable; nothing changed.");
        entries.put(prefix + "header", chinese ? "乘员恢复：显示 %s 条，共 %s 条。可用玩家 UUID 筛选。"
                : "Passenger rejoins: showing %s of %s entries. Filter with a player UUID.");
        entries.put(prefix + "entry", chinese ? "释放 %s；转移 %s；玩家 %s；阶段：%s；座位：%s；放置：%s"
                : "Release %s; transfer %s; player %s; stage: %s; seat: %s; placement: %s");
        String[] stages = {"b0", "s1", "b1", "j1", "s2", "placement", "none"};
        String[] stagesEn = {"B0: original seat proof", "S1: release write", "B1: revoked seat proof",
                "J1: transfer write", "S2: seat completion write", "seat proof complete; placement pending", "not pending"};
        String[] stagesZh = {"B0：原座位证明", "S1：释放记录写入", "B1：撤销座位证明", "J1：转移记录写入",
                "S2：座位完成写入", "座位证明已完成；等待放置", "无待处理记录"};
        for (int i = 0; i < stages.length; i++) { entries.put(prefix + "stage." + stages[i], chinese ? stagesZh[i] : stagesEn[i]); }
        entries.put(prefix + "seat.pending", chinese ? "等待撤销证明" : "revocation proof pending");
        entries.put(prefix + "seat.verified", chinese ? "撤销已确认" : "revocation verified");
        entries.put(prefix + "placement.pending", chinese ? "等待放置或存档证明" : "placement or save proof pending");
        entries.put(prefix + "placement.verified", chinese ? "放置已确认" : "placement verified");
        entries.put(prefix + "placement.not_required", chinese ? "无需放置" : "placement not required");
        return Map.copyOf(entries);
    }
}
