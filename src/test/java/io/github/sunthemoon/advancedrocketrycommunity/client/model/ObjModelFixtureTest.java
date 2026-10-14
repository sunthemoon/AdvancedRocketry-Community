package io.github.sunthemoon.advancedrocketrycommunity.client.model;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180C16cMachineData;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicControllerBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicModelProjection;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternJsonCodec;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternMatcher;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternRotation;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternTransform;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

/**
 * The centrifuge model fixture freezes orientation, scale, rendering and bounding volume, and proves that the model
 * leaves collision and the multiblock match alone (ADR-066 section 7.1).
 */
class ObjModelFixtureTest {
    private static final ObjModelFixture CENTRIFUGE = ObjModelFixture.CENTRIFUGE;
    private static final Path PATTERN = Path.of("src/generated/v1.8/resources/data/advancedrocketrycommunity/machine_patterns/centrifuge.json");

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static JsonObject fixture() throws IOException {
        try (var input = ObjModelFixtureTest.class.getResourceAsStream("/obj-model-fixtures/centrifuge.json")) {
            assertNotNull(input, "missing frozen fixture");
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static MultiblockPatternDefinition pattern() {
        return MultiblockPatternJsonCodec.decode(V180C16cMachineData.pattern("centrifuge").toString().getBytes(StandardCharsets.UTF_8));
    }

    private static PatternTransform transform(JsonObject fixture, Direction facing) {
        return new PatternTransform(PatternRotation.valueOf(fixture.getAsJsonObject("orientation")
                .getAsJsonObject("pattern_rotation").get(facing.getName()).getAsString()), false);
    }

    private static AABB box(JsonArray values) {
        return new AABB(values.get(0).getAsDouble(), values.get(1).getAsDouble(), values.get(2).getAsDouble(),
                values.get(3).getAsDouble(), values.get(4).getAsDouble(), values.get(5).getAsDouble());
    }

    /** The source-model bounds placed on a controller at the origin. */
    private static AABB placed(Direction facing) {
        var bounds = CENTRIFUGE.modelBounds();
        double[] a = CENTRIFUGE.toController(facing, bounds.minX(), bounds.minY(), bounds.minZ());
        double[] b = CENTRIFUGE.toController(facing, bounds.maxX(), bounds.maxY(), bounds.maxZ());
        return new AABB(round(a[0]), round(a[1]), round(a[2]), round(b[0]), round(b[1]), round(b[2]));
    }

    private static double round(double value) { return Math.round(value * 1.0E6) / 1.0E6; }

    @Test void catalogMatchesTheFrozenFixture() throws IOException {
        var fixture = fixture();
        assertEquals(1, fixture.get("schema_version").getAsInt());
        assertEquals(fixture.get("id").getAsString(), CENTRIFUGE.id());
        assertEquals(fixture.get("kind").getAsString(), CENTRIFUGE.kind().toString());
        assertEquals(fixture.get("model").getAsString(), CENTRIFUGE.model().toString());
        var orientation = fixture.getAsJsonObject("orientation");
        assertEquals(orientation.get("mirror_x").getAsBoolean(), CENTRIFUGE.mirrorX());
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            assertEquals(orientation.getAsJsonObject("y_rotation_degrees").get(facing.getName()).getAsFloat(),
                    ObjModelFixture.yRotationDegrees(facing));
        }
        assertEquals(fixture.get("scale").getAsDouble(), CENTRIFUGE.scale());
        var offset = fixture.getAsJsonArray("offset_north");
        assertArrayEquals(new double[] {offset.get(0).getAsDouble(), offset.get(1).getAsDouble(), offset.get(2).getAsDouble()},
                new double[] {CENTRIFUGE.offsetX(), CENTRIFUGE.offsetY(), CENTRIFUGE.offsetZ()});
        var rendering = fixture.getAsJsonObject("rendering");
        assertEquals(rendering.get("render_type").getAsString(), CENTRIFUGE.renderType());
        assertEquals(rendering.get("flip_v").getAsBoolean(), CENTRIFUGE.flipV());
        assertEquals(rendering.get("shade_quads").getAsBoolean(), ObjModelFixture.SHADE_QUADS);
        assertEquals(rendering.get("emissive_ambient").getAsBoolean(), ObjModelFixture.EMISSIVE_AMBIENT);
        assertEquals(rendering.get("automatic_culling").getAsBoolean(), ObjModelFixture.AUTOMATIC_CULLING);
        assertEquals(rendering.get("light_cell_above_controller").getAsInt(), ObjModelFixture.LIGHT_CELL_ABOVE);
        assertEquals(fixture.get("fallback_texture").getAsString(), CENTRIFUGE.fallbackTexture().toString());
        var model = box(fixture.getAsJsonObject("bounding_volume").getAsJsonArray("model"));
        var bounds = CENTRIFUGE.modelBounds();
        assertEquals(model, new AABB(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ()));
        assertEquals(List.of(CENTRIFUGE), ObjModelFixture.CATALOG);
    }

    @Test void catalogLocationsAreCompiledResourcesInTheModNamespace() throws Exception {
        for (var entry : ObjModelFixture.CATALOG) {
            assertEquals(ObjModelCheck.NAMESPACE, entry.model().getNamespace());
            assertTrue(entry.model().getPath().startsWith("models/") && entry.model().getPath().endsWith(".obj"));
            ObjModelCheck.checkReference(entry.model().toString(), false, "catalog");
            ObjModelCheck.checkReference(entry.fallbackTexture().toString(), false, "catalog");
            assertTrue(Files.isRegularFile(Path.of("src/generated/v1.8/resources/assets", entry.fallbackTexture().getNamespace(),
                    entry.fallbackTexture().getPath())), "fallback texture is not a NEW generated texture: " + entry.fallbackTexture());
            assertTrue(ClassicModelProjection.projects(entry.kind()), "catalogued model without a formed projection");
        }
    }

    @Test void theModelCoversExactlyThePatternFootprintForEveryFacing() throws IOException {
        var fixture = fixture();
        var pattern = pattern();
        var footprint = ClassicModelProjection.footprint(CENTRIFUGE.kind()).orElseThrow();
        assertEquals(new PatternPosition(pattern.size().x(), pattern.size().y(), pattern.size().z()),
                new PatternPosition(footprint.sizeX(), footprint.sizeY(), footprint.sizeZ()));
        assertEquals(pattern.controllerAnchor(), footprint.anchor());
        var relative = fixture.getAsJsonObject("bounding_volume").getAsJsonObject("controller_relative");
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var transform = transform(fixture, facing);
            AABB cells = null;
            for (var local : pattern.cells().keySet()) {
                var world = transform.localToWorld(local, pattern.controllerAnchor(), new PatternPosition(0, 0, 0));
                var cell = new AABB(new BlockPos(world.x(), world.y(), world.z()));
                cells = cells == null ? cell : cells.minmax(cell);
            }
            assertEquals(box(relative.getAsJsonArray(facing.getName())), cells, facing + " pattern footprint");
            assertEquals(cells, placed(facing), facing + " model volume");
            assertEquals(cells, ClassicModelProjection.bounds(footprint, transform, BlockPos.ZERO), facing + " render volume");
        }
    }

    @Test void theDrumAxisSitsOnTheEndgameCasingColumnForEveryFacing() throws IOException {
        var fixture = fixture();
        var pattern = pattern();
        var axis = fixture.getAsJsonObject("orientation").getAsJsonArray("drum_axis_model_xz");
        var light = pattern.cells().get(pattern.controllerAnchor().add(new PatternPosition(0, ObjModelFixture.LIGHT_CELL_ABOVE, 0)));
        assertInstanceOf(PatternMatcher.Air.class, light, "the light cell must stay empty");
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var transform = transform(fixture, facing);
            double x = 0, z = 0;
            int casings = 0;
            for (var cell : pattern.cells().entrySet()) {
                if (cell.getKey().y() == pattern.controllerAnchor().y() && cell.getValue() instanceof PatternMatcher.ExactBlock block
                        && block.blockId().equals("advancedrocketrycommunity:endgame_casing")) {
                    var world = transform.localToWorld(cell.getKey(), pattern.controllerAnchor(), new PatternPosition(0, 0, 0));
                    x += world.x() + 0.5; z += world.z() + 0.5; casings++;
                }
            }
            assertEquals(4, casings);
            double[] drum = CENTRIFUGE.toController(facing, axis.get(0).getAsDouble(), 0, axis.get(1).getAsDouble());
            assertEquals(x / casings, drum[0], 1.0E-9, facing + " drum x");
            assertEquals(z / casings, drum[2], 1.0E-9, facing + " drum z");
        }
    }

    @Test void theRendererPlacesTheMirroredGeometryWithAPureRotation() {
        var bounds = CENTRIFUGE.modelBounds();
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var pose = new PoseStack();
            ClassicObjModelRenderer.place(pose, CENTRIFUGE, facing);
            for (double x : new double[] {bounds.minX(), bounds.maxX(), 0.25}) {
                for (double y : new double[] {bounds.minY(), bounds.maxY()}) {
                    for (double z : new double[] {bounds.minZ(), bounds.maxZ(), -0.75}) {
                        // The parser receives the mirrored geometry, so the pose sees the mirrored point.
                        var placed = pose.last().pose().transformPosition(new Vector3f((float) -x, (float) y, (float) z));
                        double[] expected = CENTRIFUGE.toController(facing, x, y, z);
                        assertEquals(expected[0], placed.x(), 1.0E-5);
                        assertEquals(expected[1], placed.y(), 1.0E-5);
                        assertEquals(expected[2], placed.z(), 1.0E-5);
                    }
                }
            }
            assertEquals(1, pose.last().pose().determinant(), 1.0E-5, "the render pose must not mirror or scale");
            assertEquals(1, pose.last().normal().determinant(), 1.0E-5, "the normal matrix must stay a rotation");
        }
    }

    @Test void theModelLeavesCollisionAndTheMultiblockMatchUnchanged() throws Exception {
        var unchanged = fixture().getAsJsonObject("unchanged");
        var generated = JsonParser.parseString(Files.readString(PATTERN, StandardCharsets.UTF_8));
        assertEquals(generated, V180C16cMachineData.pattern("centrifuge"), "the generator and the generated pattern differ");
        String canonical = generated.toString();
        assertEquals(unchanged.get("pattern_canonical_sha256").getAsString(),
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))));
        assertEquals(unchanged.get("pattern").getAsString(), pattern().id().toString());
        var size = unchanged.getAsJsonArray("pattern_size");
        assertEquals(new PatternPosition(size.get(0).getAsInt(), size.get(1).getAsInt(), size.get(2).getAsInt()),
                new PatternPosition(pattern().size().x(), pattern().size().y(), pattern().size().z()));
        var anchor = unchanged.getAsJsonArray("controller_anchor");
        assertEquals(new PatternPosition(anchor.get(0).getAsInt(), anchor.get(1).getAsInt(), anchor.get(2).getAsInt()),
                pattern().controllerAnchor());
        assertEquals("full_block", unchanged.get("controller_collision").getAsString().toLowerCase(Locale.ROOT));
        var block = new ClassicControllerBlock(BlockBehaviour.Properties.of());
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var state = block.defaultBlockState().setValue(ClassicControllerBlock.FACING, facing);
            assertFalse(Shapes.joinIsNotEmpty(Shapes.block(), state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO,
                    CollisionContext.empty()), BooleanOp.NOT_SAME), "the controller's collision is no longer one full block");
            assertEquals(RenderShape.MODEL, state.getRenderShape(), "the controller keeps its JSON block model");
        }
    }
}
