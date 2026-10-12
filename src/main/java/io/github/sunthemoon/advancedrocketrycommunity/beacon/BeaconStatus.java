package io.github.sunthemoon.advancedrocketrycommunity.beacon;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.Locale;

/** What a beacon tells its owner when used; each has one stable translation key. */
public enum BeaconStatus {
    ACTIVE, DISABLED, PENDING, UNFORMED, UNLOADED, NO_PATTERN, NO_OWNER, INVALID_DATA, INDEX_UNAVAILABLE, DUPLICATE,
    BODY_UNAVAILABLE, LIMIT;

    public String translationKey() {
        return "message." + ModIdentity.MOD_ID + ".beacon.status." + name().toLowerCase(Locale.ROOT);
    }
}
