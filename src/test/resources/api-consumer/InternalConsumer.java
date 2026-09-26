package consumer;

import io.github.sunthemoon.advancedrocketrycommunity.api.version.ApiVersions;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;

public final class InternalConsumer {
    private AtmosphereLimits internal;

    public static int hostMajor() {
        return ApiVersions.current().major();
    }
}
