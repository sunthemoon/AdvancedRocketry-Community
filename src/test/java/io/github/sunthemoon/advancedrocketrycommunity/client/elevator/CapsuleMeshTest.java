package io.github.sunthemoon.advancedrocketrycommunity.client.elevator;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sunthemoon.advancedrocketrycommunity.client.elevator.CapsuleMesh.Corner;
import io.github.sunthemoon.advancedrocketrycommunity.client.elevator.CapsuleMesh.Face;
import io.github.sunthemoon.advancedrocketrycommunity.client.model.ObjModelException;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Root decision R263: the legacy Capsule group, its texture and its transform, pinned against the imported sources. */
class CapsuleMeshTest {
    private static final Path ASSETS = Path.of("src/imported/v1.8/resources/assets/advancedrocketrycommunity");
    /** entity_cutout's shader discards a texel whose alpha is below 0.1, that is 25 of 255 or less. */
    private static final int CUTOUT_ALPHA = 26;

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static byte[] obj() throws Exception { return Files.readAllBytes(ASSETS.resolve("models/spaceelevator.obj")); }

    private static CapsuleMesh mesh() throws Exception { return CapsuleMesh.read(obj(), "spaceelevator.obj"); }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    /** An independent reading of the source's Capsule triangles: (position, texture coordinate, normal) per corner. */
    private static List<float[][]> sourceCapsuleTriangles(byte[] bytes) {
        var v = new ArrayList<float[]>(); var vt = new ArrayList<float[]>(); var vn = new ArrayList<float[]>();
        var triangles = new ArrayList<float[][]>(); String part = "";
        for (String line : new String(bytes, StandardCharsets.UTF_8).split("\n")) {
            var t = line.strip().split("\\s+");
            switch (t[0]) {
                case "o", "g" -> part = t[1];
                case "v" -> v.add(new float[] {Float.parseFloat(t[1]), Float.parseFloat(t[2]), Float.parseFloat(t[3])});
                case "vt" -> vt.add(new float[] {Float.parseFloat(t[1]), Float.parseFloat(t[2])});
                case "vn" -> vn.add(new float[] {Float.parseFloat(t[1]), Float.parseFloat(t[2]), Float.parseFloat(t[3])});
                case "f" -> {
                    if (!part.equals("Capsule")) { break; }
                    var corners = new float[3][];
                    for (int i = 0; i < 3; i++) {
                        var index = t[i + 1].split("/");
                        var p = v.get(Integer.parseInt(index[0]) - 1); var uv = vt.get(Integer.parseInt(index[1]) - 1);
                        var n = vn.get(Integer.parseInt(index[2]) - 1);
                        corners[i] = new float[] {p[0], p[1], p[2], uv[0], uv[1], n[0], n[1], n[2]};
                    }
                    assertEquals(4, t.length, "the imported Capsule group has only triangles");
                    triangles.add(corners);
                }
                default -> { }
            }
        }
        return triangles;
    }

    @Test void approvedTextureAndTheK4CapsuleGroupArePinned() throws Exception {
        assertEquals("advancedrocketrycommunity:textures/models/spaceelevatorcapsule.png", ElevatorCapsuleClient.TEXTURE.toString());
        assertEquals("12c6ccc1aaed6d7135321d6299cb292d9da4ecd09cde462f33797b931b5dfcaa",
                sha(Files.readAllBytes(ASSETS.resolve("textures/models/spaceelevatorcapsule.png"))));
        // The same imported K4 OBJ the elevator_anchor fixture pins; nothing is copied or rewritten.
        assertEquals("advancedrocketrycommunity:models/spaceelevator.obj", CapsuleResources.MODEL.toString());
        try (var input = CapsuleMeshTest.class.getResourceAsStream("/obj-model-fixtures/elevator_anchor.json")) {
            assertNotNull(input);
            var pin = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonObject("imported").get("obj_sha256").getAsString();
            assertEquals(pin, sha(obj()));
        }
        var mesh = mesh();
        var source = sourceCapsuleTriangles(obj());
        assertEquals(104, source.size());
        assertEquals(104, mesh.faces().size());
        assertEquals(416, mesh.vertices());
        var positions = new HashSet<List<Float>>();
        for (int i = 0; i < source.size(); i++) {
            Face face = mesh.faces().get(i);
            assertEquals(face.c(), face.d(), "a triangle repeats its last corner");
            var drawn = List.of(face.a(), face.b(), face.c());
            for (int k = 0; k < 3; k++) {
                float[] s = source.get(i)[k]; Corner c = drawn.get(k);
                float length = (float) Math.sqrt(s[5] * s[5] + s[6] * s[6] + s[7] * s[7]);
                assertArrayEquals(new float[] {s[0], s[1], s[2], s[3], 1 - s[4], s[5] / length, s[6] / length, s[7] / length},
                        new float[] {c.x(), c.y(), c.z(), c.u(), c.v(), c.nx(), c.ny(), c.nz()}, 1.0E-6F,
                        "face " + i + " corner " + k + ": source position, flipped V and unit normal");
                positions.add(List.of(c.x(), c.y(), c.z()));
            }
        }
        assertEquals(60, positions.size());
        var b = mesh.bounds();
        assertArrayEquals(new double[] {-1.500131, -1, -1.499863, 1.499863, 3, 2.375},
                new double[] {b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()}, 1.0E-6);
        assertTrue(b.within(CapsuleMesh.MODEL_BOUNDS, 0));
        assertEquals(mesh.faces(), mesh().faces());
    }

    @Test void drawnTexelsAreOpaqueWhereTheLegacyMeshIsSolid() throws Exception {
        BufferedImage image = ImageIO.read(ASSETS.resolve("textures/models/spaceelevatorcapsule.png").toFile());
        var failures = new ArrayList<String>();
        var unmapped = new ArrayList<Face>();
        var mapped = new ArrayList<Face>();
        int texels = 0;
        for (int i = 0; i < mesh().faces().size(); i++) {
            Face face = mesh().faces().get(i);
            if (unmappedDefault(face)) { unmapped.add(face); continue; }
            mapped.add(face);
            var sampled = sampledTexels(face, image.getWidth(), image.getHeight());
            assertFalse(sampled.isEmpty());
            for (int[] texel : sampled) {
                texels++;
                int alpha = image.getRGB(texel[0], texel[1]) >>> 24;
                if (alpha < CUTOUT_ALPHA) { failures.add("face " + i + " texel " + texel[0] + "," + texel[1] + " alpha " + alpha); }
            }
        }
        assertEquals(List.of(), failures, "every mapped face is drawn whole by the cutout");
        assertEquals(98, mapped.size());
        assertEquals(26_102, texels, "texel centres inside the 98 mapped UV triangles, plus their corners");
        // Six contact faces keep the exporter's unmapped default UVs: the backs of the rail and of its two boxes.
        // Each lies against an opaque mapped face of the opposite orientation, so it is never seen.
        assertEquals(6, unmapped.size());
        for (Face face : unmapped) {
            assertEquals(-1, face.a().nz(), 1.0E-6);
            double z = face.a().z();
            var supports = mapped.stream().filter(other -> other.a().nz() > 0.999
                    && other.corners().stream().allMatch(c -> Math.abs(c.z() - z) <= 2.0E-4)).toList();
            assertFalse(supports.isEmpty(), "contact face at z " + z);
            double cx = (face.a().x() + face.b().x() + face.c().x()) / 3, cy = (face.a().y() + face.b().y() + face.c().y()) / 3;
            for (double[] point : List.of(new double[] {face.a().x(), face.a().y()}, new double[] {face.b().x(), face.b().y()},
                    new double[] {face.c().x(), face.c().y()}, new double[] {cx, cy})) {
                assertTrue(supports.stream().anyMatch(other -> inside(point[0], point[1], other.a().x(), other.a().y(),
                        other.b().x(), other.b().y(), other.c().x(), other.c().y(), 1.0E-5)),
                        "contact face point " + point[0] + "," + point[1] + " at z " + z + " rests on an opaque face");
            }
        }
    }

    private static boolean unmappedDefault(Face face) {
        var uv = new HashSet<List<Float>>();
        for (Corner c : List.of(face.a(), face.b(), face.c())) { uv.add(List.of(c.u(), c.v())); }
        return uv.equals(new HashSet<>(List.of(List.of(0F, 1F), List.of(1F, 1F), List.of(1F, 0F))));
    }

    /** Texels whose centres fall inside the UV triangle, plus each corner's texel nudged toward the centroid. */
    private static List<int[]> sampledTexels(Face face, int width, int height) {
        double[][] uv = new double[3][];
        var corners = List.of(face.a(), face.b(), face.c());
        for (int k = 0; k < 3; k++) { uv[k] = new double[] {corners.get(k).u() * width, corners.get(k).v() * height}; }
        double cx = (uv[0][0] + uv[1][0] + uv[2][0]) / 3, cy = (uv[0][1] + uv[1][1] + uv[2][1]) / 3;
        var result = new ArrayList<int[]>();
        int lowX = (int) Math.floor(Math.min(uv[0][0], Math.min(uv[1][0], uv[2][0])));
        int highX = (int) Math.ceil(Math.max(uv[0][0], Math.max(uv[1][0], uv[2][0])));
        int lowY = (int) Math.floor(Math.min(uv[0][1], Math.min(uv[1][1], uv[2][1])));
        int highY = (int) Math.ceil(Math.max(uv[0][1], Math.max(uv[1][1], uv[2][1])));
        for (int y = Math.max(0, lowY); y < Math.min(height, highY); y++) {
            for (int x = Math.max(0, lowX); x < Math.min(width, highX); x++) {
                if (inside(x + 0.5, y + 0.5, uv[0][0], uv[0][1], uv[1][0], uv[1][1], uv[2][0], uv[2][1], 0)) {
                    result.add(new int[] {x, y});
                }
            }
        }
        for (double[] corner : uv) {
            double x = corner[0] + (cx - corner[0]) * 1.0E-3, y = corner[1] + (cy - corner[1]) * 1.0E-3;
            result.add(new int[] {Math.min(width - 1, (int) x), Math.min(height - 1, (int) y)});
        }
        return result;
    }

    private static boolean inside(double px, double py, double ax, double ay, double bx, double by, double cx, double cy,
                                  double tolerance) {
        double d1 = (px - bx) * (ay - by) - (ax - bx) * (py - by);
        double d2 = (px - cx) * (by - cy) - (bx - cx) * (py - cy);
        double d3 = (px - ax) * (cy - ay) - (cx - ax) * (py - ay);
        boolean negative = d1 < -tolerance || d2 < -tolerance || d3 < -tolerance;
        boolean positive = d1 > tolerance || d2 > tolerance || d3 > tolerance;
        return !(negative && positive);
    }

    /** TileSpaceElevator at c5cd5af6: the capsule's rotationYaw by the controller's front. */
    private static float legacyYaw(Direction front) {
        return switch (front) { case EAST -> 180; case SOUTH -> 90; case NORTH -> 270; default -> 0; };
    }

    /** RenderElevatorCapsule.doRender at c5cd5af6: glTranslated(x, y + 1, z), then glRotated(entityYaw, 0, 1, 0). */
    private static double[] legacy(double[] entity, float yaw, Corner c) {
        double r = Math.toRadians(yaw);
        return new double[] {entity[0] + Math.cos(r) * c.x() + Math.sin(r) * c.z(), entity[1] + 1 + c.y(),
                entity[2] - Math.sin(r) * c.x() + Math.cos(r) * c.z()};
    }

    @Test void placementIsTheTranscribedLegacyCapsuleTransform() throws Exception {
        assertEquals(legacyYaw(Direction.NORTH), ElevatorCapsuleClient.CAPSULE_YAW_DEGREES);
        var mesh = mesh();
        int compared = 0;
        for (BlockPos endpoint : List.of(new BlockPos(0, 64, 0), new BlockPos(-2_410_007, 71, 1_234), new BlockPos(17, -40, -9))) {
            // The capsule entity's modern counterpart: the platform centre on top of the endpoint block.
            double[] entity = {endpoint.getX() + 0.5, endpoint.getY() + 1, endpoint.getZ() + 0.5};
            Vec3 camera = new Vec3(entity[0] + 3.25, entity[1] + 1.5, entity[2] - 2.75);
            var pose = new PoseStack();
            ElevatorCapsuleClient.place(pose, endpoint, camera);
            assertEquals(1, pose.last().pose().determinant(), 1.0E-5);
            assertEquals(1, pose.last().normal().determinant(), 1.0E-5);
            var box = ElevatorCapsuleClient.capsuleBox(mesh, endpoint);
            for (Face face : mesh.faces()) {
                for (Corner c : face.corners()) {
                    var placed = pose.last().pose().transformPosition(new Vector3f(c.x(), c.y(), c.z()));
                    double[] expected = legacy(entity, legacyYaw(Direction.NORTH), c);
                    assertArrayEquals(new double[] {expected[0] - camera.x, expected[1] - camera.y, expected[2] - camera.z},
                            new double[] {placed.x, placed.y, placed.z}, 1.0E-4);
                    assertTrue(box.contains(expected[0], expected[1], expected[2]), "capsule box holds " + List.of(expected[0], expected[1], expected[2]));
                    compared++;
                }
            }
            // The legacy doorway (local -x) faces north, and the floor of the shell rests on the platform's top face.
            assertEquals(entity[2] - 1.5, legacy(entity, ElevatorCapsuleClient.CAPSULE_YAW_DEGREES, new Corner(-1.5F, 0, 0, 0, 0, 0, 1, 0))[2], 1.0E-9);
            assertEquals(entity[1], box.minY, 2.0E-3);
            assertEquals(entity[1] + 4, box.maxY, 2.0E-3);
        }
        assertEquals(3 * 416, compared);
    }

    @Test void ridersStaySeenThroughTheOpenDoorwayAsLegacyAllowed() throws Exception {
        // Legacy drew Door only while the capsule moved; a pending ride is stationary, so the doorway stays open.
        var mesh = mesh();
        for (double y : new double[] {-0.5, 0, 0.5, 1.5}) {
            double[] origin = {0, y, 0};
            assertFalse(hits(mesh, origin, new double[] {-1, 0, 0}), "the doorway is open at local y " + y);
            for (double[] wall : List.of(new double[] {1, 0, 0}, new double[] {0, 0, 1}, new double[] {0, 0, -1},
                    new double[] {0.7071, 0, 0.7071}, new double[] {-0.7071, 0, 0.7071}, new double[] {0.7071, 0, -0.7071},
                    new double[] {-0.7071, 0, -0.7071})) {
                assertTrue(hits(mesh, origin, wall), "a wall at local y " + y + " toward " + List.of(wall[0], wall[2]));
            }
        }
    }

    /** Whether a ray from {@code origin} meets any drawn face (Moller-Trumbore, both sides). */
    private static boolean hits(CapsuleMesh mesh, double[] origin, double[] direction) {
        for (Face face : mesh.faces()) {
            double[] a = {face.a().x(), face.a().y(), face.a().z()}, b = {face.b().x(), face.b().y(), face.b().z()};
            double[] c = {face.c().x(), face.c().y(), face.c().z()};
            double[] e1 = minus(b, a), e2 = minus(c, a), p = cross(direction, e2);
            double det = dot(e1, p);
            if (Math.abs(det) < 1.0E-12) { continue; }
            double[] s = minus(origin, a);
            double u = dot(s, p) / det;
            double[] q = cross(s, e1);
            double v = dot(direction, q) / det, t = dot(e2, q) / det;
            if (u >= 0 && v >= 0 && u + v <= 1 && t > 0) { return true; }
        }
        return false;
    }

    private static double[] minus(double[] a, double[] b) { return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
    private static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    private static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static byte[] text(String value) { return value.getBytes(StandardCharsets.UTF_8); }

    @Test void anAbsentMalformedOversizedOrUnmappedGroupIsRefused() throws Exception {
        String source = new String(obj(), StandardCharsets.UTF_8);
        var refused = new ArrayList<byte[]>();
        refused.add(text(source.replace("o Capsule", "o Pod")));
        refused.add(text(source.replace("v -0.621264 -1.000000 -1.499863", "v -0.621264 -1.000000 -9.499863")));
        refused.add(text(source + "foo 1\n"));  // the unchanged strict check still runs on the whole file
        refused.add(text("mtllib a.mtl\no Capsule\nv 0 0 0\nv 1 0 0\nv 0 1 0\nvt 0 0\nusemtl m\nf 1/1 2/1 3/1\n"));
        var many = new StringBuilder("mtllib a.mtl\no Capsule\nv 0 0 0\nv 1 0 0\nv 0 1 0\nvt 0 0\nvn 0 0 1\nusemtl m\n");
        for (int i = 0; i <= CapsuleMesh.MAX_FACES; i++) { many.append("f 1/1/1 2/1/1 3/1/1\n"); }
        refused.add(text(many.toString()));
        for (byte[] bytes : refused) {
            assertThrows(ObjModelException.class, () -> CapsuleMesh.read(bytes, "refused"));
        }
        var exactly = new StringBuilder("mtllib a.mtl\no Capsule\nv 0 0 0\nv 1 0 0\nv 0 1 0\nvt 0 0\nvn 0 0 1\nusemtl m\n");
        for (int i = 0; i < CapsuleMesh.MAX_FACES; i++) { exactly.append("f 1/1/1 2/1/1 3/1/1\n"); }
        assertEquals(4 * CapsuleMesh.MAX_FACES, CapsuleMesh.read(text(exactly.toString()), "largest").vertices());
    }
}
