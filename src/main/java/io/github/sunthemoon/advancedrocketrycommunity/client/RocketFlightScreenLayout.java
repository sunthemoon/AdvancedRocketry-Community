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
    static final int STATUS_Y = 136;
    static final int STATUS_WIDTH = 204;
    static final int STATUS_ROWS = 2;
    static final int BOARD_BUTTON_Y = 156;
    static final int BOARD_BUTTON_HEIGHT = 18;

    static BoundedTextLayout.Line stationLabel(String full, ToIntFunction<String> width) {
        return BoundedTextLayout.fit(full, STATION_BUTTON_WIDTH - 2 * BUTTON_TEXT_INSET, width);
    }

    /** The route owns its wrapped tooltip; a shortened readout must not draw a second box behind it. */
    static void renderReadoutTooltip(boolean mapMode, int mouseX, int mouseY,
                                     Runnable routeTooltip, Runnable readoutTooltip) {
        if (!mapMode && mouseX >= 22 && mouseX < 226 && mouseY >= 33 && mouseY < 43) {
            routeTooltip.run();
        } else {
            readoutTooltip.run();
        }
    }

    private RocketFlightScreenLayout() {
    }
}
