package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;

/** Additive v1.6 labels (ADR-049): satellite kinds, components, the builder and the new status codes. */
public final class V160LanguageProvider extends LanguageProvider {
    private final boolean chinese;

    public V160LanguageProvider(PackOutput output, String locale) {
        super(output, ModIdentity.MOD_ID + "_v160", locale);
        if (!locale.equals("en_us") && !locale.equals("zh_cn")) {
            throw new IllegalArgumentException("Unsupported locale " + locale);
        }
        chinese = locale.equals("zh_cn");
    }

    @Override
    protected void addTranslations() {
        add("block.advancedrocketrycommunity.satellite_builder", chinese ? "卫星组装台" : "Satellite Builder");
        add("menu.advancedrocketrycommunity.satellite_builder", chinese ? "卫星组装台" : "Satellite Builder");
        add("item.advancedrocketrycommunity.satellite_package", chinese ? "卫星组件包" : "Satellite Package");
        add("block.advancedrocketrycommunity.microwave_receiver", chinese ? "微波接收器" : "Microwave Receiver");
        add("menu.advancedrocketrycommunity.microwave_receiver", chinese ? "微波接收器" : "Microwave Receiver");
        add("screen.advancedrocketrycommunity.microwave_receiver.output",
                chinese ? "输出 %s FE/t" : "OUTPUT  %s FE/t");
        add("screen.advancedrocketrycommunity.microwave_receiver.link.empty", chinese ? "空槽位" : "Empty slot");
        add("screen.advancedrocketrycommunity.microwave_receiver.link.holding",
                chinese ? "已连接：本接收器持有链接" : "Linked: this receiver holds the link");
        add("screen.advancedrocketrycommunity.microwave_receiver.link.elsewhere",
                chinese ? "该卫星已连接其他接收器" : "Linked to another receiver");
        add("screen.advancedrocketrycommunity.microwave_receiver.link.unavailable",
                chinese ? "卫星不存在或不是太阳能卫星" : "Satellite missing or not a solar satellite");
        add("screen.advancedrocketrycommunity.satellite.unlink", chinese ? "断开" : "UNLINK");
        add("tooltip.advancedrocketrycommunity.satellite.unlink",
                chinese ? "当所连接收器已确认丢失时，断开太阳能卫星的链接"
                        : "Clear a solar satellite's link when its receiver is confirmed missing");
        add("status.advancedrocketrycommunity.survey_scan.started",
                chinese ? "勘测扫描已开始；请留在 64 格范围内" : "Survey scan started; stay within 64 blocks");
        add("status.advancedrocketrycommunity.survey_scan.cancelled",
                chinese ? "勘测扫描已取消" : "Survey scan cancelled");
        add("status.advancedrocketrycommunity.survey_scan.complete",
                chinese ? "勘测扫描完成" : "Survey scan complete");
        add("screen.advancedrocketrycommunity.survey_scan.title",
                chinese ? "勘测扫描 %s, %s" : "SURVEY SCAN  %s, %s");
        add("screen.advancedrocketrycommunity.survey_scan.legend", chinese ? "主要生物群系" : "Dominant biomes");
        add("screen.advancedrocketrycommunity.survey_scan.cell",
                chinese ? "矿石占比 %s%% · %s" : "Ore share %s%% · %s");
        add("screen.advancedrocketrycommunity.survey_scan.unknown",
                chinese ? "未扫描：区块未加载" : "Not scanned: chunk not loaded");
        add("screen.advancedrocketrycommunity.survey_scan.other", chinese ? "其他生物群系" : "Other biome");
        add("item.advancedrocketrycommunity.advanced_solar_panel", chinese ? "高级太阳能板" : "Advanced Solar Panel");
        add("item.advancedrocketrycommunity.satellite_battery", chinese ? "卫星电池" : "Satellite Battery");
        add("item.advancedrocketrycommunity.large_satellite_battery",
                chinese ? "大型卫星电池" : "Large Satellite Battery");
        add("item.advancedrocketrycommunity.satellite_cargo_hold", chinese ? "卫星货舱" : "Satellite Cargo Hold");
        add("item.advancedrocketrycommunity.survey_scanner_module",
                chinese ? "勘测扫描模块" : "Survey Scanner Module");
        add("item.advancedrocketrycommunity.solar_transmitter_module",
                chinese ? "太阳能发射模块" : "Solar Transmitter Module");
        add("item.advancedrocketrycommunity.asteroid_drill_module",
                chinese ? "小行星钻探模块" : "Asteroid Drill Module");
        add("item.advancedrocketrycommunity.gas_intake_module", chinese ? "气体采集模块" : "Gas Intake Module");

        for (SatelliteKind kind : SatelliteKind.values()) {
            add("satellite_kind.advancedrocketrycommunity." + kind.id(), chinese ? chinese(kind) : english(kind));
        }
        add("tooltip.advancedrocketrycommunity.satellite_package.kind", chinese ? "类型：%s" : "Kind: %s");

        add("screen.advancedrocketrycommunity.satellite_builder.assemble", chinese ? "组装" : "ASSEMBLE");
        add("screen.advancedrocketrycommunity.satellite_builder.no_kind",
                chinese ? "放入底盘与主功能模块" : "Insert a chassis and a primary module");
        add("screen.advancedrocketrycommunity.satellite_builder.stats",
                chinese ? "能量 %s  电池 %s  数据 %s  货舱 %s  等级 %s"
                        : "PWR %s  BAT %s  DATA %s  CARGO %s  RATING %s");
        add("screen.advancedrocketrycommunity.satellite.decommission", chinese ? "退役" : "RETIRE");
        add("tooltip.advancedrocketrycommunity.satellite.decommission",
                chinese ? "让空闲卫星退役并清空芯片；不返还任何物品"
                        : "Decommission this idle satellite and blank its chip; nothing is refunded");
        add("screen.advancedrocketrycommunity.satellite.kind_orbit",
                chinese ? "%s：环绕 %s 运行" : "%s  ORBITING %s");
        add("screen.advancedrocketrycommunity.satellite.kind_idle",
                chinese ? "%s：等待发射" : "%s  AWAITING LAUNCH");

        for (SatelliteOperationCode code : SatelliteOperationCode.values()) {
            if (code.ordinal() > SatelliteOperationCode.SERVER_ERROR.ordinal()) {
                add(code.translationKey(), chinese ? chinese(code) : english(code));
            }
        }
    }

    private static String english(SatelliteKind kind) {
        return switch (kind) {
            case DATA -> "Data satellite";
            case SURVEY -> "Survey satellite";
            case SOLAR -> "Solar satellite";
            case ASTEROID_MINER -> "Asteroid miner";
            case GAS_HARVESTER -> "Gas harvester";
        };
    }

    private static String chinese(SatelliteKind kind) {
        return switch (kind) {
            case DATA -> "数据卫星";
            case SURVEY -> "勘测卫星";
            case SOLAR -> "太阳能卫星";
            case ASTEROID_MINER -> "小行星采矿卫星";
            case GAS_HARVESTER -> "气体采集卫星";
        };
    }

    private static String english(SatelliteOperationCode code) {
        return switch (code) {
            case STAT_LIMIT -> "Component stats exceed a satellite cap";
            case REQUIREMENT_UNMET -> "This satellite kind needs more power, data or cargo";
            case RESEARCH_LOCKED -> "More lifetime research is required";
            case COMPONENT_UNAVAILABLE -> "A satellite component is no longer defined";
            case RATE_LIMITED -> "Please wait before the next request";
            case OWNER_LIMIT -> "Your satellite or mission limit is reached";
            case STORAGE_BUDGET -> "Satellite storage budget is full";
            case AWAITING_WORLD_SAVE -> "Waiting for the next world save";
            case DELIVERY_BUFFER_FULL -> "Terminal reward buffer is full";
            case TERMINAL_RECEIPTS_FULL -> "Terminal receipt list is full";
            case TERMINAL_MISSING -> "The bound terminal is missing";
            case NO_ASTEROID_TYPES -> "No asteroid types match this system";
            case BODY_UNAVAILABLE -> "The satellite's orbit body is unavailable";
            default -> throw new IllegalArgumentException("Translated before v1.6: " + code);
        };
    }

    private static String chinese(SatelliteOperationCode code) {
        return switch (code) {
            case STAT_LIMIT -> "组件属性超过卫星上限";
            case REQUIREMENT_UNMET -> "该卫星类型需要更多能量、数据或货舱";
            case RESEARCH_LOCKED -> "累计研究数据不足";
            case COMPONENT_UNAVAILABLE -> "某个卫星组件已不再定义";
            case RATE_LIMITED -> "请稍候再发出请求";
            case OWNER_LIMIT -> "已达到你的卫星或任务上限";
            case STORAGE_BUDGET -> "卫星存储预算已满";
            case AWAITING_WORLD_SAVE -> "等待下一次世界保存";
            case DELIVERY_BUFFER_FULL -> "终端奖励缓冲区已满";
            case TERMINAL_RECEIPTS_FULL -> "终端回执列表已满";
            case TERMINAL_MISSING -> "绑定的终端已丢失";
            case NO_ASTEROID_TYPES -> "没有适用于该星系的小行星类型";
            case BODY_UNAVAILABLE -> "卫星所绕天体不可用";
            default -> throw new IllegalArgumentException("Translated before v1.6: " + code);
        };
    }
}
