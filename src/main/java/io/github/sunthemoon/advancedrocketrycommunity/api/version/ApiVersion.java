package io.github.sunthemoon.advancedrocketrycommunity.api.version;

/**
 * Public API identity, independent of mod, protocol and save-schema versions.
 *
 * @param major positive compatibility family
 * @param minor non-negative supported feature level within that family
 * @throws IllegalArgumentException if either component is outside its range
 */
public record ApiVersion(int major, int minor) {
    public ApiVersion {
        if (major <= 0 || minor < 0) {
            throw new IllegalArgumentException("API major must be positive and minor non-negative");
        }
    }
}
