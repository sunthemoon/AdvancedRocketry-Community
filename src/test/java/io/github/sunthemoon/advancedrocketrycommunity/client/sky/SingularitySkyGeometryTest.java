package io.github.sunthemoon.advancedrocketrycommunity.client.sky;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ADR-066 section 7.1: one dark disc plus a bounded ring mesh of at most 512 vertices, in painting order, drawn
 * from code at the orbited body's place (unit radius at height 100, like the sun disc it replaces).
 */
class SingularitySkyGeometryTest {
    @Test void everyAcceptedSegmentCountStaysWithinTheVertexBudget() {
        int accepted = 0;
        for (int segments = SingularitySkyGeometry.MIN_SEGMENTS; segments <= SingularitySkyGeometry.MAX_SEGMENTS;
                segments += 2) {
            List<SingularitySkyGeometry.Vertex> mesh = SingularitySkyGeometry.mesh(segments);
            assertEquals(SingularitySkyGeometry.vertexCount(segments), mesh.size(), "segments " + segments);
            assertTrue(mesh.size() <= SingularitySkyGeometry.MAX_VERTICES, "segments " + segments + ": " + mesh.size());
            assertEquals(0, mesh.size() % 3, "whole triangles");
            accepted++;
        }
        assertEquals(13, accepted);
        assertEquals(512, SingularitySkyGeometry.MAX_VERTICES);
        assertEquals(480, SingularitySkyGeometry.mesh(SingularitySkyGeometry.SEGMENTS).size());
    }

    @Test void segmentCountsOutsideTheBoundOrOddAreRefusedBeforeBuilding() {
        for (int segments : new int[] {Integer.MIN_VALUE, -2, 0, 6, 7, 9, 31, 33, 34, 1_000, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> SingularitySkyGeometry.mesh(segments), "segments " + segments);
        }
    }

    @Test void verticesAreFiniteBoundedAndOnTheSkyPlane() {
        for (int segments = SingularitySkyGeometry.MIN_SEGMENTS; segments <= SingularitySkyGeometry.MAX_SEGMENTS;
                segments += 2) {
            for (var vertex : SingularitySkyGeometry.mesh(segments)) {
                assertEquals(100F, vertex.y());
                assertTrue(Float.isFinite(vertex.x()) && Float.isFinite(vertex.z()));
                assertTrue(Math.hypot(vertex.x(), vertex.z()) <= SingularitySkyGeometry.RING_OUTER + 1e-5);
                for (float channel : new float[] {vertex.red(), vertex.green(), vertex.blue(), vertex.alpha()}) {
                    assertTrue(channel >= 0 && channel <= 1, vertex.toString());
                }
            }
        }
    }

    @Test void theFarRingRimDiscAndNearRingArePaintedInThatOrder() {
        int segments = SingularitySkyGeometry.SEGMENTS;
        var mesh = SingularitySkyGeometry.mesh(segments);
        int half = segments / 2 * 6;
        var far = mesh.subList(0, half);
        var rim = mesh.subList(half, half + segments * 6);
        var disc = mesh.subList(half + segments * 6, half + segments * 9);
        var near = mesh.subList(half + segments * 9, mesh.size());
        assertEquals(half, near.size());
        // The far half sits above the disc's centre on screen (local -X), the near half below it.
        far.forEach(vertex -> assertTrue(vertex.x() <= 1e-5, "far " + vertex));
        near.forEach(vertex -> assertTrue(vertex.x() >= -1e-5, "near " + vertex));
        for (var vertex : rim) {
            double radius = Math.hypot(vertex.x(), vertex.z());
            assertTrue(radius >= 1 - 1e-5 && radius <= SingularitySkyGeometry.RIM_OUTER + 1e-5, vertex.toString());
        }
        for (var vertex : disc) {
            assertEquals(0F, vertex.red());
            assertEquals(0F, vertex.green());
            assertEquals(0F, vertex.blue());
            assertEquals(1F, vertex.alpha(), "the disc is opaque and dark");
            assertTrue(Math.hypot(vertex.x(), vertex.z()) <= 1 + 1e-5);
        }
        // The near half crosses in front of the disc: some of its vertices lie inside the disc's outline.
        assertTrue(near.stream().anyMatch(vertex -> Math.hypot(vertex.x(), vertex.z()) < 1));
    }

    @Test void theMeshIsDeterministicAndImmutable() {
        assertEquals(SingularitySkyGeometry.mesh(16), SingularitySkyGeometry.mesh(16));
        var mesh = SingularitySkyGeometry.mesh(16);
        assertThrows(UnsupportedOperationException.class, () -> mesh.add(mesh.get(0)));
    }
}
