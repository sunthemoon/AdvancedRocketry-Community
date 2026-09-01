package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.FuelLoaderStatus;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;

public final class FlightLanguageProvider extends LanguageProvider {
    private final String locale;

    public FlightLanguageProvider(PackOutput output, String locale) {
        super(output, AdvancedRocketryCommunity.MOD_ID + "_v060", locale);
        if (!locale.equals("en_us") && !locale.equals("zh_cn")) {
            throw new IllegalArgumentException("Unsupported locale " + locale);
        }
        this.locale = locale;
    }

    @Override
    protected void addTranslations() {
        boolean chinese = locale.equals("zh_cn");
        add("block.advancedrocketrycommunity.fuel_loader", chinese ? "火箭燃料装载机" : "Rocket Fuel Loader");
        add("item.advancedrocketrycommunity.rocket_fuel_cell", chinese ? "火箭燃料单元" : "Rocket Fuel Cell");
        add("message.advancedrocketrycommunity.fuel_loader.unauthorized",
                chinese ? "用户无权操作这台燃料装载机" : "You are not authorized to use this Fuel Loader");
        add("message.advancedrocketrycommunity.fuel_loader.cell_inserted",
                chinese ? "已插入一个火箭燃料单元" : "Inserted one Rocket Fuel Cell");
        add("message.advancedrocketrycommunity.fuel_loader.cell_rejected",
                chinese ? "燃料单元无法插入" : "The fuel cell cannot be inserted");
        add("message.advancedrocketrycommunity.fuel_loader.canister_returned",
                chinese ? "已取回空罐" : "Returned the empty canister");
        add("message.advancedrocketrycommunity.fuel_loader.status",
                chinese ? "燃料装载机：%s；缓冲燃料 %s" : "Fuel Loader: %s; buffered fuel %s");
        for (FuelLoaderStatus status : FuelLoaderStatus.values()) {
            add(
                    "status.advancedrocketrycommunity.fuel_loader." + status.diagnosticKey(),
                    chinese ? chinese(status) : english(status)
            );
        }
    }

    private static String english(FuelLoaderStatus status) {
        return switch (status) {
            case UNCLAIMED -> "unclaimed";
            case IDLE -> "idle";
            case WAITING_FOR_ROCKET -> "waiting for an eligible nearby rocket";
            case TRANSFERRING -> "transferring";
            case OUTPUT_READY -> "empty canister ready";
            case UNSUPPORTED_DATA -> "unsupported saved data";
            case INVALID_DATA -> "invalid saved data";
        };
    }

    private static String chinese(FuelLoaderStatus status) {
        return switch (status) {
            case UNCLAIMED -> "未认领";
            case IDLE -> "空闲";
            case WAITING_FOR_ROCKET -> "等待符合条件的附近火箭";
            case TRANSFERRING -> "正在加注";
            case OUTPUT_READY -> "空罐可取回";
            case UNSUPPORTED_DATA -> "不支持的存档数据";
            case INVALID_DATA -> "无效的存档数据";
        };
    }
}
