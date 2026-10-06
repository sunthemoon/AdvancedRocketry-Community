package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class SealDetectorReadingTest {
    @Test void knownBoundaryAndEverySupplyCodeHaveExactlyTwoEnumArguments() {
        for (var boundary : new SealDetectorReading.Boundary[] {SealDetectorReading.Boundary.SEALED, SealDetectorReading.Boundary.OPEN}) {
            for (var supply : SealDetectorReading.Supply.values()) {
                var reading = SealDetectorReading.measured(boundary, supply);
                var message = Component.Serializer.toJsonTree(SealDetectorFeedback.message(reading)).getAsJsonObject();
                assertEquals("message.advancedrocketrycommunity.seal_detector.reading", message.get("translate").getAsString());
                var arguments = message.getAsJsonArray("with");
                assertEquals(2, arguments.size());
                assertEquals("message.advancedrocketrycommunity.seal_detector.boundary." + boundary.name().toLowerCase(java.util.Locale.ROOT),
                        arguments.get(0).getAsJsonObject().get("translate").getAsString());
                assertEquals("message.advancedrocketrycommunity.seal_detector.supply." + supply.name().toLowerCase(java.util.Locale.ROOT),
                        arguments.get(1).getAsJsonObject().get("translate").getAsString());
            }
        }
    }

    @Test void disabledAndWholeUnavailableHaveNoMeasuredFieldsOrArguments() {
        for (var reading : new SealDetectorReading[] {SealDetectorReading.disabled(), SealDetectorReading.unavailable()}) {
            assertEquals(SealDetectorReading.Boundary.UNAVAILABLE, reading.boundary());
            assertEquals(SealDetectorReading.Supply.UNAVAILABLE, reading.supply());
            var json = Component.Serializer.toJsonTree(SealDetectorFeedback.message(reading)).getAsJsonObject();
            assertFalse(json.has("with"));
            assertEquals("message.advancedrocketrycommunity.seal_detector." + reading.outcome().name().toLowerCase(java.util.Locale.ROOT),
                    json.get("translate").getAsString());
        }
    }

    @Test void invalidMixedOutcomesAndNullFieldsAreRefused() {
        assertThrows(NullPointerException.class, () -> new SealDetectorReading(null, SealDetectorReading.Boundary.OPEN, SealDetectorReading.Supply.PENDING));
        assertThrows(NullPointerException.class, () -> SealDetectorReading.measured(null, SealDetectorReading.Supply.PENDING));
        assertThrows(NullPointerException.class, () -> SealDetectorReading.measured(SealDetectorReading.Boundary.OPEN, null));
        assertThrows(IllegalArgumentException.class, () -> SealDetectorReading.measured(SealDetectorReading.Boundary.UNAVAILABLE, SealDetectorReading.Supply.PENDING));
        for (var outcome : new SealDetectorReading.Outcome[] {SealDetectorReading.Outcome.DISABLED, SealDetectorReading.Outcome.UNAVAILABLE}) {
            assertThrows(IllegalArgumentException.class, () -> new SealDetectorReading(outcome, SealDetectorReading.Boundary.OPEN, SealDetectorReading.Supply.UNAVAILABLE));
            assertThrows(IllegalArgumentException.class, () -> new SealDetectorReading(outcome, SealDetectorReading.Boundary.UNAVAILABLE, SealDetectorReading.Supply.SUPPLIED));
        }
    }

    @Test void recordComponentsRetainOnlyThreeScalarEnums() {
        assertArrayEquals(new Class<?>[] {SealDetectorReading.Outcome.class, SealDetectorReading.Boundary.class, SealDetectorReading.Supply.class},
                java.util.Arrays.stream(SealDetectorReading.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getType).toArray(Class<?>[]::new));
    }
}
