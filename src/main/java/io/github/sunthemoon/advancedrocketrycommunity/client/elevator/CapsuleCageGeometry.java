package io.github.sunthemoon.advancedrocketrycommunity.client.elevator;

import java.util.ArrayList;
import java.util.List;

/** Fixed texture strips on the existing stationary cage, never a moving passenger entity. */
final class CapsuleCageGeometry {
    static final int SIDES = 12, QUADS = 28, VERTICES = QUADS * 4;
    static final double RADIUS = 0.65, HALF_WIDTH = 0.02;
    record Point(double x, double y, double z, float u, float v) { }
    record Strip(Point a, Point b, Point c, Point d, float nx, float nz) { }

    private CapsuleCageGeometry() { }

    static List<Strip> strips(double height) {
        if (!Double.isFinite(height) || height < 0.5 || height > 3) {
            throw new IllegalArgumentException("Capsule cage height outside 0.5..3");
        }
        var strips = new ArrayList<Strip>(QUADS);
        for (int side = 0; side < SIDES; side++) {
            double a = side * Math.PI * 2 / SIDES, b = (side + 1) * Math.PI * 2 / SIDES;
            double x1 = Math.cos(a) * RADIUS, z1 = Math.sin(a) * RADIUS;
            double x2 = Math.cos(b) * RADIUS, z2 = Math.sin(b) * RADIUS;
            float u1 = side / (float) SIDES, u2 = (side + 1) / (float) SIDES;
            float nx = (float) Math.cos((a + b) / 2), nz = (float) Math.sin((a + b) / 2);
            for (double y : new double[] {-0.05 + HALF_WIDTH, height - HALF_WIDTH}) {
                strips.add(new Strip(new Point(x1,y-HALF_WIDTH,z1,u1,0), new Point(x2,y-HALF_WIDTH,z2,u2,0),
                        new Point(x2,y+HALF_WIDTH,z2,u2,0.125F), new Point(x1,y+HALF_WIDTH,z1,u1,0.125F), nx,nz));
            }
            if (side % 3 == 0) {
                double dx = -Math.sin(a) * HALF_WIDTH, dz = Math.cos(a) * HALF_WIDTH;
                strips.add(new Strip(new Point(x1-dx,-0.05,z1-dz,0.5F,1), new Point(x1+dx,-0.05,z1+dz,0.53125F,1),
                        new Point(x1+dx,height,z1+dz,0.53125F,0), new Point(x1-dx,height,z1-dz,0.5F,0),
                        (float)Math.cos(a),(float)Math.sin(a)));
            }
        }
        return List.copyOf(strips);
    }
}
