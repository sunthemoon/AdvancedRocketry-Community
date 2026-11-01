package io.github.sunthemoon.advancedrocketrycommunity.client.model;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicModelProjection;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Independent legacy transform oracle, checked source geometry and unchanged modern pattern. */
class EtcherObjModelTest {
    private static final ObjModelFixture FIXTURE = ObjModelFixture.PRECISION_LASER_ETCHER;
    private static final Path PATTERN = Path.of("src/generated/v1.8/resources/data/advancedrocketrycommunity/machine_patterns/precision_laser_etcher.json");
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static byte[] resource(ResourceLocation id) throws Exception {
        for (String root : List.of("src/imported/v1.8/resources/assets", "src/generated/v1.8/resources/assets")) {
            var path = Path.of(root, id.getNamespace(), id.getPath());
            if (Files.isRegularFile(path)) { return Files.readAllBytes(path); }
        }
        throw new AssertionError("Missing resource " + id);
    }

    private static ObjModelSet.Source source(Set<ResourceLocation> missing) {
        return (id, limit) -> {
            if (missing.contains(id)) { return Optional.empty(); }
            try {
                var bytes = resource(id); assertTrue(bytes.length <= limit); return Optional.of(bytes);
            } catch (Exception error) { throw new ObjModelException(error.toString()); }
        };
    }
    private static ObjModelSet.Model checked() throws ObjModelException { return ObjModelSet.check(List.of(FIXTURE), source(Set.of())).models().get(FIXTURE.id()); }
    private static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }

    @Test void sourceHashesMaterialsBudgetAndFixtureArePinned() throws Exception {
        var checked = checked(); var summary = checked.summary();
        assertEquals("b302204d0f8b4abbbf2912fd50a15252029a0e68483ecf911f05d141673d5176", sha(resource(FIXTURE.model())));
        assertEquals(200, summary.positions()); assertEquals(968, summary.budgetVertices());
        assertEquals(Set.of("None"), summary.materials());
        assertEquals("precision_laser_engraver.mtl", summary.materialLibrary());
        assertEquals(new ObjModelCheck.Bounds(-3,0,-3,0,2.25,0), FIXTURE.modelBounds());
        assertTrue(summary.bounds().within(FIXTURE.modelBounds(), 0));
        assertTrue(FIXTURE.modelBounds().within(summary.bounds(), 0));
        assertEquals("advancedrocketrycommunity:precision_laser_etcher", FIXTURE.kind().toString());
        assertEquals("precision_laser_etcher", FIXTURE.id());
        assertEquals("advancedrocketrycommunity:textures/block/precision_laser_etcher.png", FIXTURE.fallbackTexture().toString());
        assertTrue(FIXTURE.mirrorX()); assertTrue(FIXTURE.flipV()); assertEquals(1, FIXTURE.scale());
        assertEquals(-1, FIXTURE.offsetX()); assertEquals(0, FIXTURE.offsetY()); assertEquals(0, FIXTURE.offsetZ());
        assertEquals(270, FIXTURE.modelYRotationDegrees()); assertEquals(3, FIXTURE.lightCellAboveController());
        assertEquals(ObjModelFixture.ENTITY_CUTOUT, FIXTURE.renderType());
        assertEquals(Set.of(new ResourceLocation("advancedrocketrycommunity", "textures/models/precisionlaseretcher.png")), checked.textures().keySet());
        assertEquals(checked.textures().keySet(), new HashSet<>(checked.textures().values()));
        assertEquals(968, ObjModelCheck.checkObj(checked.parserInput(), "strict etcher").budgetVertices());
    }

    @Test void everyPositionInFourFacingsMatchesLegacyWidthReflectionAndFitsTheUnchangedPattern() throws Exception {
        var original = new String(resource(FIXTURE.model()), StandardCharsets.UTF_8).lines().filter(l -> l.startsWith("v ")).toList();
        var adapted = new String(checked().parserInput(), StandardCharsets.UTF_8).lines().filter(l -> l.startsWith("v ")).toList();
        var json = JsonParser.parseString(Files.readString(PATTERN));
        // Pinned independently of the model and fixture calculations.
        assertEquals("4565e8349b2c14462b75a8adf834ddc9e94bfb49ce1780ae42ad5b06d96927e8", sha(json.toString().getBytes(StandardCharsets.UTF_8)));
        var pattern = MultiblockPatternJsonCodec.decode(json.toString().getBytes(StandardCharsets.UTF_8));
        assertEquals(new PatternPosition(1,0,0), pattern.controllerAnchor());
        var light = pattern.cells().get(pattern.controllerAnchor().add(new PatternPosition(0,3,0)));
        assertTrue(light == null || light instanceof PatternMatcher.Air);
        assertEquals(200, original.size()); assertEquals(original.size(), adapted.size());
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var pose = new PoseStack(); ClassicObjModelRenderer.place(pose, FIXTURE, facing);
            assertEquals(1, pose.last().pose().determinant(), 1.0E-5);
            assertEquals(1, pose.last().normal().determinant(), 1.0E-5);
            var box = ClassicModelProjection.bounds(FIXTURE.kind(), facing, BlockPos.ZERO);
            for (int i = 0; i < original.size(); i++) {
                var p = point(original.get(i)); var q = point(adapted.get(i));
                var placed = pose.last().pose().transformPosition(new Vector3f((float)q[0],(float)q[1],(float)q[2]));
                // c5cd5af6 RendererPrecisionLaserEtcher: T(.5,0,.5) R(front) T(.5,0,1.5).
                double r = Math.toRadians((facing.getStepX() == 1 ? 0 : 180) + facing.getStepZ()*90);
                double x = Math.cos(r)*(p[0]+.5) + Math.sin(r)*(p[2]+1.5) + .5;
                double z = -Math.sin(r)*(p[0]+.5) + Math.cos(r)*(p[2]+1.5) + .5;
                if (facing.getAxis() == Direction.Axis.Z) { x=1-x; } else { z=1-z; }
                assertArrayEquals(new double[] {x,p[1],z}, new double[] {placed.x,placed.y,placed.z}, 1.0E-5, facing + " vertex " + i);
                assertArrayEquals(new double[] {x,p[1],z}, FIXTURE.toController(facing,p[0],p[1],p[2]), 1.0E-5);
                assertTrue(placed.x >= box.minX-1.0E-5 && placed.x <= box.maxX+1.0E-5
                        && placed.y >= box.minY-1.0E-5 && placed.y <= box.maxY+1.0E-5
                        && placed.z >= box.minZ-1.0E-5 && placed.z <= box.maxZ+1.0E-5);
            }
            for (var cell : pattern.cells().keySet()) {
                var p = ClassicModelProjection.transform(facing).localToWorld(cell, pattern.controllerAnchor(), new PatternPosition(0,0,0));
                assertEquals(box, box.minmax(new net.minecraft.world.phys.AABB(p.x(),p.y(),p.z(),p.x()+1,p.y()+1,p.z()+1)));
            }
        }
        assertEquals(Set.of("Laser", "Mount", "Hull"), parts(resource(FIXTURE.model())));
        assertEquals(parts(resource(FIXTURE.model())), parts(checked().parserInput()));
    }

    @Test void missingTextureUsesTheExplicitProjectFallbackAndMissingModelDrawsNothing() throws Exception {
        var texture = checked().textures().keySet().iterator().next();
        var fallback = ObjModelSet.check(List.of(FIXTURE), source(Set.of(texture))).models().get(FIXTURE.id());
        assertEquals(Set.of(FIXTURE.fallbackTexture()), new HashSet<>(fallback.textures().values()));
        assertEquals(Set.of(FIXTURE.id()), ObjModelSet.check(List.of(FIXTURE), source(Set.of(FIXTURE.model()))).absent());
        assertThrows(ObjModelException.class, () -> ObjModelSet.check(List.of(FIXTURE), source(Set.of(texture,FIXTURE.fallbackTexture()))));
    }

    private static double[] point(String line) {
        var tokens=line.split("\\s+"); return new double[] {Double.parseDouble(tokens[1]),Double.parseDouble(tokens[2]),Double.parseDouble(tokens[3])};
    }
    private static Set<String> parts(byte[] bytes) {
        var parts = new HashSet<String>(); String part="";
        for (String line : new String(bytes,StandardCharsets.UTF_8).split("\n")) {
            if (line.startsWith("o ") || line.startsWith("g ")) { part=line.substring(2).strip(); }
            if (line.startsWith("f ")) { parts.add(part); }
        }
        return parts;
    }
}
