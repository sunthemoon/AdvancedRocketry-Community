package io.github.sunthemoon.advancedrocketrycommunity.api.environment;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class EnvironmentSnapshotTest {
    private static final ResourceLocation ID = new ResourceLocation("test", "body");
    private static final AtmosphereProfile AIR = new AtmosphereProfile(1, true, 288, ID);

    @Test
    void immutableRecordsRetainConfiguredValuesAndIdentity() {
        var surface = surface(1, false, AIR);
        assertEquals(ID, surface.bodyId());
        assertEquals(EnvironmentSnapshot.Locus.SURFACE, surface.locus());
        assertTrue(surface.instanceId().isEmpty());
        assertEquals(surface, surface(1, false, AIR));
        assertEquals(288, surface.atmosphere().orElseThrow().temperatureKelvin());
        UUID stationId = UUID.randomUUID();
        var station = new EnvironmentSnapshot(ID, EnvironmentSnapshot.Locus.STATION_ORBIT,
                Optional.of(stationId), 10, false, Optional.empty());
        assertEquals(Optional.of(stationId), station.instanceId());
        assertEquals(10, station.gravityMultiplier());
        assertTrue(station.atmosphere().isEmpty(), "Non-vacuum alone does not imply breathable station air");
    }

    @Test
    void finiteClosedNumericBoundsAreRequired() {
        for (double invalid : new double[]{-0.001, 10.001, Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new AtmosphereProfile(invalid, false, 1, ID));
            assertThrows(IllegalArgumentException.class, () -> surface(invalid, false, AIR));
        }
        for (double invalid : new double[]{-0.001, 2_000.001, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new AtmosphereProfile(1, true, invalid, ID));
        }
        assertEquals(0, new AtmosphereProfile(0, false, 0, ID).pressure());
        assertEquals(2_000, new AtmosphereProfile(10, true, 2_000, ID).temperatureKelvin());
    }

    @Test
    void surfaceAndStationShapesCannotInventMissingData() {
        assertThrows(IllegalArgumentException.class, () -> new AtmosphereProfile(0, true, 1, ID));
        assertThrows(IllegalArgumentException.class, () -> surface(1, true, AIR));
        assertThrows(IllegalArgumentException.class,
                () -> surface(1, false, new AtmosphereProfile(0, false, 1, ID)));
        assertThrows(IllegalArgumentException.class, () -> new EnvironmentSnapshot(ID,
                EnvironmentSnapshot.Locus.SURFACE, Optional.empty(), 1, true, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new EnvironmentSnapshot(ID,
                EnvironmentSnapshot.Locus.SURFACE, Optional.of(UUID.randomUUID()), 1, false, Optional.of(AIR)));
        assertThrows(IllegalArgumentException.class, () -> new EnvironmentSnapshot(ID,
                EnvironmentSnapshot.Locus.STATION_ORBIT, Optional.empty(), 0, true, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new EnvironmentSnapshot(ID,
                EnvironmentSnapshot.Locus.STATION_ORBIT, Optional.of(UUID.randomUUID()), 1, false, Optional.of(AIR)));
    }

    @Test
    void bothResourceIdentifiersHaveTheSameLengthBound() {
        ResourceLocation longest = new ResourceLocation("a", "b".repeat(126));
        ResourceLocation tooLong = new ResourceLocation("a", "b".repeat(127));
        var profile = new AtmosphereProfile(1, true, 1, longest);
        assertEquals(longest, new EnvironmentSnapshot(longest, EnvironmentSnapshot.Locus.SURFACE,
                Optional.empty(), 1, false, Optional.of(profile)).bodyId());
        assertThrows(IllegalArgumentException.class, () -> new AtmosphereProfile(1, true, 1, tooLong));
        assertThrows(IllegalArgumentException.class, () -> new EnvironmentSnapshot(tooLong,
                EnvironmentSnapshot.Locus.SURFACE, Optional.empty(), 1, false, Optional.of(AIR)));
    }

    @Test
    void nullValuesAndMissingHandleFailExplicitly() {
        assertThrows(NullPointerException.class, () -> new AtmosphereProfile(1, true, 1, null));
        assertThrows(NullPointerException.class, () -> new EnvironmentSnapshot(null,
                EnvironmentSnapshot.Locus.SURFACE, Optional.empty(), 1, false, Optional.of(AIR)));
        assertThrows(NullPointerException.class, () -> new EnvironmentSnapshot(ID,
                null, Optional.empty(), 1, false, Optional.of(AIR)));
        assertThrows(NullPointerException.class, () -> new EnvironmentSnapshot(ID,
                EnvironmentSnapshot.Locus.SURFACE, null, 1, false, Optional.of(AIR)));
        assertThrows(NullPointerException.class, () -> new EnvironmentSnapshot(ID,
                EnvironmentSnapshot.Locus.SURFACE, Optional.empty(), 1, false, null));
        assertThrows(NullPointerException.class, () -> new ServerEnvironmentReadyEvent(null));
        EnvironmentQueries handle = (level, position) -> Optional.empty();
        assertSame(handle, new ServerEnvironmentReadyEvent(handle).queries());
        assertFalse(new ServerEnvironmentReadyEvent(handle).isCancelable());
    }

    private static EnvironmentSnapshot surface(double gravity, boolean vacuum, AtmosphereProfile profile) {
        return new EnvironmentSnapshot(ID, EnvironmentSnapshot.Locus.SURFACE,
                Optional.empty(), gravity, vacuum, Optional.of(profile));
    }
}
