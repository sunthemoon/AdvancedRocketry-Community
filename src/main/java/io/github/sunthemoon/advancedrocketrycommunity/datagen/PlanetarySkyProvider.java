package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.visual.SkyProfile;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.visual.SkyProfiles;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** New original profiles and narrowly scoped replacements for existing dimension effects. */
public final class PlanetarySkyProvider implements DataProvider {
    private final PackOutput output;
    private final boolean client;
    private final boolean server;

    public PlanetarySkyProvider(PackOutput output, boolean client, boolean server) {
        this.output = output;
        this.client = client;
        this.server = server;
    }

    @Override public CompletableFuture<?> run(CachedOutput cache) {
        var writes = new ArrayList<CompletableFuture<?>>();
        if (client) {
            var profiles = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, SkyProfile.DIRECTORY);
            SkyProfiles.builtins().forEach((id, profile) ->
                    writes.add(DataProvider.saveStable(cache, profile.encode(), profiles.json(id))));
        }
        if (server) {
            var types = output.createPathProvider(PackOutput.Target.DATA_PACK, "dimension_type");
            writes.add(DataProvider.saveStable(cache, FixedDimensionProvider.dimensionType(true, 0.1,
                    SkyProfiles.SURFACE_EFFECTS.toString()), types.json(ModIdentity.id("moon"))));
            writes.add(DataProvider.saveStable(cache, FixedDimensionProvider.dimensionType(false, 0,
                    SkyProfiles.SPACE_EFFECTS.toString()), types.json(ModIdentity.id("space"))));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override public String getName() { return "ARCE v1.4 planetary sky profiles and legacy effects"; }
}
