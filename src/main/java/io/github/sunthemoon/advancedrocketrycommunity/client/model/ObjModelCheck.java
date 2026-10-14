package io.github.sunthemoon.advancedrocketrycommunity.client.model;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import net.minecraft.resources.ResourceLocation;

/**
 * The ADR-066 section 7.1 and 7.3 rules for OBJ, MTL and texture bytes, applied before Forge's parser sees them. They
 * are the rules of {@code tools/audit/obj_mtl_check.py}: what Forge's OBJ loader would refuse, drop or load
 * differently fails here first. Every file is size-checked while it is read, and counting stops at the first
 * statement over a budget, so an oversized model never reaches the parser.
 */
public final class ObjModelCheck {
    public static final String NAMESPACE = ModIdentity.MOD_ID;
    public static final int MAX_BAKED_OBJECT_VERTICES = 65_536;
    public static final int MAX_SET_VERTICES = 262_144;
    public static final int MAX_SET_BYTES = 16 * 1024 * 1024;
    /** Keeps a decoded texture at 4 MiB or less; the largest legacy model texture is 512 pixels a side. */
    public static final int MAX_TEXTURE_SIDE = 1_024;
    public static final int QUAD_CORNERS = 4;

    private static final Set<String> OBJ_STATEMENTS = Set.of("v", "vt", "vn", "f", "l", "s", "o", "g", "mtllib", "usemtl");
    private static final Set<String> COLOUR_STATEMENTS = Set.of("Ka", "Kd", "Ks", "Ke");
    private static final Set<String> SCALAR_STATEMENTS = Set.of("Ns", "Ni", "d", "Tr");
    private static final Set<String> INTEGER_STATEMENTS = Set.of("illum", "forge_TintIndex");
    private static final Set<String> MAP_STATEMENTS = Set.of("map_Ka", "map_Kd", "map_Ks");
    private static final Pattern NUMBER = Pattern.compile("[+-]?(?:[0-9]+\\.?[0-9]*|\\.[0-9]+)(?:[eE][+-]?[0-9]{1,3})?");
    private static final Pattern INDEX = Pattern.compile("-?[0-9]{1,9}");
    private static final Pattern RESOURCE_PART = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern RESOURCE_PATH = Pattern.compile("[a-z0-9_./-]+");
    private static final Pattern DRIVE = Pattern.compile("[A-Za-z]:[\\\\/].*");
    private static final Pattern SPLIT = Pattern.compile("[\t ]+");
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private ObjModelCheck() { }

    /** The axis-aligned box of an OBJ's positions, in model units. */
    public record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public boolean within(Bounds outer, double tolerance) {
            return minX >= outer.minX - tolerance && minY >= outer.minY - tolerance && minZ >= outer.minZ - tolerance
                    && maxX <= outer.maxX + tolerance && maxY <= outer.maxY + tolerance && maxZ <= outer.maxZ + tolerance;
        }
    }

    public record ObjSummary(int positions, int textureCoordinates, int normals, int faces, int ignoredLines,
            @Nullable String materialLibrary, Set<String> materials, @Nullable Bounds bounds) {
        public int bakedVertices() { return QUAD_CORNERS * faces; }
        /** The vertices a baked object counts against the budgets: its positions or its quad corners. */
        public int budgetVertices() { return Math.max(positions, bakedVertices()); }
    }

    /** Material name to its texture maps ({@code map_Ka}, {@code map_Kd}, {@code map_Ks}). */
    public record MtlSummary(Map<String, Map<String, String>> materials) { }

    /** Reads at most {@code limit} bytes and fails on the first byte beyond it, without buffering the rest. */
    public static byte[] readBounded(InputStream input, int limit, String label) throws IOException, ObjModelException {
        var output = new ByteArrayOutputStream(Math.min(limit, 8_192));
        byte[] buffer = new byte[8_192];
        int total = 0;
        for (int read; (read = input.read(buffer, 0, Math.min(buffer.length, limit + 1 - total))) > 0; ) {
            total += read;
            if (total > limit) { throw new ObjModelException(label + ": more than " + limit + " bytes"); }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    public static ObjSummary checkObj(byte[] data, String label) throws ObjModelException {
        int positions = 0, coordinates = 0, normals = 0, faces = 0, lines = 0;
        boolean emptyCoordinate = false;
        String library = null, current = null;
        var materials = new TreeSet<String>();
        double[] box = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        List<String> text = lines(data, label);
        for (int number = 1; number <= text.size(); number++) {
            String[] tokens = tokens(text.get(number - 1));
            if (tokens.length == 0) { continue; }
            String where = label + ":" + number, keyword = tokens[0];
            String[] arguments = Arrays.copyOfRange(tokens, 1, tokens.length);
            if (!OBJ_STATEMENTS.contains(keyword)) { throw new ObjModelException(where + ": unsupported statement " + keyword); }
            switch (keyword) {
                case "v" -> {
                    double[] values = numbers(arguments, 3, 4, where);
                    double w = values.length == 4 ? values[3] : 1;
                    if (w == 0) { throw new ObjModelException(where + ": vertex weight is zero"); }
                    if (++positions > MAX_BAKED_OBJECT_VERTICES) {
                        throw new ObjModelException(label + ": more than " + MAX_BAKED_OBJECT_VERTICES + " vertices in one baked object");
                    }
                    for (int axis = 0; axis < 3; axis++) {
                        box[axis] = Math.min(box[axis], values[axis] / w);
                        box[axis + 3] = Math.max(box[axis + 3], values[axis] / w);
                    }
                }
                case "vt" -> { numbers(arguments, 1, 3, where); coordinates++; }
                case "vn" -> { numbers(arguments, 3, 3, where); normals++; }
                case "f" -> {
                    if (arguments.length < 3 || arguments.length > QUAD_CORNERS) {
                        throw new ObjModelException(where + ": a face needs 3 or 4 corners, not " + arguments.length);
                    }
                    if (current == null) { throw new ObjModelException(where + ": face outside any material (Forge drops it)"); }
                    for (String corner : arguments) {
                        String[] parts = corner.split("/", -1);
                        if (parts.length > 3 || parts[0].isEmpty()) { throw new ObjModelException(where + ": malformed corner " + corner); }
                        index(parts[0], positions, "position", where);
                        if (parts.length > 1 && !parts[1].isEmpty()) { index(parts[1], coordinates, "texture coordinate", where); }
                        emptyCoordinate |= parts.length > 1 && parts[1].isEmpty();
                        if (parts.length > 2) {
                            if (parts[2].isEmpty()) { throw new ObjModelException(where + ": malformed corner " + corner); }
                            index(parts[2], normals, "normal", where);
                        }
                    }
                    if (QUAD_CORNERS * ++faces > MAX_BAKED_OBJECT_VERTICES) {
                        throw new ObjModelException(label + ": more than " + MAX_BAKED_OBJECT_VERTICES + " baked vertices in one object");
                    }
                }
                case "l" -> {
                    if (arguments.length < 2) { throw new ObjModelException(where + ": a line element needs at least two points"); }
                    for (String point : arguments) {
                        String[] parts = point.split("/", -1);
                        if (parts.length > 2 || parts[0].isEmpty()) { throw new ObjModelException(where + ": malformed line point " + point); }
                        index(parts[0], positions, "position", where);
                        if (parts.length > 1 && !parts[1].isEmpty()) { index(parts[1], coordinates, "texture coordinate", where); }
                    }
                    lines++;
                }
                case "s", "o", "g" -> {
                    if (arguments.length != 1) { throw new ObjModelException(where + ": " + keyword + " takes exactly one name"); }
                }
                case "mtllib" -> {
                    if (arguments.length != 1) { throw new ObjModelException(where + ": mtllib takes exactly one library"); }
                    if (library != null) { throw new ObjModelException(where + ": a second material library"); }
                    if (!materials.isEmpty()) { throw new ObjModelException(where + ": material library named after usemtl"); }
                    checkReference(arguments[0], true, where);
                    library = arguments[0];
                }
                default -> {
                    if (arguments.length == 0) { throw new ObjModelException(where + ": usemtl without a name"); }
                    if (library == null) { throw new ObjModelException(where + ": usemtl before any material library"); }
                    current = String.join(" ", arguments);
                    materials.add(current);
                }
            }
        }
        // Forge reads an empty texture index as -1 and fails to bake once the file has any texture coordinate.
        if (emptyCoordinate && coordinates > 0) {
            throw new ObjModelException(label + ": a corner without a texture index in a model with texture coordinates");
        }
        if (faces == 0) { throw new ObjModelException(label + ": has no faces"); }
        var bounds = positions == 0 ? null : new Bounds(box[0], box[1], box[2], box[3], box[4], box[5]);
        return new ObjSummary(positions, coordinates, normals, faces, lines, library, Collections.unmodifiableSet(materials), bounds);
    }

    public static MtlSummary checkMtl(byte[] data, String label) throws ObjModelException {
        var materials = new LinkedHashMap<String, Map<String, String>>();
        String current = null;
        List<String> text = lines(data, label);
        for (int number = 1; number <= text.size(); number++) {
            String[] tokens = tokens(text.get(number - 1));
            if (tokens.length == 0) { continue; }
            String where = label + ":" + number, keyword = tokens[0];
            String[] arguments = Arrays.copyOfRange(tokens, 1, tokens.length);
            if (keyword.equals("newmtl")) {
                if (arguments.length == 0) { throw new ObjModelException(where + ": newmtl without a name"); }
                current = String.join(" ", arguments);
                if (materials.putIfAbsent(current, new TreeMap<>()) != null) {
                    throw new ObjModelException(where + ": material " + current + " defined twice");
                }
                continue;
            }
            if (current == null) { throw new ObjModelException(where + ": " + keyword + " before any newmtl"); }
            if (COLOUR_STATEMENTS.contains(keyword)) {
                numbers(arguments, 1, 4, where);
            } else if (SCALAR_STATEMENTS.contains(keyword)) {
                numbers(arguments, 1, 1, where);
            } else if (INTEGER_STATEMENTS.contains(keyword)) {
                if (arguments.length != 1 || !INDEX.matcher(arguments[0]).matches()) {
                    throw new ObjModelException(where + ": expected one integer");
                }
            } else if (MAP_STATEMENTS.contains(keyword)) {
                if (arguments.length != 1) { throw new ObjModelException(where + ": " + keyword + " takes one texture and no options"); }
                checkReference(arguments[0], false, where);
                if (materials.get(current).putIfAbsent(keyword, arguments[0]) != null) {
                    throw new ObjModelException(where + ": " + keyword + " given twice for " + current);
                }
            } else {
                throw new ObjModelException(where + ": unsupported statement " + keyword);
            }
        }
        var frozen = new TreeMap<String, Map<String, String>>();
        materials.forEach((name, maps) -> frozen.put(name, Collections.unmodifiableMap(maps)));
        return new MtlSummary(Collections.unmodifiableMap(frozen));
    }

    /** A reference that resolves inside the mod's own namespace; {@code relative} allows a bare relative path. */
    public static String checkReference(String value, boolean relative, String where) throws ObjModelException {
        if (value.isEmpty() || value.contains("\\") || value.startsWith("/") || value.startsWith("#") || value.startsWith("~")
                || value.contains("://") || DRIVE.matcher(value).matches()) {
            throw new ObjModelException(where + ": " + value + " is an absolute, external or variable reference");
        }
        int colon = value.indexOf(':');
        if (colon < 0 && !relative) { throw new ObjModelException(where + ": " + value + " has no namespace, so it resolves outside the mod"); }
        String namespace = colon < 0 ? NAMESPACE : value.substring(0, colon);
        String path = colon < 0 ? value : value.substring(colon + 1);
        if (!RESOURCE_PART.matcher(namespace).matches() || path.contains(":") || !RESOURCE_PATH.matcher(path).matches()) {
            throw new ObjModelException(where + ": " + value + " is not a valid resource location");
        }
        if (!namespace.equals(NAMESPACE)) { throw new ObjModelException(where + ": " + value + " is in the external namespace " + namespace); }
        for (String part : path.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw new ObjModelException(where + ": " + value + " has an empty, current or parent directory segment");
            }
        }
        return value;
    }

    /** The location of an OBJ's {@code mtllib}, resolved as Forge's parser resolves it. */
    public static ResourceLocation libraryLocation(ResourceLocation model, String value) throws ObjModelException {
        String path = model.getPath();
        return location(value.contains(":") ? ResourceLocation.tryParse(value)
                : ResourceLocation.tryBuild(model.getNamespace(), path.substring(0, path.lastIndexOf('/') + 1) + value), value);
    }

    /** The PNG a texture map names, as {@code ObjModel.bakeRenderable} binds it. */
    public static ResourceLocation texturePath(String value) throws ObjModelException {
        var location = location(ResourceLocation.tryParse(value), value);
        return location(ResourceLocation.tryBuild(location.getNamespace(), "textures/" + location.getPath() + ".png"), value);
    }

    private static ResourceLocation location(@Nullable ResourceLocation location, String value) throws ObjModelException {
        if (location == null) { throw new ObjModelException(value + " is not a valid resource location"); }
        return location;
    }

    /**
     * Mirrors a checked OBJ in x: negates the x of every position and normal and reverses every face's corners, so the
     * mirrored faces keep a consistent winding and normals. Every other statement is kept.
     */
    public static byte[] mirrorX(byte[] checked, String label) throws ObjModelException {
        var output = new StringBuilder(checked.length + checked.length / 8);
        for (String line : lines(checked, label)) {
            String[] tokens = tokens(line);
            if (tokens.length > 1 && (tokens[0].equals("v") || tokens[0].equals("vn"))) {
                tokens[1] = tokens[1].startsWith("-") ? tokens[1].substring(1)
                        : "-" + (tokens[1].startsWith("+") ? tokens[1].substring(1) : tokens[1]);
                line = String.join(" ", tokens);
            } else if (tokens.length > 1 && tokens[0].equals("f")) {
                var corners = new ArrayList<String>(List.of(tokens).subList(1, tokens.length));
                Collections.reverse(corners.subList(1, corners.size()));
                line = "f " + String.join(" ", corners);
            }
            output.append(line).append('\n');
        }
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** A PNG that decodes to at most {@link #MAX_TEXTURE_SIDE} pixels a side; the header is read before decoding. */
    public static void checkPng(byte[] data, String label) throws ObjModelException {
        if (data.length < 33 || !Arrays.equals(Arrays.copyOf(data, 8), PNG_SIGNATURE)
                || ByteBuffer.wrap(data, 8, 4).getInt() != 13 || !new String(data, 12, 4, StandardCharsets.US_ASCII).equals("IHDR")) {
            throw new ObjModelException(label + ": not a PNG image");
        }
        int width = ByteBuffer.wrap(data, 16, 4).getInt(), height = ByteBuffer.wrap(data, 20, 4).getInt();
        if (width < 1 || height < 1 || width > MAX_TEXTURE_SIDE || height > MAX_TEXTURE_SIDE) {
            throw new ObjModelException(label + ": " + width + "x" + height + " is outside 1 to " + MAX_TEXTURE_SIDE + " pixels a side");
        }
        try {
            var image = ImageIO.read(new ByteArrayInputStream(data));
            if (image == null || image.getWidth() != width || image.getHeight() != height) {
                throw new ObjModelException(label + ": the PNG image does not decode");
            }
        } catch (IOException | RuntimeException failure) {
            throw new ObjModelException(label + ": the PNG image does not decode");
        }
    }

    private static List<String> lines(byte[] data, String label) throws ObjModelException {
        if (data.length > MAX_SET_BYTES) { throw new ObjModelException(label + ": " + data.length + " bytes exceed the model set budget"); }
        if (data.length >= 3 && data[0] == (byte) 0xEF && data[1] == (byte) 0xBB && data[2] == (byte) 0xBF) {
            throw new ObjModelException(label + ": starts with a byte-order mark");
        }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString();
        } catch (CharacterCodingException failure) {
            throw new ObjModelException(label + ": not UTF-8");
        }
        var result = new ArrayList<String>();
        String[] raw = text.split("\n", -1);
        for (int index = 0; index < raw.length; index++) {
            if (index == raw.length - 1 && raw[index].isEmpty()) { break; }
            String line = raw[index].endsWith("\r") ? raw[index].substring(0, raw[index].length() - 1) : raw[index];
            for (int i = 0; i < line.length(); i++) {
                char character = line.charAt(i);
                if (character < 32 && character != '\t' || character == 127) {
                    throw new ObjModelException(label + ":" + (index + 1) + ": contains a control character");
                }
            }
            if (line.endsWith("\\")) { throw new ObjModelException(label + ":" + (index + 1) + ": uses a line continuation"); }
            result.add(line);
        }
        return result;
    }

    private static String[] tokens(String line) {
        String stripped = line.replaceFirst("^[ \t]+", "");
        if (stripped.startsWith("#") || stripped.isEmpty()) { return new String[0]; }
        return SPLIT.split(stripped);
    }

    private static double[] numbers(String[] tokens, int low, int high, String where) throws ObjModelException {
        if (tokens.length < low || tokens.length > high) { throw new ObjModelException(where + ": expected " + low + " to " + high + " numbers"); }
        double[] values = new double[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            if (!NUMBER.matcher(tokens[i]).matches()) { throw new ObjModelException(where + ": not a number"); }
            values[i] = Double.parseDouble(tokens[i]);
            if (!Double.isFinite(values[i]) || !Float.isFinite((float) values[i])) { throw new ObjModelException(where + ": not a finite number"); }
        }
        return values;
    }

    private static void index(String raw, int count, String kind, String where) throws ObjModelException {
        if (!INDEX.matcher(raw).matches() || Integer.parseInt(raw) == 0) {
            throw new ObjModelException(where + ": " + kind + " index " + raw + " is not a non-zero integer");
        }
        int index = Integer.parseInt(raw);
        int resolved = index > 0 ? index - 1 : count + index;
        if (resolved < 0 || resolved >= count) {
            throw new ObjModelException(where + ": " + kind + " index " + index + " is outside the " + count + " defined so far");
        }
    }
}
