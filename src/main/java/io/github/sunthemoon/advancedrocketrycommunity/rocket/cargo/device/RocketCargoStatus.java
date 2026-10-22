package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device;

import java.util.Locale;

/** W11CD section 5.4. These scalar display values never authorize a resource mutation. */
public enum RocketCargoStatus {
    ADMISSION_DISABLED, ESCROW_FULL, LEDGER_UNAVAILABLE, TRANSFER_JOURNAL_UNPROVED,
    ESCROW_PENDING, UNBOUND, STALE_BINDING, WAITING_UNLOADED, OUT_OF_RANGE, NOT_OWNER,
    ROCKET_BUSY, UNSUPPORTED_DATA, INVALID_DATA, READY, PREPARING, TRANSFERRING,
    DEVICE_EMPTY, DEVICE_FULL, NO_CARGO, CARGO_FULL, NO_CARGO_STORAGE, INCOMPATIBLE_CARGO,
    CARGO_TOO_LARGE, UNRESOLVED_LOOT, OTHER_TRANSFER_PENDING, RECEIPT_BACKED, LEGACY_ROCKET;

    public String translationKey() {
        return "message.advancedrocketrycommunity.rocket_cargo." + name().toLowerCase(Locale.ROOT);
    }

    /** Item displays share neutral statuses, but never describe their buffer as a fluid tank. */
    public String itemTranslationKey() {
        return switch (this) {
            case DEVICE_EMPTY, DEVICE_FULL, INCOMPATIBLE_CARGO ->
                    "message.advancedrocketrycommunity.rocket_item_cargo." + name().toLowerCase(Locale.ROOT);
            default -> translationKey();
        };
    }
}
