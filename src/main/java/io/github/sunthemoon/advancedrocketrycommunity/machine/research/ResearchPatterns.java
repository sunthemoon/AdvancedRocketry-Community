package io.github.sunthemoon.advancedrocketrycommunity.machine.research;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.*;
import java.nio.charset.StandardCharsets;

/** Frozen audited layout facts; no upstream code/art is copied. */
public final class ResearchPatterns {
    private static final MultiblockPatternDefinition OBSERVATORY = decode(false), PROCESSOR = decode(true);
    public static MultiblockPatternDefinition definition(boolean processor) { return processor ? PROCESSOR : OBSERVATORY; }
    private static MultiblockPatternDefinition decode(boolean processor) {
        return MultiblockPatternJsonCodec.decode(json(processor).toString().getBytes(StandardCharsets.UTF_8));
    }
    public static JsonObject json(boolean processor) {
        String[][] layers = processor ? new String[][]{{"LCL", "LLL"}, {"PSS", "SSS"}}
                : new String[][]{{".....", ".SGS.", ".SSS.", ".SSS.", "....."},
                    {".....", ".SSS.", ".SGS.", ".SSS.", "....."},
                    {".SSS.", "SAAAS", "SAAAS", "SAGAS", ".SSS."},
                    {".*C*.", "*SSS*", "*SSS*", "*SSS*", ".***."},
                    {".***.", "*TTT*", "*TMT*", "*TTT*", ".***."}};
        var root = new JsonObject(); root.addProperty("id", ModIdentity.id(processor ? "astrobody_data_processor" : "observatory").toString());
        root.addProperty("schema_version", 1); root.add("size", position(processor ? 3 : 5, layers.length, processor ? 2 : 5));
        root.add("controller_anchor", position(processor ? 1 : 2, 1, 0)); var rotations = new JsonArray();
        for (int angle : new int[]{0, 90, 180, 270}) { rotations.add(angle); }
        root.add("allowed_rotations", rotations); root.addProperty("allow_mirror_local_x", false); var cells = new JsonArray();
        for (int layer = 0; layer < layers.length; layer++) {
            for (int row = 0; row < layers[layer].length; row++) {
                for (int col = 0; col < layers[layer][row].length(); col++) {
                    var cell = position(col, layers.length - layer - 1, row); var matcher = new JsonObject();
                    switch (layers[layer][row].charAt(col)) {
                        case 'C' -> matcher.addProperty("controller", true);
                        case 'A' -> matcher.addProperty("air", true);
                        case 'S', 'T' -> matcher.addProperty("block", ModIdentity.id("machine_casing").toString());
                        case 'G' -> matcher.addProperty("block", "minecraft:glass");
                        case 'L' -> matcher.addProperty("tag", "minecraft:slabs");
                        case 'M' -> matcher.addProperty("tag", ModIdentity.id("motors").toString());
                        case 'P' -> matcher.addProperty("port", "energy_input");
                        case '*' -> matcher.addProperty("tag", ModIdentity.id("observatory_shell_parts").toString());
                        case '.' -> matcher.addProperty("tag", ModIdentity.id("machine_clearance").toString());
                        default -> throw new IllegalStateException("Research layout symbol");
                    }
                    cell.add("matcher", matcher); cells.add(cell);
                }
            }
        }
        root.add("cells", cells); return root;
    }
    private static JsonObject position(int x, int y, int z) {
        var value = new JsonObject(); value.addProperty("x", x); value.addProperty("y", y); value.addProperty("z", z); return value;
    }
    private ResearchPatterns() { }
}
