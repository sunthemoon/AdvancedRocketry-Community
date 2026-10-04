package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.classiccomponent.MotorDataFiles;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Registry-independent motor crafting, tags, loot, blockstate and model generation. */
public final class V180MotorData implements DataProvider {
    private final PackOutput output;
    private final boolean client;
    private final boolean server;

    public V180MotorData(PackOutput output, boolean client, boolean server) {
        this.output = output;
        this.client = client;
        this.server = server;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        if (client) {
            MotorDataFiles.client().forEach((path, json) ->
                    writes.add(DataProvider.saveStable(cache, json, output.getOutputFolder().resolve(path))));
        }
        if (server) {
            MotorDataFiles.server().forEach((path, json) ->
                    writes.add(DataProvider.saveStable(cache, json, output.getOutputFolder().resolve(path))));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override public String getName() { return "v1.8 motor crafting and resources"; }
}
