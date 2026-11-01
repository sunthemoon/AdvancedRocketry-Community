package io.github.sunthemoon.advancedrocketrycommunity.client.model;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicControllerBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicModelProjection;
import io.github.sunthemoon.advancedrocketrycommunity.machine.research.ResearchPatterns;
import io.github.sunthemoon.advancedrocketrycommunity.machine.solar.SolarArrayLayout;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180BeaconData;
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

/** Real K3 source bytes, closed/idle poses, bounded references and all-facing placement. */
class K3ObjModelTest {
    private static final List<ObjModelFixture> K3 = List.of(ObjModelFixture.ASTROBODY_DATA_PROCESSOR,
            ObjModelFixture.OBSERVATORY, ObjModelFixture.PLANETARY_BEACON, ObjModelFixture.SOLAR_ARRAY);
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static ObjModelFixture model(String id) { return K3.stream().filter(f -> f.id().equals(id)).findFirst().orElseThrow(); }
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
        try (var input = K3ObjModelTest.class.getResourceAsStream("/obj-model-fixtures/" + id + ".json")) {
            assertNotNull(input);
            return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static AABB box(JsonArray a) { return new AABB(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble(),
            a.get(3).getAsDouble(), a.get(4).getAsDouble(), a.get(5).getAsDouble()); }
    private static List<double[]> positions(byte[] bytes) {
        var points = new ArrayList<double[]>();
        for (String line : new String(bytes, StandardCharsets.UTF_8).split("\n")) {
            if (line.startsWith("v ")) { var t = line.split("\\s+"); points.add(new double[] {
                    Double.parseDouble(t[1]), Double.parseDouble(t[2]), Double.parseDouble(t[3])}); }
        }
        return points;
    }
    private static Map<String, List<double[]>> parts(byte[] bytes) {
        var points = positions(bytes); var result = new HashMap<String, List<double[]>>(); String part = "";
        for (String line : new String(bytes, StandardCharsets.UTF_8).split("\n")) {
            var t = line.strip().split("\\s+");
            if (t[0].equals("o") || t[0].equals("g")) { part = t[1]; }
            if (t[0].equals("f")) { for (int i = 1; i < t.length; i++) {
                result.computeIfAbsent(part, unused -> new ArrayList<>()).add(points.get(Integer.parseInt(t[i].split("/")[0]) - 1));
            } }
        }
        return result;
    }

    @ParameterizedTest @ValueSource(strings = {"astrobody_data_processor", "observatory", "planetary_beacon", "solar_array"})
    void sourceLoaderAndEveryFixtureSettingArePinned(String id) throws Exception {
        var f = model(id); var j = frozen(id); var result = ObjModelSet.check(List.of(f), source(Set.of()));
        var checked = result.models().get(id); var summary = checked.summary(); var raw = resource(f.model());
        assertEquals(1, j.get("schema_version").getAsInt()); assertEquals(j.get("id").getAsString(), f.id());
        assertEquals(j.get("model").getAsString(), f.model().toString());
        assertEquals(j.get("fallback_texture").getAsString(), f.fallbackTexture().toString());
        assertEquals(j.get("mirror_x").getAsBoolean(), f.mirrorX()); assertEquals(j.get("flip_v").getAsBoolean(), f.flipV());
        assertEquals(j.get("scale").getAsDouble(), f.scale());
        assertEquals(j.get("model_y_rotation_degrees").getAsDouble(), f.modelYRotationDegrees());
        var offset = j.getAsJsonArray("offset_north");
        assertArrayEquals(new double[] {offset.get(0).getAsDouble(), offset.get(1).getAsDouble(), offset.get(2).getAsDouble()},
                new double[] {f.offsetX(), f.offsetY(), f.offsetZ()});
        assertEquals(j.get("render_type").getAsString(), f.renderType());
        assertEquals(j.get("light_cell_above_controller").getAsInt(), f.lightCellAboveController());
        var bounds = f.modelBounds();
        assertEquals(box(j.getAsJsonArray("model_bounds")), new AABB(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ()));
        assertTrue(summary.bounds().within(bounds, 1.0E-6));
        var imported = j.getAsJsonObject("imported"); assertEquals(imported.get("obj_sha256").getAsString(), sha(raw));
        assertEquals(imported.get("positions").getAsInt(), summary.positions());
        assertEquals(imported.get("vertices").getAsInt(), summary.budgetVertices());
        assertTrue(summary.budgetVertices() < ObjModelCheck.MAX_BAKED_OBJECT_VERTICES);
        var library = ObjModelCheck.libraryLocation(f.model(), summary.materialLibrary());
        var materials = ObjModelCheck.checkMtl(resource(library), id).materials();
        for (String name : summary.materials()) {
            assertTrue(materials.containsKey(name));
            var texture = ObjModelCheck.texturePath(materials.get(name).get("map_Kd"));
            assertEquals(texture, checked.textures().get(texture));
            ObjModelCheck.checkPng(resource(texture), texture.toString());
        }
        ObjModelCheck.checkObj(checked.parserInput(), id + " strict parser input");
        assertArrayEquals(raw, resource(f.model()), "Preparing parser input must not rewrite source bytes");
    }

    @ParameterizedTest @ValueSource(strings = {"astrobody_data_processor", "observatory", "planetary_beacon", "solar_array"})
    void allFacingGeometryFitsItsExplicitProjectionAndThePoseMatchesTheFixture(String id) throws Exception {
        var f = model(id); var j = frozen(id); assertTrue(ClassicModelProjection.projects(f.kind()));
        var checked = ObjModelSet.check(List.of(f), source(Set.of())).models().get(id);
        var sourcePoints = positions(resource(f.model())); var parserPoints = positions(checked.parserInput());
        assertEquals(sourcePoints.size(), parserPoints.size());
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var expectedBox = box(j.getAsJsonObject("render_bounds").getAsJsonArray(facing.getName()));
            assertEquals(expectedBox, ClassicModelProjection.bounds(f.kind(), facing, BlockPos.ZERO));
            var pose = new PoseStack(); ClassicObjModelRenderer.place(pose, f, facing);
            assertEquals(1, pose.last().pose().determinant(), 1.0E-5);
            assertEquals(1, pose.last().normal().determinant(), 1.0E-5);
            for (var points : parts(checked.parserInput()).values()) { for (var p : points) {
                var placed = pose.last().pose().transformPosition(new Vector3f((float)p[0], (float)p[1], (float)p[2]));
                assertTrue(placed.x >= expectedBox.minX - 1.0E-5 && placed.x <= expectedBox.maxX + 1.0E-5
                        && placed.y >= expectedBox.minY - 1.0E-5 && placed.y <= expectedBox.maxY + 1.0E-5
                        && placed.z >= expectedBox.minZ - 1.0E-5 && placed.z <= expectedBox.maxZ + 1.0E-5, id + " outside render bounds: " + placed);
                double[] expected = f.toController(facing, -p[0], p[1], p[2]);
                assertEquals(expected[0], placed.x, 1.0E-5); assertEquals(expected[1], placed.y, 1.0E-5); assertEquals(expected[2], placed.z, 1.0E-5);
            } }
        }
        var footprint = ClassicModelProjection.footprint(f.kind()).orElseThrow();
        if (id.equals("solar_array")) {
            assertEquals(SolarArrayLayout.WIDTH, footprint.sizeX()); assertEquals(1, footprint.sizeY());
            assertEquals(SolarArrayLayout.DEPTH, footprint.sizeZ()); assertEquals(SolarArrayLayout.ANCHOR, footprint.anchor());
            assertEquals(j.getAsJsonObject("unchanged_layout").get("cells").getAsInt(), SolarArrayLayout.cells().size());
        } else {
            var pin = j.getAsJsonObject("unchanged_pattern"); var committed = JsonParser.parseString(Files.readString(Path.of(pin.get("path").getAsString())));
            assertEquals(pin.get("canonical_sha256").getAsString(), sha(committed.toString().getBytes(StandardCharsets.UTF_8)));
            var generated = id.equals("planetary_beacon") ? V180BeaconData.pattern() : ResearchPatterns.json(id.equals("astrobody_data_processor"));
            assertEquals(committed, generated);
            var pattern = MultiblockPatternJsonCodec.decode(generated.toString().getBytes(StandardCharsets.UTF_8));
            assertEquals(pattern.controllerAnchor(), footprint.anchor());
            assertEquals(List.of(pattern.size().x(), pattern.size().y(), pattern.size().z()), List.of(footprint.sizeX(), footprint.sizeY(), footprint.sizeZ()));
            var light = pattern.cells().get(footprint.anchor().add(new PatternPosition(0, f.lightCellAboveController(), 0)));
            assertTrue(light == null || light instanceof PatternMatcher.Air, "Light sample must be outside occupied/optional pattern cells");
        }
    }

    /** Each face's part and the zero-based position indices its faces reference, in file order. */
    private static Map<String, Set<Integer>> faceIndices(byte[] bytes) {
        var result = new LinkedHashMap<String, Set<Integer>>(); String part = "";
        for (String line : new String(bytes, StandardCharsets.UTF_8).split("\n")) {
            var t = line.strip().split("\\s+");
            if (t[0].equals("o") || t[0].equals("g")) { part = t[1]; }
            if (t[0].equals("f")) { for (int i = 1; i < t.length; i++) {
                result.computeIfAbsent(part, unused -> new TreeSet<>()).add(Integer.parseInt(t[i].split("/")[0]) - 1);
            } }
        }
        return result;
    }

    /** The parts the 1.12 renderers draw while the machine is closed or disabled; the others call renderAll(). */
    private static final Map<String, Set<String>> LEGACY_IDLE_PARTS = Map.of(
            "observatory", Set.of("Base", "CasingXMinus", "CasingXPlus"),   // RendererObservatory.java:75-79, closed branch
            "planetary_beacon", Set.of("Base", "OuterSpin", "InnerSpin"));  // RenderBeacon.java:48, 54 and 60

    /**
     * The 1.12 placement of one source position relative to the controller block's corner, transcribed from upstream
     * {@code c5cd5af62fc07cd4e0d24f06a16033f181c47c04} {@code client/render/multiblocks}: {@code glTranslated(x + .5, y, z + .5)},
     * {@code glRotatef(turn, 0, 1, 0)}, then the renderer's own translation. EnumFacing's front offsets equal Direction's steps.
     */
    private static double[] legacy(String id, Direction front, String part, double[] p) {
        int fx = front.getStepX(), fz = front.getStepZ(); double turn; double[] t;
        switch (id) {
            // RendererSolarArray.java:39-42
            case "solar_array" -> { turn = (fx == 1 ? 0 : 180) + fz * 90; t = new double[] {-0.5, 0, 0.5}; }
            // RenderAstrobodyDataProcessor.java:39-42
            case "astrobody_data_processor" -> { turn = (fx == 1 ? 180 : 0) + fz * 90; t = new double[] {-0.5, -1, -1.5}; }
            // RendererObservatory.java:49-52
            case "observatory" -> { turn = (fx == 1 ? 180 : 0) + fz * 90; t = new double[] {2, -1, 0}; }
            // RenderBeacon.java:42-45 and 50: the spin groups sit one block over and do not turn while disabled
            case "planetary_beacon" -> { turn = (fx == 1 ? 180 : 0) + fz * 90;
                t = part.equals("Base") ? new double[] {0, 0, 0} : new double[] {1, 0, 0}; }
            default -> throw new AssertionError("No legacy renderer for " + id);
        }
        double r = Math.toRadians(turn), x = p[0] + t[0], z = p[2] + t[2];
        return new double[] {Math.cos(r) * x + Math.sin(r) * z + 0.5, p[1] + t[1], -Math.sin(r) * x + Math.cos(r) * z + 0.5};
    }

    /** The modern patterns are the 1.12 structures mirrored in local x: across the pattern's width, about the controller. */
    private static double[] acrossPatternWidth(Direction facing, double[] p) {
        return facing.getAxis() == Direction.Axis.Z ? new double[] {1 - p[0], p[1], p[2]} : new double[] {p[0], p[1], 1 - p[2]};
    }

    /**
     * W236-R1 and W236-R2 (Root decision R248) pin orientation, not only containment. Every drawn source position,
     * prepared and placed by the real fixture and pose, must equal its 1.12 renderer's placement mirrored across the
     * pattern's width. A model turned or mirrored front to back stays inside its render box but fails here.
     */
    @ParameterizedTest @ValueSource(strings = {"astrobody_data_processor", "observatory", "planetary_beacon", "solar_array"})
    void everyFacingEqualsTheLegacyRendererMirroredAcrossThePatternWidth(String id) throws Exception {
        var f = model(id); byte[] raw = resource(f.model());
        var checked = ObjModelSet.check(List.of(f), source(Set.of())).models().get(id);
        var sourcePoints = positions(raw); var parserPoints = positions(checked.parserInput());
        assertEquals(sourcePoints.size(), parserPoints.size());
        var drawn = faceIndices(checked.parserInput());
        assertEquals(LEGACY_IDLE_PARTS.getOrDefault(id, faceIndices(raw).keySet()), drawn.keySet(), id + " draws the legacy idle parts");
        int compared = 0;
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var pose = new PoseStack(); ClassicObjModelRenderer.place(pose, f, facing);
            for (var part : drawn.entrySet()) { for (int index : part.getValue()) {
                var p = parserPoints.get(index);
                var placed = pose.last().pose().transformPosition(new Vector3f((float) p[0], (float) p[1], (float) p[2]));
                var expected = acrossPatternWidth(facing, legacy(id, facing, part.getKey(), sourcePoints.get(index)));
                assertArrayEquals(expected, new double[] {placed.x, placed.y, placed.z}, 1.0E-5,
                        id + " " + facing.getName() + " " + part.getKey() + " position " + (index + 1) + " leaves the legacy orientation");
                compared++;
            } }
        }
        // The observatory's closed shell is the smallest drawn set: 33 distinct positions in each facing.
        assertTrue(compared >= 4 * 33, id + " compares too few positions: " + compared);
    }

    @ParameterizedTest @ValueSource(strings = {"astrobody_data_processor", "observatory", "planetary_beacon", "solar_array"})
    void explicitFallbackAndMissingModelKeepTheExistingFailurePolicy(String id) throws Exception {
        var f = model(id); var loaded = ObjModelSet.check(List.of(f), source(Set.of())).models().get(id);
        var missing = new HashSet<>(loaded.textures().keySet());
        var fallback = ObjModelSet.check(List.of(f), source(missing)).models().get(id);
        assertTrue(fallback.textures().values().stream().allMatch(f.fallbackTexture()::equals));
        var absent = ObjModelSet.check(List.of(f), source(Set.of(f.model())));
        assertEquals(Set.of(id), absent.absent()); assertTrue(absent.models().isEmpty());
        var broken = new HashSet<>(missing); broken.add(f.fallbackTexture());
        assertThrows(ObjModelException.class, () -> ObjModelSet.check(List.of(f), source(broken)));
    }

    @ParameterizedTest @ValueSource(strings = {"astrobody_data_processor", "observatory", "planetary_beacon", "solar_array"})
    void idleGeometryMatchesLegacyClosedOrStationaryPartsWithoutChangingTheSource(String id) throws Exception {
        var f = model(id); byte[] raw = resource(f.model()); byte[] idle = ObjModelIdleGeometry.prepare(f, raw);
        var before = parts(raw); var after = parts(idle); var pin = frozen(id).getAsJsonObject("idle");
        if (pin.has("hidden")) { for (var part : pin.getAsJsonArray("hidden")) {
            assertTrue(before.containsKey(part.getAsString())); assertFalse(after.containsKey(part.getAsString()));
        } }
        if (pin.has("translated")) { for (var part : pin.getAsJsonArray("translated")) {
            var a = before.get(part.getAsString()); var b = after.get(part.getAsString()); assertEquals(a.size(), b.size());
            for (int i = 0; i < a.size(); i++) { for (int axis = 0; axis < 3; axis++) {
                assertEquals(a.get(i)[axis] + pin.getAsJsonArray("translation").get(axis).getAsDouble(), b.get(i)[axis], 1.0E-9);
            } }
        } }
        if (pin.size() == 0) { assertArrayEquals(raw, idle); }
        assertArrayEquals(raw, resource(f.model()));
    }

    @Test void completeCatalogRemainsWithinUnchangedBudgets() throws Exception {
        var result = ObjModelSet.check(ObjModelFixture.CATALOG, source(Set.of()));
        assertEquals(15, result.models().size()); assertTrue(result.absent().isEmpty());
        assertEquals(34_300, result.vertices());
        assertTrue(result.vertices() < ObjModelCheck.MAX_SET_VERTICES); assertTrue(result.bytes() < ObjModelCheck.MAX_SET_BYTES);
    }
    @Test void beaconTextureIsExactlyTheExistingNewControllerArt() throws Exception {
        assertArrayEquals(resource(ObjModelFixture.PLANETARY_BEACON.fallbackTexture()),
                resource(new ResourceLocation("advancedrocketrycommunity", "textures/models/beacon_new.png")));
    }
}
