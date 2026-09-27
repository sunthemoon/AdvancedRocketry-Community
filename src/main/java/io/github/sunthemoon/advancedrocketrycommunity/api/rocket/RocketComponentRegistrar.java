package io.github.sunthemoon.advancedrocketrycommunity.api.rocket;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Owner-bound loading registration; no mutable registry or world access is exported. */
@FunctionalInterface
public interface RocketComponentRegistrar {
    /**
     * Registers one immutable definition for every state of the supplied known,
     * non-air blocks. Definition ID must belong to the receiving mod; block IDs
     * may be foreign. All IDs are <=255 characters. Duplicate/overlapping claims
     * reject atomically; the four built-in host components are reserved.
     * Limits: 256 definitions, 64 blocks per call, 1,024 blocks total.
     * Explicit definitions precede legacy role tags without overriding movement,
     * forbidden-block or BlockEntity policies. Loading-thread handles expire.
     */
    void register(ResourceLocation definitionId, Set<ResourceLocation> blocks, RocketComponentDefinition definition);
}
