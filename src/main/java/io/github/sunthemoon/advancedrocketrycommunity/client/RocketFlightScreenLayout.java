package io.github.sunthemoon.advancedrocketrycommunity.client;

import java.util.function.ToIntFunction;

/** Logical-pixel bounds shared by the console's panel and text rendering. */
final class RocketFlightScreenLayout {
    static final int PANEL_TOP = 27;
    static final int PANEL_BOTTOM = 84;
    static final int FUEL_TEXT_Y = 72;
    static final int DESTINATION_BUTTON_Y = 89;
    static final int STATION_BUTTON_WIDTH = 98;
    static final int BUTTON_TEXT_INSET = 6;

    static BoundedTextLayout.Line stationLabel(String full, ToIntFunction<String> width) {
        return BoundedTextLayout.fit(full, STATION_BUTTON_WIDTH - 2 * BUTTON_TEXT_INSET, width);
    }

    private RocketFlightScreenLayout() {
    }
}
