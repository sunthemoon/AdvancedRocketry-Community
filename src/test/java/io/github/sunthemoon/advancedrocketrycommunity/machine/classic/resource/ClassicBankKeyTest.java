package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKey;
import org.junit.jupiter.api.Test;

class ClassicBankKeyTest {
    @Test void everyKindRoundTripsFullSignedCoordinatesWithoutPackingOrAliases() {
        int[] coordinates = {Integer.MIN_VALUE, -30_000_000, -1, 0, 1, 30_000_000, Integer.MAX_VALUE};
        for (ClassicBankKind kind : ClassicBankKind.values()) {
            for (int x : coordinates) { for (int y : coordinates) { for (int z : coordinates) {
                ClassicBankKey key = new ClassicBankKey(kind, x, y, z);
                assertEquals(key, ClassicBankKey.parse(key.channel()));
                assertTrue(key.channel().length() <= ProcessResourceKey.MAX_CHANNEL_CHARS);
            } } }
        }
    }

    @Test void noncanonicalCoordinatesUnknownKindsAndOversizedChannelsRefuse() {
        for (String channel : new String[]{"item_input.+1.0.0", "item_input.01.0.0", "item_input.-0.0.0",
                "item_input.1..0", "item_input.2147483648.0.0", "item_input.-2147483649.0.0",
                "item_input.1.0.0.extra", "item_input.1.0", "ITEM_INPUT.1.0.0", "power_input.1.0.0",
                "item_input. 1.0.0", "item_input.\u0661.0.0", "item_input." + "1".repeat(65) + ".0.0"}) {
            assertThrows(IllegalArgumentException.class, () -> ClassicBankKey.parse(channel), channel);
        }
        assertThrows(NullPointerException.class, () -> ClassicBankKey.parse(null));
        assertThrows(NullPointerException.class, () -> new ClassicBankKey(null, 0, 0, 0));
    }

    @Test void exactRoleAndAbsolutePositionAreIdentityNotGenerationOrRotation() {
        ClassicBankKey input = new ClassicBankKey(ClassicBankKind.ITEM_INPUT, -10, 64, 30);
        assertNotEquals(input, new ClassicBankKey(ClassicBankKind.ITEM_OUTPUT, -10, 64, 30));
        assertNotEquals(input, new ClassicBankKey(ClassicBankKind.FLUID_INPUT, -10, 64, 30));
        assertNotEquals(input, new ClassicBankKey(ClassicBankKind.ITEM_INPUT, 30, 64, 10));
        assertEquals(input, ClassicBankKey.parse("item_input.-10.64.30"));
    }
}
