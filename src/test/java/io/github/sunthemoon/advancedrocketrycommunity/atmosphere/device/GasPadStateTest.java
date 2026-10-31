package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.TankData.Gas;
import org.junit.jupiter.api.Test;

class GasPadStateTest {
    @Test void emptyBankCannotFillAndFullBankCommitsOutputWithDebit() {
        var empty = new GasPadState(Gas.EMPTY, 0, GasPadState.Canister.NONE, GasPadState.Canister.EMPTY);
        assertEquals(empty, empty.process());
        for (var gas : new Gas[]{Gas.OXYGEN, Gas.HYDROGEN}) {
            var full = new GasPadState(gas, 16000, GasPadState.Canister.NONE, GasPadState.Canister.EMPTY);
            var after = full.process(); assertEquals(15000, after.amount());
            assertEquals(GasPadState.Canister.valueOf(gas.name()), after.output());
            assertEquals(after, after.process()); assertEquals(16000, full.amount());
        }
    }
    @Test void inputAndOutputConserveUnitsAndBankCannotMixGases() {
        var input = new GasPadState(Gas.EMPTY, 0, GasPadState.Canister.HYDROGEN, GasPadState.Canister.EMPTY);
        var after = input.process(); assertEquals(0, after.amount()); assertEquals(Gas.HYDROGEN, after.gas());
        assertEquals(GasPadState.Canister.EMPTY, after.input()); assertEquals(GasPadState.Canister.HYDROGEN, after.output());
        var mixed = new GasPadState(Gas.OXYGEN, 1000, GasPadState.Canister.HYDROGEN, GasPadState.Canister.NONE);
        assertEquals(mixed, mixed.process());
        var full = new GasPadState(Gas.OXYGEN, 16000, GasPadState.Canister.OXYGEN, GasPadState.Canister.NONE);
        assertEquals(full, full.process());
    }
    @Test void strictCodecRoundTripsAndRejectsUnknownFieldsAndVersions() {
        var state = new GasPadState(Gas.HYDROGEN, 15000, GasPadState.Canister.EMPTY, GasPadState.Canister.HYDROGEN);
        assertEquals(state, GasPadState.decode(state.encode()));
        var raw = state.encode(); raw.putInt("schema_version", 2); assertNull(GasPadState.decode(raw));
        raw = state.encode(); raw.putInt("unexpected", 0); assertNull(GasPadState.decode(raw));
        raw = state.encode(); raw.putLong("amount", 15); assertNull(GasPadState.decode(raw));
        assertThrows(IllegalArgumentException.class, () -> new GasPadState(Gas.EMPTY, 1, GasPadState.Canister.NONE, GasPadState.Canister.NONE));
    }
}
