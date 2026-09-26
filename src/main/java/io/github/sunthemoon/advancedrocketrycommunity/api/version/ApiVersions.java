package io.github.sunthemoon.advancedrocketrycommunity.api.version;

import java.util.Objects;

/** Pure, thread-safe API metadata queries, usable on either physical side. */
public final class ApiVersions {
    private static final ApiVersion CURRENT = new ApiVersion(1, 1);

    private ApiVersions() {
    }

    /** Returns the host API version without an inlined consumer-side numeric constant. */
    public static ApiVersion current() {
        return CURRENT;
    }

    /**
     * Checks an exact required major and minimum required minor, major first.
     *
     * @throws NullPointerException if either argument is null
     */
    public static ApiCompatibility check(ApiVersion offered, ApiVersion required) {
        Objects.requireNonNull(offered, "offered");
        Objects.requireNonNull(required, "required");
        if (offered.major() != required.major()) {
            return ApiCompatibility.MAJOR_MISMATCH;
        }
        return offered.minor() < required.minor()
                ? ApiCompatibility.MINOR_TOO_OLD : ApiCompatibility.COMPATIBLE;
    }
}
