package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.machine.tank.TankDataFiles;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Deterministic tank crafting/loot/models in the current v1.8 resource root. */
public final class V180TankData implements DataProvider {
    private final PackOutput output;
    private final boolean client;
    private final boolean server;
    public V180TankData(PackOutput output, boolean client, boolean server) {
        this.output = output; this.client = client; this.server = server;
    }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        var writes = new ArrayList<CompletableFuture<?>>();
        if (client) { TankDataFiles.client().forEach((path, json) ->
                writes.add(DataProvider.saveStable(cache, json, output.getOutputFolder().resolve(path)))); }
        if (server) { TankDataFiles.server().forEach((path, json) ->
                writes.add(DataProvider.saveStable(cache, json, output.getOutputFolder().resolve(path)))); }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }
    @Override public String getName() { return "v1.8 pressurized tank crafting and resources"; }
}
