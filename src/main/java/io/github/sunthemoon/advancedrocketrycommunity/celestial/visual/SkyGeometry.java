package io.github.sunthemoon.advancedrocketrycommunity.celestial.visual;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Original finite triangle meshes, generated only when a render cache is built. */
public final class SkyGeometry {
    public static final int STAR_COUNT = 512;
    public static final int SEGMENTS = 32;
    public static final int MAX_VERTICES = 6_000;

    private SkyGeometry() { }

    public static List<Vertex> sphere() {
        var vertices = new ArrayList<Vertex>();
        for (int band = 0; band < 12; band++) {
            double low = -Math.PI / 2 + band * Math.PI / 12;
            double high = low + Math.PI / 12;
            for (int section = 0; section < 24; section++) {
                double left = section * Math.PI * 2 / 24;
                double right = left + Math.PI * 2 / 24;
                quad(vertices, polar(low, left, 100, 1), polar(low, right, 100, 1),
                        polar(high, right, 100, 1), polar(high, left, 100, 1));
            }
        }
        return List.copyOf(vertices);
    }

    public static List<Vertex> horizon() {
        var vertices = new ArrayList<Vertex>();
        for (int section = 0; section < SEGMENTS; section++) {
            double left = section * Math.PI * 2 / SEGMENTS;
            double right = left + Math.PI * 2 / SEGMENTS;
            for (int half : new int[] {-1, 1}) {
                quad(vertices, polar(0, left, 95, 0.85), polar(0, right, 95, 0.85),
                        polar(half * Math.PI / 5, right, 95, 0), polar(half * Math.PI / 5, left, 95, 0));
            }
        }
        return List.copyOf(vertices);
    }

    public static List<Vertex> stars() {
        var vertices = new ArrayList<Vertex>();
        var random = new Random(0x41524345534B59L);
        double goldenAngle = Math.PI * (3 - Math.sqrt(5));
        for (int index = 0; index < STAR_COUNT; index++) {
            double y = 1 - 2 * (index + 0.5) / STAR_COUNT;
            double longitude = index * goldenAngle + random.nextDouble() * 0.12;
            double latitude = Math.asin(y);
            double size = 0.001 + random.nextDouble() * 0.002;
            double alpha = 0.4 + random.nextDouble() * 0.6;
            quad(vertices, polar(latitude - size, longitude - size, 90, alpha),
                    polar(latitude - size, longitude + size, 90, alpha),
                    polar(latitude + size, longitude + size, 90, alpha),
                    polar(latitude + size, longitude - size, 90, alpha));
        }
        return List.copyOf(vertices);
    }

    /** Unit radius on the X/Z plane; callers scale angular size around +Y. */
    public static List<Vertex> sun(boolean halo) {
        var vertices = new ArrayList<Vertex>();
        for (int index = 0; index < SEGMENTS; index++) {
            double left = index * Math.PI * 2 / SEGMENTS;
            double right = left + Math.PI * 2 / SEGMENTS;
            if (halo) {
                quad(vertices, disc(left, 1, 0.35), disc(right, 1, 0.35),
                        disc(right, 2.5, 0), disc(left, 2.5, 0));
            } else {
                vertices.add(new Vertex(0, 100, 0, 1));
                vertices.add(disc(left, 1, 1));
                vertices.add(disc(right, 1, 1));
            }
        }
        return List.copyOf(vertices);
    }

    private static Vertex disc(double angle, double radius, double alpha) {
        return new Vertex((float) (Math.cos(angle) * radius), 100,
                (float) (Math.sin(angle) * radius), (float) alpha);
    }

    private static Vertex polar(double latitude, double longitude, double radius, double alpha) {
        return new Vertex((float) (Math.cos(latitude) * Math.cos(longitude) * radius),
                (float) (Math.sin(latitude) * radius),
                (float) (Math.cos(latitude) * Math.sin(longitude) * radius), (float) alpha);
    }

    private static void quad(List<Vertex> vertices, Vertex a, Vertex b, Vertex c, Vertex d) {
        vertices.add(a); vertices.add(b); vertices.add(c);
        vertices.add(a); vertices.add(c); vertices.add(d);
    }

    public record Vertex(float x, float y, float z, float alpha) { }
}
