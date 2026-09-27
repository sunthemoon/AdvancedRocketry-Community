package io.github.sunthemoon.advancedrocketrycommunity.celestial.visual;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkyMathTest {
    @Test void fogNeverIncreasesEitherPlaneAndRemainsOrdered() {
        for (var profile : SkyProfiles.builtins().values()) {
            for (float far : new float[] {1, 16, 48, 128, 512, 2048}) {
                for (float near : new float[] {-8, 0, far * 0.2F, far * 0.9F}) {
                    var result = SkyMath.fog(profile, near, far);
                    assertTrue(result.near() <= near);
                    assertTrue(result.far() <= far);
                    assertTrue(result.near() < result.far());
                }
            }
        }
        var highStart = SkyProfiles.builtins().get(ModIdentity.id("moon"));
        assertEquals(new SkyMath.Fog(115.2F, 128), SkyMath.fog(highStart, 115.2F, 128));
        assertEquals(new SkyMath.Fog(0, -1), SkyMath.fog(highStart, 0, -1));
        assertEquals(new SkyMath.Fog(12, 10), SkyMath.fog(highStart, 12, 10));
        assertTrue(Float.isNaN(SkyMath.fog(highStart, Float.NaN, 100).near()));
    }

    @Test void presentationMathIsFiniteAcrossInputsAndPreservesDayNightEndpoints() {
        for (var profile : SkyProfiles.builtins().values()) {
            assertEquals(profile.dayColor(), SkyMath.skyColor(profile, 1));
            assertEquals(profile.nightColor(), SkyMath.skyColor(profile, 0));
            for (double input : new double[] {-20, 0, 0.01, 0.5, 1, 16, 100, Double.NaN, Double.POSITIVE_INFINITY}) {
                double radius = SkyMath.sunRadius(profile, input);
                assertTrue(radius >= 0.5 && radius <= 12);
                double stars = SkyMath.stars(profile, input, input);
                assertTrue(stars >= 0 && stars <= 1);
                assertTrue(SkyMath.daylight(input) >= 0 && SkyMath.daylight(input) <= 1);
            }
        }
        assertEquals(1, SkyMath.daylight(0));
        assertEquals(0, SkyMath.daylight(Math.PI));
        var mars = SkyProfiles.builtins().get(ModIdentity.id("mars"));
        assertTrue(SkyMath.stars(mars, 0, 1) > SkyMath.stars(mars, 1, 1));
        assertTrue(SkyMath.sunRadius(mars, 0.43) < SkyMath.sunRadius(mars, 1.91));
    }

    @Test void originalMeshesAreDeterministicFiniteAndWithinCombinedBudget() {
        var meshes = List.of(SkyGeometry.sphere(), SkyGeometry.horizon(), SkyGeometry.stars(),
                SkyGeometry.sun(false), SkyGeometry.sun(true));
        assertEquals(SkyGeometry.stars(), SkyGeometry.stars());
        assertEquals(512 * 6, SkyGeometry.stars().size());
        assertEquals(32 * 3, SkyGeometry.sun(false).size());
        assertEquals(5472, meshes.stream().mapToInt(List::size).sum());
        assertTrue(meshes.stream().mapToInt(List::size).sum() <= SkyGeometry.MAX_VERTICES);
        for (var mesh : meshes) {
            assertEquals(0, mesh.size() % 3);
            for (var vertex : mesh) {
                assertTrue(Float.isFinite(vertex.x()) && Float.isFinite(vertex.y()) && Float.isFinite(vertex.z()));
                assertTrue(vertex.alpha() >= 0 && vertex.alpha() <= 1);
                assertTrue(Math.abs(vertex.x()) <= 100 && Math.abs(vertex.y()) <= 100 && Math.abs(vertex.z()) <= 100);
            }
            assertThrows(UnsupportedOperationException.class, mesh::clear);
        }
    }
}
