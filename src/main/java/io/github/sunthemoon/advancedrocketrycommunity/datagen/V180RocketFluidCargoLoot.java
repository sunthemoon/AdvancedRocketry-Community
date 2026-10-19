package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoRole;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** C-A loot only. Each block attaches one bounded carrier; protected devices return no drops. */
public final class V180RocketFluidCargoLoot implements DataProvider {
    private final PackOutput output;
    public V180RocketFluidCargoLoot(PackOutput output) { this.output = output; }

    @Override public CompletableFuture<?> run(CachedOutput cache) {
        var writes = new ArrayList<CompletableFuture<?>>();
        for (RocketCargoRole role : RocketCargoRole.values()) {
            JsonObject loot = JsonParser.parseString("{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,"
                    + "\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"" + ModIdentity.id(role.id()) + "\"}]}]}").getAsJsonObject();
            writes.add(DataProvider.saveStable(cache, loot, output.getOutputFolder().resolve(
                    "data/" + ModIdentity.MOD_ID + "/loot_tables/blocks/" + role.id() + ".json")));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override public String getName() { return "v1.8 rocket fluid cargo device loot"; }
}
