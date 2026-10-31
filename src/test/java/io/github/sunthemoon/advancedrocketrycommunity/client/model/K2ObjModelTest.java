package io.github.sunthemoon.advancedrocketrycommunity.client.model;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180ArcCuttingData;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180C16cMachineData;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180ClassicMachineData;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicControllerBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicModelProjection;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** K2 is a closed four-model addition to K1, not an expansion to the etcher or later batches. */
class K2ObjModelTest {
    private static final Path IMPORTED = Path.of("src/imported/v1.8/resources/assets");
    private static final Path GENERATED = Path.of("src/generated/v1.8/resources/assets");
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static ObjModelFixture model(String kind) {
        return ObjModelFixture.CATALOG.stream().filter(f -> f.kind().equals(ModIdentity.id(kind))).findFirst().orElseThrow();
    }

    private static JsonObject frozen(String kind) throws Exception {
        try (var stream = K2ObjModelTest.class.getResourceAsStream("/obj-model-fixtures/" + kind + ".json")) {
            assertNotNull(stream);
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static byte[] resource(ResourceLocation location) throws Exception {
        for (var root : List.of(IMPORTED, GENERATED)) {
            var path = root.resolve(location.getNamespace()).resolve(location.getPath());
            if (Files.isRegularFile(path)) { return Files.readAllBytes(path); }
        }
        throw new AssertionError("Resource not found: " + location);
    }

    private static ObjModelSet.Source source(Set<ResourceLocation> missing) {
        return (location, limit) -> {
            if (missing.contains(location)) { return Optional.empty(); }
            try {
                return Optional.of(ObjModelCheck.readBounded(new ByteArrayInputStream(resource(location)), limit, location.toString()));
            } catch (Exception failure) { throw new ObjModelException(failure.toString()); }
        };
    }

    private static AABB box(JsonArray a) {
        return new AABB(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble(),
                a.get(3).getAsDouble(), a.get(4).getAsDouble(), a.get(5).getAsDouble());
    }

    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static JsonObject generatedPattern(String kind) {
        return switch (kind) {
            case "lathe" -> V180ClassicMachineData.lathePattern();
            case "cutting_machine" -> V180ArcCuttingData.cuttingPattern();
            default -> V180C16cMachineData.pattern(kind);
        };
    }

    /** Face-referenced positions only: an omitted idle part retains its OBJ indices but contributes no geometry. */
    private static Map<String, List<double[]>> visibleParts(byte[] parserInput) {
        var positions = new ArrayList<double[]>();
        var parts = new HashMap<String, List<double[]>>();
        String part = "";
        for (String line : new String(parserInput, StandardCharsets.UTF_8).split("\n")) {
            String[] t = line.strip().split("\\s+");
            if (t[0].equals("v")) { positions.add(new double[] {Double.parseDouble(t[1]), Double.parseDouble(t[2]), Double.parseDouble(t[3])}); }
            if (t[0].equals("o") || t[0].equals("g")) { part = t[1]; }
            if (t[0].equals("f")) {
                for (int i = 1; i < t.length; i++) {
                    parts.computeIfAbsent(part, unused -> new ArrayList<>()).add(positions.get(Integer.parseInt(t[i].split("/")[0]) - 1));
                }
            }
        }
        return parts;
    }

    @Test void catalogContainsExactlyK1AndTheFourK2Kinds() {
        assertEquals(Set.of("centrifuge", "chemical_reactor", "crystallizer", "cutting_machine", "lathe"),
                ObjModelFixture.CATALOG.stream().map(f -> f.kind().getPath()).collect(Collectors.toSet()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"chemical_reactor", "crystallizer", "cutting_machine", "lathe"})
    void eachK2KindHasItsOwnCompiledModelFallbackAndProjection(String kind) {
        var fixture = model(kind);
        assertTrue(ClassicModelProjection.projects(fixture.kind()), kind);
        assertEquals(ModIdentity.id("textures/block/" + kind + ".png"), fixture.fallbackTexture());
        assertEquals(1, fixture.scale());
        assertTrue(fixture.mirrorX());
    }

    @ParameterizedTest
    @ValueSource(strings = {"chemical_reactor", "crystallizer", "cutting_machine", "lathe"})
    void catalogAndOriginalBytesMatchEachFrozenFixture(String kind) throws Exception {
        var f = model(kind); var json = frozen(kind);
        assertEquals(1, json.get("schema_version").getAsInt());
        assertEquals(json.get("id").getAsString(), f.id());
        assertEquals(json.get("kind").getAsString(), f.kind().toString());
        assertEquals(json.get("model").getAsString(), f.model().toString());
        assertEquals(json.get("fallback_texture").getAsString(), f.fallbackTexture().toString());
        var orientation = json.getAsJsonObject("orientation");
        assertEquals(orientation.get("mirror_x").getAsBoolean(), f.mirrorX());
        assertEquals(orientation.get("model_y_rotation_degrees").getAsDouble(), f.modelYRotationDegrees());
        for (var facing : Direction.Plane.HORIZONTAL) {
            assertEquals(orientation.getAsJsonObject("y_rotation_degrees").get(facing.getName()).getAsFloat(), ObjModelFixture.yRotationDegrees(facing));
        }
        assertEquals(json.get("scale").getAsDouble(), f.scale());
        var offset = json.getAsJsonArray("offset_north");
        assertArrayEquals(new double[] {offset.get(0).getAsDouble(), offset.get(1).getAsDouble(), offset.get(2).getAsDouble()},
                new double[] {f.offsetX(), f.offsetY(), f.offsetZ()});
        var rendering = json.getAsJsonObject("rendering");
        assertEquals(rendering.get("render_type").getAsString(), f.renderType());
        assertEquals(rendering.get("flip_v").getAsBoolean(), f.flipV());
        assertEquals(rendering.get("shade_quads").getAsBoolean(), ObjModelFixture.SHADE_QUADS);
        assertEquals(rendering.get("emissive_ambient").getAsBoolean(), ObjModelFixture.EMISSIVE_AMBIENT);
        assertEquals(rendering.get("automatic_culling").getAsBoolean(), ObjModelFixture.AUTOMATIC_CULLING);
        assertEquals(rendering.get("light_cell_above_controller").getAsInt(), f.lightCellAboveController());
        byte[] bytes = resource(f.model());
        var summary = ObjModelCheck.checkObj(bytes, kind, ObjModelIdleGeometry.hiddenParts(f));
        var bounds = f.modelBounds();
        assertEquals(box(json.getAsJsonObject("bounding_volume").getAsJsonArray("model")),
                new AABB(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ()));
        assertTrue(summary.bounds().within(bounds, 1.0E-6));
        var imported = json.getAsJsonObject("imported");
        assertEquals(imported.get("obj_sha256").getAsString(), hash(bytes));
        assertEquals(imported.get("source_baked_vertices").getAsInt(), summary.budgetVertices());
        assertEquals(imported.get("source_positions").getAsInt(), summary.positions());
        var checked = ObjModelSet.check(List.of(f), source(Set.of())).models().get(f.id());
        assertEquals(1, checked.textures().size());
        assertEquals(imported.get("texture_sha256").getAsString(), hash(resource(checked.textures().keySet().iterator().next())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"chemical_reactor", "crystallizer", "cutting_machine", "lathe"})
    void eachModelResolvesReferencesAndFallsBackWithoutMissingno(String kind) throws Exception {
        var f = model(kind);
        var good = ObjModelSet.check(List.of(f), source(Set.of())).models().get(kind);
        var texture = good.textures().keySet().iterator().next();
        assertEquals(Map.of(texture, texture), good.textures());
        var missing = ObjModelSet.check(List.of(f), source(Set.of(texture))).models().get(kind);
        assertEquals(Map.of(texture, f.fallbackTexture()), missing.textures());
        var cached = new ObjModelCache.Entry<>(f, "checked-model", missing.textures());
        assertEquals(f.fallbackTexture(), cached.texture(texture));
        assertEquals(f.fallbackTexture(), cached.texture(ModIdentity.id("unresolved")));
        ObjModelCheck.checkPng(resource(f.fallbackTexture()), "explicit fallback");
        var library = ObjModelCheck.libraryLocation(f.model(), good.summary().materialLibrary());
        assertThrows(ObjModelException.class, () -> ObjModelSet.check(List.of(f), source(Set.of(library))));
        var absent = ObjModelSet.check(List.of(f), source(Set.of(f.model())));
        assertEquals(Set.of(kind), absent.absent());
        assertTrue(absent.models().isEmpty());
    }

    @Test void combinedK1K2SetRetainsTheExistingBudgets() throws Exception {
        var result = ObjModelSet.check(ObjModelFixture.CATALOG, source(Set.of()));
        assertTrue(result.absent().isEmpty());
        assertEquals(5, result.models().size());
        assertEquals(7_688, result.vertices());
        assertEquals(281_641, result.bytes());
        assertTrue(result.vertices() < ObjModelCheck.MAX_SET_VERTICES);
        assertTrue(result.bytes() < ObjModelCheck.MAX_SET_BYTES);
    }

    @ParameterizedTest
    @ValueSource(strings = {"chemical_reactor", "crystallizer", "cutting_machine", "lathe"})
    void allFacingsKeepPatternCollisionAndVisualVolumeInvariant(String kind) throws Exception {
        var f = model(kind); var json = frozen(kind);
        var unchanged = json.getAsJsonObject("unchanged");
        var generated = generatedPattern(kind);
        var committed = JsonParser.parseString(Files.readString(Path.of("src/generated/v1.8/resources/data/advancedrocketrycommunity/machine_patterns/" + kind + ".json")));
        assertEquals(committed, generated);
        assertEquals(unchanged.get("pattern_canonical_sha256").getAsString(), hash(committed.toString().getBytes(StandardCharsets.UTF_8)));
        var pattern = MultiblockPatternJsonCodec.decode(generated.toString().getBytes(StandardCharsets.UTF_8));
        assertEquals(unchanged.get("pattern").getAsString(), pattern.id().toString());
        var footprint = ClassicModelProjection.footprint(f.kind()).orElseThrow();
        assertEquals(new PatternPosition(pattern.size().x(), pattern.size().y(), pattern.size().z()),
                new PatternPosition(footprint.sizeX(), footprint.sizeY(), footprint.sizeZ()));
        assertEquals(pattern.controllerAnchor(), footprint.anchor());
        var lightCell = pattern.cells().get(pattern.controllerAnchor().add(new PatternPosition(0, f.lightCellAboveController(), 0)));
        assertTrue(lightCell == null || lightCell instanceof PatternMatcher.Air, "light sample is inside an occupied pattern cell");
        var size = unchanged.getAsJsonArray("pattern_size"); var anchor = unchanged.getAsJsonArray("controller_anchor");
        assertEquals(new PatternPosition(size.get(0).getAsInt(), size.get(1).getAsInt(), size.get(2).getAsInt()),
                new PatternPosition(footprint.sizeX(), footprint.sizeY(), footprint.sizeZ()));
        assertEquals(new PatternPosition(anchor.get(0).getAsInt(), anchor.get(1).getAsInt(), anchor.get(2).getAsInt()), footprint.anchor());
        assertEquals("full_block", unchanged.get("controller_collision").getAsString());
        var block = new ClassicControllerBlock(BlockBehaviour.Properties.of());
        var visible = visibleParts(ObjModelSet.check(List.of(f), source(Set.of())).models().get(kind).parserInput());
        var hull = box(json.getAsJsonObject("bounding_volume").getAsJsonArray("hull_model"));
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var rotation = PatternRotation.valueOf(json.getAsJsonObject("orientation").getAsJsonObject("pattern_rotation").get(facing.getName()).getAsString());
            var transform = new PatternTransform(rotation, false);
            var bounds = box(json.getAsJsonObject("bounding_volume").getAsJsonObject("controller_relative").getAsJsonArray(facing.getName()));
            assertEquals(bounds, ClassicModelProjection.bounds(footprint, transform, BlockPos.ZERO));
            var a = f.toController(facing, hull.minX, hull.minY, hull.minZ);
            var b = f.toController(facing, hull.maxX, hull.maxY, hull.maxZ);
            var hullBox = new AABB(a[0], a[1], a[2], b[0], b[1], b[2]);
            assertEquals(bounds.minX, hullBox.minX, 1.0E-6); assertEquals(bounds.maxX, hullBox.maxX, 1.0E-6);
            assertEquals(bounds.minZ, hullBox.minZ, 1.0E-6); assertEquals(bounds.maxZ, hullBox.maxZ, 1.0E-6);
            var pose = new PoseStack(); ClassicObjModelRenderer.place(pose, f, facing);
            assertEquals(1, pose.last().pose().determinant(), 1.0E-5);
            assertEquals(1, pose.last().normal().determinant(), 1.0E-5);
            for (var points : visible.values()) {
                for (var point : points) {
                    var p = pose.last().pose().transformPosition(new Vector3f((float)point[0], (float)point[1], (float)point[2]));
                    assertTrue(p.x >= bounds.minX - 1.0E-5 && p.x <= bounds.maxX + 1.0E-5
                            && p.y >= bounds.minY - 1.0E-5 && p.y <= bounds.maxY + 1.0E-5
                            && p.z >= bounds.minZ - 1.0E-5 && p.z <= bounds.maxZ + 1.0E-5, kind + " visible geometry left render bounds " + facing + ": " + p);
                    var expected = f.toController(facing, -point[0], point[1], point[2]);
                    assertEquals(expected[0], p.x, 1.0E-5); assertEquals(expected[1], p.y, 1.0E-5); assertEquals(expected[2], p.z, 1.0E-5);
                }
            }
            var state = block.defaultBlockState().setValue(ClassicControllerBlock.FACING, facing);
            assertFalse(Shapes.joinIsNotEmpty(Shapes.block(), state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty()), BooleanOp.NOT_SAME));
            assertEquals(RenderShape.MODEL, state.getRenderShape());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"chemical_reactor", "crystallizer", "cutting_machine", "lathe"})
    void staticIdlePartsHaveNoRecipeOnlyGeometry(String kind) throws Exception {
        var f = model(kind); var json = frozen(kind);
        byte[] raw = resource(f.model());
        byte[] idle = ObjModelIdleGeometry.prepare(f, raw);
        var sourceParts = visibleParts(raw); var idleParts = visibleParts(idle);
        var pose = json.getAsJsonObject("idle_geometry");
        if (pose.has("hidden")) {
            for (var name : pose.getAsJsonArray("hidden")) {
                assertTrue(sourceParts.containsKey(name.getAsString()));
                assertFalse(idleParts.containsKey(name.getAsString()));
            }
        } else { assertArrayEquals(raw, idle); }
        if (pose.has("shaft_translation")) {
            var translation = pose.getAsJsonArray("shaft_translation");
            var before = sourceParts.get("Shaft"); var after = idleParts.get("Shaft");
            assertEquals(before.size(), after.size());
            for (int i = 0; i < before.size(); i++) {
                for (int axis = 0; axis < 3; axis++) { assertEquals(before.get(i)[axis] + translation.get(axis).getAsDouble(), after.get(i)[axis], 1.0E-9); }
            }
        }
        assertEquals(ObjModelCheck.checkObj(raw, kind, ObjModelIdleGeometry.hiddenParts(f)).positions(), ObjModelCheck.checkObj(idle, kind).positions());
        assertArrayEquals(raw, resource(f.model()), "idle adaptation must not change committed resource bytes");
    }

    @Test void untexturedLatheRodIsOmittedButVisibleOrMalformedFacesStillFail() throws Exception {
        var f = model("lathe");
        byte[] raw = resource(f.model());
        var rejected = assertThrows(ObjModelException.class, () -> ObjModelCheck.checkObj(raw, "full source"));
        assertTrue(rejected.getMessage().contains("without a texture index"));
        var full = ObjModelCheck.checkObj(raw, "full source", ObjModelIdleGeometry.hiddenParts(f));
        assertEquals(1_232, full.budgetVertices(), "hidden faces still count against the original budgets");
        var idle = ObjModelCheck.checkObj(ObjModelIdleGeometry.prepare(f, raw), "strict parser input");
        assertEquals(full.faces() - 28, idle.faces());
        var visibleRod = new String(raw, StandardCharsets.UTF_8).replace("o Rod\n", "o VisibleRod\n").getBytes(StandardCharsets.UTF_8);
        assertThrows(ObjModelException.class, () -> ObjModelCheck.checkObj(visibleRod, "visible rod", ObjModelIdleGeometry.hiddenParts(f)));
        var malformed = new String(raw, StandardCharsets.UTF_8).replaceFirst("f [^\\n]+", "f 999999//1 1//1 2//1").getBytes(StandardCharsets.UTF_8);
        assertThrows(ObjModelException.class, () -> ObjModelCheck.checkObj(malformed, "invalid hidden index", ObjModelIdleGeometry.hiddenParts(f)));
    }
}
