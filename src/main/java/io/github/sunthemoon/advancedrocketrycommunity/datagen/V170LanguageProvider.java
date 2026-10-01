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
        return codes;
    }

    private static void put(Map<EndgameCode, String[]> codes, EndgameCode code, String english, String chinese) {
        codes.put(code, new String[] {english, chinese});
    }
}
