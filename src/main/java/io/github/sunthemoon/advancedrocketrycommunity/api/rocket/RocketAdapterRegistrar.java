package io.github.sunthemoon.advancedrocketrycommunity.api.rocket;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Owner-bound registration capability, valid only within its synchronous loading event. */
@FunctionalInterface
public interface RocketAdapterRegistrar {
    /**
     * Register an owner-namespaced ID, nonempty exact type set and positive data
     * version. Invalid/conflicting registrations fail loading. Do not retain
     * this capability or call it on another thread.
     */
    void register(ResourceLocation adapterId, Set<ResourceLocation> blockEntityTypes,
                  int payloadVersion, RocketBlockEntityAdapter adapter);
}
