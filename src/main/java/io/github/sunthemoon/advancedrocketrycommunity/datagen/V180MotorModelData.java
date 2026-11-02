package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Baked mono motor meshes shared by placed blocks, block items and assembled rockets (W249). */
public final class V180MotorModelData implements DataProvider {
    private static final String NS = ModIdentity.MOD_ID;
    private final PackOutput output;

    public V180MotorModelData(PackOutput output) { this.output = output; }

    public static JsonObject block(String id) {
        String legacy = switch (id) {
            case "rocket_motor" -> "rocketmotor";
            case "advanced_rocket_motor" -> "advrocketmotor";
            default -> throw new IllegalArgumentException("Not a mono motor: " + id);
        };
        String texture = id.equals("rocket_motor") ? "combustion" : "advcombustion";
        JsonObject model = json("{\"loader\":\"forge:obj\",\"flip_v\":false,\"automatic_culling\":false}");
        model.addProperty("model", NS + ":models/block/models/" + legacy + ".obj");
        JsonObject textures = new JsonObject();
        textures.addProperty("particle", NS + ":block/" + texture + "_classic");
        model.add("textures", textures);
        return model;
    }

    public static JsonObject item(String id) {
        // Validate the ID as for the block model; the item always inherits that same geometry.
        block(id);
        JsonObject model = json("""
                {"display": {
                  "gui": {"rotation":[30,225,0],"translation":[0,0,0],"scale":[0.625,0.625,0.625]},
                  "ground": {"rotation":[0,0,0],"translation":[0,3,0],"scale":[0.25,0.25,0.25]},
                  "fixed": {"rotation":[0,0,0],"translation":[0,0,0],"scale":[0.5,0.5,0.5]},
                  "thirdperson_righthand": {"rotation":[75,45,0],"translation":[0,2.5,0],"scale":[0.375,0.375,0.375]},
                  "thirdperson_lefthand": {"rotation":[75,45,0],"translation":[0,2.5,0],"scale":[0.375,0.375,0.375]},
                  "firstperson_righthand": {"rotation":[0,45,0],"translation":[0,0,0],"scale":[0.4,0.4,0.4]},
                  "firstperson_lefthand": {"rotation":[0,225,0],"translation":[0,0,0],"scale":[0.4,0.4,0.4]},
                  "head": {"rotation":[0,0,0],"translation":[0,0,0],"scale":[1,1,1]}
                }}
                """);
        model.addProperty("parent", NS + ":block/" + id);
        return model;
    }

    /** The advanced motor is emitted by its existing propulsion provider; only v0.5's basic models move here. */
    public static Map<String, JsonObject> files() {
        String root = "assets/" + NS + "/models/";
        return Map.of(root + "block/rocket_motor.json", block("rocket_motor"),
                root + "item/rocket_motor.json", item("rocket_motor"));
    }

    @Override public CompletableFuture<?> run(CachedOutput cache) {
        return CompletableFuture.allOf(files().entrySet().stream()
                .map(entry -> DataProvider.saveStable(cache, entry.getValue(), output.getOutputFolder().resolve(entry.getKey())))
                .toArray(CompletableFuture[]::new));
    }

    @Override public String getName() { return "v1.8 classic mono motor models"; }

    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
}
