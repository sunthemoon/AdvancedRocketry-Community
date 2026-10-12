package io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network;

import net.minecraft.server.level.ServerPlayer;

/**
 * A server menu that accepts {@link ClassicControlIntentPacket}s. The packet handler only routes an intent to the
 * sender's open menu with the same container ID and kind; the menu checks its holder, reach, permission, state,
 * range and loaded chunk before it acts.
 */
public interface ClassicControlTarget {
    ClassicControlKind classicControlKind();

    /** Main thread: one intent from {@code sender}, whose open menu this is. */
    void acceptClassicControl(ServerPlayer sender, ClassicControlIntentPacket intent);
}
