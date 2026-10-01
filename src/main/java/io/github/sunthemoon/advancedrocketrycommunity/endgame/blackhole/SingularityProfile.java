package io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * ADR-057 section 1: a body becomes a singularity through separate data. The profile applies only while its body
 * exists in the live catalog, is orbitable and is not landable.
 *
 * @param version the first 16 hex characters of the SHA-256 of the file's raw bytes
 */
public record SingularityProfile(ResourceLocation id, ResourceLocation body, int outputFePerTick,
                                 ResourceLocation fuelTable, String version) {
    public static final int MAX_OUTPUT = 2_048;

    public SingularityProfile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(fuelTable, "fuelTable");
        Objects.requireNonNull(version, "version");
        if (outputFePerTick < 1 || outputFePerTick > MAX_OUTPUT) {
            throw new IllegalArgumentException("A singularity outputs 1..2,048 FE per tick");
        }
    }
}
