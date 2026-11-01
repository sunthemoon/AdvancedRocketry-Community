package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.sunthemoon.advancedrocketrycommunity.client.model.ObjModelCheck;
import io.github.sunthemoon.advancedrocketrycommunity.client.model.ObjModelException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** Resource-only Forge OBJ walk for the packaged atlas test; no renderer or client singleton is constructed. */
final class PackagedObjAtlasReferences {
    @FunctionalInterface interface Source {
        byte[] read(ResourceLocation location) throws IOException;
    }

    private PackagedObjAtlasReferences() { }

    static void collect(JsonObject definition, String use, Source source, Map<String, List<String>> sprites,
            List<String> problems) throws IOException {
        if (definition.has("overrides")) {
            problems.add(use + " has overrides, which are not modelled by this test");
        }
        if (!definition.has("loader")) { return; }
        JsonElement loader = definition.get("loader");
        if (!loader.isJsonPrimitive() || !loader.getAsJsonPrimitive().isString()
                || !loader.getAsString().equals("forge:obj")) {
            problems.add(use + " has loader " + loader.toString().replace("\"", "") + ", which is not modelled by this test");
            return;
        }
        if (definition.has("mtl_override")) {
            problems.add(use + " has mtl_override, which is not modelled by this test");
            return;
        }
        JsonElement model = definition.get("model");
        if (model == null || !model.isJsonPrimitive() || !model.getAsJsonPrimitive().isString()) {
            problems.add(use + " has no OBJ model resource location");
            return;
        }
        try {
            String name = model.getAsString();
            ObjModelCheck.checkReference(name, false, use + "#model");
            ResourceLocation mesh = ResourceLocation.tryParse(name);
            if (mesh == null || !mesh.getPath().endsWith(".obj")) {
                problems.add(use + " names a non-OBJ model " + name);
                return;
            }
            byte[] objBytes = source.read(mesh);
            if (objBytes == null) { problems.add(use + " is missing packaged OBJ " + mesh); return; }
            var obj = ObjModelCheck.checkObj(objBytes, mesh.toString());
            if (obj.faces() == 0 || obj.materialLibrary() == null) {
                problems.add(mesh + " has no faces or mtllib");
                return;
            }
            ResourceLocation library = ObjModelCheck.libraryLocation(mesh, obj.materialLibrary());
            byte[] mtlBytes = source.read(library);
            if (mtlBytes == null) { problems.add(mesh + " is missing packaged MTL " + library); return; }
            var mtl = ObjModelCheck.checkMtl(mtlBytes, library.toString());
            for (String material : obj.materials()) {
                var maps = mtl.materials().get(material);
                if (maps == null) {
                    // Forge's ObjMaterialLibrary.getMaterial throws on a name the library does not define.
                    problems.add(mesh + " uses material " + material + ", which " + library + " does not define");
                } else if (!maps.containsKey("map_Kd")) {
                    problems.add(mesh + " material " + material + " has no map_Kd in " + library);
                }
            }
            // Even an unused material can become visible after a pack edit. Check every map, not only map_Kd.
            mtl.materials().forEach((material, maps) -> maps.forEach((map, sprite) ->
                    sprites.computeIfAbsent(sprite, key -> new ArrayList<>()).add(use + " -> " + mesh + " -> "
                            + library + "#" + material + "/" + map)));
        } catch (ObjModelException exception) {
            problems.add(use + ": " + exception.getMessage());
        }
    }
}
