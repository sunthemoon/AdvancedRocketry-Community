package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;

/** Additive v1.3 feedback; historical language outputs remain unchanged. */
public final class V130RocketLanguageProvider extends LanguageProvider {
    private final boolean chinese;

    public V130RocketLanguageProvider(PackOutput output, String locale) {
        super(output, AdvancedRocketryCommunity.MOD_ID + "_v130", locale);
        if (!locale.equals("en_us") && !locale.equals("zh_cn")) {
            throw new IllegalArgumentException("Unsupported locale " + locale);
        }
        chinese = locale.equals("zh_cn");
    }

    @Override
    protected void addTranslations() {
        add("message.advancedrocketrycommunity.fuel_loader.fuel_inserted", chinese ? "已插入一个燃料物品" : "Inserted one fuel item");
        add("message.advancedrocketrycommunity.fuel_loader.fuel_rejected", chinese ? "燃料物品无法插入" : "The fuel item cannot be inserted");
        add("message.advancedrocketrycommunity.fuel_loader.item_returned", chinese ? "已取回物品" : "Returned the item");
        add("message.advancedrocketrycommunity.fuel_loader.repair_required", chinese
                ? "装载机存档超出支持范围；请备份世界并离线修复，暂时不能拆除。"
                : "Loader data exceeds supported limits; back up the world and repair it offline before removal.");
        add("status.advancedrocketrycommunity.fuel_loader.unsupported_fuel", chinese
                ? "未注册的燃料，可取回物品" : "unregistered fuel; the item can be recovered");
        add("status.advancedrocketrycommunity.fuel_loader.item_ready", chinese ? "产物可取回" : "output ready");
        add("message.advancedrocketrycommunity.rocket.disassembly_fuel_warning", chinese
                ? "火箭仍有 %s 单位燃料。拆解将丢弃这些燃料且无法回收。确认在 %s 秒后过期；不确认则保留火箭。"
                : "Rocket has %s fuel units. Disassembly will discard them without refund. Confirmation expires in %s seconds; do nothing to keep the rocket.");
        add("message.advancedrocketrycommunity.rocket.disassembly_discard_action", chinese
                ? "[丢弃 %s 单位燃料并拆解：点击后提交命令]"
                : "[Discard %s fuel units and disassemble: click, then submit the command]");
        add("message.advancedrocketrycommunity.rocket.disassembly_confirmation_invalid", chinese
                ? "拆解确认已失效或火箭状态已改变；请靠近火箭重新潜行交互。"
                : "Disassembly confirmation expired or the rocket changed; sneak-use the nearby rocket again.");
        add("message.advancedrocketrycommunity.rocket.disassembly_fuel_discarded", chinese
                ? "拆解完成，已按确认丢弃 %s 单位燃料。"
                : "Disassembly complete; discarded %s fuel units as confirmed.");
        add(RocketValidationCode.FUEL_DISPOSAL_REQUIRED.translationKey(), chinese
                ? "拆解需要明确确认丢弃剩余燃料"
                : "Disassembly requires explicit consent to discard remaining fuel");
        add(RocketValidationCode.FUEL_CAPACITY_EXCEEDED.translationKey(), chinese
                ? "火箭燃料容量超过支持上限"
                : "Rocket fuel capacity exceeds the supported limit");
    }
}
