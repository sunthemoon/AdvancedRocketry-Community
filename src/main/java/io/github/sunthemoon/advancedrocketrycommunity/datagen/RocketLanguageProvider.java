package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;

public final class RocketLanguageProvider extends LanguageProvider {
    private final String locale;

    public RocketLanguageProvider(PackOutput output, String locale) {
        super(output, AdvancedRocketryCommunity.MOD_ID + "_v050", locale);
        if (!locale.equals("en_us") && !locale.equals("zh_cn")) {
            throw new IllegalArgumentException("Unsupported locale " + locale);
        }
        this.locale = locale;
    }

    @Override
    protected void addTranslations() {
        boolean chinese = locale.equals("zh_cn");
        add("block.advancedrocketrycommunity.rocket_assembler", chinese ? "火箭组装机" : "Rocket Assembler");
        add("block.advancedrocketrycommunity.rocket_motor", chinese ? "火箭发动机" : "Rocket Motor");
        add("block.advancedrocketrycommunity.rocket_fuel_tank", chinese ? "火箭燃料箱" : "Rocket Fuel Tank");
        add("block.advancedrocketrycommunity.rocket_seat", chinese ? "火箭座椅" : "Rocket Seat");
        add("block.advancedrocketrycommunity.guidance_computer", chinese ? "导航计算机" : "Guidance Computer");
        add("entity.advancedrocketrycommunity.rocket", chinese ? "组装火箭" : "Assembled Rocket");
        add(
                "message.advancedrocketrycommunity.rocket.service_unavailable",
                chinese ? "火箭服务尚未就绪" : "Rocket service is not available"
        );
        add(
                "message.advancedrocketrycommunity.rocket.scan_started",
                chinese ? "开始扫描组装机上方的已加载结构" : "Scanning the loaded structure above the assembler"
        );
        add(
                "message.advancedrocketrycommunity.rocket.stats",
                chinese
                        ? "%s：方块 %s，质量 %s，推力 %s，燃料 %s，座位 %s"
                        : "%s: blocks %s, mass %s, thrust %s, fuel %s, seats %s"
        );
        for (RocketValidationCode code : RocketValidationCode.values()) {
            add(code.translationKey(), chinese ? chinese(code) : english(code));
        }
    }

    private static String english(RocketValidationCode code) {
        return switch (code) {
            case SUCCESS -> "Success";
            case SCAN_IN_PROGRESS -> "Scanning structure";
            case EMPTY_STRUCTURE -> "No movable structure found";
            case TOO_MANY_BLOCKS -> "Rocket exceeds the block limit";
            case BOUNDING_VOLUME_EXCEEDED -> "Rocket bounding volume is too large";
            case TOO_MANY_PALETTE_ENTRIES -> "Rocket uses too many block states";
            case TOO_MANY_BLOCK_ENTITIES -> "Rocket contains too many block entities";
            case BLOCK_ENTITY_DATA_TOO_LARGE -> "Block entity data exceeds the limit";
            case SNAPSHOT_DATA_TOO_LARGE -> "Rocket snapshot exceeds the data limit";
            case DUPLICATE_BLOCK_POSITION -> "Rocket snapshot contains a duplicate position";
            case POSITION_OVERFLOW -> "Rocket coordinate calculation overflowed";
            case INVALID_BLOCK_STATE -> "Rocket contains an invalid block state";
            case INVALID_BLOCK_ENTITY_DATA -> "Rocket contains invalid block entity data";
            case STATS_MISMATCH -> "Rocket statistics do not match its structure";
            case HASH_MISMATCH -> "Rocket snapshot hash does not match";
            case UNSUPPORTED_SCHEMA -> "Saved data comes from an unsupported schema";
            case MALFORMED_SNAPSHOT -> "Rocket snapshot is malformed";
            case MISSING_ENGINE -> "Rocket requires at least one motor";
            case MISSING_SEAT -> "Rocket requires at least one seat";
            case MISSING_GUIDANCE -> "Rocket requires one guidance computer";
            case INSUFFICIENT_THRUST -> "Rocket thrust is below its mass";
            case SCAN_BUDGET_EXCEEDED -> "Rocket scan exceeded its fixed budget";
            case UNLOADED_CHUNK -> "Rocket touches an unloaded chunk";
            case FORBIDDEN_BLOCK -> "Rocket contains a forbidden block";
            case BLOCK_NOT_MOVABLE -> "Selected block is not rocket-movable";
            case UNSUPPORTED_BLOCK_ENTITY -> "Rocket contains an unsupported block entity";
            case REGION_BUSY -> "Rocket region is busy";
            case OPERATION_LEDGER_FULL -> "Rocket operation queue is full";
            case WORLD_CHANGED -> "World changed after validation";
            case EXTRACTION_FAILED -> "Rocket block extraction failed";
            case SPAWN_FAILED -> "Rocket entity could not be created";
            case ROLLBACK_FAILED -> "Rocket rollback needs recovery";
            case TARGET_OCCUPIED -> "Rocket disassembly target is occupied";
            case ENTITY_STATE_INVALID -> "Rocket or assembler state is invalid";
            case REQUEST_REPLAYED -> "Duplicate rocket request was rejected";
            case OUT_OF_RANGE -> "Rocket interaction is out of range";
            case UNAUTHORIZED -> "Player is not authorized for this rocket";
        };
    }

    private static String chinese(RocketValidationCode code) {
        return switch (code) {
            case SUCCESS -> "成功";
            case SCAN_IN_PROGRESS -> "正在扫描结构";
            case EMPTY_STRUCTURE -> "未找到可移动结构";
            case TOO_MANY_BLOCKS -> "火箭超过方块数量上限";
            case BOUNDING_VOLUME_EXCEEDED -> "火箭包围盒体积过大";
            case TOO_MANY_PALETTE_ENTRIES -> "火箭使用了过多方块状态";
            case TOO_MANY_BLOCK_ENTITIES -> "火箭包含过多方块实体";
            case BLOCK_ENTITY_DATA_TOO_LARGE -> "方块实体数据超过上限";
            case SNAPSHOT_DATA_TOO_LARGE -> "火箭快照数据超过上限";
            case DUPLICATE_BLOCK_POSITION -> "火箭快照包含重复位置";
            case POSITION_OVERFLOW -> "火箭坐标计算溢出";
            case INVALID_BLOCK_STATE -> "火箭包含无效方块状态";
            case INVALID_BLOCK_ENTITY_DATA -> "火箭包含无效方块实体数据";
            case STATS_MISMATCH -> "火箭统计与结构不一致";
            case HASH_MISMATCH -> "火箭快照哈希不匹配";
            case UNSUPPORTED_SCHEMA -> "存档数据来自不支持的 schema";
            case MALFORMED_SNAPSHOT -> "火箭快照格式错误";
            case MISSING_ENGINE -> "火箭至少需要一个发动机";
            case MISSING_SEAT -> "火箭至少需要一个座椅";
            case MISSING_GUIDANCE -> "火箭需要一个导航计算机";
            case INSUFFICIENT_THRUST -> "火箭推力低于质量";
            case SCAN_BUDGET_EXCEEDED -> "火箭扫描超过固定预算";
            case UNLOADED_CHUNK -> "火箭接触了未加载区块";
            case FORBIDDEN_BLOCK -> "火箭包含禁用方块";
            case BLOCK_NOT_MOVABLE -> "所选方块不可作为火箭移动";
            case UNSUPPORTED_BLOCK_ENTITY -> "火箭包含不支持的方块实体";
            case REGION_BUSY -> "火箭区域正忙";
            case OPERATION_LEDGER_FULL -> "火箭操作队列已满";
            case WORLD_CHANGED -> "验证后世界发生变化";
            case EXTRACTION_FAILED -> "提取火箭方块失败";
            case SPAWN_FAILED -> "无法创建火箭实体";
            case ROLLBACK_FAILED -> "火箭回滚需要恢复";
            case TARGET_OCCUPIED -> "火箭拆解目标区域被占用";
            case ENTITY_STATE_INVALID -> "火箭或组装机状态无效";
            case REQUEST_REPLAYED -> "已拒绝重复火箭请求";
            case OUT_OF_RANGE -> "火箭交互超出距离";
            case UNAUTHORIZED -> "玩家无权操作这枚火箭";
        };
    }
}
