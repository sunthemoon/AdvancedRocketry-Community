package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ResearchStatus;
import java.util.LinkedHashMap;
import java.util.Map;

public final class V180ResearchLanguage {
    public static Map<String, String> entries(boolean chinese) {
        var entries = new LinkedHashMap<String, String>(); String prefix = "screen.advancedrocketrycommunity.research.";
        entries.put("block.advancedrocketrycommunity.observatory", chinese ? "天文台" : "Observatory");
        entries.put("block.advancedrocketrycommunity.astrobody_data_processor", chinese ? "天体数据处理器" : "Astrobody Data Processor");
        String[] buttons = {"start", "cancel", "previous", "next", "claim", "rebind"};
        String[] en = {"Start survey", "Cancel survey", "Previous", "Next", "Claim analysis", "Rebind here"};
        String[] zh = {"开始巡天", "取消巡天", "上一项", "下一项", "认领分析", "重新绑定"};
        for (int i = 0; i < buttons.length; i++) { entries.put(prefix + buttons[i], chinese ? zh[i] : en[i]); }
        entries.put(prefix + "progress", chinese ? "进度：%s / %s tick" : "Progress: %s / %s ticks");
        entries.put(prefix + "energy", chinese ? "能量：%s FE（需要 %s FE/t）" : "Energy: %s FE (needs %s FE/t)");
        entries.put(prefix + "selection", chinese ? "任务：%s / %s" : "Job: %s / %s");
        entries.put(prefix + "diagnostic", chinese ? "缺失单元：(%s, %s, %s)，需要 %s" : "Missing cell: (%s, %s, %s), needs %s");
        entries.put(prefix + "state", chinese ? "任务 %s / 分析 %s" : "Survey: %s / analysis: %s");
        entries.put(prefix + "owner", chinese ? "所有者：%s" : "Owner: %s");
        labels(entries, prefix + "phase.", chinese ? new String[]{"无", "巡天中", "就绪", "已处理", "已取消", "已隔离"}
                : new String[]{"None", "Active", "Ready", "Processed", "Cancelled", "Quarantined"});
        labels(entries, prefix + "analysis.", chinese ? new String[]{"无", "待认领", "运行中", "已记分"}
                : new String[]{"None", "Waiting", "Running", "Credited"});
        labels(entries, prefix + "owner.", chinese ? new String[]{"无", "自己", "其他玩家", "未知"}
                : new String[]{"None", "You", "Another player", "Unknown"});
        labels(entries, prefix + "role.", chinese ? new String[]{"无", "空气", "机器外壳", "玻璃", "台阶", "马达", "电力输入插头", "外壳或插头", "净空", "控制器", "布局单元"}
                : new String[]{"None", "Air", "Machine casing", "Glass", "Slab", "Motor", "Power input plug", "Shell or plug", "Clearance", "Controller", "Pattern cell"});
        String[] statusesEn = {"Idle", "Running", "Complete", "Registry blocked; back up and repair offline", "Ground research disabled",
            "Multiblock not formed", "Waiting for loaded cells", "Lens cannot see sky", "Waiting for night", "Precipitation pauses observation",
            "Body or station permission unavailable", "Star system changed", "Asteroid tables unavailable", "Insufficient power", "Waiting for registry save",
            "Queued", "Source missing; owner must rebind", "Copied identity quarantined; repair offline", "Quarantined; inspect with an operator",
            "Research account full", "Back up and repair job/instance or device offline", "Only the owner can operate", "Observatory busy", "Processor busy",
            "Owner record limit", "Global capacity reached", "Storage budget reached", "Analysis already claimed or credited", "Job not ready"};
        String[] statusesZh = {"待机", "运行中", "已完成", "注册表已阻断；请备份并离线修复", "地面研究已禁用", "多方块未形成", "等待单元加载",
            "透镜无法看见天空", "等待夜间", "降水暂停观测", "天体或空间站访问权限不可用", "恒星系已变化", "小行星数据表不可用", "能量不足",
            "等待注册表保存", "排队中", "来源缺失；所有者须重新绑定", "复制的设备身份已隔离；须离线修复", "任务已隔离；请管理员检查", "研究账户已满",
            "请备份并离线修复任务/实例或设备", "只有所有者可以操作", "天文台忙碌", "处理器忙碌", "所有者记录已达上限", "全局容量已达上限",
            "存储预算已达上限", "分析已被认领或已记入研究点", "任务尚未就绪"};
        if (statusesEn.length != ResearchStatus.values().length || statusesZh.length != statusesEn.length) { throw new IllegalStateException("Research labels"); }
        for (var status : ResearchStatus.values()) { entries.put(status.key(), chinese ? statusesZh[status.ordinal()] : statusesEn[status.ordinal()]); }
        return entries;
    }
    private static void labels(Map<String, String> entries, String prefix, String[] labels) {
        for (int code = 0; code < labels.length; code++) { entries.put(prefix + code, labels[code]); }
    }
    private V180ResearchLanguage() { }
}
