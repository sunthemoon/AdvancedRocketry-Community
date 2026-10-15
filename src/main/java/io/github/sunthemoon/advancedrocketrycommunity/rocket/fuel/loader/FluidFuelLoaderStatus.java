package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

import java.util.Locale;

/**
 * The fluid loader's player status, in a fixed order: the refusal members, then the progress members. The text comes
 * with the data leaf; it never shows tentative credit or calls a held operation complete.
 */
public enum FluidFuelLoaderStatus {
    ADMISSION_DISABLED,
    ESCROW_FULL,
    LEDGER_UNAVAILABLE,
    TRANSFER_JOURNAL_UNPROVED,
    /** Resources are locked pending durable confirmation; its text must not claim that no fuel moved. */
    ESCROW_PENDING,
    UNBOUND,
    STALE_BINDING,
    WAITING_UNLOADED,
    OUT_OF_RANGE,
    NOT_OWNER,
    /** Also the stricter-than-ADR-065 refusal while the rocket has actual or assigned passengers or a rider. */
    ROCKET_BUSY,
    EMPTY,
    BANK_FULL,
    INCOMPATIBLE_FLUID,
    AMBIGUOUS_BANK,
    UNSUPPORTED_DATA,
    INVALID_DATA,
    READY,
    /** A queued operation whose creation baseline or L1 is not yet acknowledged, UNKNOWN included; nothing has moved. */
    PREPARING,
    /** An applied operation progressing through its proofs and L2. */
    TRANSFERRING;

    public static final String KEY_PREFIX = "message.advancedrocketrycommunity.fluid_fuel_loader.";

    public boolean refusal() { return ordinal() < READY.ordinal(); }

    public String translationKey() { return KEY_PREFIX + name().toLowerCase(Locale.ROOT); }
}
