package io.github.sunthemoon.advancedrocketrycommunity.client.equipment;

import static org.junit.jupiter.api.Assertions.*;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class EarthbrightLightTest {
    @Test void darkColoursLiftTowardFullBrightnessKeepingTheirHue() {
        Vector3f colors = new Vector3f(0.1F, 0.05F, 0.02F);
        EarthbrightLight.brighten(colors);
        assertEquals(0.1F + (1.0F - 0.1F) * 0.7F, colors.x(), 1.0E-5F);
        assertEquals(0.05F + (0.5F - 0.05F) * 0.7F, colors.y(), 1.0E-5F);
        assertEquals(0.02F + (0.2F - 0.02F) * 0.7F, colors.z(), 1.0E-5F);
    }

    @Test void blackBrightAndInvalidColoursAreUnchanged() {
        for (Vector3f colors : new Vector3f[] {new Vector3f(0, 0, 0), new Vector3f(1, 0.5F, 0.2F),
                new Vector3f(Float.NaN, 0.1F, 0.1F), new Vector3f(0.2F, Float.POSITIVE_INFINITY, 0)}) {
            Vector3f before = new Vector3f(colors);
            EarthbrightLight.brighten(colors);
            assertEquals(before.toString(), colors.toString());
        }
    }
}
