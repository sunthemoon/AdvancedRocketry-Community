package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.resource.ResourceTableCodec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/** ADR-064: narrowly supersedes the gas table; no historical provider runs here. */
public final class V180FluidData implements DataProvider {
    private final PackOutput output;
    private final boolean client;
    private final boolean server;

    public V180FluidData(PackOutput output, boolean client, boolean server) {
        this.output = output;
        this.client = client;
        this.server = server;
    }

    public static JsonObject gasTable() {
        JsonObject table = new JsonObject();
        table.addProperty("schema_version", 1);
        table.addProperty("body", ModIdentity.id("gas_giant").toString());
        JsonArray products = new JsonArray();
        for (String gas : List.of("hydrogen", "nitrogen")) {
            JsonObject product = new JsonObject();
            product.addProperty("item", ModIdentity.id(gas + "_canister").toString());
            product.addProperty("amount_per_1000_ticks", 8);
            products.add(product);
        }
        table.add("products", products);
        return table;
    }

    @Override public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        if (server) {
            JsonObject table = gasTable();
            ResourceTableCodec.decodeGasTable(ModIdentity.id("gas_giant"),
                    table.toString().getBytes(StandardCharsets.UTF_8), id -> ForgeRegistries.ITEMS.containsKey(id)
                            && ForgeRegistries.ITEMS.getValue(id) != Items.AIR);
            save(cache, writes, PackOutput.Target.DATA_PACK, ResourceTableCodec.GAS_DIRECTORY, "gas_giant", table);
        }
        if (client) {
            for (String liquid : List.of("rocket_fuel", "enriched_lava")) {
                JsonObject particle = new JsonObject();
                particle.addProperty("particle", ModIdentity.id("block/fluid/" + liquid + "_still").toString());
                JsonObject model = new JsonObject();
                model.add("textures", particle);
                save(cache, writes, PackOutput.Target.RESOURCE_PACK, "models/block", liquid, model);
                JsonObject variant = new JsonObject();
                variant.addProperty("model", ModIdentity.id("block/" + liquid).toString());
                JsonObject variants = new JsonObject();
                variants.add("", variant);
                JsonObject state = new JsonObject();
                state.add("variants", variants);
                save(cache, writes, PackOutput.Target.RESOURCE_PACK, "blockstates", liquid, state);
            }
            for (String item : List.of("rocket_fuel_bucket", "enriched_lava_bucket", "nitrogen_canister")) {
                JsonObject model = new JsonObject();
                model.addProperty("parent", "minecraft:item/generated");
                JsonObject textures = new JsonObject();
                textures.addProperty("layer0", ModIdentity.id("item/" + item).toString());
                model.add("textures", textures);
                save(cache, writes, PackOutput.Target.RESOURCE_PACK, "models/item", item, model);
            }
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    private void save(CachedOutput cache, List<CompletableFuture<?>> writes, PackOutput.Target target,
                      String directory, String name, JsonObject json) {
        writes.add(DataProvider.saveStable(cache, json, output.createPathProvider(target, directory).json(ModIdentity.id(name))));
    }

    @Override public String getName() { return "v1.8 classic fluid data"; }
}
