package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

import net.minecraft.server.MinecraftServer;

/**
 * Where a source refreshes its election disposition immediately before a potentially first action (W44 section 3.3,
 * step 4). Production uses {@link ClassicFirstEventDelivery#disposition}; the isolated A1 GameTests pass their detached
 * delivery service, so a shared election cannot decide their slot.
 */
@FunctionalInterface
public interface FirstEventDispositionSource {
    FirstEventDispositionSource WORLD = FirstEventCutSeams::disposition;

    FirstEventDisposition disposition(MinecraftServer server, FirstEventKind kind, FirstEventSourceView source,
                                      FirstEventOutbox ram);
}
