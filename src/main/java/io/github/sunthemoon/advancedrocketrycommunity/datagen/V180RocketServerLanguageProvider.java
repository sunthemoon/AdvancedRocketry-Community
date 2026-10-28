package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.gson.JsonObject;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Rocket-only namespace visible to server discovery and Forge's redirected lang/ asset lookup. */
public final class V180RocketServerLanguageProvider implements DataProvider {
    private final PackOutput output;

    public V180RocketServerLanguageProvider(PackOutput output) {
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        var directory = ModIdentity.MOD_ID + "_v180_rocket/lang/";
        var english = translations(false);
        // The data copy exposes the namespace without making unrelated v1.8 labels server-visible.
        // PathPackResources redirects lang/ to assets; packs without that redirect can use the data copy.
        return CompletableFuture.allOf(
                DataProvider.saveStable(cache, english, output.getOutputFolder(PackOutput.Target.DATA_PACK)
                        .resolve(directory + "en_us.json")),
                DataProvider.saveStable(cache, english, output.getOutputFolder(PackOutput.Target.RESOURCE_PACK)
                        .resolve(directory + "en_us.json")),
                DataProvider.saveStable(cache, translations(true), output.getOutputFolder(PackOutput.Target.RESOURCE_PACK)
                        .resolve(directory + "zh_cn.json")));
    }

    private static JsonObject translations(boolean chinese) {
        var entries = new JsonObject();
        entries.addProperty("entity." + ModIdentity.MOD_ID + ".rocket", chinese ? "组装火箭" : "Assembled Rocket");
        for (var code : RocketValidationCode.values()) {
            entries.addProperty(code.translationKey(), chinese ? RocketLanguageProvider.chinese(code) : RocketLanguageProvider.english(code));
        }
        return entries;
    }

    @Override
    public String getName() {
        return "v1.8 rocket server language";
    }
}
