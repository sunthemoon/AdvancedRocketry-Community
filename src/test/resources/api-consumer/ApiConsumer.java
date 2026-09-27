package consumer;

import io.github.sunthemoon.advancedrocketrycommunity.api.version.ApiCompatibility;
import io.github.sunthemoon.advancedrocketrycommunity.api.version.ApiVersion;
import io.github.sunthemoon.advancedrocketrycommunity.api.version.ApiVersions;

public final class ApiConsumer {
    public static String verify() {
        ApiVersion host = ApiVersions.current();
        if (host.major() != 1 || host.minor() != 6) {
            throw new AssertionError("Unexpected host version: " + host);
        }
        ApiCompatibility result = ApiVersions.check(host, new ApiVersion(1, 0));
        return switch (result) {
            case COMPATIBLE -> "compatible";
            case MAJOR_MISMATCH -> "major mismatch";
            case MINOR_TOO_OLD -> "minor too old";
        };
    }
}
