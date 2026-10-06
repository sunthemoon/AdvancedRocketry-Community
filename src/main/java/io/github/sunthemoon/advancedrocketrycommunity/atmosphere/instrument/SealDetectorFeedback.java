package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument;

import java.util.Locale;
import java.util.Objects;
import net.minecraft.network.chat.Component;

/** Fixed scalar enum feedback; no positions, errors, NBT or volume IDs reach chat. */
final class SealDetectorFeedback {
    private static final String PREFIX = "message.advancedrocketrycommunity.seal_detector.";

    static Component message(SealDetectorReading reading) {
        Objects.requireNonNull(reading, "reading");
        return switch (reading.outcome()) {
            case DISABLED -> Component.translatable(PREFIX + "disabled");
            case UNAVAILABLE -> Component.translatable(PREFIX + "unavailable");
            case READING -> Component.translatable(PREFIX + "reading",
                    Component.translatable(PREFIX + "boundary." + reading.boundary().name().toLowerCase(Locale.ROOT)),
                    Component.translatable(PREFIX + "supply." + reading.supply().name().toLowerCase(Locale.ROOT)));
        };
    }

    private SealDetectorFeedback() { }
}
