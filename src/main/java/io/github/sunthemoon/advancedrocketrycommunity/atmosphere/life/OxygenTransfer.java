package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;

/** Atomic whole-canister transfer; partial canisters do not exist in v0.4. */
public final class OxygenTransfer {
    private OxygenTransfer() {
    }

    public static OxygenTransferResult fillOneCanister(int oxygenUnits, int capacity) {
        if (capacity <= 0 || capacity > AtmosphereLimits.VENT_OXYGEN_CAPACITY) {
            throw new IllegalArgumentException("Invalid oxygen storage capacity");
        }
        if (oxygenUnits < 0 || oxygenUnits > capacity) {
            throw new IllegalArgumentException("Stored oxygen is outside capacity");
        }
        if (capacity - oxygenUnits < AtmosphereLimits.OXYGEN_UNITS_PER_CANISTER) {
            return new OxygenTransferResult(false, oxygenUnits, 0);
        }
        return new OxygenTransferResult(
                true,
                oxygenUnits + AtmosphereLimits.OXYGEN_UNITS_PER_CANISTER,
                1
        );
    }
}
