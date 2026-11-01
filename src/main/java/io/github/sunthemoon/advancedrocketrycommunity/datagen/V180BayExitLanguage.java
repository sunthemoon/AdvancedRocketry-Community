package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;

/** Physical exit feedback is distinct from LEAVE's ordinary seat-release result. */
public final class V180BayExitLanguage extends LanguageProvider {
    private final boolean chinese;
    public V180BayExitLanguage(PackOutput output, String locale) {
        super(output, ModIdentity.MOD_ID + "_v180_bay_exit", locale);
        if (!locale.equals("en_us") && !locale.equals("zh_cn")) { throw new IllegalArgumentException(locale); }
        chinese = locale.equals("zh_cn");
    }
    @Override protected void addTranslations() {
        String seat = chinese ? " 已有座位分配保持不变，后续飞行或重连可能再次移动您。仍需生命保障。"
                : " Existing seat assignments are retained; later flight or reconnect may move you again. Life support is still required.";
        message("safe", (chinese ? "已下船，放置位置已检查。" : "Exited to a checked standing position.") + seat);
        message("fall_offer", (chinese ? "未找到安全位置，已原地下船。仅本次下降的普通摔落可获得一次有限保护；最多 200 刻，移动、传送、掉线或未知接触可能提前终止。尖刺等其他伤害不受保护。"
                : "No safe position found; detached in place. One limited ordinary-fall safeguard is offered for this descent only, for less than 200 ticks; movement, teleport, disconnect or uncertain contact can end it early. Dripstone and other damage are not protected.") + seat);
        message("authority_unavailable", (chinese ? "退出权威不可用：未证明安全位置时原地下船，无摔落保护。"
                : "Exit authority unavailable: detached without an unproved move or fall protection.") + seat);
        message("capacity_or_used", (chinese ? "未找到安全位置，已原地下船。保护容量已满或本次转移的首次退出机会已使用；无新增摔落保护。"
                : "No safe position found; detached in place. Safeguard capacity is full or this transfer's first-exit offer was used; no new fall protection.") + seat);
        message("budget_exhausted", (chinese ? "安全位置检查预算已用尽，已原地下船，无摔落保护。"
                : "Exit search budget exhausted; detached in place without fall protection.") + seat);
        message("no_safe_exit", (chinese ? "未找到安全位置，已原地下船；摔落保护不可用。"
                : "No safe position found; detached in place. Fall protection is unavailable.") + seat);
    }
    private void message(String outcome, String text) { add("message.advancedrocketrycommunity.bay_exit." + outcome, text); }
    @Override public String getName() { return "v1.8 bay exit " + (chinese ? "zh_cn" : "en_us"); }
}
