package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.gson.JsonObject;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

public final class AtmosphereDamageTypeProvider implements DataProvider {
    private final Path outputPath;

    public AtmosphereDamageTypeProvider(PackOutput output) {
        outputPath = output.getOutputFolder(PackOutput.Target.DATA_PACK)
                .resolve(AdvancedRocketryCommunity.MOD_ID)
                .resolve("damage_type/vacuum.json");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        JsonObject json = new JsonObject();
        json.addProperty("message_id", "advancedrocketrycommunity.vacuum");
        json.addProperty("scaling", "never");
        json.addProperty("exhaustion", 0.0F);
        json.addProperty("effects", "hurt");
        return DataProvider.saveStable(output, json, outputPath);
    }

    @Override
    public String getName() {
        return "ARCE vacuum damage type";
    }
}
