package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.gson.JsonObject;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Forge's server LanguageHook reads data-pack languages, not client asset languages. */
public final class V180RocketServerLanguageProvider implements DataProvider {
    private final PackOutput output;

    public V180RocketServerLanguageProvider(PackOutput output) {
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        var entries = new JsonObject();
        entries.addProperty("entity." + ModIdentity.MOD_ID + ".rocket", "Assembled Rocket");
        for (var code : RocketValidationCode.values()) {
            entries.addProperty(code.translationKey(), RocketLanguageProvider.english(code));
        }
        return DataProvider.saveStable(cache, entries, output.getOutputFolder(PackOutput.Target.DATA_PACK)
                .resolve(ModIdentity.MOD_ID + "_v180/lang/en_us.json"));
    }

    @Override
    public String getName() {
        return "v1.8 rocket server language";
    }
}
