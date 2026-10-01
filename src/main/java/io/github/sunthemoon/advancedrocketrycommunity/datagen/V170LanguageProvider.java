package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;

/**
 * v1.7 (ADR-054..059) labels in their own namespace. Every endgame status and refusal code has a translation, so a
 * status is never shown by colour or an icon alone (ADR-054 section 4); a missing one fails DataGen.
 */
public final class V170LanguageProvider extends LanguageProvider {
    private final boolean chinese;

    public V170LanguageProvider(PackOutput output, String locale) {
        super(output, ModIdentity.MOD_ID + "_v170", locale);
        if (!locale.equals("en_us") && !locale.equals("zh_cn")) {
            throw new IllegalArgumentException("Unsupported locale " + locale);
        }
        chinese = locale.equals("zh_cn");
    }

    @Override
    protected void addTranslations() {
        add("block.advancedrocketrycommunity.endgame_casing", chinese ? "终局机械外壳" : "Endgame Casing");
        add("item.advancedrocketrycommunity.laser_lens", chinese ? "激光透镜" : "Laser Lens");
        add("advancedrocketrycommunity.endgame.status_line", "%s [%s]");
        add("body.advancedrocketrycommunity.cygnus_x1", chinese ? "天鹅座 X-1" : "Cygnus X-1");
        add("block.advancedrocketrycommunity.orbital_laser_drill", chinese ? "轨道激光钻" : "Orbital Laser Drill");
        add("block.advancedrocketrycommunity.laser_target", chinese ? "激光目标" : "Laser Target");
        add("block.advancedrocketrycommunity.gravity_field_controller", chinese ? "区域重力控制器" : "Gravity Field Controller");
        add("block.advancedrocketrycommunity.black_hole_generator", chinese ? "黑洞发电机" : "Black Hole Generator");
        add("block.advancedrocketrycommunity.railgun", chinese ? "轨道炮货运发射器" : "Railgun Cargo Launcher");
        add("screen.advancedrocketrycommunity.railgun.previous", "<");
        add("screen.advancedrocketrycommunity.railgun.next", ">");
        add("screen.advancedrocketrycommunity.railgun.launch", chinese ? "发射" : "Launch");
        add("screen.advancedrocketrycommunity.railgun.auto", chinese ? "自动" : "Auto");
        add("screen.advancedrocketrycommunity.railgun.redstone", chinese ? "红石" : "Redstone");
        add("screen.advancedrocketrycommunity.railgun.min_down_16", "-16");
        add("screen.advancedrocketrycommunity.railgun.min_down_1", "-1");
        add("screen.advancedrocketrycommunity.railgun.min_up_1", "+1");
        add("screen.advancedrocketrycommunity.railgun.min_up_16", "+16");
        add("screen.advancedrocketrycommunity.railgun.received", chinese ? "已接收" : "Received");
        add("screen.advancedrocketrycommunity.railgun.last", chinese ? "上次：%s" : "Last: %s");
        String gravity = "screen.advancedrocketrycommunity.gravity_field_controller.";
        add(gravity + "start", chinese ? "启动" : "Start");
        add(gravity + "stop", chinese ? "停止" : "Stop");
        add(gravity + "redstone", chinese ? "红石" : "Redstone");
        add(gravity + "radius_down", chinese ? "半径-" : "Radius -");
        add(gravity + "radius_up", chinese ? "半径+" : "Radius +");
        add(gravity + "multiplier_down", chinese ? "重力-" : "Gravity -");
        add(gravity + "multiplier_up", chinese ? "重力+" : "Gravity +");
        String message = "message.advancedrocketrycommunity.endgame.field.";
        add(message + "trusted", chinese ? "你现在受 %s 的重力场影响" : "Fields of %s now affect you");
        add(message + "untrusted", chinese ? "%s 的重力场不再影响你" : "Fields of %s no longer affect you");
        add(message + "list", chinese ? "你信任的力场所有者：%s / %s" : "Field owners you trust: %s of %s");
        add("screen.advancedrocketrycommunity.laser_target.reset", chinese ? "重置链接" : "Reset link");
        String screen = "screen.advancedrocketrycommunity.orbital_laser_drill.";
        add(screen + "start", chinese ? "启动" : "Start");
        add(screen + "stop", chinese ? "停止" : "Stop");
        add(screen + "redstone", chinese ? "红石模式" : "Redstone");
        add(screen + "last_stop", chinese ? "上次停止：%s" : "Last stop: %s");
        add(screen + "mode", chinese ? "模式" : "Mode");
        add(screen + "previous", "<");
        add(screen + "next", ">");
        add(screen + "link", chinese ? "链接" : "Link");
        add(screen + "unlink", chinese ? "断开" : "Unlink");
        add(screen + "confirm", chinese ? "确认" : "Confirm");
        String view = "advancedrocketrycommunity.endgame.view.";
        add(view + "running", chinese ? "运行" : "Running");
        add(view + "structure", chinese ? "结构" : "Structure");
        add(view + "mode", chinese ? "模式" : "Mode");
        add(view + "redstone", chinese ? "红石" : "Redstone");
        add(view + "lens", chinese ? "透镜" : "Lens");
        add(view + "output", chinese ? "输出槽" : "Output slots");
        add(view + "operations", chinese ? "作业次数" : "Operations");
        add(view + "body", chinese ? "环绕天体" : "Orbit body");
        add(view + "table", chinese ? "开采表" : "Table");
        add(view + "endpoint", chinese ? "端点" : "Endpoint");
        add(view + "cursor", chinese ? "下一层" : "Next layer");
        add(view + "floor", chinese ? "底层" : "Floor");
        add(view + "linked", chinese ? "已链接" : "Linked");
        add(view + "layers", chinese ? "已挖层数" : "Layers dug");
        add(view + "footprint", chinese ? "占地" : "Footprint");
        add(view + "target", chinese ? "选中目标" : "Selected target");
        add(view + "target_at", chinese ? "目标位置" : "Target at");
        add(view + "link", chinese ? "链接" : "Link");
        add(view + "warning", chinese ? "警告" : "Warning");
        add(view + "radius", chinese ? "半径" : "Radius");
        add(view + "multiplier", chinese ? "重力" : "Gravity");
        add(view + "upkeep", chinese ? "维持能耗" : "Upkeep");
        add(view + "box", chinese ? "作用范围" : "Field size");
        add(view + "box_at", chinese ? "范围坐标" : "Field box");
        add(view + "rate", chinese ? "输出功率" : "Output");
        add(view + "remaining", chinese ? "剩余燃烧" : "Burn left");
        add(view + "fuel_table", chinese ? "燃料表" : "Fuel table");
        add(view + "destination", chinese ? "目的地" : "Destination");
        add(view + "destination_at", chinese ? "目的地位置" : "Destination at");
        add(view + "route", chinese ? "航线" : "Route");
        add(view + "auto", chinese ? "自动发射" : "Auto launch");
        add(view + "min_stack", chinese ? "最小堆叠" : "Minimum stack");
        add(view + "outbox", chinese ? "待登记" : "Awaiting registration");
        add(view + "in_transit", chinese ? "运输中" : "In transit");
        add(view + "incoming", chinese ? "入站" : "Incoming");
        add(view + "receipts", chinese ? "回执" : "Receipts");
        String value = "advancedrocketrycommunity.endgame.value.";
        add(value + "on", chinese ? "开" : "On");
        add(value + "off", chinese ? "关" : "Off");
        add(value + "removes_blocks", chinese ? "此操作会移除方块" : "This removes blocks");
        add(value + "break_resolves", chinese ? "拆除它会结算其中冻结的货物" : "Breaking it settles its frozen cargo");
        add(value + "present", chinese ? "已安装" : "Present");
        add(value + "missing", chinese ? "缺失" : "Missing");
        add(value + "mode.logical", chinese ? "逻辑采样" : "Logical sampling");
        add(value + "mode.physical", chinese ? "实体挖掘" : "Physical mining");
        add(value + "redstone.ignored", chinese ? "忽略红石" : "Ignore redstone");
        add(value + "redstone.on", chinese ? "有信号时运行" : "Run with signal");
        add(value + "redstone.inverted", chinese ? "无信号时运行" : "Run without signal");
        Map<EndgameCode, String[]> codes = codes();
        for (EndgameCode code : EndgameCode.values()) {
            String[] text = codes.get(code);
            if (text == null) {
                throw new IllegalStateException("Missing translation for endgame code " + code);
            }
            add(code.translationKey(), chinese ? text[1] : text[0]);
        }
    }

    static Map<EndgameCode, String[]> codes() {
        Map<EndgameCode, String[]> codes = new EnumMap<>(EndgameCode.class);
        put(codes, EndgameCode.OK, "OK", "正常");
        put(codes, EndgameCode.SYSTEM_DISABLED, "This system is disabled on the server", "该系统已在服务器上停用");
        put(codes, EndgameCode.UNOWNED, "No owner: an operator must assign one", "无所有者：需由管理员指定");
        put(codes, EndgameCode.DEVICE_QUARANTINED, "Device data is quarantined", "设备数据已隔离");
        put(codes, EndgameCode.UNFORMED, "Structure incomplete", "结构不完整");
        put(codes, EndgameCode.STRUCTURE_UNLOADED, "Part of the structure is not loaded", "结构有部分未加载");
        put(codes, EndgameCode.UNAUTHORIZED, "You are not allowed to do this", "你没有执行此操作的权限");
        put(codes, EndgameCode.STATION_UNAVAILABLE, "No usable station here", "此处没有可用的空间站");
        put(codes, EndgameCode.NOT_A_PLAYER, "Only a connected player can do this", "只有在线玩家可以执行此操作");
        put(codes, EndgameCode.DEVICE_CHANGED, "The device changed; reopen it", "设备已变化，请重新打开");
        put(codes, EndgameCode.TOO_FAR, "Too far from the device", "距离设备太远");
        put(codes, EndgameCode.WRONG_LEVEL, "The device is in another dimension", "设备位于其他维度");
        put(codes, EndgameCode.CHUNK_UNLOADED, "The device's area is not loaded", "设备所在区域未加载");
        put(codes, EndgameCode.RATE_LIMITED, "Too fast; wait a moment", "操作过快，请稍候");
        put(codes, EndgameCode.TARGET_UNLOADED, "Target area not loaded", "目标区域未加载");
        put(codes, EndgameCode.TARGET_OUT_OF_BOUNDS, "Target outside the world border or build height",
                "目标超出世界边界或建筑高度");
        put(codes, EndgameCode.TARGET_PROTECTED, "Target protected", "目标受保护");
        put(codes, EndgameCode.ACTIVE_LIMIT, "Too many active devices; retrying", "活动设备过多，稍后重试");
        put(codes, EndgameCode.ROOT_UNAVAILABLE, "Endgame data is unavailable", "终局数据不可用");
        put(codes, EndgameCode.ROOT_FULL, "Endgame storage is full", "终局存储已满");
        put(codes, EndgameCode.ROOT_BUSY, "Busy; try again shortly", "繁忙，请稍后再试");
        put(codes, EndgameCode.ENDPOINT_LIMIT, "Endpoint limit reached", "已达端点数量上限");
        put(codes, EndgameCode.AWAITING_WORLD_SAVE, "Waiting for the world to save", "等待世界保存");
        put(codes, EndgameCode.ENDPOINT_RETIRED, "Retired device: place a new one", "设备已退役：请放置新设备");
        put(codes, EndgameCode.ENDPOINT_POSITION_CONFLICT, "This device was copied; it is inactive",
                "该设备是复制品，已停用");
        put(codes, EndgameCode.ENDPOINT_BUSY, "Busy: settle its contents first", "繁忙：请先处理其中的物品");
        put(codes, EndgameCode.ENDPOINT_NOT_FOUND, "No such endpoint", "没有该端点");
        put(codes, EndgameCode.ENDPOINT_CHUNK_LOADED, "Its chunk is loaded: break the block instead",
                "其所在区块已加载：请直接拆除方块");
        put(codes, EndgameCode.ZONE_INVALID, "Invalid zone name or size", "保护区名称或大小无效");
        put(codes, EndgameCode.ZONE_EXISTS, "A zone with this name exists", "已存在同名保护区");
        put(codes, EndgameCode.ZONE_NOT_FOUND, "No such zone", "没有该保护区");
        put(codes, EndgameCode.ZONE_LIMIT, "Zone limit reached", "已达保护区数量上限");
        put(codes, EndgameCode.NO_LENS, "Insert a laser lens", "请放入激光透镜");
        put(codes, EndgameCode.STOPPED, "Stopped", "已停止");
        put(codes, EndgameCode.REDSTONE_BLOCKED, "Paused by the redstone setting", "因红石设置而暂停");
        put(codes, EndgameCode.ORBIT_BODY_UNAVAILABLE, "The orbited body is unavailable", "所环绕的天体不可用");
        put(codes, EndgameCode.NO_TABLE, "Nothing to mine from this body", "该天体没有可开采的资源");
        put(codes, EndgameCode.INSUFFICIENT_ENERGY, "Not enough energy", "能量不足");
        put(codes, EndgameCode.OUTPUT_FULL, "Output full: empty it to continue", "输出已满：取出物品后继续");
        put(codes, EndgameCode.PHYSICAL_DISABLED, "Physical mining is disabled on this server", "本服务器已停用实体挖掘");
        put(codes, EndgameCode.NO_TARGET, "No target selected", "未选择目标");
        put(codes, EndgameCode.FOOTPRINT_AT_CHUNK_EDGE, "Target too close to a chunk edge", "目标离区块边缘太近");
        put(codes, EndgameCode.TARGET_FOREIGN, "The target belongs to someone else", "该目标属于他人");
        put(codes, EndgameCode.TARGET_WRONG_BODY, "The target is not on the orbited body", "目标不在所环绕的天体上");
        put(codes, EndgameCode.LINK_LOST, "The link to the target is lost", "与目标的链接已丢失");
        put(codes, EndgameCode.LINK_UNSETTLED, "Settle the link with its target first", "请先与目标完成结算");
        put(codes, EndgameCode.CONFIRM_REQUIRED, "Confirm: this removes blocks", "请确认：此操作会移除方块");
        put(codes, EndgameCode.BLOCKED_IMMUNE, "Blocked by a protected or special block", "被受保护或特殊方块阻挡");
        put(codes, EndgameCode.BLOCKED_FLUID, "Blocked by a fluid", "被流体阻挡");
        put(codes, EndgameCode.TARGET_BUFFER_FULL, "Target buffer full: empty it to continue", "目标缓存已满：取出物品后继续");
        put(codes, EndgameCode.ENERGY_DEBT, "Paying for layers already dug", "正在补付已挖掘层的能量");
        put(codes, EndgameCode.COMPLETE, "Shaft complete", "竖井已完成");
        put(codes, EndgameCode.BODY_UNAVAILABLE, "No celestial body here", "此处没有天体");
        put(codes, EndgameCode.STATION_OWNER_REQUIRED, "Only the station owner's field can run here",
                "此处只能运行空间站所有者的力场");
        put(codes, EndgameCode.FIELD_OUTSIDE_STATION, "The field lies outside the station", "力场位于空间站之外");
        put(codes, EndgameCode.FIELD_DENSITY, "Too many fields here", "此处力场过多");
        put(codes, EndgameCode.TRUST_LIMIT, "You trust the maximum of 32 owners", "你信任的所有者已达 32 个上限");
        put(codes, EndgameCode.GENERATING, "Generating", "发电中");
        put(codes, EndgameCode.PAUSED_FULL, "Paused: the energy buffer is full", "暂停：能量缓存已满");
        put(codes, EndgameCode.NO_FUEL, "Waiting for fuel", "等待燃料");
        put(codes, EndgameCode.NO_SINGULARITY, "The station does not orbit a singularity", "空间站未环绕奇点");
        put(codes, EndgameCode.TRANSIT_LIMIT, "Too much cargo in transit; wait for deliveries", "运输中的货物过多，请等待送达");
        put(codes, EndgameCode.OUTBOX_FULL, "Four launches wait for registration", "已有四次发射等待登记");
        put(codes, EndgameCode.OUTBOX_QUARANTINED, "Escrowed cargo no longer loads; an operator must purge it",
                "托管货物已无法读取，需由管理员清除");
        put(codes, EndgameCode.PAYLOAD_TOO_LARGE, "A stack is too large to ship", "物品堆过大，无法运送");
        put(codes, EndgameCode.DESTINATION_MISSING, "The destination is gone; redirect the cargo", "目的地已不存在，请改投货物");
        put(codes, EndgameCode.TRANSFER_NOT_FOUND, "No such transfer", "没有该运输记录");
        put(codes, EndgameCode.DESTINATION_ACTIVE, "The destination still exists", "目的地仍然存在");
        put(codes, EndgameCode.ROUTE_REFUSED, "That endpoint cannot receive this cargo", "该端点不能接收此货物");
        put(codes, EndgameCode.NO_PAYLOAD, "No input stack is large enough to launch", "没有达到发射数量的输入物品");
        put(codes, EndgameCode.ROUTE_OUT_OF_SYSTEM, "The destination is in another star system", "目的地位于其他恒星系");
        put(codes, EndgameCode.ELEVATOR_RULE, "An elevator endpoint rule fails", "电梯端点规则不满足");
        put(codes, EndgameCode.TERMINAL_UNAVAILABLE, "The terminal is not usable in this station", "终端在此空间站不可用");
        put(codes, EndgameCode.TERMINAL_ON_PAD, "Too close to the landing pad", "离着陆台太近");
        put(codes, EndgameCode.ANCHOR_UNAVAILABLE, "The anchor is not usable", "锚点不可用");
        put(codes, EndgameCode.ANCHOR_FOREIGN, "The anchor belongs to someone else", "该锚点属于他人");
        put(codes, EndgameCode.STATION_BOUND, "The station already has an elevator", "该空间站已有电梯");
        put(codes, EndgameCode.ANCHOR_BOUND, "The anchor is already bound", "该锚点已绑定");
        put(codes, EndgameCode.TERMINAL_BOUND, "The terminal is already bound", "该终端已绑定");
        put(codes, EndgameCode.COLUMN_BOUND, "An elevator already stands on this column", "此位置已有电梯");
        put(codes, EndgameCode.WARP_PENDING, "A station warp is pending", "空间站跃迁待执行");
        put(codes, EndgameCode.PAIR_LEVEL_CHANGED, "The body now uses another dimension", "该天体已改用其他维度");
        put(codes, EndgameCode.NOT_BOUND, "Not bound to an elevator", "未绑定电梯");
        put(codes, EndgameCode.PAIR_LIMIT, "Elevator limit reached", "已达电梯数量上限");
        put(codes, EndgameCode.ANCHOR_OWNER_NOT_MEMBER, "The anchor's owner is not in this station",
                "锚点所有者不是该空间站成员");
        put(codes, EndgameCode.NOT_ON_PLATFORM, "Stand on the platform", "请站在平台上");
        put(codes, EndgameCode.DISMOUNT_FIRST, "Dismount and drop passengers first", "请先下坐骑并放下乘客");
        put(codes, EndgameCode.RIDE_PENDING, "A ride is already waiting", "已有乘坐在等待");
        put(codes, EndgameCode.RIDE_LIMIT, "Too many rides waiting", "等待乘坐的人过多");
        put(codes, EndgameCode.RIDE_COUNTDOWN, "Ride starting: stay on the platform", "即将出发：请留在平台上");
        put(codes, EndgameCode.RIDE_CANCELLED, "Ride cancelled", "乘坐已取消");
        put(codes, EndgameCode.ARRIVAL_UNLOADED, "The arrival could not be loaded", "无法加载到达点");
        put(codes, EndgameCode.ARRIVAL_OBSTRUCTED, "The arrival platform is blocked", "到达平台被阻挡");
        return codes;
    }

    private static void put(Map<EndgameCode, String[]> codes, EndgameCode code, String english, String chinese) {
        codes.put(code, new String[] {english, chinese});
    }
}
