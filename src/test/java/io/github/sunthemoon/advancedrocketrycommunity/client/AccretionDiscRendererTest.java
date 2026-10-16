package io.github.sunthemoon.advancedrocketrycommunity.client;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class AccretionDiscRendererTest {
    @Test void bothWindingsKeepTheOriginalGradientAndStayWithinTheBudget() {
        List<Vertex> vertices = new ArrayList<>();
        float[] position = new float[3];
        int[] color = new int[4];
        VertexConsumer consumer = (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                new Class<?>[]{VertexConsumer.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "vertex" -> {
                            assertInstanceOf(Matrix4f.class, args[0]);
                            for (int i = 0; i < 3; i++) { position[i] = (Float) args[i + 1]; }
                        }
                        case "color" -> {
                            for (int i = 0; i < 4; i++) { color[i] = (Integer) args[i]; }
                        }
                        case "endVertex" -> vertices.add(new Vertex(position[0], position[1], position[2],
                                color[0], color[1], color[2], color[3]));
                        default -> fail("Unexpected vertex operation: " + method.getName());
                    }
                    return method.getReturnType() == void.class ? null : proxy;
                });
        for (int i = 0; i < AccretionDiscRenderer.SEGMENTS; i++) {
            AccretionDiscRenderer.quad(consumer, new Matrix4f(), Math.PI * 2 * i / AccretionDiscRenderer.SEGMENTS,
                    Math.PI * 2 * (i + 1) / AccretionDiscRenderer.SEGMENTS, 70 + i % 6 * 12);
        }
        assertEquals(384, vertices.size());
        assertTrue(vertices.size() <= 512);
        for (int i = 0; i < vertices.size(); i += 8) {
            for (int j = 0; j < 4; j++) { assertEquals(vertices.get(i + j), vertices.get(i + 7 - j)); }
            assertTrue(normalY(vertices.get(i), vertices.get(i + 1), vertices.get(i + 2)) < 0);
            assertTrue(normalY(vertices.get(i + 4), vertices.get(i + 5), vertices.get(i + 6)) > 0);
            assertEquals(0, vertices.get(i + 1).alpha());
            assertEquals(0, vertices.get(i + 2).alpha());
            assertEquals(70 + (i / 8) % 6 * 12, vertices.get(i).alpha());
            assertEquals(210, vertices.get(i).green());
            assertEquals(120, vertices.get(i + 1).green());
            assertEquals(1.2, Math.hypot(vertices.get(i).x(), vertices.get(i).z()), 0.00001);
            assertEquals(2.6, Math.hypot(vertices.get(i + 1).x(), vertices.get(i + 1).z()), 0.00001);
        }
    }

    private static float normalY(Vertex a, Vertex b, Vertex c) {
        return (b.z() - a.z()) * (c.x() - a.x()) - (b.x() - a.x()) * (c.z() - a.z());
    }

    private record Vertex(float x, float y, float z, int red, int green, int blue, int alpha) { }
}
