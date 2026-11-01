package io.github.sunthemoon.advancedrocketrycommunity.client.elevator;

import io.github.sunthemoon.advancedrocketrycommunity.client.model.ObjModelCheck;
import io.github.sunthemoon.advancedrocketrycommunity.client.model.ObjModelException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The legacy {@code Capsule} group of the K4 {@code spaceelevator.obj} (Root decision R263), read from bytes that pass
 * the unchanged strict {@link ObjModelCheck#checkObj} first. V is flipped ({@code 1 - v}) as the legacy
 * {@code WavefrontObject} and the K4 fixture's {@code flipV} do. A triangle repeats its last corner, as Forge's OBJ
 * baker emits it, so every face is four vertices. No Minecraft client class is used here.
 *
 * This small reader is separate from Forge's hull baker because K4's idle geometry omits {@code Capsule}, its legacy
 * texture is not named by the OBJ's MTL, and the countdown tint needs a colour on every vertex. A second catalog
 * fixture would also change the pinned catalog totals; this group already counts in K4's geometry budget.
 */
final class CapsuleMesh {
    static final String GROUP = "Capsule";
    static final int MAX_FACES = 256;
    /** The model-space box the group must lie in; the legacy group spans (-1.5, -1, -1.5) to (1.5, 3, 2.375). */
    static final ObjModelCheck.Bounds MODEL_BOUNDS = new ObjModelCheck.Bounds(-2.5, -1, -2.5, 2.5, 3, 2.5);

    record Corner(float x, float y, float z, float u, float v, float nx, float ny, float nz) { }

    /** One drawn face: a quad, or a triangle whose {@code d} repeats {@code c}. */
    record Face(Corner a, Corner b, Corner c, Corner d) {
        List<Corner> corners() { return List.of(a, b, c, d); }
    }

    private final List<Face> faces;
    private final ObjModelCheck.Bounds bounds;

    private CapsuleMesh(List<Face> faces, ObjModelCheck.Bounds bounds) {
        this.faces = List.copyOf(faces);
        this.bounds = bounds;
    }

    List<Face> faces() { return faces; }

    int vertices() { return faces.size() * 4; }

    /** The model-space box of the drawn corners. */
    ObjModelCheck.Bounds bounds() { return bounds; }

    static CapsuleMesh read(byte[] obj, String label) throws ObjModelException {
        ObjModelCheck.checkObj(obj, label);
        var positions = new ArrayList<float[]>();
        var coordinates = new ArrayList<float[]>();
        var normals = new ArrayList<float[]>();
        var faces = new ArrayList<Face>();
        double[] box = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        String part = "";
        for (String line : new String(obj, StandardCharsets.UTF_8).split("\n")) {
            String[] tokens = line.strip().split("[\t ]+");
            switch (tokens[0]) {
                case "o", "g" -> part = tokens.length > 1 ? tokens[1] : part;
                case "v" -> {
                    float w = tokens.length > 4 ? Float.parseFloat(tokens[4]) : 1;
                    positions.add(new float[] {Float.parseFloat(tokens[1]) / w, Float.parseFloat(tokens[2]) / w,
                            Float.parseFloat(tokens[3]) / w});
                }
                case "vt" -> coordinates.add(new float[] {Float.parseFloat(tokens[1]),
                        tokens.length > 2 ? Float.parseFloat(tokens[2]) : 0});
                case "vn" -> normals.add(new float[] {Float.parseFloat(tokens[1]), Float.parseFloat(tokens[2]),
                        Float.parseFloat(tokens[3])});
                case "f" -> {
                    if (!part.equals(GROUP)) { break; }
                    if (faces.size() == MAX_FACES) { throw new ObjModelException(label + ": " + GROUP + " has more than " + MAX_FACES + " faces"); }
                    var corners = new Corner[4];
                    for (int i = 1; i < tokens.length; i++) {
                        String[] parts = tokens[i].split("/", -1);
                        if (parts.length != 3 || parts[1].isEmpty()) {
                            throw new ObjModelException(label + ": a " + GROUP + " corner needs a position, texture coordinate and normal");
                        }
                        float[] p = positions.get(index(parts[0], positions.size()));
                        float[] t = coordinates.get(index(parts[1], coordinates.size()));
                        float[] n = normals.get(index(parts[2], normals.size()));
                        double length = Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
                        if (!(length > 0)) { throw new ObjModelException(label + ": a " + GROUP + " normal has no length"); }
                        corners[i - 1] = new Corner(p[0], p[1], p[2], t[0], 1 - t[1],
                                (float) (n[0] / length), (float) (n[1] / length), (float) (n[2] / length));
                        for (int axis = 0; axis < 3; axis++) {
                            box[axis] = Math.min(box[axis], p[axis]);
                            box[axis + 3] = Math.max(box[axis + 3], p[axis]);
                        }
                    }
                    faces.add(new Face(corners[0], corners[1], corners[2], tokens.length == 4 ? corners[2] : corners[3]));
                }
                default -> { }
            }
        }
        if (faces.isEmpty()) { throw new ObjModelException(label + ": has no " + GROUP + " faces"); }
        var bounds = new ObjModelCheck.Bounds(box[0], box[1], box[2], box[3], box[4], box[5]);
        if (!bounds.within(MODEL_BOUNDS, 1.0E-4)) {
            throw new ObjModelException(label + ": " + GROUP + " positions " + bounds + " leave " + MODEL_BOUNDS);
        }
        return new CapsuleMesh(faces, bounds);
    }

    /** A checked, non-zero OBJ index: positive from the start, negative from the last one defined so far. */
    private static int index(String raw, int count) {
        int index = Integer.parseInt(raw);
        return index > 0 ? index - 1 : count + index;
    }
}
