package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Supported json_v1 marker; no automatic conversion or lineage claim. */
public record ClassicSignatureMarker(Optional<ResourceLocation> recipeId) {
    public ClassicSignatureMarker {
        Objects.requireNonNull(recipeId, "recipeId").ifPresent(ClassicValueChecks::id);
    }
}
