package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.FuelLoaderStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
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
        add("menu.advancedrocketrycommunity.rocket_flight", chinese ? "火箭飞行控制" : "Rocket Flight Control");
        add("body.advancedrocketrycommunity.earth", chinese ? "地球" : "Earth");
        add("body.advancedrocketrycommunity.moon", chinese ? "月球" : "Moon");
        add("screen.advancedrocketrycommunity.rocket.route",
                chinese ? "航线：%s → %s" : "ROUTE  %s → %s");
        add("screen.advancedrocketrycommunity.rocket.state",
                chinese ? "状态：%s" : "STATE  %s");
        add("screen.advancedrocketrycommunity.rocket.fuel",
                chinese ? "燃料 %s/%s · 航程需要 %s" : "FUEL %s/%s · ROUTE NEEDS %s");
        add("screen.advancedrocketrycommunity.rocket.countdown",
                chinese ? "倒计时 %s tick" : "COUNTDOWN  %s ticks");
        add("screen.advancedrocketrycommunity.rocket.passengers",
                chinese ? "已登记乘客：%s" : "MANIFESTED PASSENGERS: %s");
        add("screen.advancedrocketrycommunity.rocket.launch", chinese ? "确认发射" : "CONFIRM LAUNCH");
        add("screen.advancedrocketrycommunity.rocket.cancel", chinese ? "取消倒计时" : "CANCEL COUNTDOWN");
        for (FuelLoaderStatus status : FuelLoaderStatus.values()) {
            add(
                    "status.advancedrocketrycommunity.fuel_loader." + status.diagnosticKey(),
                    chinese ? chinese(status) : english(status)
            );
        }
        for (RocketFlightState state : RocketFlightState.values()) {
            add(
                    "flight.advancedrocketrycommunity.state."
                            + state.name().toLowerCase(java.util.Locale.ROOT),
                    chinese ? flightStateChinese(state) : flightStateEnglish(state)
            );
        }
        for (RocketFlightRequestCode code : RocketFlightRequestCode.values()) {
            add(code.translationKey(), chinese ? requestChinese(code) : requestEnglish(code));
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

    private static String flightStateEnglish(RocketFlightState state) {
        return state.name().replace('_', ' ');
    }

    private static String flightStateChinese(RocketFlightState state) {
        return switch (state) {
            case ASSEMBLED -> "已组装";
            case FUELED -> "已加注";
            case COUNTDOWN -> "倒计时";
            case ASCENT -> "上升";
            case TRANSIT -> "转移中";
            case DESCENT -> "下降";
            case LANDED -> "已着陆";
            case FAILED_RECOVERABLE -> "等待恢复";
            case DISASSEMBLED -> "已拆解";
        };
    }

    private static String requestEnglish(RocketFlightRequestCode code) {
        return switch (code) {
            case SUCCESS -> "Flight request accepted; required fuel %s";
            case ENTITY_UNAVAILABLE -> "Rocket is unavailable";
            case OUT_OF_RANGE -> "Rocket is outside interaction range";
            case UNAUTHORIZED -> "Only the owner or an operator may control this rocket";
            case INVALID_STATE -> "Rocket state does not allow that action";
            case INVALID_DESTINATION -> "Destination is not valid for this rocket";
            case REQUEST_REPLAYED -> "Duplicate flight request rejected";
            case REQUEST_LEDGER_FULL -> "Flight request queue is full";
            case MISSING_FLIGHT_COMPONENTS -> "Rocket is missing a motor, seat, or guidance computer";
            case INSUFFICIENT_THRUST -> "Rocket thrust is below its mass";
            case FUEL_STATE_MISMATCH -> "Rocket fuel state does not match its structure";
            case INSUFFICIENT_CAPACITY -> "Fuel capacity is below the required %s units";
            case INSUFFICIENT_FUEL -> "Not enough fuel; route requires %s units";
            case ARITHMETIC_OVERFLOW -> "Route calculation exceeded its fixed bounds";
        };
    }

    private static String requestChinese(RocketFlightRequestCode code) {
        return switch (code) {
            case SUCCESS -> "飞行请求已接受；需要燃料 %s";
            case ENTITY_UNAVAILABLE -> "火箭不可用";
            case OUT_OF_RANGE -> "火箭超出交互距离";
            case UNAUTHORIZED -> "只有所有者或管理员可以控制这枚火箭";
            case INVALID_STATE -> "火箭当前状态不允许该操作";
            case INVALID_DESTINATION -> "该目的地不适用于这枚火箭";
            case REQUEST_REPLAYED -> "已拒绝重复飞行请求";
            case REQUEST_LEDGER_FULL -> "飞行请求队列已满";
            case MISSING_FLIGHT_COMPONENTS -> "火箭缺少发动机、座椅或导航计算机";
            case INSUFFICIENT_THRUST -> "火箭推力低于质量";
            case FUEL_STATE_MISMATCH -> "火箭燃料状态与结构不匹配";
            case INSUFFICIENT_CAPACITY -> "燃料容量低于航程所需的 %s 单位";
            case INSUFFICIENT_FUEL -> "燃料不足；航程需要 %s 单位";
            case ARITHMETIC_OVERFLOW -> "航程计算超出固定边界";
        };
    }
}
