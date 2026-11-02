package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.gson.JsonObject;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;

/** W258: one baked nuclear nozzle mesh; existing blockstate rotations provide its six facings. */
public final class V180NuclearMotorModelData {
    private static final String NS = ModIdentity.MOD_ID;

    private V180NuclearMotorModelData() { }

    public static JsonObject block() {
        JsonObject model = new JsonObject();
        model.addProperty("loader", "forge:obj");
        model.addProperty("model", NS + ":models/block/models/nuclearrocketmotor.obj");
        model.addProperty("flip_v", false);
        model.addProperty("automatic_culling", false);
        JsonObject textures = new JsonObject();
        textures.addProperty("particle", NS + ":block/nuclearengine");
        model.add("textures", textures);
        return model;
    }

    public static JsonObject item() {
        // Share P1's explicit camera presentation, not its geometry or legacy inventory mesh.
        JsonObject model = V180MotorModelData.item("rocket_motor");
        model.addProperty("parent", NS + ":block/nuclear_rocket_motor");
        return model;
    }
}
