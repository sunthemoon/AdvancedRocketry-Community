package io.github.sunthemoon.advancedrocketrycommunity.client.model;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicModelProjection;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.K4ModelProjection;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Full K4 sources, explicit idle parts, fallback and independent legacy-placement oracle. */
class K4ObjModelTest {
    private static final List<ObjModelFixture> K4 = List.of(ObjModelFixture.BLACK_HOLE_GENERATOR,
            ObjModelFixture.ORBITAL_LASER_DRILL, ObjModelFixture.PRECISION_ASSEMBLER,
            ObjModelFixture.RAILGUN, ObjModelFixture.ROLLING_MACHINE, ObjModelFixture.ELEVATOR_ANCHOR);
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static ObjModelFixture model(String id) { return K4.stream().filter(f -> f.id().equals(id)).findFirst().orElseThrow(); }
    private static byte[] resource(ResourceLocation location) throws Exception {
        for (String root : List.of("src/imported/v1.8/resources/assets", "src/generated/v1.8/resources/assets")) {
            Path path = Path.of(root, location.getNamespace(), location.getPath());
            if (Files.isRegularFile(path)) { return Files.readAllBytes(path); }
        }
        throw new AssertionError("Missing resource " + location);
    }
    private static ObjModelSet.Source source(Set<ResourceLocation> missing) {
        return (location, limit) -> {
            if (missing.contains(location)) { return Optional.empty(); }
            try { return Optional.of(ObjModelCheck.readBounded(new ByteArrayInputStream(resource(location)), limit, location.toString())); }
            catch (Exception failure) { throw new ObjModelException(failure.toString()); }
        };
    }
    private static JsonObject frozen(String id) throws Exception {
        try (var input = K4ObjModelTest.class.getResourceAsStream("/obj-model-fixtures/" + id + ".json")) {
            assertNotNull(input);
            return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static double[] vector(JsonArray a) { var v = new double[a.size()]; for (int i = 0; i < v.length; i++) { v[i] = a.get(i).getAsDouble(); } return v; }
    private static AABB box(JsonArray a) { var b = vector(a); return new AABB(b[0], b[1], b[2], b[3], b[4], b[5]); }
    private static List<double[]> positions(byte[] bytes) {
        var points = new ArrayList<double[]>();
        for (String line : new String(bytes, StandardCharsets.UTF_8).split("\n")) {
            if (line.startsWith("v ")) { var t = line.split("\\s+"); points.add(new double[] {
                    Double.parseDouble(t[1]), Double.parseDouble(t[2]), Double.parseDouble(t[3])}); }
        }
        return points;
    }
    private static Map<String, List<Integer>> indices(byte[] bytes) {
        var result = new LinkedHashMap<String, List<Integer>>(); String part = "";
        for (String line : new String(bytes, StandardCharsets.UTF_8).split("\n")) {
            var t = line.strip().split("\\s+");
            if (t[0].equals("o") || t[0].equals("g")) { part = t[1]; }
            if (t[0].equals("f")) { for (int i = 1; i < t.length; i++) {
                result.computeIfAbsent(part, unused -> new ArrayList<>()).add(Integer.parseInt(t[i].split("/")[0]) - 1);
            } }
        }
        return result;
    }

    @ParameterizedTest @ValueSource(strings = {"black_hole_generator", "orbital_laser_drill", "precision_assembler", "railgun", "rolling_machine", "elevator_anchor"})
    void fullSourceAndEveryFixtureSettingArePinned(String id) throws Exception {
        var f = model(id); var j = frozen(id); var checked = ObjModelSet.check(List.of(f), source(Set.of())).models().get(id);
        var summary = checked.summary(); var raw = resource(f.model());
        assertEquals(1, j.get("schema_version").getAsInt()); assertEquals(j.get("id").getAsString(), f.id());
        assertEquals(j.get("model").getAsString(), f.model().toString());
        assertEquals(j.get("fallback_texture").getAsString(), f.fallbackTexture().toString());
        assertEquals(j.get("mirror_x").getAsBoolean(), f.mirrorX()); assertEquals(j.get("flip_v").getAsBoolean(), f.flipV());
        assertEquals(j.get("scale").getAsDouble(), f.scale());
        assertEquals(j.get("model_y_rotation_degrees").getAsDouble(), f.modelYRotationDegrees());
        assertArrayEquals(vector(j.getAsJsonArray("offset_north")), new double[] {f.offsetX(), f.offsetY(), f.offsetZ()});
        assertEquals(j.get("render_type").getAsString(), f.renderType());
        assertEquals(j.get("light_cell_above_controller").getAsInt(), f.lightCellAboveController());
        var b = f.modelBounds(); assertEquals(box(j.getAsJsonArray("model_bounds")), new AABB(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()));
        assertTrue(summary.bounds().within(b, 1.0E-6));
        var pin = j.getAsJsonObject("imported"); assertEquals(pin.get("obj_sha256").getAsString(), sha(raw));
        assertEquals(pin.get("positions").getAsInt(), summary.positions()); assertEquals(pin.get("vertices").getAsInt(), summary.budgetVertices());
        var library = ObjModelCheck.libraryLocation(f.model(), summary.materialLibrary());
        var materials = ObjModelCheck.checkMtl(resource(library), id).materials();
        assertEquals(summary.materials(), materials.keySet());
        for (var maps : materials.values()) {
            var texture = ObjModelCheck.texturePath(maps.get("map_Kd"));
            assertEquals(texture, checked.textures().get(texture)); ObjModelCheck.checkPng(resource(texture), texture.toString());
        }
        var adapted = ObjModelCheck.checkObj(checked.parserInput(), id + " strict parser input");
        assertTrue(adapted.budgetVertices() <= summary.budgetVertices());
        assertArrayEquals(raw, resource(f.model()));
    }

    /** Original upstream c5cd5af6, client/render/multiblocks, disconnected/non-running/recoil-complete branches. */
    private static double[] legacy(String id, Direction front, String part, double[] p) {
        int fx = front.getStepX(), fz = front.getStepZ(); double y = 0;
        double turn = (fx == 1 ? 180 : 0) + fz * 90; double[] t;
        switch (id) {
            // RenderBlackHoleGenerator: centre at y+.5, no inner translation, different facing formula.
            case "black_hole_generator" -> { turn = (fz == 1 ? 180 : 0) - fx * 90; y = .5; t = new double[] {0,0,0}; }
            case "orbital_laser_drill" -> t = new double[] {2.5,0,4.5};
            case "precision_assembler" -> t = new double[] {-.5,0,-.5};
            case "railgun" -> t = new double[] {3,0,0};
            case "elevator_anchor" -> t = new double[] {4.5,0,.5};
            case "rolling_machine" -> {
                t = new double[] {-.5,0,-1.5};
                if (part.equals("roller_1")) { t[0] += 1.375; t[1] += .6875; }
                if (part.equals("roller_2")) { t[0] += 1.9375; t[1] += .6875; }
                if (part.equals("roller_2_upper")) { t[0] += 1.65625; t[1] += 1.125; }
            }
            default -> throw new AssertionError(id);
        }
        double r = Math.toRadians(turn), x = p[0] + t[0], z = p[2] + t[2];
        var v = new double[] {Math.cos(r)*x + Math.sin(r)*z + .5, p[1] + t[1] + y, -Math.sin(r)*x + Math.cos(r)*z + .5};
        // Same width-axis reflection about the controller as the K3 contract, never a front/back reflection.
        return front.getAxis() == Direction.Axis.Z ? new double[] {1-v[0],v[1],v[2]} : new double[] {v[0],v[1],1-v[2]};
    }

    @ParameterizedTest @ValueSource(strings = {"black_hole_generator", "orbital_laser_drill", "precision_assembler", "railgun", "rolling_machine", "elevator_anchor"})
    void everyDrawnPositionInEveryFacingEqualsTheTranscribedLegacyRenderer(String id) throws Exception {
        var f = model(id); byte[] raw = resource(f.model()); var sourcePoints = positions(raw); var sourceParts = indices(raw);
        var checked = ObjModelSet.check(List.of(f), source(Set.of())).models().get(id);
        var parserPoints = positions(checked.parserInput()); var drawn = indices(checked.parserInput());
        assertEquals(sourcePoints.size(), parserPoints.size());
        var expectedParts = id.equals("elevator_anchor") ? Set.of("Anchor") : id.equals("rolling_machine")
                ? Set.of("Hull", "roller_1", "roller_2", "roller_2_upper") : sourceParts.keySet();
        assertEquals(expectedParts, drawn.keySet()); int compared = 0;
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var pose = new PoseStack(); ClassicObjModelRenderer.place(pose, f, facing);
            assertEquals(1, pose.last().pose().determinant(), 1.0E-5); assertEquals(1, pose.last().normal().determinant(), 1.0E-5);
            for (var part : drawn.entrySet()) {
                String sourcePart = part.getKey().equals("roller_2_upper") ? "roller_2" : part.getKey();
                var originalIndices = sourceParts.get(sourcePart); assertEquals(originalIndices.size(), part.getValue().size());
                int sourceOffset = part.getKey().equals("roller_2_upper")
                        ? Collections.min(originalIndices) - Collections.min(sourceParts.get("roller_3")) : 0;
                var expectedIndices = new TreeSet<>(sourceParts.get(part.getKey().equals("roller_2_upper") ? "roller_3" : sourcePart));
                assertEquals(expectedIndices, new TreeSet<>(part.getValue()), "Mirroring may reverse winding, not position references");
                for (int i = 0; i < part.getValue().size(); i++) {
                    var p = parserPoints.get(part.getValue().get(i));
                    var placed = pose.last().pose().transformPosition(new Vector3f((float)p[0], (float)p[1], (float)p[2]));
                    var expected = legacy(id, facing, part.getKey(), sourcePoints.get(part.getValue().get(i) + sourceOffset));
                    assertArrayEquals(expected, new double[] {placed.x,placed.y,placed.z}, 1.0E-5, id + " " + facing + " " + part.getKey() + " face position " + i);
                    compared++;
                }
            }
        }
        assertTrue(compared >= 4 * 100, id + " compares too few positions");
    }

    @ParameterizedTest @ValueSource(strings = {"black_hole_generator", "orbital_laser_drill", "precision_assembler", "railgun", "rolling_machine", "elevator_anchor"})
    void renderBoxesContainDrawnMeshAndUnchangedPatternAndLightSampleIsClear(String id) throws Exception {
        var f = model(id); var j = frozen(id); var pin = j.getAsJsonObject("unchanged_pattern");
        var json = JsonParser.parseString(Files.readString(Path.of(pin.get("path").getAsString())));
        assertEquals(pin.get("canonical_sha256").getAsString(), sha(json.toString().getBytes(StandardCharsets.UTF_8)));
        var pattern = MultiblockPatternJsonCodec.decode(json.toString().getBytes(StandardCharsets.UTF_8));
        var lightCell = pattern.cells().get(pattern.controllerAnchor().add(new PatternPosition(0, f.lightCellAboveController(), 0)));
        assertTrue(lightCell == null || lightCell instanceof PatternMatcher.Air);
        var checked = ObjModelSet.check(List.of(f), source(Set.of())).models().get(id); var points = positions(checked.parserInput());
        assertTrue(K4ModelProjection.projects(f.kind()));
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var b = K4ModelProjection.bounds(id, facing, BlockPos.ZERO);
            assertEquals(box(j.getAsJsonObject("render_bounds").getAsJsonArray(facing.getName())), b);
            var pose = new PoseStack(); ClassicObjModelRenderer.place(pose, f, facing);
            for (var part : indices(checked.parserInput()).values()) { for (int index : part) {
                var p = points.get(index); var v = pose.last().pose().transformPosition(new Vector3f((float)p[0],(float)p[1],(float)p[2]));
                assertTrue(v.x >= b.minX - 1.0E-5 && v.x <= b.maxX + 1.0E-5 && v.y >= b.minY - 1.0E-5 && v.y <= b.maxY + 1.0E-5
                        && v.z >= b.minZ - 1.0E-5 && v.z <= b.maxZ + 1.0E-5, id + " mesh outside render box: " + v);
            } }
            for (var cell : pattern.cells().keySet()) {
                var p = ClassicModelProjection.transform(facing).localToWorld(cell, pattern.controllerAnchor(), new PatternPosition(0,0,0));
                var cellBox = new AABB(p.x(),p.y(),p.z(),p.x()+1,p.y()+1,p.z()+1);
                assertEquals(b, b.minmax(cellBox), id + " pattern outside render box");
            }
        }
    }

    @ParameterizedTest @ValueSource(strings = {"black_hole_generator", "orbital_laser_drill", "precision_assembler", "railgun", "rolling_machine", "elevator_anchor"})
    void explicitFallbackAndMissingModelRetainTheFailurePolicy(String id) throws Exception {
        var f = model(id); var loaded = ObjModelSet.check(List.of(f), source(Set.of())).models().get(id);
        var missing = new HashSet<>(loaded.textures().keySet());
        var fallback = ObjModelSet.check(List.of(f), source(missing)).models().get(id);
        assertTrue(fallback.textures().values().stream().allMatch(f.fallbackTexture()::equals));
        assertEquals(Set.of(id), ObjModelSet.check(List.of(f), source(Set.of(f.model()))).absent());
        missing.add(f.fallbackTexture()); assertThrows(ObjModelException.class, () -> ObjModelSet.check(List.of(f), source(missing)));
    }

    @Test void rollingUpperInstanceRetainsEverySourcePositionUvNormalAndFaceIndex() throws Exception {
        var groups = new HashMap<String,List<String>>(); var counts = new int[3]; int[] base = new int[3]; String part = "";
        for (String line : new String(resource(ObjModelFixture.ROLLING_MACHINE.model()), StandardCharsets.UTF_8).split("\n")) {
            var t = line.strip().split("\\s+");
            if (t[0].equals("o")) { part=t[1]; groups.put(part,new ArrayList<>()); base=counts.clone(); }
            int axis = List.of("v","vt","vn").indexOf(t[0]);
            if (axis >= 0) { counts[axis]++; groups.get(part).add(line); }
            if (t[0].equals("f")) {
                var face = new StringBuilder("f");
                for (int i=1;i<t.length;i++) { var triple=t[i].split("/"); face.append(' ');
                    for (int a=0;a<3;a++) { if(a>0){face.append('/');} face.append(Integer.parseInt(triple[a])-base[a]); }
                }
                groups.get(part).add(face.toString());
            }
        }
        assertEquals(groups.get("roller_2"), groups.get("roller_3")); assertEquals(102, groups.get("roller_2").size());
    }

    @Test void completeCatalogStaysWithinOriginalBudgetsIncludingHiddenElevatorTether() throws Exception {
        var set = ObjModelSet.check(ObjModelFixture.CATALOG, source(Set.of()));
        assertEquals(16, set.models().size()); assertTrue(set.absent().isEmpty()); assertEquals(35_268, set.vertices());
        assertTrue(set.vertices() < ObjModelCheck.MAX_SET_VERTICES); assertTrue(set.bytes() < ObjModelCheck.MAX_SET_BYTES);
        var elevator = set.models().get("elevator_anchor"); assertEquals(8_776, elevator.summary().budgetVertices());
        assertTrue(ObjModelCheck.checkObj(elevator.parserInput(), "idle elevator").budgetVertices() < 8_776);
    }
}
