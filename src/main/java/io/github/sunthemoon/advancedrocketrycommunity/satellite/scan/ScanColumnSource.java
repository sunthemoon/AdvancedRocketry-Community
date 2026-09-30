package io.github.sunthemoon.advancedrocketrycommunity.satellite.scan;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** What a survey scan may read: already-loaded blocks only, never a chunk load (ADR-049 section 8). */
public interface ScanColumnSource {
    int UNLOADED = -1;
    int AIR = 0;
    int SOLID = 1;
    int ORE = 2;

    /** {@link #UNLOADED}, {@link #AIR}, {@link #SOLID} or {@link #ORE} for one block position. */
    int classify(int x, int y, int z);

    /** The biome at one position of a loaded chunk, if it has a registry ID. */
    Optional<ResourceLocation> biome(int x, int y, int z);
}
