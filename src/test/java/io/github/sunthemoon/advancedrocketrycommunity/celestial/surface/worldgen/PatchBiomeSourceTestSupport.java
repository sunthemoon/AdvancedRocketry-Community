package io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/** Builds a patch source over placeholder holders: {@link PatchBiomeSource#index} never reads a biome. */
final class PatchBiomeSourceTestSupport {
    private PatchBiomeSourceTestSupport() {
    }

    static PatchBiomeSource source(int biomes, int cellSize, long salt) {
        List<Holder<Biome>> holders = new ArrayList<>();
        for (int i = 0; i < biomes; i++) {
            holders.add(Holder.direct(null));
        }
        return new PatchBiomeSource(holders, cellSize, salt);
    }
}
