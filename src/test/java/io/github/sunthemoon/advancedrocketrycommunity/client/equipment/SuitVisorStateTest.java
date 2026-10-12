package io.github.sunthemoon.advancedrocketrycommunity.client.equipment;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SuitVisorStateTest {
    @Test void aNewConnectionHasNoEquipmentAuthorityEvenWithBothVisors() {
        var state = new SuitVisorState();
        state.observe(true, true);
        assertFalse(state.enabled());
        assertFalse(state.antiFog());
        assertFalse(state.earthbright());
        state.authorize(false);
        state.observe(true, true);
        assertFalse(state.antiFog());
        assertFalse(state.earthbright());
    }

    @Test void disablingImmediatelyClearsBothEffectsWhileStillEquipped() {
        var state = new SuitVisorState();
        state.authorize(true);
        state.observe(true, true);
        assertTrue(state.antiFog());
        assertTrue(state.earthbright());
        state.authorize(false);
        assertFalse(state.antiFog());
        assertFalse(state.earthbright());
        state.observe(true, true);
        assertFalse(state.antiFog());
        assertFalse(state.earthbright());
    }

    @Test void reloadAndEquipmentInvalidationClearEffectsButLogoutAlsoDropsAuthority() {
        var state = new SuitVisorState();
        state.authorize(true);
        state.observe(true, true);
        state.clear();
        assertTrue(state.enabled());
        assertFalse(state.antiFog());
        assertFalse(state.earthbright());
        state.observe(true, false);
        assertTrue(state.antiFog());
        assertFalse(state.earthbright());
        state.observe(false, true);
        assertFalse(state.antiFog());
        assertTrue(state.earthbright());
        state.observe(false, false);
        assertFalse(state.antiFog());
        assertFalse(state.earthbright());
        state.authorize(false);
        assertFalse(state.enabled());
    }
}
