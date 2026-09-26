package io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Owner-bound loading handle. Registration is synchronous, non-reentrant and atomic;
 * do not retain the handle or use it on another thread. IDs must use the receiving
 * mod's namespace (at most 255 characters); blocks must exist and may have any owner.
 * Duplicate IDs and overlapping block claims are errors. Limits: 256 providers,
 * 64 blocks per provider, 1,024 blocks total, 4,096 states per registration and 16,384
 * states total. Callback exceptions, null results and exceeded budgets reject the
 * complete registration without publishing partial entries.
 *
 * <p>Existing sealing tags, built-in doors and permeable tags take precedence.
 * Dynamic boundaries must be represented in BlockState and issue ordinary neighbor
 * notifications when their state or block changes. No new saved data is introduced;
 * removing the provider restores the host's fallback rules on the next startup.
 */
@FunctionalInterface
public interface AtmosphereBoundaryRegistrar {
    void register(ResourceLocation providerId, Set<ResourceLocation> blockIds,
                  AtmosphereBoundaryProvider provider);
}
