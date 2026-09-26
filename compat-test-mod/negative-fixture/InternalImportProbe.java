import io.github.sunthemoon.advancedrocketrycommunity.api.version.ApiVersions;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapter;

public final class InternalImportProbe {
    private RocketBlockEntityAdapter forbiddenImplementation;

    public static int publicMajor() {
        return ApiVersions.current().major();
    }
}
