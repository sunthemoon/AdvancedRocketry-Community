package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;

public final class SatelliteLanguageProvider extends LanguageProvider {
    private final boolean chinese;

    public SatelliteLanguageProvider(PackOutput output, String locale) {
        // A versioned asset namespace avoids duplicate Copy entries while the
        // translation keys themselves remain in the real mod namespace.
        super(output, AdvancedRocketryCommunity.MOD_ID + "_v080", locale);
        if (!locale.equals("en_us") && !locale.equals("zh_cn")) {
            throw new IllegalArgumentException("Unsupported locale " + locale);
        }
        chinese = locale.equals("zh_cn");
    }

    @Override
    protected void addTranslations() {
        add("block.advancedrocketrycommunity.satellite_terminal",
                chinese ? "卫星任务终端" : "Satellite Mission Terminal");
        add("item.advancedrocketrycommunity.satellite_chassis",
                chinese ? "卫星底盘" : "Satellite Chassis");
        add("item.advancedrocketrycommunity.satellite_solar_module",
                chinese ? "卫星太阳能模块" : "Satellite Solar Module");
        add("item.advancedrocketrycommunity.satellite_control_chip",
                chinese ? "卫星控制芯片" : "Satellite Control Chip");
        add("item.advancedrocketrycommunity.data_satellite_package",
                chinese ? "数据卫星组件" : "Data Satellite Package");
        add("menu.advancedrocketrycommunity.satellite_terminal",
                chinese ? "卫星任务终端" : "Satellite Mission Terminal");

        add("tooltip.advancedrocketrycommunity.satellite_chip.blank",
                chinese ? "未绑定：在卫星终端组装" : "Unbound: assemble in a Satellite Terminal");
        add("tooltip.advancedrocketrycommunity.satellite_chip.bound",
                chinese ? "已绑定卫星 %s" : "Bound satellite %s");
        add("tooltip.advancedrocketrycommunity.satellite_package.bound",
                chinese ? "已组装卫星 %s" : "Assembled satellite %s");
        add("tooltip.advancedrocketrycommunity.satellite_item.unsupported",
                chinese ? "不支持或损坏的卫星数据" : "Unsupported or invalid satellite data");

        add("screen.advancedrocketrycommunity.satellite.assemble", chinese ? "组装" : "ASSEMBLE");
        add("screen.advancedrocketrycommunity.satellite.launch", chinese ? "发射/任务" : "LAUNCH");
        add("screen.advancedrocketrycommunity.satellite.claim", chinese ? "领取" : "CLAIM");
        add("screen.advancedrocketrycommunity.satellite.cancel", chinese ? "取消" : "CANCEL");
        add("screen.advancedrocketrycommunity.satellite.research",
                chinese ? "研究数据：%s" : "RESEARCH  %s");
        add("screen.advancedrocketrycommunity.satellite.power",
                chinese ? "终端能源：%s FE" : "TERMINAL POWER  %s FE");
        add("screen.advancedrocketrycommunity.satellite.no_target",
                chinese ? "没有可用目标" : "NO AVAILABLE TARGET");
        add("screen.advancedrocketrycommunity.satellite.discovered",
                chinese ? "已发现" : "DISCOVERED");
        add("screen.advancedrocketrycommunity.satellite.unknown",
                chinese ? "未知目标" : "UNKNOWN TARGET");
        add("screen.advancedrocketrycommunity.satellite.mission.none",
                chinese ? "任务：空闲" : "MISSION  IDLE");
        add("screen.advancedrocketrycommunity.satellite.mission.active",
                chinese ? "任务：运行中（%s 秒）" : "MISSION  ACTIVE  %ss");
        add("screen.advancedrocketrycommunity.satellite.mission.ready",
                chinese ? "任务：数据已就绪" : "MISSION  DATA READY");
        add("screen.advancedrocketrycommunity.satellite.mission.claim_pending_discovery",
                chinese ? "任务：正在提交发现" : "MISSION  COMMITTING DISCOVERY");
        add("screen.advancedrocketrycommunity.satellite.mission.claimed",
                chinese ? "任务：已领取" : "MISSION  CLAIMED");
        add("screen.advancedrocketrycommunity.satellite.mission.cancelled",
                chinese ? "任务：已取消" : "MISSION  CANCELLED");

        for (SatelliteOperationCode code : SatelliteOperationCode.values()) {
            add(code.translationKey(), chinese ? chinese(code) : english(code));
        }
    }

    private static String english(SatelliteOperationCode code) {
        return switch (code) {
            case SUCCESS -> "Terminal ready / operation completed";
            case IDEMPOTENT -> "Request already applied safely";
            case UNSUPPORTED_DATA -> "Satellite data is unsupported or invalid";
            case CATALOG_UNAVAILABLE -> "Satellite definitions are unavailable";
            case DEFINITION_NOT_FOUND -> "Satellite definition was not found";
            case TARGET_NOT_ALLOWED -> "Selected target is not allowed";
            case CAPACITY_REACHED -> "Satellite or research capacity reached";
            case IDENTITY_CONFLICT -> "Satellite identity conflicts with durable state";
            case SATELLITE_NOT_FOUND -> "Satellite was not found";
            case MISSION_NOT_FOUND -> "No unfinished mission was found";
            case MISSION_BUSY -> "Satellite already has an unfinished mission";
            case NOT_READY -> "Mission result is not ready";
            case ALREADY_CLAIMED -> "Mission result was already claimed";
            case CANCELLED -> "Mission is cancelled";
            case PENDING_DISCOVERY -> "Research credited; discovery commit is pending";
            case UNAUTHORIZED -> "This terminal or satellite belongs to another player";
            case RECEIVER_REQUIRED -> "Insert the matching bound control chip and package";
            case NO_POWER -> "Terminal needs more power";
            case INVALID_COMPONENTS -> "Insert chassis, solar module, data storage, and blank chip";
            case OUTPUT_BLOCKED -> "Clear the satellite package bay";
            case UNLOADED_CHUNK -> "Terminal chunk is not loaded";
            case OUT_OF_RANGE -> "Move closer to the loaded terminal";
            case RECOVERY_REQUIRED -> "Satellite requires operator recovery";
            case SERVER_ERROR -> "Satellite service is unavailable";
        };
    }

    private static String chinese(SatelliteOperationCode code) {
        return switch (code) {
            case SUCCESS -> "终端就绪 / 操作完成";
            case IDEMPOTENT -> "请求已安全执行，无重复结果";
            case UNSUPPORTED_DATA -> "卫星数据不受支持或已损坏";
            case CATALOG_UNAVAILABLE -> "卫星定义暂不可用";
            case DEFINITION_NOT_FOUND -> "找不到卫星定义";
            case TARGET_NOT_ALLOWED -> "所选任务目标不允许";
            case CAPACITY_REACHED -> "卫星或研究数据容量已满";
            case IDENTITY_CONFLICT -> "卫星身份与持久化状态冲突";
            case SATELLITE_NOT_FOUND -> "找不到该卫星";
            case MISSION_NOT_FOUND -> "没有未结束任务";
            case MISSION_BUSY -> "该卫星已有未结束任务";
            case NOT_READY -> "任务结果尚未就绪";
            case ALREADY_CLAIMED -> "任务结果已经领取";
            case CANCELLED -> "任务已取消";
            case PENDING_DISCOVERY -> "研究数据已入账，等待提交天体发现";
            case UNAUTHORIZED -> "该终端或卫星属于其他玩家";
            case RECEIVER_REQUIRED -> "请放入匹配的控制芯片与卫星组件";
            case NO_POWER -> "终端能源不足";
            case INVALID_COMPONENTS -> "请放入底盘、太阳能模块、数据存储和空白芯片";
            case OUTPUT_BLOCKED -> "请清空卫星组件舱";
            case UNLOADED_CHUNK -> "终端区块未加载";
            case OUT_OF_RANGE -> "请靠近已加载的终端";
            case RECOVERY_REQUIRED -> "卫星需要管理员恢复";
            case SERVER_ERROR -> "卫星服务不可用";
        };
    }
}
