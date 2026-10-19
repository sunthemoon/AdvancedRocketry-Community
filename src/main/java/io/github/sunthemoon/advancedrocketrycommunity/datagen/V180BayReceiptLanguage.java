package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import java.util.LinkedHashMap;
import java.util.Map;

/** W27c1: the messages of {@code /arce bay receipt retire-rejected} (W27A-DESIGN section 5, N5), in English and Chinese. */
public final class V180BayReceiptLanguage {
    private static final String KEY = "message.advancedrocketrycommunity.bay_receipt.";
    private V180BayReceiptLanguage() { }

    public static Map<String, String> entries(boolean chinese) {
        var entries = new LinkedHashMap<String, String>();
        entries.put(KEY + "requested", chinese ? "正在退役已拒绝收据 %s（卫星 %s）；结果将在确切确认后报告"
                : "Retiring rejected receipt %s for satellite %s; the result follows its exact acknowledgement");
        entries.put(KEY + "retired", chinese ? "已退役已拒绝收据 %s（卫星 %s）" : "Retired rejected receipt %s for satellite %s");
        entries.put(KEY + "failed", chinese ? "收据写入结果为 %s；收据 %s 保留" : "The receipt write ended with %s; receipt %s is kept");
        entries.put(KEY + "refused.not_loaded", chinese ? "卫星舱收据尚未加载；未退役任何收据"
                : "Bay receipts are not loaded; nothing was retired");
        entries.put(KEY + "refused.unavailable", chinese ? "卫星舱收据存储不可用；未退役任何收据"
                : "The bay receipt store is unavailable; nothing was retired");
        entries.put(KEY + "refused.not_found", chinese ? "没有收据指向卫星 %s" : "No bay receipt names satellite %s");
        entries.put(KEY + "refused.not_rejected", chinese ? "卫星 %s 的收据处于 %s，而非 REJECTED；只能退役已拒绝的收据"
                : "The receipt for satellite %s is %s, not REJECTED; only rejected receipts can be retired");
        entries.put(KEY + "refused.released", chinese ? "卫星 %s 的收据属于已释放的飞行，不能在线退役"
                : "The receipt for satellite %s belongs to a released flight and cannot be retired online");
        entries.put(KEY + "refused.unacknowledged", chinese ? "卫星 %s 的收据正在写入；请在确切确认后重试"
                : "The receipt for satellite %s has a write in progress; retry after its exact acknowledgement");
        entries.put(KEY + "refused.rocket_pending", chinese ? "火箭 %s 仍有未决收据；未退役任何收据"
                : "Rocket %s still has an unresolved receipt; nothing was retired");
        entries.put(KEY + "refused.transfer_record", chinese ? "转移日志中有火箭 %s 的记录或无法读取；未退役任何收据"
                : "The transfer journal holds a record for rocket %s or cannot be read; nothing was retired");
        entries.put(KEY + "refused.transaction_record", chinese ? "事务日志中有可能属于火箭 %s 的记录或无法读取；未退役任何收据"
                : "The transaction journal holds a record that may belong to rocket %s or cannot be read; nothing was retired");
        return entries;
    }
}
