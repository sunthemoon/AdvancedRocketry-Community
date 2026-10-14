package io.github.sunthemoon.advancedrocketrycommunity.client.sky;

import java.util.ArrayList;
import java.util.List;

/**
 * ADR-066 section 7.1: the black-hole sky mesh, drawn from code with no texture. One opaque dark disc plus a
 * bounded accretion ring and a thin photon rim, at most {@value #MAX_VERTICES} vertices for every accepted
 * segment count. Like {@code SkyGeometry.sun}, it is centred at (0, 100, 0) with a unit disc radius on the X/Z
 * plane, so the renderer scales it by the orbited body's apparent size. Depth is off in the sky, so the order of
 * the triangles is the painting order: the far half of the ring, the rim, the disc that covers the far half's
 * middle, then the near half that crosses in front of the disc.
 */
final class SingularitySkyGeometry {
    static final int MAX_VERTICES = 512;
    static final int MIN_SEGMENTS = 8;
    static final int MAX_SEGMENTS = 32;
    static final int SEGMENTS = MAX_SEGMENTS;
    /** The rim hugs the disc edge, from radius 1 to this. */
    static final double RIM_OUTER = 1.12;
    static final double RING_INNER = 1.35;
    static final double RING_OUTER = 2.4;
    /** Minor to major axis of the ring as seen: a view a little above the ring's plane. */
    static final double RING_FORESHORTENING = 0.28;
    private static final double HEIGHT = 100;

    private SingularitySkyGeometry() { }

    /** A ring and a rim quad (six vertices each) and a disc triangle per segment. */
    static int vertexCount(int segments) {
        return 15 * segments;
    }

    /**
     * {@code segments} is even and from {@value #MIN_SEGMENTS} to {@value #MAX_SEGMENTS}, so the ring splits
     * into equal far and near halves and the mesh stays within {@value #MAX_VERTICES} vertices.
     */
    static List<Vertex> mesh(int segments) {
        if (segments < MIN_SEGMENTS || segments > MAX_SEGMENTS || segments % 2 != 0) {
            throw new IllegalArgumentException("Singularity sky segments must be even and within "
                    + MIN_SEGMENTS + ".." + MAX_SEGMENTS);
        }
        var vertices = new ArrayList<Vertex>(vertexCount(segments));
        double step = Math.PI * 2 / segments;
        // The local -X half projects above the disc's centre, so it is the far side of the ring.
        ring(vertices, segments / 2, segments, step);
        for (int index = 0; index < segments; index++) {
            double left = index * step;
            double right = left + step;
            quad(vertices, rim(left, 1, 0.9), rim(right, 1, 0.9), rim(right, RIM_OUTER, 0), rim(left, RIM_OUTER, 0));
        }
        for (int index = 0; index < segments; index++) {
            double left = index * step;
            double right = left + step;
            vertices.add(new Vertex(0, (float) HEIGHT, 0, 0, 0, 0, 1));
            vertices.add(dark(left));
            vertices.add(dark(right));
        }
        ring(vertices, 0, segments / 2, step);
        return List.copyOf(vertices);
    }

    private static void ring(List<Vertex> vertices, int from, int to, double step) {
        for (int index = from; index < to; index++) {
            double left = index * step;
            double right = left + step;
            // A fixed streak pattern and a brighter approaching side; no texture.
            double streak = 0.7 + 0.3 * ((index * 7) % 5) / 4.0;
            quad(vertices, ring(left, RING_INNER, streak), ring(right, RING_INNER, streak),
                    ring(right, RING_OUTER, streak), ring(left, RING_OUTER, streak));
        }
    }

    private static Vertex ring(double angle, double radius, double streak) {
        double outer = (radius - RING_INNER) / (RING_OUTER - RING_INNER);
        double doppler = 1 + 0.3 * Math.cos(angle);
        return new Vertex((float) (Math.sin(angle) * radius * RING_FORESHORTENING), (float) HEIGHT,
                (float) (Math.cos(angle) * radius),
                channel(mix(1.0, 0.85, outer) * doppler), channel(mix(0.86, 0.32, outer) * doppler),
                channel(mix(0.62, 0.08, outer) * doppler), (float) (mix(0.9, 0, outer) * streak));
    }

    private static Vertex rim(double angle, double radius, double alpha) {
        return new Vertex((float) (Math.sin(angle) * radius), (float) HEIGHT, (float) (Math.cos(angle) * radius),
                1.0F, 0.93F, 0.8F, (float) alpha);
    }

    private static Vertex dark(double angle) {
        return new Vertex((float) Math.sin(angle), (float) HEIGHT, (float) Math.cos(angle), 0, 0, 0, 1);
    }

    private static double mix(double from, double to, double amount) {
        return from + (to - from) * amount;
    }

    private static float channel(double value) {
        return (float) Math.max(0, Math.min(1, value));
    }

    private static void quad(List<Vertex> vertices, Vertex a, Vertex b, Vertex c, Vertex d) {
        vertices.add(a); vertices.add(b); vertices.add(c);
        vertices.add(a); vertices.add(c); vertices.add(d);
    }

    record Vertex(float x, float y, float z, float red, float green, float blue, float alpha) { }
}
