package io.github.sunthemoon.advancedrocketrycommunity.machine.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessInput;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKind;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceSnapshot;

/** Reconstructs item alternatives from an already validated retained snapshot, never from current tags. */
public final class RetainedRecipePlan {
    private RetainedRecipePlan() { }

    public static JsonElement ingredient(ProcessResourceSnapshot snapshot, String channel) {
        JsonArray result = new JsonArray();
        for (var key : snapshot.balances().keySet()) {
            if (key.kind() == ProcessResourceKind.ITEM && key.channel().equals(channel)) {
                JsonObject entry = new JsonObject(); entry.addProperty("item", key.resourceId()); result.add(entry);
                if (result.size() > ProcessInput.MAX_VARIANTS) {
                    throw new IllegalArgumentException("retained ingredient exceeds the variant bound");
                }
            }
        }
        if (result.isEmpty()) { throw new IllegalArgumentException("retained ingredient is missing"); }
        return result;
    }
}
