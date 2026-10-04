package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.ClassicInventoryAdvancements;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Writes only the six v1.8 inventory tutorials; earlier resources remain inputs. */
public final class V180ClassicAdvancementData implements DataProvider {
    private final PackOutput output;

    public V180ClassicAdvancementData(PackOutput output) {
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        ClassicInventoryAdvancements.files().forEach((path, json) -> writes.add(
                DataProvider.saveStable(cache, json, output.getOutputFolder().resolve(path))));
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "v1.8 classic inventory tutorials";
    }
}
