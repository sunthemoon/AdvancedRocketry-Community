package io.github.sunthemoon.advancedrocketrycommunity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModIdentityTest {
    @Test
    void modIdMatchesForgeRequirements() {
        assertTrue(ModIdentity.MOD_ID.matches("[a-z][a-z0-9_]{1,63}"));
        assertEquals("advancedrocketrycommunity", ModIdentity.MOD_ID);
    }

    @Test
    void displayNameMatchesApprovedIdentity() {
        assertEquals("Advanced Rocketry: Community Edition", ModIdentity.DISPLAY_NAME);
    }
}
