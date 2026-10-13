package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.SleepRespawnRules.RespawnOutcome;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.SleepRespawnRules.SleepRefusal;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.SleepRespawnService;
import java.util.LinkedHashMap;
import java.util.Map;

/** ADR-066 section 3.3 sleep refusals and planet-respawn fallbacks, delegated to the existing bilingual provider. */
public final class V180SleepLanguage {
    private V180SleepLanguage() { }

    public static Map<String, String> translations(boolean chinese) {
        Map<String, String> entries = new LinkedHashMap<>();
        for (SleepRefusal refusal : SleepRefusal.values()) {
            if (refusal != SleepRefusal.NONE) {
                entries.put(SleepRespawnService.sleepKey(refusal), sleep(refusal, chinese));
            }
        }
        for (RespawnOutcome outcome : RespawnOutcome.values()) {
            if (!outcome.accepted()) {
                entries.put(SleepRespawnService.respawnKey(outcome), respawn(outcome, chinese));
            }
        }
        return entries;
    }

    private static String sleep(SleepRefusal refusal, boolean chinese) {
        return switch (refusal) {
            case UNAVAILABLE -> chinese ? "无法在此睡觉：无法检测这里的空气。" : "You can't sleep here: the air here can't be checked.";
            case STATION_UNAVAILABLE -> chinese ? "无法在此睡觉：空间站登记暂不可用。"
                    : "You can't sleep here: the station registry is unavailable.";
            case STATION_DENIED -> chinese ? "无法在此睡觉：你没有访问此空间站的权限。"
                    : "You can't sleep here: you may not visit this station.";
            case PENDING -> chinese ? "暂时无法在此睡觉：仍在检测这里的空气。" : "You can't sleep here yet: the air is still being checked.";
            case UNBREATHABLE -> chinese ? "无法在此睡觉：这里的空气不可呼吸。" : "You can't sleep here: the air isn't breathable.";
            case NONE -> throw new IllegalArgumentException("An admitted sleeper has no message");
        };
    }

    private static String respawn(RespawnOutcome outcome, boolean chinese) {
        String reason = switch (outcome) {
            case DISABLED -> chinese ? "此服务器未启用星球重生" : "planetary respawn is disabled on this server";
            case MISSING_BODY -> chinese ? "重生点所在的世界已不存在" : "the world of your respawn point is no longer available";
            case UNLOADED -> chinese ? "重生点所在区域未加载" : "your respawn point is not loaded";
            case NO_SPAWN_BLOCK -> chinese ? "重生点没有床或可用的重生锚" : "your respawn point has no bed or usable respawn anchor";
            case OBSTRUCTED -> chinese ? "重生点的床被阻挡" : "your bed is obstructed";
            case HAZARD -> chinese ? "重生点被方块或液体阻挡" : "your respawn point is blocked by a solid block or a liquid";
            case STATION_UNAVAILABLE -> chinese ? "空间站登记暂不可用" : "the station registry is unavailable";
            case STATION_DENIED -> chinese ? "你没有访问该空间站的权限" : "you may not visit the station at your respawn point";
            case PENDING -> chinese ? "重生点的空气仍在检测中" : "the air at your respawn point is still being checked";
            case UNBREATHABLE -> chinese ? "重生点的空气不可呼吸" : "the air at your respawn point isn't breathable";
            case ACCEPT -> throw new IllegalArgumentException("An accepted respawn has no message");
        };
        return chinese ? "未使用你在其他星球的重生点（" + reason + "），已在主世界重生；重生点仍保留。"
                : "Your off-world respawn point was not used (" + reason
                        + "), so you respawned in the Overworld. The respawn point is kept.";
    }
}
