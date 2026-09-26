package io.github.sunthemoon.advancedrocketrycommunity.api.version;

/** Metadata compatibility; not a guarantee of provider behavior or loader compatibility. */
public enum ApiCompatibility {
    /** The offered major matches and its minor satisfies the requirement. */
    COMPATIBLE,
    /** The offered and required API compatibility families differ. */
    MAJOR_MISMATCH,
    /** The major matches, but the offered minor lacks required API features. */
    MINOR_TOO_OLD
}
