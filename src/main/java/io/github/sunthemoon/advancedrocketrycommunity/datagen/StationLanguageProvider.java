package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationCreationCode;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;

public final class StationLanguageProvider extends LanguageProvider {
    private final boolean chinese;

    public StationLanguageProvider(PackOutput output, String locale) {
        super(output, AdvancedRocketryCommunity.MOD_ID + "_v070", locale);
        if (!locale.equals("en_us") && !locale.equals("zh_cn")) {
            throw new IllegalArgumentException("Unsupported locale " + locale);
        }
        chinese = locale.equals("zh_cn");
    }

    @Override
    protected void addTranslations() {
        add("item.advancedrocketrycommunity.station_deployment_kit",
                chinese ? "空间站部署组件" : "Station Deployment Kit");
        add("station.advancedrocketrycommunity.creation.details",
                chinese ? "已部署 %s（%s），可在火箭控制台选择" :
                        "Deployed %s (%s); it is now available in the rocket console");
        add("station.advancedrocketrycommunity.access.denied",
                chinese ? "用户无权修改该空间站区域" : "You may not modify this station region");
        add("body.advancedrocketrycommunity.space_station", chinese ? "空间站" : "Space Station");
        add("screen.advancedrocketrycommunity.rocket.no_stations",
                chinese ? "没有可访问的空间站" : "NO ACCESSIBLE STATIONS");
        add("screen.advancedrocketrycommunity.rocket.station_choice",
                chinese ? "空间站：%s（%s/%s）" : "STATION  %s  (%s/%s)");
        for (StationCreationCode code : StationCreationCode.values()) {
            add(code.translationKey(), chinese ? chinese(code) : english(code));
        }
    }

    private static String english(StationCreationCode code) {
        return switch (code) {
            case SUCCESS -> "Station deployment completed";
            case SERVICE_UNAVAILABLE -> "Station service is unavailable";
            case REGISTRY_BLOCKED -> "Station registry contains unsupported or invalid data";
            case OWNER_LIMIT_REACHED -> "You already own the maximum number of stations";
            case SPACE_UNAVAILABLE -> "The fixed Space Level is unavailable";
            case INVALID_SOURCE -> "Deploy a station from Earth or Moon";
            case REGION_UNAVAILABLE -> "No station region is currently available";
            case PLATFORM_BLOCKED -> "The allocated station platform footprint is occupied";
            case PERSISTENCE_FAILED -> "Station deployment was rolled back after a persistence failure";
        };
    }

    private static String chinese(StationCreationCode code) {
        return switch (code) {
            case SUCCESS -> "空间站部署完成";
            case SERVICE_UNAVAILABLE -> "空间站服务不可用";
            case REGISTRY_BLOCKED -> "空间站注册表包含不支持或无效的数据";
            case OWNER_LIMIT_REACHED -> "用户已达到空间站拥有数量上限";
            case SPACE_UNAVAILABLE -> "固定 Space Level 不可用";
            case INVALID_SOURCE -> "请从地球或月球部署空间站";
            case REGION_UNAVAILABLE -> "当前没有可用的空间站区域";
            case PLATFORM_BLOCKED -> "分配的空间站平台区域已被占用";
            case PERSISTENCE_FAILED -> "持久化失败，空间站部署已回滚";
        };
    }
}
