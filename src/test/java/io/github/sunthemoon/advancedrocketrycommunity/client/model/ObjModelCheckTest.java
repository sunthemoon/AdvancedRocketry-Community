package io.github.sunthemoon.advancedrocketrycommunity.client.model;

import static io.github.sunthemoon.advancedrocketrycommunity.client.model.ObjModelTestFiles.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraftforge.client.model.obj.ObjTokenizer;
import org.junit.jupiter.api.Test;

/** ADR-066 sections 7.1 and 7.3 on the client path, with no running client. */
class ObjModelCheckTest {
    @Test void acceptsAModelAndCountsItLikeTheImportCheck() throws Exception {
        var summary = ObjModelCheck.checkObj(bytes(OBJ), "widget.obj");
        assertEquals(8, summary.positions());
        assertEquals(4, summary.textureCoordinates());
        assertEquals(2, summary.normals());
        assertEquals(3, summary.faces());
        assertEquals(12, summary.bakedVertices());
        assertEquals(12, summary.budgetVertices());
        assertEquals(1, summary.ignoredLines());
        assertEquals("widget.mtl", summary.materialLibrary());
        assertEquals(Set.of("Body"), summary.materials());
        assertEquals(new ObjModelCheck.Bounds(0, 0, 0, 1, 1, 1), summary.bounds());
        var crlf = ObjModelCheck.checkObj(bytes(OBJ.replace("\n", "\r\n")), "widget.obj");
        assertEquals(summary, crlf);
        var weighted = ObjModelCheck.checkObj(bytes(OBJ.replace("v 1 1 1", "v 2 2 2 2")), "widget.obj");
        assertEquals(summary.bounds(), weighted.bounds());
    }

    @Test void refusesWhatForgeWouldRefuseDropOrLoadDifferently() {
        Map<String, String> cases = Map.ofEntries(
                Map.entry(OBJ.replace("s off", "vc 1 1 1"), "unsupported statement vc"),
                Map.entry(OBJ.replace("usemtl Body\n", ""), "face outside any material"),
                Map.entry(OBJ.replace("f 1/1/1 2/2/1 3/3/1", "f 1 2 3 4 5"), "3 or 4 corners"),
                Map.entry(OBJ.replace("f 1/1/1 2/2/1 3/3/1", "f 1 2"), "3 or 4 corners"),
                Map.entry(OBJ.replace("f 1/1/1 2/2/1 3/3/1", "f 1 2 9"), "outside the 8 defined"),
                Map.entry(OBJ.replace("f 1/1/1 2/2/1 3/3/1", "f 0 1 2"), "not a non-zero integer"),
                Map.entry(OBJ.replace("f 1/1/1 2/2/1 3/3/1", "f 1/1/3 2 3"), "normal index 3"),
                Map.entry(OBJ.replace("f 1/1/1 2/2/1 3/3/1", "f 1//1 2//1 3//1"), "without a texture index"),
                Map.entry(OBJ.replace("f 1/1/1 2/2/1 3/3/1", "f 1/1/1/1 2 3"), "malformed corner"),
                Map.entry(OBJ.replace("v 0 0 1", "v 0 0 x"), "not a number"),
                Map.entry(OBJ.replace("v 0 0 1", "v 0 0 1e999"), "not a finite number"),
                Map.entry(OBJ.replace("v 0 0 1", "v 0 0 NaN"), "not a number"),
                Map.entry(OBJ.replace("v 0 0 1", "v 0x1p3 0 1"), "not a number"),
                Map.entry(OBJ.replace("v 0 0 1", "v 0 0 1 0"), "weight is zero"),
                Map.entry(OBJ.replace("o Body", "o"), "exactly one name"),
                Map.entry(OBJ.replace("o Body", "mtllib other.mtl"), "a second material library"),
                Map.entry(OBJ.replace("s off", "mtllib other.mtl"), "a second material library"),
                Map.entry(OBJ.replace("mtllib widget.mtl\n", ""), "usemtl before any material library"),
                Map.entry(OBJ.replace("l 1 2", "l 1"), "at least two points"),
                Map.entry(OBJ.replace("v 0 0 1", "v 0 0 1 \\"), "line continuation"),
                Map.entry(OBJ.replace("o Body", "o B\u0007ody"), "control character"),
                Map.entry("\uFEFF" + OBJ, "byte-order mark"),
                Map.entry(OBJ.replaceAll("(?m)^f .*\\n", ""), "has no faces"));
        for (var entry : cases.entrySet()) {
            var failure = assertThrows(ObjModelException.class, () -> ObjModelCheck.checkObj(bytes(entry.getKey()), "widget.obj"),
                    entry.getValue());
            assertTrue(failure.getMessage().contains(entry.getValue()), failure.getMessage() + " should name " + entry.getValue());
        }
        assertThrows(ObjModelException.class, () -> ObjModelCheck.checkObj(new byte[] {'v', ' ', (byte) 0xC3, '(', '\n'}, "widget.obj"));
    }

    @Test void holdsTheBakedObjectBudgetAndStopsAtTheFirstStatementOverIt() throws Exception {
        var positions = new StringBuilder("mtllib widget.mtl\n");
        for (int i = 0; i < ObjModelCheck.MAX_BAKED_OBJECT_VERTICES; i++) { positions.append("v 0 0 0\n"); }
        String face = "usemtl Body\nf 1 2 3\n";
        assertEquals(ObjModelCheck.MAX_BAKED_OBJECT_VERTICES, ObjModelCheck.checkObj(bytes(positions + face), "big.obj").positions());
        var over = assertThrows(ObjModelException.class,
                () -> ObjModelCheck.checkObj(bytes(positions + "v 0 0 0\nv broken\n" + face), "big.obj"));
        assertTrue(over.getMessage().contains("more than 65536 vertices"), over.getMessage());
        int quads = ObjModelCheck.MAX_BAKED_OBJECT_VERTICES / ObjModelCheck.QUAD_CORNERS;
        assertEquals(ObjModelCheck.MAX_BAKED_OBJECT_VERTICES, ObjModelCheck.checkObj(bytes(quads(quads)), "quads.obj").budgetVertices());
        var baked = assertThrows(ObjModelException.class, () -> ObjModelCheck.checkObj(bytes(quads(quads + 1) + "f broken\n"), "quads.obj"));
        assertTrue(baked.getMessage().contains("more than 65536 baked vertices"), baked.getMessage());
        var huge = new byte[ObjModelCheck.MAX_SET_BYTES + 1];
        Arrays.fill(huge, (byte) '#');
        assertTrue(assertThrows(ObjModelException.class, () -> ObjModelCheck.checkObj(huge, "huge.obj")).getMessage().contains("budget"));
    }

    @Test void readsNoMoreThanOneByteBeyondTheLimit() throws Exception {
        int[] served = {0};
        InputStream endless = new InputStream() {
            @Override public int read() { served[0]++; return '#'; }
            @Override public int read(byte[] buffer, int offset, int length) {
                Arrays.fill(buffer, offset, offset + length, (byte) '#'); served[0] += length; return length;
            }
        };
        assertThrows(ObjModelException.class, () -> ObjModelCheck.readBounded(endless, 100_000, "endless.obj"));
        assertEquals(100_001, served[0]);
        assertArrayEquals(bytes(OBJ), ObjModelCheck.readBounded(new ByteArrayInputStream(bytes(OBJ)), bytes(OBJ).length, "widget.obj"));
    }

    @Test void referencesStayInsideTheModNamespace() throws Exception {
        for (String accepted : List.of("widget.mtl", "sub/widget.mtl", "advancedrocketrycommunity:models/widget.mtl")) {
            assertEquals(accepted, ObjModelCheck.checkReference(accepted, true, "test"));
        }
        assertEquals("advancedrocketrycommunity:models/widget", ObjModelCheck.checkReference("advancedrocketrycommunity:models/widget", false, "test"));
        for (String refused : List.of("", "/abs/widget.mtl", "C:\\widget.mtl", "C:/widget.mtl", "http://example.com/widget.mtl",
                "https://example.com/w", "file://widget.mtl", "#texture", "~/widget.mtl", "minecraft:block/stone",
                "../widget.mtl", "a/../widget.mtl", "a//widget.mtl", "./widget.mtl", "Widget.mtl", "a\\widget.mtl",
                "advancedrocketrycommunity:a:b", "advancedrocketrycommunity:")) {
            assertThrows(ObjModelException.class, () -> ObjModelCheck.checkReference(refused, true, "test"), refused);
        }
        assertThrows(ObjModelException.class, () -> ObjModelCheck.checkReference("models/widget", false, "test"));
        assertEquals("advancedrocketrycommunity:models/widget.mtl",
                ObjModelCheck.libraryLocation(ObjModelTestFiles.WIDGET.model(), "widget.mtl").toString());
        assertEquals("advancedrocketrycommunity:textures/models/widget.png",
                ObjModelCheck.texturePath("advancedrocketrycommunity:models/widget").toString());
    }

    @Test void checksMaterialLibraries() throws Exception {
        var summary = ObjModelCheck.checkMtl(bytes(MTL), "widget.mtl");
        assertEquals(Map.of("Body", Map.of("map_Kd", "advancedrocketrycommunity:models/widget")), summary.materials());
        Map<String, String> cases = Map.of(
                "Kd 1 1 1\n" + MTL, "before any newmtl",
                MTL + "newmtl Body\n", "defined twice",
                MTL + "map_Kd advancedrocketrycommunity:models/other\n", "given twice",
                MTL.replace("map_Kd advancedrocketrycommunity:models/widget", "map_Kd -clamp on advancedrocketrycommunity:models/widget"),
                "no options",
                MTL.replace("advancedrocketrycommunity:models/widget", "models/widget"), "no namespace",
                MTL.replace("advancedrocketrycommunity:models/widget", "minecraft:block/stone"), "external namespace",
                MTL + "bump advancedrocketrycommunity:models/widget\n", "unsupported statement",
                MTL + "illum two\n", "one integer",
                MTL.replace("Kd 1 1 1", "Kd 1 1 1 1 1"), "1 to 4 numbers");
        for (var entry : cases.entrySet()) {
            var failure = assertThrows(ObjModelException.class, () -> ObjModelCheck.checkMtl(bytes(entry.getKey()), "widget.mtl"));
            assertTrue(failure.getMessage().contains(entry.getValue()), failure.getMessage() + " should name " + entry.getValue());
        }
    }

    @Test void mirrorNegatesXAndReversesCornersExactlyAsForgeTokenizesIt() throws Exception {
        String source = "# note\nmtllib widget.mtl\nv 1.5 2 3\nv -0.25 0 1\nv +2 0 0\nv 0 1 0\nvn -1 0 0\nvt 0.5 0.5\nusemtl Body\n"
                + "f 1/1/1 2/1/1 3/1/1 4/1/1\nf 1 2 3\n";
        String expected = "# note\nmtllib widget.mtl\nv -1.5 2 3\nv 0.25 0 1\nv -2 0 0\nv -0 1 0\nvn 1 0 0\nvt 0.5 0.5\nusemtl Body\n"
                + "f 1/1/1 4/1/1 3/1/1 2/1/1\nf 1 3 2\n";
        byte[] mirrored = ObjModelCheck.mirrorX(bytes(source), "widget.obj");
        assertEquals(expected, new String(mirrored, StandardCharsets.UTF_8));
        var forge = new ArrayList<List<String>>();
        try (var tokenizer = new ObjTokenizer(new ByteArrayInputStream(mirrored))) {
            for (String[] line; (line = tokenizer.readAndSplitLine(true)) != null; ) { forge.add(List.of(line)); }
        }
        assertEquals(List.of("v", "-1.5", "2", "3"), forge.get(1));
        assertEquals(List.of("f", "1/1/1", "4/1/1", "3/1/1", "2/1/1"), forge.get(8));
        var summary = ObjModelCheck.checkObj(mirrored, "widget.obj");
        assertEquals(new ObjModelCheck.Bounds(-2, 0, 0, 0.25, 2, 3), summary.bounds());
    }

    @Test void mirroredFacesKeepWindingConsistentWithTheirNormals() throws Exception {
        String source = "mtllib widget.mtl\nv 0 0 0\nv 1 0 0\nv 0 1 0\nv 0 0 1\nv 2 0.5 0.25\n"
                + "vn 0 0 1\nvn 0 -1 0\nvn -1 0 0\nvn 0.1 0.2 0.97\nusemtl Body\n"
                + "f 1//1 2//1 3//1\nf 1//2 2//2 4//2\nf 1//3 4//3 3//3\nf 2//4 5//4 3//4\nf 1//1 2//1 5//1 3//1\n";
        for (byte[] data : List.of(bytes(source), ObjModelCheck.mirrorX(bytes(source), "widget.obj"))) {
            var positions = new ArrayList<double[]>();
            var normals = new ArrayList<double[]>();
            for (String line : new String(data, StandardCharsets.UTF_8).split("\n")) {
                String[] tokens = line.split(" ");
                switch (tokens[0]) {
                    case "v" -> positions.add(vector(tokens));
                    case "vn" -> normals.add(vector(tokens));
                    case "f" -> {
                        double[] a = positions.get(index(tokens[1], 0)), b = positions.get(index(tokens[2], 0)), c = positions.get(index(tokens[3], 0));
                        double[] normal = normals.get(index(tokens[1], 2));
                        double[] u = {b[0] - a[0], b[1] - a[1], b[2] - a[2]}, v = {c[0] - a[0], c[1] - a[1], c[2] - a[2]};
                        double[] cross = {u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]};
                        assertTrue(cross[0] * normal[0] + cross[1] * normal[1] + cross[2] * normal[2] > 0, line);
                    }
                    default -> { }
                }
            }
        }
    }

    @Test void texturesMustBeSmallDecodablePngImages() throws Exception {
        ObjModelCheck.checkPng(png(16, 16), "ok.png");
        ObjModelCheck.checkPng(png(ObjModelCheck.MAX_TEXTURE_SIDE, 1), "wide.png");
        for (byte[] refused : List.of(png(ObjModelCheck.MAX_TEXTURE_SIDE + 1, 1), bytes("not a png"), Arrays.copyOf(png(16, 16), 40),
                corrupt(png(16, 16)), new byte[0])) {
            assertThrows(ObjModelException.class, () -> ObjModelCheck.checkPng(refused, "bad.png"));
        }
    }

    private static byte[] corrupt(byte[] png) {
        byte[] copy = png.clone();
        for (int i = 41; i < copy.length - 12; i++) { copy[i] = (byte) (copy[i] ^ 0x5A); }
        return copy;
    }

    private static double[] vector(String[] tokens) {
        return new double[] {Double.parseDouble(tokens[1]), Double.parseDouble(tokens[2]), Double.parseDouble(tokens[3])};
    }

    private static int index(String corner, int part) { return Integer.parseInt(corner.split("/", -1)[part]) - 1; }
}
