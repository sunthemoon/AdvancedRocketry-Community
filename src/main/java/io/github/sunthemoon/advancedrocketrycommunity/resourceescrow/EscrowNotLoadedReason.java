package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;

/**
 * W328 (W327 audit L1): why a startup lookup left the escrow ledger or the bay receipt index NOT_LOADED. The WARN names
 * it, so an operator can tell a file that did not load from a path whose status could not be checked.
 */
enum EscrowNotLoadedReason {
    /** The file exists, but the load-only lookup returned no root. */
    LOAD_FAILED("the file exists but did not load"),
    /** The status check failed, so neither presence nor absence is proven; no file need exist. */
    PRESENCE_UNKNOWN("the file's status could not be checked, so its absence is not proven");

    private final String description;

    EscrowNotLoadedReason(String description) { this.description = description; }

    String description() { return description; }

    /** The reason for a lookup that found no root; a proven absence is never NOT_LOADED. */
    static EscrowNotLoadedReason of(CheckedSavedDataFile.Presence presence) {
        EscrowTags.require(presence != CheckedSavedDataFile.Presence.ABSENT, "A proven absence is never NOT_LOADED");
        return presence == CheckedSavedDataFile.Presence.UNKNOWN ? PRESENCE_UNKNOWN : LOAD_FAILED;
    }
}
