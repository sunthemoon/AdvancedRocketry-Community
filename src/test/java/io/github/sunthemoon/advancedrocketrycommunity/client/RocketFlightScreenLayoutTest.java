package io.github.sunthemoon.advancedrocketrycommunity.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RocketFlightScreenLayoutTest {
    @Test
    void defaultFontFuelRowHasSpaceBeforeThePanelBorderAndDestinationControls() {
        int defaultFontLineHeight = 9;
        assertTrue(RocketFlightScreenLayout.PANEL_TOP < RocketFlightScreenLayout.FUEL_TEXT_Y);
        assertTrue(RocketFlightScreenLayout.FUEL_TEXT_Y + defaultFontLineHeight + 2
                < RocketFlightScreenLayout.PANEL_BOTTOM,
                "Fuel text needs padding before the panel's lower border");
        assertTrue(RocketFlightScreenLayout.PANEL_BOTTOM + 4
                <= RocketFlightScreenLayout.DESTINATION_BUTTON_Y,
                "The panel must leave room before the destination controls");
    }
}
